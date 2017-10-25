#include <jni.h>
#include <string>
#include <Android/log.h>
#include <openssl/aes.h>
#include "iostream"
#include "fstream"

#if 1
#define TAG "secret"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)
#define LOGF(...) __android_log_print(ANDROID_LOG_FATAL, TAG ,__VA_ARGS__)
#define LOGW(...) __android_log_print(ANDROID_LOG_WARN, TAG ,__VA_ARGS__)
#else
#define LOGI(...)
#define LOGD(...)
#define LOGE(...)
#define LOGF(...)
#define LOGW(...)
#endif

#define RELESE(P) if (P)        \
{                               \
    delete P;                   \
    P = NULL;                   \
}

#define RELESE_ARRAY(P) if (P)  \
{                               \
    delete[] P;                 \
    P = NULL;                   \
}

int
handleFile(JNIEnv *env, const char *sourceFilePath, const char *destFilePath, bool isEncodeFile);

extern "C"
JNIEXPORT jstring JNICALL
Java_com_oubowu_secret_NdkHelper_e(JNIEnv *env, jclass type, jstring sourceFilePath_,
                                   jstring destFilePath_) {
    const char *sourceFilePath = env->GetStringUTFChars(sourceFilePath_, JNI_FALSE);
    const char *destFilePath = env->GetStringUTFChars(destFilePath_, JNI_FALSE);

    jstring result = NULL;
    // int res = handleFile(env, sourceFilePath, destFilePath);
    int res = handleFile(env, sourceFilePath, destFilePath, true);

    env->ReleaseStringUTFChars(sourceFilePath_, sourceFilePath);
    env->ReleaseStringUTFChars(destFilePath_, destFilePath);

    switch (res) {
        case -1:
            result = env->NewStringUTF("Can not open fin file.");
            break;
        case -2:
            result = env->NewStringUTF("Can not open fout file.");
            break;
        case 1:
            result = env->NewStringUTF("E Success");
            break;
        default:
            break;
    }

    return result;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_oubowu_secret_NdkHelper_d(JNIEnv *env, jclass type, jstring sourceFilePath_,
                                   jstring destFilePath_) {
    const char *sourceFilePath = env->GetStringUTFChars(sourceFilePath_, JNI_FALSE);
    const char *destFilePath = env->GetStringUTFChars(destFilePath_, JNI_FALSE);

    jstring result = NULL;

    int res = handleFile(env, sourceFilePath, destFilePath, false);

    env->ReleaseStringUTFChars(sourceFilePath_, sourceFilePath);
    env->ReleaseStringUTFChars(destFilePath_, destFilePath);

    switch (res) {
        case -1:
            result = env->NewStringUTF("Can not open fin file.");
            break;
        case -2:
            result = env->NewStringUTF("Can not open fout file.");
            break;
        case 1:
            result = env->NewStringUTF("D Success");
            break;
        default:
            break;
    }

    return result;
}

int
handleFile(JNIEnv *env, const char *sourceFilePath, const char *destFilePath, bool isEncodeFile) {
    int result = NULL;

    std::ifstream fin(sourceFilePath, std::ios_base::binary);
    std::ofstream fout(destFilePath, std::ios_base::binary);

    if (!fin) {
        std::cout << "Can not open fin file." << std::endl;
        result = -1;
        return result;
    }
    if (!fout) {
        std::cout << "Can not open fout file." << std::endl;
        result = -2;
        return result;
    }

    //用指定密钥对一段内存进行加密，结果放在outbuffer中
    unsigned char aes_keybuf[32];
    memset(aes_keybuf, 0, sizeof(aes_keybuf));
    strcpy((char *) aes_keybuf, "woshioubowu");
    AES_KEY aeskey;
    AES_set_decrypt_key(aes_keybuf, 256, &aeskey);

    int encrypt_chunk_size = 16;

    char *in_data = new char[encrypt_chunk_size + 1];
    char *out_data = new char[encrypt_chunk_size + 1];

    while (!fin.eof()) {
        fin.read(in_data, encrypt_chunk_size);
        if (fin.gcount() < encrypt_chunk_size) {
            fout.write(in_data, fin.gcount());
        } else {
            if (isEncodeFile) {
                AES_encrypt((const unsigned char *) in_data, (unsigned char *) out_data, &aeskey);
            } else {
                AES_decrypt((const unsigned char *) in_data, (unsigned char *) out_data, &aeskey);
            }
            fout.write(out_data, fin.gcount());
        }
    };

    fout.close();
    fin.close();

    RELESE_ARRAY(in_data);
    RELESE_ARRAY(out_data);

    result = 1;

    return result;
}

extern "C"
JNIEXPORT jstring JNICALL
Java_com_oubowu_secret_NdkHelper_p(JNIEnv *env, jclass type, jobject ctx, jstring pName_,
                                   jboolean e) {
    const char *pName = env->GetStringUTFChars(pName_, JNI_FALSE);

    jstring destFilePath = NULL;

    // 加密安卓手机 /data/data/包名/files/补丁.jar，生成 /data/data/包名/files/e-补丁.jar

    // context.getFilesDir()
    jclass ctxClass = env->GetObjectClass(ctx);
    jmethodID getFilesDirId = env->GetMethodID(ctxClass, "getFilesDir", "()Ljava/io/File;");
    jobject fileObj = env->CallObjectMethod(ctx, getFilesDirId);
    // context.getFilesDir().getPath()
    jclass fileClass = env->GetObjectClass(fileObj);
    jmethodID getFilesPathId = env->GetMethodID(fileClass, "getPath", "()Ljava/lang/String;");
    jstring filesPath = (jstring) env->CallObjectMethod(fileObj, getFilesPathId);
    const char *sourceFilePathChar = env->GetStringUTFChars(filesPath, JNI_FALSE);

    // File.seprator
    jclass clz = env->FindClass("java/io/File");
    jfieldID id = env->GetStaticFieldID(clz, "separator", "Ljava/lang/String;");
    jstring separator = (jstring) env->GetStaticObjectField(clz, id);
    const char *separatorChar = env->GetStringUTFChars(separator, JNI_FALSE);

    std::string sourceFilePathStr = sourceFilePathChar;//char数组自动转为string
    std::string separatorStr = separatorChar;//char数组自动转为string
    std::string dPatchNameStr = pName;
    std::string sourcePathPathStr = sourceFilePathStr + separatorStr + dPatchNameStr;

    const char *sourcePatchPathChar = sourcePathPathStr.c_str();
    LOGE("%s", sourcePatchPathChar);

    // destFilePathChar拼接分隔符和文件名称
    const char *destFilePathChar = env->GetStringUTFChars(filesPath, JNI_FALSE);

    std::string destFilePathStr = destFilePathChar;
    std::string destPathPathStr =
            destFilePathStr + separatorStr + (e == JNI_TRUE ? "e-" : "d-") +
            dPatchNameStr;

    const char *destPatchPathChar = destPathPathStr.c_str();
    LOGE("%s", destPatchPathChar);

    // sourceFilePathChar就是需要加密的文件路径；destFilePathChar就是输出加密的文件路径
    int res = handleFile(env, sourcePatchPathChar, destPatchPathChar, e == JNI_TRUE);

    env->ReleaseStringUTFChars(filesPath, sourceFilePathChar);
    env->ReleaseStringUTFChars(separator, separatorChar);
    env->ReleaseStringUTFChars(filesPath, destFilePathChar);

    env->ReleaseStringUTFChars(pName_, pName);

    switch (res) {
        case -1:
            destFilePath = env->NewStringUTF("Can not open fin file.");
            break;
        case -2:
            destFilePath = env->NewStringUTF("Can not open fout file.");
            break;
        case 1:
            destFilePath = env->NewStringUTF(destPatchPathChar);
            break;
        default:
            break;
    }

    return destFilePath;
}

///////////////////////////////////////////////////////////////////////////
// 测试的方法
///////////////////////////////////////////////////////////////////////////

extern "C"
JNIEXPORT jstring JNICALL
Java_com_oubowu_secret_NdkHelper_getStrFromCPlus(
        JNIEnv *env,
        jobject) {
    std::string str = "Hello from C++";
    return env->NewStringUTF(str.c_str());
}