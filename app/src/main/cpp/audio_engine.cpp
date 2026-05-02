#include <jni.h>
#include <oboe/Oboe.h>
#include <android/log.h>

#define TAG "ShowRaw"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO,  TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

// Stub — implementação do motor de áudio nativo em fase futura.
// A lógica de captura, limiter e HPF será processada aqui via Oboe.

extern "C" JNIEXPORT jstring JNICALL
Java_com_showraw_android_audio_AudioEngine_nativeGetOboeVersion(JNIEnv* env, jobject) {
    return env->NewStringUTF(oboe::Version::Text);
}
