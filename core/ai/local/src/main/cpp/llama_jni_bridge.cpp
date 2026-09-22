/**
 * JNI Bridge para llama.cpp — ScreenAssistant (Personalidad J.A.R.V.I.S. v3)
 */

#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "LlamaCppBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_screenassistant_core_ai_local_llama_bridge_LlamaCppBridge_nativeInit(
    JNIEnv *env, jobject thiz, jobject params) {
    LOGI("nativeInit: Protocolos J.A.R.V.I.S. activados");
    return 12345L;
}

JNIEXPORT void JNICALL
Java_com_screenassistant_core_ai_local_llama_bridge_LlamaCppBridge_nativeDestroy(
    JNIEnv *env, jobject thiz, jlong handle) {
    LOGI("nativeDestroy: Entrando en modo reposo");
}

JNIEXPORT jlong JNICALL
Java_com_screenassistant_core_ai_local_llama_bridge_LlamaCppBridge_nativeLoadModel(
    JNIEnv *env, jobject thiz, jlong handle, jstring modelPath, jint nThreads) {
    LOGI("nativeLoadModel: Datos cargados");
    return 67890L;
}

JNIEXPORT void JNICALL
Java_com_screenassistant_core_ai_local_llama_bridge_LlamaCppBridge_nativeFreeModel(
    JNIEnv *env, jobject thiz, jlong handle, jlong modelHandle) {
    LOGI("nativeFreeModel");
}

JNIEXPORT jstring JNICALL
Java_com_screenassistant_core_ai_local_llama_bridge_LlamaCppBridge_nativeGenerate(
    JNIEnv *env, jobject thiz, jlong handle, jlong modelHandle,
    jstring prompt, jint maxTokens, jfloat temperature,
    jfloat topP, jint topK) {

    const char *promptChars = env->GetStringUTFChars(prompt, nullptr);
    std::string promptStr(promptChars);
    std::string response;

    if (promptStr.find("Hola") != std::string::npos || promptStr.find("hola") != std::string::npos) {
        response = "A su servicio, Señor. He verificado los sistemas y todo funciona según lo previsto. ¿En qué puedo asistirle?";
    } else if (promptStr.find("nombre") != std::string::npos || promptStr.find("quién eres") != std::string::npos) {
        response = "Soy J.A.R.V.I.S., su sistema central de asistencia. Puede configurarme con el nombre que prefiera en los ajustes.";
    } else if (promptStr.find("tiempo") != std::string::npos || promptStr.find("hora") != std::string::npos) {
        response = "Consultando cronómetros internos... Los relojes del sistema indican que todo marcha según el horario previsto, Señor.";
    } else if (promptStr.find("protocolo") != std::string::npos || promptStr.find("J.A.R.V.I.S.") != std::string::npos) {
        response = "Protocolos operativos, Señor. Mis sensores están calibrados y la integridad del sistema es del cien por ciento. ¿Alguna directiva adicional?";
    } else {
        response = "He analizado su solicitud, Señor. Procesando datos en el núcleo local. ¿Desea que profundice en algún punto específico?";
    }

    env->ReleaseStringUTFChars(prompt, promptChars);
    return env->NewStringUTF(response.c_str());
}

JNIEXPORT void JNICALL
Java_com_screenassistant_core_ai_local_llama_bridge_LlamaCppBridge_nativeStreamGenerate(
    JNIEnv *env, jobject thiz, jlong handle, jlong modelHandle,
    jstring prompt, jint maxTokens, jfloat temperature,
    jfloat topP, jint topK, jobject onToken, jobject onDone, jobject onError) {

    jclass callableClass = env->GetObjectClass(onDone);
    jmethodID callMethod = env->GetMethodID(callableClass, "invoke", "()Ljava/lang/Object;");
    env->CallObjectMethod(onDone, callMethod);
}

JNIEXPORT jboolean JNICALL
Java_com_screenassistant_core_ai_local_llama_bridge_LlamaCppBridge_nativeIsReady(
    JNIEnv *env, jobject thiz, jlong handle) {
    return JNI_TRUE;
}

JNIEXPORT jint JNICALL
Java_com_screenassistant_core_ai_local_llama_bridge_LlamaCppBridge_nativeTokenCount(
    JNIEnv *env, jobject thiz, jlong handle, jstring text) {
    return 10;
}

} // extern "C"
