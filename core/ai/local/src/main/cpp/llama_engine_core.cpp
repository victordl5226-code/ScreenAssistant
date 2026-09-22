/**
 * LlamaEngineCore - Lógica pura de inferencia llama.cpp
 *
 * Implementación separada del JNI para testabilidad en host.
 * Usa la API pública de llama.cpp (commit reciente, API v2+).
 *
 * Thread-safety: Todos los métodos públicos usan std::lock_guard.
 * OOM fallback: Manejado por el llamador (reduce n_ctx y reintenta).
 */

#include "llama_engine_core.h"

#include <llama.h>
#include <common/common.h>
#include <vector>
#include <algorithm>
#include <stdexcept>

LlamaEngineCore::LlamaEngineCore() = default;

LlamaEngineCore::~LlamaEngineCore() {
    destroy();
}

bool LlamaEngineCore::init(const InitParams& params) {
    std::lock_guard<std::mutex> lock(mtx_);

    if (ready_) {
        return true;
    }

    n_ctx_ = params.n_ctx;

    // Inicializar backend llama.cpp (una vez por proceso)
    static std::once_flag llama_init_flag;
    std::call_once(llama_init_flag, []() {
        llama_backend_init();
    });

    ready_ = true;
    return true;
}

bool LlamaEngineCore::loadModel(const char* modelPath, int nThreads) {
    std::lock_guard<std::mutex> lock(mtx_);

    if (!ready_) {
        return false;
    }

    if (!modelPath) {
        return false;
    }

    // Liberar modelo anterior si existe
    freeModel();

    // Parámetros del modelo (nueva API)
    llama_model_params model_params = llama_model_default_params();
    // J.A.R.V.I.S. v3.8: Descargar capas a la GPU (Vulkan) para máxima fluidez
    model_params.n_gpu_layers = 32;
    model_params.vocab_only = false;
    model_params.check_tensors = true;

    // Cargar modelo (nueva API)
    model_ = llama_model_load_from_file(modelPath, model_params);
    if (!model_) {
        return false;
    }

    // Parámetros del contexto
    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = static_cast<uint32_t>(n_ctx_);
    ctx_params.n_batch = 512;
    ctx_params.n_ubatch = 512;
    ctx_params.n_threads = (nThreads > 0) ? nThreads : 4;
    ctx_params.n_threads_batch = ctx_params.n_threads;

    // Crear contexto (nueva API)
    ctx_ = llama_init_from_model(model_, ctx_params);
    if (!ctx_) {
        llama_model_free(model_);
        model_ = nullptr;
        return false;
    }

    return true;
}

