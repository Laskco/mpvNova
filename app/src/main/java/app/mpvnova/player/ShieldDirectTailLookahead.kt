package app.mpvnova.player

import android.util.Log

private const val TAIL_WINDOW_MS = 3_000L
private const val VIDEO_LOOKAHEAD_OPTION = "file-local-options/video-latency-hacks"

/** Confined to the mpv event thread; mpv restores the file-local option at EOF. */
internal class ShieldDirectTailLookahead {
    private var fileLoaded = false
    private var inTail = false
    private var ownsOption = false

    fun reset() {
        fileLoaded = false
        inTail = false
        ownsOption = false
    }

    fun fileLoaded() {
        fileLoaded = true
        refreshPosition()
    }

    fun refreshPosition() {
        if (!fileLoaded) return
        val positionMs = mpvGetPropertyDouble("time-pos/full")?.times(MPV_MILLIS_PER_SECOND_DOUBLE)?.toLong()
        val durationMs = mpvGetPropertyDouble("duration/full")?.times(MPV_MILLIS_PER_SECOND_DOUBLE)?.toLong()
        if (positionMs != null && durationMs != null) update(positionMs, durationMs, recheck = true)
    }

    fun update(positionMs: Long, durationMs: Long, recheck: Boolean = false) {
        val nearEnd = isShieldDirectTailWindow(positionMs, durationMs)
        if (!fileLoaded || (!recheck && nearEnd == inTail)) return
        inTail = nearEnd

        val useReducedLookahead = nearEnd &&
            mpvGetPropertyString("current-vo") == MPV_VIEW_VO_GPU_NEXT &&
            mpvGetPropertyString("hwdec-current") == MPV_VIEW_HWDEC_MEDIACODEC &&
            mpvGetPropertyString("current-tracks/video/codec") == "h264" &&
            (mpvGetPropertyBoolean("options/interpolation") == false ||
                mpvGetPropertyString("options/video-sync") == "audio")

        if (useReducedLookahead && !ownsOption) {
            if (mpvGetPropertyBoolean(VIDEO_LOOKAHEAD_OPTION) != false) return
            // Leave the last decoded frame available to render while NVIDIA switches
            // output formats, instead of waiting for another decoder surface first.
            mpvSetPropertyBoolean(VIDEO_LOOKAHEAD_OPTION, true)
            ownsOption = true
            Log.v(MPV_ACTIVITY_TAG, "gpu-next Direct: reduced Shield H.264 tail lookahead")
        } else if (!useReducedLookahead && ownsOption) {
            mpvSetPropertyBoolean(VIDEO_LOOKAHEAD_OPTION, false)
            ownsOption = false
        }
    }
}

private fun isShieldDirectTailWindow(positionMs: Long, durationMs: Long): Boolean =
    durationMs > TAIL_WINDOW_MS && positionMs > 0L &&
        positionMs in (durationMs - TAIL_WINDOW_MS)..durationMs
