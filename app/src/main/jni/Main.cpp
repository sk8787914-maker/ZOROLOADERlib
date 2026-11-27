#include <jni.h>
#include <string>
#include <android/log.h>
#include <unistd.h>
#include <fcntl.h>
#include <time.h>
#include <cstdio>
#include <cstring>
#include <backends/oxorany.h>
#include <sys/stat.h>
#include "backends/ModsLoader.h"

#define NATIVE_TAG "ZoroNative"

/**
 * Native diagnostics only. Writes a dedicated file so native-side steps can be
 * inspected independently of the Java/AAR logs. No runtime behaviour is changed.
 */
static void ZoroNativeLog(const char *step, const char *msg) {
    __android_log_print(ANDROID_LOG_INFO, NATIVE_TAG, "[%s] %s", step, msg);

    char line[1024];
    time_t now = time(NULL);
    struct tm tm_now;
    localtime_r(&now, &tm_now);
    snprintf(line, sizeof(line), "%04d-%02d-%02d %02d:%02d:%02d [%s] %s\n",
             tm_now.tm_year + 1900, tm_now.tm_mon + 1, tm_now.tm_mday,
             tm_now.tm_hour, tm_now.tm_min, tm_now.tm_sec, step, msg);

    int fd = open("/sdcard/Download/zoro_native.txt", O_WRONLY | O_CREAT | O_APPEND, 0666);
    if (fd >= 0) {
        ssize_t ignored = write(fd, line, strlen(line));
        (void) ignored;
        close(fd);
    }
}

/**
 * v20: the old SIGSTOP/SIGCONT guard is still removed. The virtual process can
 * hit an ART MarkCompact abort while the isolated WebView owns the screen, so
 * the web result is stored in the loader's shared internal files directory.
 * The stored result now records its owner PID: when that process is gone the
 * stale result is discarded immediately instead of blocking the next Twitter
 * or Facebook login. Twitter cancellations call o5.a.k(boolean,int,int,Intent)
 * with the real request code; Facebook replays into the recreated IMSDK
 * WebLoginActivity listener. Game relaunch is rate-limited to avoid a restore
 * storm after a native/ART crash. No process signals and no VM/anti-cheat
 * changes are used here.
 */

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    ZoroNativeLog("JNI_OnLoad", "libMCoreEsp loaded v20 (com.google, owner-aware pending cleanup)");
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_google_BoxApplication_BoxApp(JNIEnv* env, jobject thiz) {
    ZoroNativeLog("BoxApp", "license key requested");
    return env->NewStringUTF(oxorany("MANISH->4e15d8fc66"));
}