std::string LlamaEngineCore::generate(const GenerateParams& params) {
    std::lock_guard<std::mutex> lock(mtx_);

    if (!ready_ || !ctx_ || !model_) {
        return "[ERROR: Modelo no cargado]";
    }

    if (params.prompt.empty()) {
        return "[ERROR: Prompt vacío]";
    }

    std::string result;

    // Tokenizar prompt
    std::vector<llama_token> tokens = common_tokenize(ctx_, params.prompt.c_str(), true, true);
    if (tokens.empty()) {
        return "[ERROR: Tokenización vacía]";
    }

    // Verificar espacio en contexto
    int n_ctx = llama_n_ctx(ctx_);
    int n_tokens = static_cast<int>(tokens.size());
    if (n_tokens >= n_ctx - 4) {
        tokens.resize(n_ctx - 4);
        n_tokens = static_cast<int>(tokens.size());
    }

    // Preparar batch inicial
    llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);
    if (llama_decode(ctx_, batch) != 0) {
        return "[ERROR: Fallo decodificación inicial]";
    }

    // Obtener vocabulario del modelo
    const struct llama_vocab* vocab = llama_model_get_vocab(model_);
    if (!vocab) {
        return "[ERROR: No se pudo obtener vocabulario]";
    }

    // Configurar sampler chain
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    sampler_ = llama_sampler_chain_init(sparams);
    if (!sampler_) {
        return "[ERROR: Fallo al crear sampler]";
    }

    // Agregar samplers en orden: top_k -> top_p -> temp -> penalties -> dist
    llama_sampler_chain_add(sampler_, llama_sampler_init_top_k(params.top_k));
    
    // top_p requiere min_keep (usar 1 como mínimo)
    llama_sampler_chain_add(sampler_, llama_sampler_init_top_p(params.top_p, 1));
    
    llama_sampler_chain_add(sampler_, llama_sampler_init_temp(params.temperature));
    
    // Penalty por repetición (nueva API: n_vocab, penalty_last_n, penalty_repeat, penalty_freq, penalty_present)
    if (params.repeat_penalty > 1.0f) {
        int32_t n_vocab = llama_vocab_n_tokens(vocab);
        llama_sampler_chain_add(sampler_, llama_sampler_init_penalties(
            n_vocab, 64, params.repeat_penalty, 0.0f, 0.0f));
    }

    // Sampler de distribución (seed)
    uint32_t seed = (params.seed == -1) ? LLAMA_DEFAULT_SEED : static_cast<uint32_t>(params.seed);
    llama_sampler_chain_add(sampler_, llama_sampler_init_dist(seed));

    // Generar tokens
    int n_generated = 0;
    const int max_gen = std::min(params.n_predict, n_ctx - n_tokens - 4);

    while (n_generated < max_gen) {
        llama_token new_token = llama_sampler_sample(sampler_, ctx_, -1);

        // Verificar token de fin (EOS)
        if (llama_vocab_is_eog(vocab, new_token)) {
            break;
        }

        // Convertir token a texto (nueva API: vocab en lugar de ctx)
        char buf[128];
        int n = llama_token_to_piece(vocab, new_token, buf, sizeof(buf), 0, true);
        if (n > 0) {
            result.append(buf, n);
        }

        // Alimentar token de vuelta para siguiente iteración
        llama_batch next_batch = llama_batch_get_one(&new_token, 1);
        if (llama_decode(ctx_, next_batch) != 0) {
            break;
        }

        n_generated++;
    }

    llama_sampler_free(sampler_);
    sampler_ = nullptr;

    return result;
}

void LlamaEngineCore::streamGenerate(const GenerateParams& params, TokenCallback callback) {
    std::lock_guard<std::mutex> lock(mtx_);

    if (!ready_ || !ctx_ || !model_ || !callback) {
        if (callback) callback("[ERROR: Modelo no cargado o callback nulo]", true);
        return;
    }

    if (params.prompt.empty()) {
        callback("[ERROR: Prompt vacío]", true);
        return;
    }

    // Tokenizar prompt
    std::vector<llama_token> tokens = common_tokenize(ctx_, params.prompt.c_str(), true, true);
    if (tokens.empty()) {
        callback("[ERROR: Tokenización vacía]", true);
        return;
    }

    // Verificar espacio en contexto
    int n_ctx = llama_n_ctx(ctx_);
    int n_tokens = static_cast<int>(tokens.size());
    if (n_tokens >= n_ctx - 4) {
        tokens.resize(n_ctx - 4);
        n_tokens = static_cast<int>(tokens.size());
    }

    // Decodificar prompt inicial
    llama_batch batch = llama_batch_get_one(tokens.data(), n_tokens);
    if (llama_decode(ctx_, batch) != 0) {
        callback("[ERROR: Fallo decodificación inicial]", true);
        return;
    }

    // Obtener vocabulario
    const struct llama_vocab* vocab = llama_model_get_vocab(model_);
    if (!vocab) {
        callback("[ERROR: No se pudo obtener vocabulario]", true);
        return;
    }

    // Configurar sampler chain
    llama_sampler_chain_params sparams = llama_sampler_chain_default_params();
    sampler_ = llama_sampler_chain_init(sparams);
    if (!sampler_) {
        callback("[ERROR: Fallo al crear sampler]", true);
        return;
    }

    llama_sampler_chain_add(sampler_, llama_sampler_init_top_k(params.top_k));
    llama_sampler_chain_add(sampler_, llama_sampler_init_top_p(params.top_p, 1));
    llama_sampler_chain_add(sampler_, llama_sampler_init_temp(params.temperature));
    
    if (params.repeat_penalty > 1.0f) {
        int32_t n_vocab = llama_vocab_n_tokens(vocab);
        llama_sampler_chain_add(sampler_, llama_sampler_init_penalties(
            n_vocab, 64, params.repeat_penalty, 0.0f, 0.0f));
    }

    uint32_t seed = (params.seed == -1) ? LLAMA_DEFAULT_SEED : static_cast<uint32_t>(params.seed);
    llama_sampler_chain_add(sampler_, llama_sampler_init_dist(seed));

    // Streaming loop
    int n_generated = 0;
    const int max_gen = std::min(params.n_predict, n_ctx - n_tokens - 4);

    while (n_generated < max_gen) {
        llama_token new_token = llama_sampler_sample(sampler_, ctx_, -1);

        if (llama_vocab_is_eog(vocab, new_token)) {
            callback("", true);  // is_last = true
            break;
        }

        char buf[128];
        int n = llama_token_to_piece(vocab, new_token, buf, sizeof(buf), 0, true);
        if (n > 0) {
            callback(std::string(buf, n).c_str(), false);
        }

        llama_batch next_batch = llama_batch_get_one(&new_token, 1);
        if (llama_decode(ctx_, next_batch) != 0) {
            callback("[ERROR: Fallo decodificación streaming]", true);
            break;
        }

        n_generated++;
    }

    // Callback final si no se llamó con EOS
    if (n_generated >= max_gen) {
        callback("", true);
    }

    llama_sampler_free(sampler_);
    sampler_ = nullptr;
}

