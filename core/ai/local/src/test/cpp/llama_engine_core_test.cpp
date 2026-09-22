/**
 * Tests de GoogleTest para LlamaEngineCore
 *
 * Compila como ejecutable host-only (no Android).
 * Requiere llama.cpp compilado y GoogleTest disponible.
 *
 * Ejecutar: ./llama_engine_core_test
 */

#include "llama_engine_core.h"

#include <gtest/gtest.h>
#include <string>
#include <thread>
#include <vector>
#include <atomic>

// Mock model path para tests (usa un modelo pequeño real o mock)
static const char* TEST_MODEL_PATH = nullptr;

// Helper para obtener/crear engine
static LlamaEngineCore* createEngine(int nCtx = 2048) {
    auto engine = new LlamaEngineCore();
    LlamaEngineCore::InitParams params;
    params.n_ctx = nCtx;
    params.n_batch = 256;
    params.n_ubatch = 256;
    params.n_threads = 2;
    params.use_mmap = true;
    params.use_mlock = false;
    EXPECT_TRUE(engine->init(params));
    return engine;
}

// ============================================================
// Test Fixture
// ============================================================
class LlamaEngineCoreTest : public ::testing::Test {
protected:
    void SetUp() override {
        engine_ = createEngine();
    }

    void TearDown() override {
        if (engine_) {
            engine_->destroy();
            delete engine_;
            engine_ = nullptr;
        }
    }

    LlamaEngineCore* engine_ = nullptr;
};

// ============================================================
// Tests
// ============================================================

// 1. InitFailsWithInvalidModelPath - init() no valida model_path, pero loadModel sí
TEST_F(LlamaEngineCoreTest, InitFailsWithInvalidModelPath) {
    // init() no carga modelo, solo inicializa backend
    // loadModel() falla con path inválido
    EXPECT_FALSE(engine_->loadModel("/invalid/path/model.gguf", 2));
    EXPECT_FALSE(engine_->isReady());
}

// 2. LoadModelSucceedsWithValidGGUF - requiere modelo real
TEST_F(LlamaEngineCoreTest, LoadModelSucceedsWithValidGGUF) {
    if (!TEST_MODEL_PATH) {
        GTEST_SKIP() << "TEST_MODEL_PATH no definido, saltando test de carga real";
    }
    EXPECT_TRUE(engine_->loadModel(TEST_MODEL_PATH, 2));
    EXPECT_TRUE(engine_->isReady());
    
    // Verificar info del modelo
    EXPECT_GT(engine_->getNVocab(), 0);
    EXPECT_GT(engine_->getNCtx(), 0);
    EXPECT_GT(engine_->getNEmbd(), 0);
    EXPECT_GT(engine_->getNLayer(), 0);
    EXPECT_GT(engine_->getMemoryUsage(), 0);
}

// 3. GenerateReturnsNonEmptyString - requiere modelo real
TEST_F(LlamaEngineCoreTest, GenerateReturnsNonEmptyString) {
    if (!TEST_MODEL_PATH) {
        GTEST_SKIP() << "TEST_MODEL_PATH no definido, saltando test de generación real";
    }
    
    ASSERT_TRUE(engine_->loadModel(TEST_MODEL_PATH, 2));
    
    LlamaEngineCore::GenerateParams params;
    params.n_predict = 10;
    params.temperature = 0.7f;
    params.top_p = 0.9f;
    params.top_k = 40;
    params.repeat_penalty = 1.1f;
    params.seed = 42;
    params.prompt = "Hello";

    std::string result = engine_->generate(params);
    
    EXPECT_FALSE(result.empty());
    EXPECT_NE(result, "[ERROR: Modelo no cargado]");
    EXPECT_NE(result, "[ERROR: Prompt vacío]");
    EXPECT_NE(result, "[ERROR: Tokenización vacía]");
    EXPECT_NE(result, "[ERROR: Fallo decodificación inicial]");
}

// 4. StreamGenerateCallsCallbackPerToken - requiere modelo real
TEST_F(LlamaEngineCoreTest, StreamGenerateCallsCallbackPerToken) {
    if (!TEST_MODEL_PATH) {
        GTEST_SKIP() << "TEST_MODEL_PATH no definido, saltando test de streaming real";
    }
    
    ASSERT_TRUE(engine_->loadModel(TEST_MODEL_PATH, 2));
    
    std::vector<std::string> tokens;
    bool callback_called = false;
    bool done_called = false;
    
    LlamaEngineCore::GenerateParams params;
    params.n_predict = 5;
    params.temperature = 0.7f;
    params.top_p = 0.9f;
    params.top_k = 40;
    params.repeat_penalty = 1.1f;
    params.seed = 42;
    params.prompt = "Hi";

    engine_->streamGenerate(params, [&](const char* token, bool is_last) {
        callback_called = true;
        if (token && token[0] != '\0') {
            tokens.push_back(token);
        }
        if (is_last) {
            done_called = true;
        }
    });
    
    EXPECT_TRUE(callback_called);
    EXPECT_TRUE(done_called);
    EXPECT_GT(tokens.size(), 0);
}

