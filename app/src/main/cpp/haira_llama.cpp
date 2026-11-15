#include <jni.h>
#include <android/log.h>
#include <mutex>
#include <string>
#include <vector>
#include "llama.h"

#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, "HAIRA-LLAMA", __VA_ARGS__)
static std::mutex g_mutex;
static llama_model * g_model = nullptr;
static llama_context * g_ctx = nullptr;

static void release_engine() {
    if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
    if (g_model) { llama_model_free(g_model); g_model = nullptr; }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_haira_ai_ai_LocalInference_nativeLoad(JNIEnv * env, jobject, jstring path, jint contextSize) {
    std::lock_guard<std::mutex> lock(g_mutex);
    release_engine();
    const char * modelPath = env->GetStringUTFChars(path, nullptr);
    if (!modelPath) return JNI_FALSE;
    llama_backend_init();
    llama_model_params modelParams = llama_model_default_params();
    modelParams.load_mode = LLAMA_LOAD_MODE_MMAP;
    modelParams.n_gpu_layers = 0;
    g_model = llama_model_load_from_file(modelPath, modelParams);
    env->ReleaseStringUTFChars(path, modelPath);
    if (!g_model) { LOGE("Model load failed"); return JNI_FALSE; }
    llama_context_params contextParams = llama_context_default_params();
    contextParams.n_ctx = static_cast<uint32_t>(contextSize);
    contextParams.n_batch = 512;
    contextParams.n_threads = 4;
    contextParams.n_threads_batch = 4;
    g_ctx = llama_init_from_model(g_model, contextParams);
    if (!g_ctx) { LOGE("Context creation failed"); release_engine(); return JNI_FALSE; }
    return JNI_TRUE;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_haira_ai_ai_LocalInference_nativeGenerate(JNIEnv * env, jobject, jstring prompt, jint maxTokens) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_model || !g_ctx) return env->NewStringUTF("");
    const char * input = env->GetStringUTFChars(prompt, nullptr);
    if (!input) return env->NewStringUTF("");
    const llama_vocab * vocab = llama_model_get_vocab(g_model);
    std::string text(input);
    env->ReleaseStringUTFChars(prompt, input);
    llama_memory_clear(llama_get_memory(g_ctx), true);
    int32_t needed = llama_tokenize(vocab, text.c_str(), static_cast<int32_t>(text.size()), nullptr, 0, true, false);
    if (needed >= 0) return env->NewStringUTF("");
    std::vector<llama_token> tokens(static_cast<size_t>(-needed));
    int32_t tokenCount = llama_tokenize(vocab, text.c_str(), static_cast<int32_t>(text.size()), tokens.data(), static_cast<int32_t>(tokens.size()), true, false);
    if (tokenCount < 0) return env->NewStringUTF("");
    tokens.resize(static_cast<size_t>(tokenCount));
    llama_batch batch = llama_batch_get_one(tokens.data(), tokenCount);
    if (llama_decode(g_ctx, batch) != 0) return env->NewStringUTF("");
    llama_sampler * sampler = llama_sampler_chain_init(llama_sampler_chain_default_params());
    llama_sampler_chain_add(sampler, llama_sampler_init_greedy());
    std::string output;
    for (int i = 0; i < maxTokens; ++i) {
        llama_token token = llama_sampler_sample(sampler, g_ctx, -1);
        if (llama_vocab_is_eog(vocab, token)) break;
        llama_sampler_accept(sampler, token);
        char piece[512];
        int32_t count = llama_token_to_piece(vocab, token, piece, sizeof(piece), 0, true);
        if (count > 0) output.append(piece, static_cast<size_t>(count));
        batch = llama_batch_get_one(&token, 1);
        if (llama_decode(g_ctx, batch) != 0) break;
    }
    llama_sampler_free(sampler);
    return env->NewStringUTF(output.c_str());
}

extern "C" JNIEXPORT void JNICALL
Java_com_haira_ai_ai_LocalInference_nativeUnload(JNIEnv *, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    release_engine();
}