int LlamaEngineCore::tokenCount(const char* text) {
    std::lock_guard<std::mutex> lock(mtx_);

    if (!ready_ || !ctx_ || !text) {
        return -1;
    }

    std::vector<llama_token> tokens = common_tokenize(ctx_, text, true, false);
    return static_cast<int>(tokens.size());
}

bool LlamaEngineCore::isReady() const {
    std::lock_guard<std::mutex> lock(mtx_);
    return ready_ && ctx_ != nullptr && model_ != nullptr;
}

void LlamaEngineCore::freeModel() {
    std::lock_guard<std::mutex> lock(mtx_);

    if (sampler_) {
        llama_sampler_free(sampler_);
        sampler_ = nullptr;
    }
    if (ctx_) {
        llama_free(ctx_);
        ctx_ = nullptr;
    }
    if (model_) {
        llama_model_free(model_);
        model_ = nullptr;
    }
    ready_ = false;
}

void LlamaEngineCore::destroy() {
    freeModel();
    
    // Nota: llama_backend_free() se llama una vez al finalizar el proceso
    // No lo llamamos aquí para permitir múltiples ciclos init/destroy
    ready_ = false;
}

int LlamaEngineCore::getNVocab() const {
    std::lock_guard<std::mutex> lock(mtx_);
    if (!model_) return 0;
    const struct llama_vocab* vocab = llama_model_get_vocab(model_);
    if (!vocab) return 0;
    return llama_vocab_n_tokens(vocab);
}

int LlamaEngineCore::getNCtx() const {
    std::lock_guard<std::mutex> lock(mtx_);
    if (!ctx_) return n_ctx_;
    return llama_n_ctx(ctx_);
}

int LlamaEngineCore::getNEmbd() const {
    std::lock_guard<std::mutex> lock(mtx_);
    if (!model_) return 0;
    return llama_model_n_embd(model_);
}

int LlamaEngineCore::getNLayer() const {
    std::lock_guard<std::mutex> lock(mtx_);
    if (!model_) return 0;
    return llama_model_n_layer(model_);
}

size_t LlamaEngineCore::getMemoryUsage() const {
    std::lock_guard<std::mutex> lock(mtx_);
    if (!model_) return 0;
    return llama_model_size(model_);
}