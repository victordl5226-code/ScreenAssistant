#pragma once

#include <string>
#include <functional>
#include <memory>
#include <mutex>

struct llama_context;
struct llama_model;
struct llama_sampler;

class LlamaEngineCore {
public:
    struct InitParams {
        int n_ctx = 2048;
        int n_batch = 256;
        int n_ubatch = 256;
        int n_threads = 4;
        bool use_mmap = true;
        bool use_mlock = false;
        std::string model_path;
    };

    struct GenerateParams {
        int n_predict = 256;
        float temperature = 0.7f;
        float top_p = 0.9f;
        int top_k = 40;
        float repeat_penalty = 1.1f;
        int seed = -1;
        std::string prompt;
    };

    using TokenCallback = std::function<void(const char* token, bool is_last)>;

    LlamaEngineCore();
    ~LlamaEngineCore();

    // Non-copyable, non-movable (due to mutex)
    LlamaEngineCore(const LlamaEngineCore&) = delete;
    LlamaEngineCore& operator=(const LlamaEngineCore&) = delete;
    LlamaEngineCore(LlamaEngineCore&&) = delete;
    LlamaEngineCore& operator=(LlamaEngineCore&&) = delete;

    bool init(const InitParams& params);
    bool loadModel(const char* modelPath, int nThreads);
    std::string generate(const GenerateParams& params);
    void streamGenerate(const GenerateParams& params, TokenCallback callback);
    int tokenCount(const char* text);
    bool isReady() const;
    void freeModel();
    void destroy();

    // Info
    int getNVocab() const;
    int getNCtx() const;
    int getNEmbd() const;
    int getNLayer() const;
    size_t getMemoryUsage() const;

private:
    llama_context* ctx_ = nullptr;
    llama_model* model_ = nullptr;
    llama_sampler* sampler_ = nullptr;
    mutable std::mutex mtx_;
    bool ready_ = false;
    int n_ctx_ = 2048;
};