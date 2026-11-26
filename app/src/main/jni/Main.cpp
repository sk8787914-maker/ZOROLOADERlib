#include <jni.h>
#include <string>
#include <android/log.h>
#include <unistd.h>
#include <fcntl.h>
#include <signal.h>
#include <errno.h>
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
 * v15 process guard. The isolated WebView owns the screen while the virtual
 * game process is stopped, so ART cannot start the MarkCompact pass that was
 * aborting it. Only SIGSTOP/SIGCONT are used; no game code or anti-cheat state
 * is touched. Only the pid and signal name are logged.
 */
static void ZoroSignalProcess(jint pid, int signal, const char *name) {
    if (pid <= 0 || pid == static_cast<jint>(getpid())) {
        return;
    }
    if (kill(static_cast<pid_t>(pid), signal) == 0) {
        char msg[128];
        snprintf(msg, sizeof(msg), "%s virtual pid=%d ok", name, static_cast<int>(pid));
        ZoroNativeLog("WebLoginGuard", msg);
    } else {
        char msg[160];
        snprintf(msg, sizeof(msg), "%s virtual pid=%d failed errno=%d",
                 name, static_cast<int>(pid), errno);
        ZoroNativeLog("WebLoginGuard", msg);
    }
}

extern "C" JNIEXPORT void JNICALL
Java_top_niunaijun_blackbox_utils_auth_WebLoginProcessGuard_freeze(JNIEnv *env, jclass clazz, jint pid) {
    (void) env;
    (void) clazz;
    ZoroSignalProcess(pid, SIGSTOP, "freeze");
}

extern "C" JNIEXPORT void JNICALL
Java_top_niunaijun_blackbox_utils_auth_WebLoginProcessGuard_resume(JNIEnv *env, jclass clazz, jint pid) {
    (void) env;
    (void) clazz;
    ZoroSignalProcess(pid, SIGCONT, "resume");
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    ZoroNativeLog("JNI_OnLoad", "libMCoreEsp loaded v15 (com.google, web login process guard)");
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT jstring JNICALL
Java_com_google_BoxApplication_BoxApp(JNIEnv* env, jobject thiz) {
    ZoroNativeLog("BoxApp", "license key requested");
    return env->NewStringUTF(oxorany("MANISH->4e15d8fc66"));
}
