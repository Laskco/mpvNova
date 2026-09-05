#include <jni.h>

#include <mpv/client.h>

#include "globals.h"
#include "jni_utils.h"
#include "log.h"

static void sendPropertyUpdateToJava(JNIEnv *env, mpv_event_property *prop)
{
    jstring jprop = env->NewStringUTF(prop->name);
    jstring jvalue = NULL;
    switch (prop->format) {
    case MPV_FORMAT_NONE:
        env->CallStaticVoidMethod(mpv_MPVLib, mpv_MPVLib_eventProperty_S, jprop);
        break;
    case MPV_FORMAT_FLAG:
        env->CallStaticVoidMethod(mpv_MPVLib, mpv_MPVLib_eventProperty_Sb, jprop,
            (jboolean) (*(int*)prop->data != 0));
        break;
    case MPV_FORMAT_INT64:
        env->CallStaticVoidMethod(mpv_MPVLib, mpv_MPVLib_eventProperty_Sl, jprop,
            (jlong) *(int64_t*)prop->data);
        break;
    case MPV_FORMAT_DOUBLE:
        env->CallStaticVoidMethod(mpv_MPVLib, mpv_MPVLib_eventProperty_Sd, jprop,
            (jdouble) *(double*)prop->data);
        break;
    case MPV_FORMAT_STRING:
        jvalue = new_utf8_string(env, *(const char**)prop->data);
        env->CallStaticVoidMethod(mpv_MPVLib, mpv_MPVLib_eventProperty_SS, jprop, jvalue);
        break;
    default:
        ALOGV("sendPropertyUpdateToJava: Unknown property update format received in callback: %d!", prop->format);
        break;
    }
    if (jprop)
        env->DeleteLocalRef(jprop);
    if (jvalue)
        env->DeleteLocalRef(jvalue);
}

static void sendEventToJava(JNIEnv *env, int event)
{
    env->CallStaticVoidMethod(mpv_MPVLib, mpv_MPVLib_event, event);
}

static void sendLogMessageToJava(JNIEnv *env, mpv_event_log_message *msg)
{
    jstring jprefix = new_utf8_string(env, msg->prefix);
    jstring jtext = new_utf8_string(env, msg->text);

    env->CallStaticVoidMethod(mpv_MPVLib, mpv_MPVLib_logMessage_SiS,
        jprefix, (jint) msg->log_level, jtext);

    if (jprefix)
        env->DeleteLocalRef(jprefix);
    if (jtext)
        env->DeleteLocalRef(jtext);
}

void *event_thread(void *arg)
{
    JNIEnv *env = NULL;
    acquire_jni_env(g_vm, &env);
    if (!env)
        die("failed to acquire java env");

    while (1) {
        mpv_event *mp_event;
        mpv_event_property *mp_property = NULL;
        mpv_event_log_message *msg = NULL;

        mp_event = mpv_wait_event(g_mpv, -1.0);

        if (g_event_thread_request_exit)
            break;

        if (mp_event->event_id == MPV_EVENT_NONE)
            continue;

        switch (mp_event->event_id) {
        case MPV_EVENT_LOG_MESSAGE:
            msg = (mpv_event_log_message*)mp_event->data;
            ALOGV("[%s:%s] %s", msg->prefix, msg->level, msg->text);
            sendLogMessageToJava(env, msg);
            break;
        case MPV_EVENT_PROPERTY_CHANGE:
            mp_property = (mpv_event_property*)mp_event->data;
            sendPropertyUpdateToJava(env, mp_property);
            break;
        default:
            if (mp_event->event_id == MPV_EVENT_START_FILE) {
                const auto *start = static_cast<mpv_event_start_file*>(mp_event->data);
                int64_t entry_id = start ? start->playlist_entry_id : -1;
                mpv_event_property entry_property = {
                    "file-event-entry-id", MPV_FORMAT_INT64, &entry_id
                };
                sendPropertyUpdateToJava(env, &entry_property);
            } else if (mp_event->event_id == MPV_EVENT_END_FILE) {
                const auto *end = static_cast<mpv_event_end_file*>(mp_event->data);
                int64_t reason = end ? end->reason : -1;
                mpv_event_property reason_property = {
                    "end-file-reason", MPV_FORMAT_INT64, &reason
                };
                sendPropertyUpdateToJava(env, &reason_property);
                int64_t entry_id = end ? end->playlist_entry_id : -1;
                mpv_event_property entry_property = {
                    "file-event-entry-id", MPV_FORMAT_INT64, &entry_id
                };
                sendPropertyUpdateToJava(env, &entry_property);
            }
            ALOGV("event: %s\n", mpv_event_name(mp_event->event_id));
            sendEventToJava(env, mp_event->event_id);
            break;
        }
    }

    g_vm->DetachCurrentThread();

    return NULL;
}
