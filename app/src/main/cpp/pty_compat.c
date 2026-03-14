#include <jni.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <fcntl.h>
#include <errno.h>
#include <signal.h>
#include <termios.h>
#include <sys/ioctl.h>
#include <sys/wait.h>

#include <android/log.h>

#define TAG "PtyCompat"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static int open_pty_master(void) {
    int master = open("/dev/ptmx", O_RDWR | O_NOCTTY);
    if (master < 0) {
        LOGE("open /dev/ptmx failed: %s", strerror(errno));
        return -1;
    }
    if (grantpt(master) < 0) {
        LOGE("grantpt failed: %s", strerror(errno));
        close(master);
        return -1;
    }
    if (unlockpt(master) < 0) {
        LOGE("unlockpt failed: %s", strerror(errno));
        close(master);
        return -1;
    }
    return master;
}

/*
 * Class:     com_ssh_relay_shell_PtyCompat
 * Method:    createSubprocess
 * Returns:   int[2] = { pid, masterFd }
 */
JNIEXPORT jintArray JNICALL
Java_com_ssh_relay_shell_PtyCompat_createSubprocess(
    JNIEnv *env, jobject thiz,
    jstring cmd, jobjectArray args, jobjectArray envVars)
{
    int master = open_pty_master();
    if (master < 0) return NULL;

    const char *slave_name = ptsname(master);
    if (!slave_name) {
        LOGE("ptsname failed: %s", strerror(errno));
        close(master);
        return NULL;
    }

    pid_t pid = fork();
    if (pid < 0) {
        LOGE("fork failed: %s", strerror(errno));
        close(master);
        return NULL;
    }

    if (pid == 0) {
        /* Child process */
        close(master);
        setsid();

        int slave = open(slave_name, O_RDWR);
        if (slave < 0) {
            _exit(127);
        }

        dup2(slave, STDIN_FILENO);
        dup2(slave, STDOUT_FILENO);
        dup2(slave, STDERR_FILENO);
        if (slave > 2) close(slave);

        /* Set environment variables */
        if (envVars) {
            int envCount = (*env)->GetArrayLength(env, envVars);
            for (int i = 0; i < envCount; i++) {
                jstring envStr = (*env)->GetObjectArrayElement(env, envVars, i);
                const char *envCStr = (*env)->GetStringUTFChars(env, envStr, NULL);
                if (envCStr) {
                    putenv(strdup(envCStr));
                    (*env)->ReleaseStringUTFChars(env, envStr, envCStr);
                }
            }
        }

        /* Build argv */
        const char *cmdStr = (*env)->GetStringUTFChars(env, cmd, NULL);
        int argc = args ? (*env)->GetArrayLength(env, args) : 0;
        char **argv = malloc(sizeof(char*) * (argc + 2));
        argv[0] = strdup(cmdStr);
        for (int i = 0; i < argc; i++) {
            jstring argStr = (*env)->GetObjectArrayElement(env, args, i);
            const char *argCStr = (*env)->GetStringUTFChars(env, argStr, NULL);
            argv[i + 1] = strdup(argCStr);
            (*env)->ReleaseStringUTFChars(env, argStr, argCStr);
        }
        argv[argc + 1] = NULL;

        execvp(argv[0], argv);
        _exit(127);
    }

    /* Parent process */
    LOGI("Forked subprocess: pid=%d, master=%d", pid, master);

    jintArray result = (*env)->NewIntArray(env, 2);
    jint buf[2] = { pid, master };
    (*env)->SetIntArrayRegion(env, result, 0, 2, buf);
    return result;
}

JNIEXPORT void JNICALL
Java_com_ssh_relay_shell_PtyCompat_setWindowSize(
    JNIEnv *env, jobject thiz,
    jint masterFd, jint rows, jint cols)
{
    struct winsize ws = {
        .ws_row = (unsigned short)rows,
        .ws_col = (unsigned short)cols,
        .ws_xpixel = 0,
        .ws_ypixel = 0
    };
    ioctl(masterFd, TIOCSWINSZ, &ws);
}

JNIEXPORT jint JNICALL
Java_com_ssh_relay_shell_PtyCompat_waitFor(
    JNIEnv *env, jobject thiz, jint pid)
{
    int status = 0;
    waitpid(pid, &status, 0);
    if (WIFEXITED(status)) return WEXITSTATUS(status);
    return -1;
}

JNIEXPORT void JNICALL
Java_com_ssh_relay_shell_PtyCompat_sendSignal(
    JNIEnv *env, jobject thiz,
    jint pid, jint signal)
{
    kill(pid, signal);
}

JNIEXPORT void JNICALL
Java_com_ssh_relay_shell_PtyCompat_closeFd(
    JNIEnv *env, jobject thiz, jint fd)
{
    close(fd);
}
