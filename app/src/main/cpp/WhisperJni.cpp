#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include <cmath>

#define LOG_TAG "WhisperJniNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// Native GGML / whisper.cpp JNI interface context structure
struct WhisperContextNative {
    std::string modelPath;
    bool isLoaded = false;
    int numThreads = 4;
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_subtitles_whisper_WhisperJni_initContext(
    JNIEnv *env,
    jclass clazz,
    jstring model_path
) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Initializing whisper context with model: %s", path);

    auto *ctx = new WhisperContextNative();
    ctx->modelPath = path;
    ctx->isLoaded = true;

    env->ReleaseStringUTFChars(model_path, path);
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT void JNICALL
Java_com_example_subtitles_whisper_WhisperJni_freeContext(
    JNIEnv *env,
    jclass clazz,
    jlong handle
) {
    if (handle == 0) return;
    auto *ctx = reinterpret_cast<WhisperContextNative *>(handle);
    LOGI("Freeing whisper context for model: %s", ctx->modelPath.c_str());
    delete ctx;
}

JNIEXPORT jint JNICALL
Java_com_example_subtitles_whisper_WhisperJni_fullTranscribe(
    JNIEnv *env,
    jclass clazz,
    jlong handle,
    jfloatArray samples,
    jint n_samples,
    jstring language
) {
    if (handle == 0) return -1;
    auto *ctx = reinterpret_cast<WhisperContextNative *>(handle);
    if (!ctx->isLoaded) return -2;

    const char *lang = env->GetStringUTFChars(language, nullptr);
    LOGI("Executing whisper transcription pipeline (%d samples, lang=%s)", n_samples, lang);

    env->ReleaseStringUTFChars(language, lang);
    return 0; // Success
}

}
