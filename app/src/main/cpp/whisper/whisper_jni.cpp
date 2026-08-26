#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>

#define TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Structure representing active Whisper session
struct whisper_context_wrapper {
    void* ctx;
    std::vector<std::string> segments;
    std::vector<int64_t> t0;
    std::vector<int64_t> t1;
};

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_example_util_WhisperNative_initContext(JNIEnv *env, jobject thiz, jstring model_path) {
    const char *path = env->GetStringUTFChars(model_path, nullptr);
    LOGI("Initializing Whisper model from path: %s", path);

    whisper_context_wrapper* wrapper = new whisper_context_wrapper();
    wrapper->ctx = (void*)1; // Context placeholder handle
    env->ReleaseStringUTFChars(model_path, path);

    return reinterpret_cast<jlong>(wrapper);
}

JNIEXPORT void JNICALL
Java_com_example_util_WhisperNative_freeContext(JNIEnv *env, jobject thiz, jlong context_ptr) {
    if (context_ptr == 0) return;
    whisper_context_wrapper* wrapper = reinterpret_cast<whisper_context_wrapper*>(context_ptr);
    delete wrapper;
    LOGI("Whisper context freed.");
}

JNIEXPORT jint JNICALL
Java_com_example_util_WhisperNative_fullTranscribe(
        JNIEnv *env,
        jobject thiz,
        jlong context_ptr,
        jfloatArray audio_data,
        jint n_threads,
        jstring language,
        jboolean translate) {
    if (context_ptr == 0) return -1;
    whisper_context_wrapper* wrapper = reinterpret_cast<whisper_context_wrapper*>(context_ptr);

    jsize sample_count = env->GetArrayLength(audio_data);
    jfloat *samples = env->GetFloatArrayElements(audio_data, nullptr);

    const char *lang_str = env->GetStringUTFChars(language, nullptr);
    LOGI("Transcribing %d samples (threads=%d, lang=%s)", sample_count, n_threads, lang_str);

    wrapper->segments.clear();
    wrapper->t0.clear();
    wrapper->t1.clear();

    env->ReleaseStringUTFChars(language, lang_str);
    env->ReleaseFloatArrayElements(audio_data, samples, JNI_ABORT);

    return 0;
}

JNIEXPORT jint JNICALL
Java_com_example_util_WhisperNative_getTextSegmentCount(JNIEnv *env, jobject thiz, jlong context_ptr) {
    if (context_ptr == 0) return 0;
    whisper_context_wrapper* wrapper = reinterpret_cast<whisper_context_wrapper*>(context_ptr);
    return static_cast<jint>(wrapper->segments.size());
}

JNIEXPORT jstring JNICALL
Java_com_example_util_WhisperNative_getTextSegment(JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    if (context_ptr == 0) return env->NewStringUTF("");
    whisper_context_wrapper* wrapper = reinterpret_cast<whisper_context_wrapper*>(context_ptr);
    if (index < 0 || index >= static_cast<jint>(wrapper->segments.size())) {
        return env->NewStringUTF("");
    }
    return env->NewStringUTF(wrapper->segments[index].c_str());
}

JNIEXPORT jlong JNICALL
Java_com_example_util_WhisperNative_getTextSegmentT0(JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    if (context_ptr == 0) return 0;
    whisper_context_wrapper* wrapper = reinterpret_cast<whisper_context_wrapper*>(context_ptr);
    if (index < 0 || index >= static_cast<jint>(wrapper->t0.size())) return 0;
    return wrapper->t0[index];
}

JNIEXPORT jlong JNICALL
Java_com_example_util_WhisperNative_getTextSegmentT1(JNIEnv *env, jobject thiz, jlong context_ptr, jint index) {
    if (context_ptr == 0) return 0;
    whisper_context_wrapper* wrapper = reinterpret_cast<whisper_context_wrapper*>(context_ptr);
    if (index < 0 || index >= static_cast<jint>(wrapper->t1.size())) return 0;
    return wrapper->t1[index];
}

}
