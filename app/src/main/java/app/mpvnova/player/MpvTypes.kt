package app.mpvnova.player

interface MpvEventObserver {
    fun eventProperty(property: String)
    fun eventProperty(property: String, value: Long)
    fun eventProperty(property: String, value: Boolean)
    fun eventProperty(property: String, value: String)
    fun eventProperty(property: String, value: Double)
    fun event(eventId: Int)
}

interface MpvLogObserver {
    fun logMessage(prefix: String, level: Int, text: String)
}

object MpvFormat {
    const val MPV_FORMAT_NONE: Int = 0
    const val MPV_FORMAT_STRING: Int = 1
    const val MPV_FORMAT_OSD_STRING: Int = 2
    const val MPV_FORMAT_FLAG: Int = 3
    const val MPV_FORMAT_INT64: Int = 4
    const val MPV_FORMAT_DOUBLE: Int = 5
    const val MPV_FORMAT_NODE: Int = 6
    const val MPV_FORMAT_NODE_ARRAY: Int = 7
    const val MPV_FORMAT_NODE_MAP: Int = 8
    const val MPV_FORMAT_BYTE_ARRAY: Int = 9
}

object MpvEvent {
    const val MPV_EVENT_NONE: Int = 0
    const val MPV_EVENT_SHUTDOWN: Int = 1
    const val MPV_EVENT_LOG_MESSAGE: Int = 2
    const val MPV_EVENT_GET_PROPERTY_REPLY: Int = 3
    const val MPV_EVENT_SET_PROPERTY_REPLY: Int = 4
    const val MPV_EVENT_COMMAND_REPLY: Int = 5
    const val MPV_EVENT_START_FILE: Int = 6
    const val MPV_EVENT_END_FILE: Int = 7
    const val MPV_EVENT_FILE_LOADED: Int = 8
    @Deprecated("")
    const val MPV_EVENT_IDLE: Int = 11
    @Deprecated("")
    const val MPV_EVENT_TICK: Int = 14
    const val MPV_EVENT_CLIENT_MESSAGE: Int = 16
    const val MPV_EVENT_VIDEO_RECONFIG: Int = 17
    const val MPV_EVENT_AUDIO_RECONFIG: Int = 18
    const val MPV_EVENT_SEEK: Int = 20
    const val MPV_EVENT_PLAYBACK_RESTART: Int = 21
    const val MPV_EVENT_PROPERTY_CHANGE: Int = 22
    const val MPV_EVENT_QUEUE_OVERFLOW: Int = 24
    const val MPV_EVENT_HOOK: Int = 25
}

object MpvLogLevel {
    const val MPV_LOG_LEVEL_NONE: Int = 0
    const val MPV_LOG_LEVEL_FATAL: Int = 10
    const val MPV_LOG_LEVEL_ERROR: Int = 20
    const val MPV_LOG_LEVEL_WARN: Int = 30
    const val MPV_LOG_LEVEL_INFO: Int = 40
    const val MPV_LOG_LEVEL_V: Int = 50
    const val MPV_LOG_LEVEL_DEBUG: Int = 60
    const val MPV_LOG_LEVEL_TRACE: Int = 70
}
