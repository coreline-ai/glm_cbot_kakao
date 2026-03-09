#include <jni.h>
#include <string>

#ifndef GLM_KEY_PART_A
#define GLM_KEY_PART_A ""
#endif

#ifndef GLM_KEY_PART_B
#define GLM_KEY_PART_B ""
#endif

#ifndef GLM_KEY_PART_C
#define GLM_KEY_PART_C ""
#endif

#ifndef OPENAI_KEY_PART_A
#define OPENAI_KEY_PART_A ""
#endif

#ifndef OPENAI_KEY_PART_B
#define OPENAI_KEY_PART_B ""
#endif

#ifndef OPENAI_KEY_PART_C
#define OPENAI_KEY_PART_C ""
#endif

extern "C"
JNIEXPORT jstring JNICALL
Java_com_coreline_cbot_data_security_NativeSecrets_nativeGetEmbeddedGlmKey(
    JNIEnv *env,
    jobject /* this */) {
    std::string secret = std::string(GLM_KEY_PART_A) + std::string(GLM_KEY_PART_B) + std::string(GLM_KEY_PART_C);
    return env->NewStringUTF(secret.c_str());
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_coreline_cbot_data_security_NativeSecrets_nativeGetEmbeddedOpenAiKey(
    JNIEnv *env,
    jobject /* this */) {
    std::string secret = std::string(OPENAI_KEY_PART_A) + std::string(OPENAI_KEY_PART_B) + std::string(OPENAI_KEY_PART_C);
    return env->NewStringUTF(secret.c_str());
}
