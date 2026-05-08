package app.mpvnova.player

private const val DEFAULT_PLAYBACK_SPEED = 1.0
private const val ROTATED_ASPECT_NUMERATOR = 1.0

/**
 * Returns the video aspect ratio. Rotation is taken into account.
 */
internal fun MPVView.getVideoAspect(): Double? {
    return mpvGetPropertyDouble("video-params/aspect")?.let {
        if (it < MPV_VIEW_MIN_VALID_ASPECT)
            return 0.0
        val rot = mpvGetPropertyInt("video-params/rotate") ?: 0
        if (rot % MPV_VIEW_HALF_ROTATION_DEGREES == MPV_VIEW_RIGHT_ANGLE_DEGREES)
            ROTATED_ASPECT_NUMERATOR / it
        else
            it
    }
}

internal fun MPVView.setAudioSessionId(id: Int) {
    mpvSetPropertyInt("audiotrack-session-id", id)
    mpvSetPropertyInt("aaudio-session-id", id)
}

internal fun MPVView.cyclePause() = mpvCommand(arrayOf("cycle", "pause"))

internal fun MPVView.cycleAudio() = mpvCommand(arrayOf("cycle", "audio"))

internal fun MPVView.cycleSub() = mpvCommand(arrayOf("cycle", "sub"))

internal fun MPVView.cycleHwdec() = mpvCommand(arrayOf("cycle-values", "hwdec", MPV_VIEW_HWDECS, "no"))

internal fun MPVView.cycleSpeed() {
    val currentSpeed = playbackSpeed ?: DEFAULT_PLAYBACK_SPEED
    val index = MPV_VIEW_PLAYBACK_SPEED_STEPS.indexOfFirst { it > currentSpeed }
    playbackSpeed = MPV_VIEW_PLAYBACK_SPEED_STEPS[if (index == -1) 0 else index]
}
