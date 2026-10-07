package app.mpvnova.player

internal fun MPVActivity.recordPlaybackDiagnostic(event: String) {
    MpvLogRingBuffer.recordPlaybackEvent(
        "$event position_ms=${psc.position} duration_ms=${psc.duration} " +
            "paused=${psc.pause} last_selected_audio=[$diagnosticAudioTrack]"
    )
}

internal fun MPVActivity.updateDiagnosticAudioTrack() {
    val id = mpvGetPropertyInt("current-tracks/audio/id") ?: return
    val language = mpvGetPropertyString("current-tracks/audio/lang") ?: "unknown"
    val codec = mpvGetPropertyString("current-tracks/audio/codec") ?: "unknown"
    val track = "id=$id language=$language codec=$codec"
    if (track != diagnosticAudioTrack) {
        diagnosticAudioTrack = track
        recordPlaybackDiagnostic("audio-selected")
    }
}

private const val END_REASON_EOF = 0L
private const val END_REASON_STOP = 2L
private const val END_REASON_QUIT = 3L
private const val END_REASON_ERROR = 4L
private const val END_REASON_REDIRECT = 5L

internal fun playbackEndReason(reason: Long?): String = when (reason) {
    END_REASON_EOF -> "eof"
    END_REASON_STOP -> "stop"
    END_REASON_QUIT -> "quit"
    END_REASON_ERROR -> "error"
    END_REASON_REDIRECT -> "redirect"
    else -> "unknown($reason)"
}
