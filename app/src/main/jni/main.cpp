#include <jni.h>
#include <dlfcn.h>
#include <malloc.h>
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <time.h>
#include <locale.h>
#include <atomic>
#include <string>
#include <vector>

#include <mpv/client.h>

#include <pthread.h>

extern "C" {
    #include <libavcodec/jni.h>
}

#include "log.h"
#include "jni_utils.h"
#include "event.h"

#define ARRAYLEN(a) (sizeof(a)/sizeof(a[0]))

extern "C" {
    jni_func(void, create, jobject appctx);
    jni_func(void, init);
    jni_func(void, destroy);
    jni_func(void, purgeNativeHeap);

    jni_func(void, command, jobjectArray jarray);
    jni_func(jint, commandAsync, jlong request_id, jobjectArray jarray);
    jni_func(jlong, loadFile, jstring jpath);
};

JavaVM *g_vm;
mpv_handle *g_mpv;
std::atomic<bool> g_event_thread_request_exit(false);

static pthread_t event_thread_id;
static jobject global_appctx;

static void prepare_environment(JNIEnv *env, jobject appctx) {
    setlocale(LC_NUMERIC, "C");

    g_vm = NULL;
    env->GetJavaVM(&g_vm);
    if (!g_vm)
        die("failed to get jvm");
    av_jni_set_java_vm(g_vm, NULL);

    if (global_appctx)
        env->DeleteGlobalRef(global_appctx);
    global_appctx = env->NewGlobalRef(appctx);
    if (global_appctx)
        av_jni_set_android_app_ctx(global_appctx, NULL);

    init_methods_cache(env);
}

jni_func(void, create, jobject appctx) {
    if (g_mpv)
        die("mpv is already initialized");

    prepare_environment(env, appctx);

    g_mpv = mpv_create();
    if (!g_mpv)
        die("context init failed");

    // Keep cplayer verbose for AboutActivity feature discovery and renderer
    // recovery; everything else only needs info/warn/error in release builds.
    mpv_request_log_messages(g_mpv, "v");
#if !defined(NDEBUG)
    mpv_set_option_string(g_mpv, "msg-level", "all=v");
#else
    mpv_set_option_string(g_mpv, "msg-level", "all=info,cplayer=v");
#endif
}

jni_func(void, init) {
    if (!g_mpv)
        die("mpv is not created");

    if (mpv_initialize(g_mpv) < 0)
        die("mpv init failed");

    g_event_thread_request_exit = false;
    if (pthread_create(&event_thread_id, NULL, event_thread, NULL) != 0)
        die("thread create failed");
    pthread_setname_np(event_thread_id, "event_thread");
}

static void purge_native_heap_pages() {
#if defined(__ANDROID__) && defined(M_PURGE)
    using mallopt_fn = int (*)(int, int);
    static mallopt_fn fn = reinterpret_cast<mallopt_fn>(dlsym(RTLD_DEFAULT, "mallopt"));
    if (fn)
        fn(M_PURGE, 0);
#endif
}

jni_func(void, destroy) {
    if (!g_mpv) {
        ALOGV("mpv destroy called but it's already destroyed");
        return;
    }

    // poke event thread and wait for it to exit
    g_event_thread_request_exit = true;
    mpv_wakeup(g_mpv);
    pthread_join(event_thread_id, NULL);

    mpv_terminate_destroy(g_mpv);
    g_mpv = NULL;
    purge_native_heap_pages();
}

jni_func(void, purgeNativeHeap) {
    purge_native_heap_pages();
}

static int run_command(JNIEnv *env, jobjectArray jarray, uint64_t request_id) {
    CHECK_MPV_INIT();

    const char *arguments[128] = {0};
    int len = env->GetArrayLength(jarray);
    if (len >= ARRAYLEN(arguments))
        die("too many command arguments");

    std::vector<std::string> utf8_arguments(static_cast<size_t>(len));
    for (int i = 0; i < len; ++i) {
        jstring argument = static_cast<jstring>(env->GetObjectArrayElement(jarray, i));
        utf8_arguments[static_cast<size_t>(i)] = get_utf8_string(env, argument);
        arguments[i] = utf8_arguments[static_cast<size_t>(i)].c_str();
        env->DeleteLocalRef(argument);
    }

    return request_id ? mpv_command_async(g_mpv, request_id, arguments) : mpv_command(g_mpv, arguments);
}

jni_func(void, command, jobjectArray jarray) {
    run_command(env, jarray, 0);
}

jni_func(jint, commandAsync, jlong request_id, jobjectArray jarray) {
    if (request_id <= 0)
        return MPV_ERROR_INVALID_PARAMETER;
    return run_command(env, jarray, static_cast<uint64_t>(request_id));
}

jni_func(jlong, loadFile, jstring jpath) {
    if (!g_mpv || !jpath) {
        ALOGE("loadfile replace requires an initialized player and a path");
        return -1;
    }

    const std::string path = get_utf8_string(env, jpath);
    if (env->ExceptionCheck()) {
        ALOGE("loadfile replace UTF-8 conversion failed");
        return -1;
    }
    const char *arguments[] = {"loadfile", path.c_str(), "replace", NULL};
    mpv_node result = {};
    const int status = mpv_command_ret(g_mpv, arguments, &result);
    if (status < 0) {
        ALOGE("loadfile replace failed: %s", mpv_error_string(status));
        return -1;
    }

    jlong entry_id = -1;
    if (result.format == MPV_FORMAT_NODE_MAP && result.u.list) {
        const mpv_node_list *map = result.u.list;
        for (int i = 0; i < map->num; ++i) {
            if (strcmp(map->keys[i], "playlist_entry_id") == 0 &&
                map->values[i].format == MPV_FORMAT_INT64) {
                entry_id = static_cast<jlong>(map->values[i].u.int64);
                break;
            }
        }
    }
    mpv_free_node_contents(&result);
    if (entry_id < 0) {
        ALOGE("loadfile replace returned no valid playlist_entry_id");
        return -1;
    }
    return entry_id;
}