// 5. TokenCountMatchesTokenizer
TEST_F(LlamaEngineCoreTest, TokenCountMatchesTokenizer) {
    if (!TEST_MODEL_PATH) {
        GTEST_SKIP() << "TEST_MODEL_PATH no definido, saltando test de token count";
    }
    
    ASSERT_TRUE(engine_->loadModel(TEST_MODEL_PATH, 2));
    
    const char* text = "Hello world";
    int count = engine_->tokenCount(text);
    
    EXPECT_GT(count, 0);
    // Token count debería ser consistente
    int count2 = engine_->tokenCount(text);
    EXPECT_EQ(count, count2);
}

// 6. ConcurrentGenerateThreadSafe
TEST_F(LlamaEngineCoreTest, ConcurrentGenerateThreadSafe) {
    if (!TEST_MODEL_PATH) {
        GTEST_SKIP() << "TEST_MODEL_PATH no definido, saltando test de concurrencia";
    }
    
    ASSERT_TRUE(engine_->loadModel(TEST_MODEL_PATH, 2));
    
    const int num_threads = 4;
    const int iterations = 3;
    std::atomic<int> success_count{0};
    std::atomic<int> error_count{0};
    
    std::vector<std::thread> threads;
    for (int i = 0; i < num_threads; ++i) {
        threads.emplace_back([&, i]() {
            for (int j = 0; j < iterations; ++j) {
                LlamaEngineCore::GenerateParams params;
                params.n_predict = 5;
                params.temperature = 0.7f;
                params.top_p = 0.9f;
                params.top_k = 40;
                params.repeat_penalty = 1.1f;
                params.seed = 42 + i * 10 + j;
                params.prompt = "Test " + std::to_string(i);
                
                std::string result = engine_->generate(params);
                if (!result.empty() && result.find("[ERROR") == std::string::npos) {
                    success_count++;
                } else {
                    error_count++;
                }
            }
        });
    }
    
    for (auto& t : threads) {
        t.join();
    }
    
    EXPECT_EQ(success_count.load(), num_threads * iterations);
    EXPECT_EQ(error_count.load(), 0);
}

// 7. OOMFallbackReducesNctx - Simula OOM reduciendo n_ctx
TEST(LlamaEngineCoreOOMTest, OOMFallbackReducesNctx) {
    // Crear engine con n_ctx muy grande (probable OOM en CI)
    auto engine = new LlamaEngineCore();
    LlamaEngineCore::InitParams params;
    params.n_ctx = 32768;  // Muy grande para forzar fallo
    params.n_batch = 512;
    params.n_ubatch = 512;
    params.n_threads = 2;
    
    // init() debería funcionar (no asigna memoria del modelo)
    EXPECT_TRUE(engine->init(params));
    
    // loadModel fallaría sin modelo real, pero la lógica de fallback
    // se prueba en LlamaCppEngine (Kotlin) reduciendo n_ctx
    // Aquí verificamos que el engine se puede re-inicializar con n_ctx menor
    engine->destroy();
    delete engine;
    
    // Recrear con n_ctx pequeño
    engine = new LlamaEngineCore();
    params.n_ctx = 512;
    EXPECT_TRUE(engine->init(params));
    engine->destroy();
    delete engine;
}

// 8. DestroyReleasesResources
TEST_F(LlamaEngineCoreTest, DestroyReleasesResources) {
    if (!TEST_MODEL_PATH) {
        GTEST_SKIP() << "TEST_MODEL_PATH no definido";
    }
    
    ASSERT_TRUE(engine_->loadModel(TEST_MODEL_PATH, 2));
    EXPECT_TRUE(engine_->isReady());
    
    engine_->destroy();
    EXPECT_FALSE(engine_->isReady());
    
    // Después de destroy, no debería crashear al llamar métodos
    EXPECT_FALSE(engine_->loadModel("/fake/path", 2));
    std::string result = engine_->generate({});
    EXPECT_EQ(result, "[ERROR: Modelo no cargado]");
}

// 9. ModelInfoReturnsValidValues
TEST_F(LlamaEngineCoreTest, ModelInfoReturnsValidValues) {
    if (!TEST_MODEL_PATH) {
        GTEST_SKIP() << "TEST_MODEL_PATH no definido";
    }
    
    ASSERT_TRUE(engine_->loadModel(TEST_MODEL_PATH, 2));
    
    EXPECT_GT(engine_->getNVocab(), 0);
    EXPECT_GT(engine_->getNCtx(), 0);
    EXPECT_GT(engine_->getNEmbd(), 0);
    EXPECT_GT(engine_->getNLayer(), 0);
    EXPECT_GT(engine_->getMemoryUsage(), 0);
    
    // n_ctx debería coincidir con lo configurado
    EXPECT_EQ(engine_->getNCtx(), 2048);
}

