package app.mpvnova.player

internal var MPVView.paused: Boolean?
    get() = mpvGetPropertyBoolean("pause")
    set(paused) = mpvSetPropertyBoolean("pause", paused!!)

internal var MPVView.timePos: Double?
    get() = mpvGetPropertyDouble("time-pos/full")
    set(progress) = mpvSetPropertyDouble("time-pos", progress!!)

/** name of currently active hardware decoder or "no" */
internal val MPVView.hwdecActive: String
    get() = mpvGetPropertyString("hwdec-current") ?: "no"

/** name of the active video output backend, if available */
internal val MPVView.activeVideoOutput: String
    get() = mpvGetPropertyString("current-vo")
        ?: mpvGetPropertyString("options/vo")
        ?: ""

/** name of the requested video output backend */
internal val MPVView.requestedVideoOutput: String
    get() = getOptionString("vo")

internal var MPVView.playbackSpeed: Double?
    get() = mpvGetPropertyDouble("speed")
    set(speed) = mpvSetPropertyDouble("speed", speed!!)

internal var MPVView.subDelay: Double?
    get() = mpvGetPropertyDouble("sub-delay")
    set(speed) = mpvSetPropertyDouble("sub-delay", speed!!)

internal var MPVView.secondarySubDelay: Double?
    get() = mpvGetPropertyDouble("secondary-sub-delay")
    set(speed) = mpvSetPropertyDouble("secondary-sub-delay", speed!!)

internal val MPVView.estimatedVfFps: Double?
    get() = mpvGetPropertyDouble("estimated-vf-fps")

internal fun MPVView.getOptionString(name: String): String {
    return mpvGetPropertyString(name)
        ?: mpvGetPropertyString("options/$name")
        ?: ""
}