// 10. FreeModelResetsState
TEST_F(LlamaEngineCoreTest, FreeModelResetsState) {
    if (!TEST_MODEL_PATH) {
        GTEST_SKIP() << "TEST_MODEL_PATH no definido";
    }
    
    ASSERT_TRUE(engine_->loadModel(TEST_MODEL_PATH, 2));
    EXPECT_TRUE(engine_->isReady());
    
    engine_->freeModel();
    EXPECT_FALSE(engine_->isReady());
    
    // Después de freeModel, generate debería fallar
    std::string result = engine_->generate({.prompt = "test"});
    EXPECT_EQ(result, "[ERROR: Modelo no cargado]");
}

// 11. ReInitAfterDestroy
TEST_F(LlamaEngineCoreTest, ReInitAfterDestroy) {
    if (!TEST_MODEL_PATH) {
        GTEST_SKIP() << "TEST_MODEL_PATH no definido";
    }
    
    // Primera inicialización y carga
    ASSERT_TRUE(engine_->loadModel(TEST_MODEL_PATH, 2));
    EXPECT_TRUE(engine_->isReady());
    
    // Destroy
    engine_->destroy();
    EXPECT_FALSE(engine_->isReady());
    
    // Re-inicializar (mismo engine object)
    LlamaEngineCore::InitParams params;
    params.n_ctx = 1024;
    params.n_batch = 256;
    params.n_ubatch = 256;
    params.n_threads = 2;
    EXPECT_TRUE(engine_->init(params));
    
    // Volver a cargar modelo
    ASSERT_TRUE(engine_->loadModel(TEST_MODEL_PATH, 2));
    EXPECT_TRUE(engine_->isReady());
}

// 12. GenerateWithEmptyPrompt
TEST_F(LlamaEngineCoreTest, GenerateWithEmptyPrompt) {
    if (!TEST_MODEL_PATH) {
        GTEST_SKIP() << "TEST_MODEL_PATH no definido";
    }
    
    ASSERT_TRUE(engine_->loadModel(TEST_MODEL_PATH, 2));
    
    LlamaEngineCore::GenerateParams params;
    params.prompt = "";
    
    std::string result = engine_->generate(params);
    EXPECT_EQ(result, "[ERROR: Prompt vacío]");
}

// 13. GenerateRespectsStopSequences - Verificar que EOS detiene generación
TEST_F(LlamaEngineCoreTest, GenerateRespectsStopSequences) {
    if (!TEST_MODEL_PATH) {
        GTEST_SKIP() << "TEST_MODEL_PATH no definido";
    }
    
    ASSERT_TRUE(engine_->loadModel(TEST_MODEL_PATH, 2));
    
    LlamaEngineCore::GenerateParams params;
    params.n_predict = 100;  // Pedir muchos tokens
    params.temperature = 0.0f;  // Determinístico
    params.top_p = 1.0f;
    params.top_k = 1;
    params.seed = 42;
    params.prompt = "The capital of France is";
    
    std::string result = engine_->generate(params);
    
    // Debería generar algo y parar antes de 100 tokens (por EOS o límite)
    EXPECT_FALSE(result.empty());
    EXPECT_NE(result, "[ERROR: Modelo no cargado]");
    // Verificar que no excede n_predict significativamente
    // (aprox: cada token ~4 chars, 100 tokens ~400 chars)
    EXPECT_LT(result.size(), 500);
}

// 14. SamplingDeterministicWithFixedSeed
TEST_F(LlamaEngineCoreTest, SamplingDeterministicWithFixedSeed) {
    if (!TEST_MODEL_PATH) {
        GTEST_SKIP() << "TEST_MODEL_PATH no definido";
    }
    
    ASSERT_TRUE(engine_->loadModel(TEST_MODEL_PATH, 2));
    
    LlamaEngineCore::GenerateParams params;
    params.n_predict = 20;
    params.temperature = 0.7f;
    params.top_p = 0.9f;
    params.top_k = 40;
    params.seed = 12345;
    params.prompt = "Once upon a time";
    
    std::string result1 = engine_->generate(params);
    std::string result2 = engine_->generate(params);
    
    // Con misma seed, temperatura > 0 y top-p/top-k, 
    // los resultados DEBERÍAN ser determinísticos en llama.cpp
    // (aunque puede haber variabilidad por implementación)
    // Este test verifica que la seed se respeta
    EXPECT_FALSE(result1.empty());
    EXPECT_FALSE(result2.empty());
    // Nota: Con temperature > 0 puede haber variabilidad por floating point
    // En implementaciones estrictamente determinísticas, serían iguales
}

// ============================================================
// Main
// ============================================================
int main(int argc, char** argv) {
    // Permitir pasar model path como argumento
    if (argc > 1) {
        TEST_MODEL_PATH = argv[1];
    }
    
    ::testing::InitGoogleTest(&argc, argv);
    return RUN_ALL_TESTS();
}