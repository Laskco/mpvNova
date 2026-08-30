package app.mpvnova.player

import android.os.Build
import android.util.Log

private const val FIRE_TV_STICK_4K_2019_MODEL = "AFTDCT31"
private const val UHD_WIDTH = 3840
private const val UHD_HEIGHT = 2160
private const val EDGE_CROP_ROWS = 2

internal data class VideoTrackEdgeInfo(
    val codec: String,
    val dolbyVisionProfile: Int?,
    val width: Int,
    val height: Int,
)

internal fun fireTvVideoEdgeCrop(
    model: String,
    track: VideoTrackEdgeInfo,
): String? {
    val isHevc = track.codec.equals("hevc", ignoreCase = true) ||
        track.codec.equals("h265", ignoreCase = true)
    val affected = model.equals(FIRE_TV_STICK_4K_2019_MODEL, ignoreCase = true) &&
        isHevc &&
        track.dolbyVisionProfile == 7 &&
        track.width == UHD_WIDTH &&
        track.height == UHD_HEIGHT
    return if (affected) {
        "${track.width}x${track.height - EDGE_CROP_ROWS}+0+0"
    } else {
        null
    }
}

internal fun MPVActivity.applyFireTvVideoEdgeCropIfNeeded() {
    if (fireTvVideoEdgeCropApplied)
        return
    val crop = fireTvVideoEdgeCrop(Build.MODEL, selectedVideoTrackEdgeInfo()) ?: return
    Log.i(MPV_ACTIVITY_TAG, "Applying Fire TV decoder edge crop: $crop")
    setRuntimeOption("video-crop", crop)
    fireTvVideoEdgeCropApplied = true
}

internal fun MPVActivity.clearFireTvVideoEdgeCrop() {
    if (!fireTvVideoEdgeCropApplied)
        return
    setRuntimeOption("video-crop", "")
    fireTvVideoEdgeCropApplied = false
}

private fun selectedVideoTrackEdgeInfo(): VideoTrackEdgeInfo {
    val trackCount = mpvGetPropertyInt("track-list/count") ?: 0
    val selectedTrack = (0 until trackCount).firstOrNull { index ->
        mpvGetPropertyString("track-list/$index/type") == "video" &&
            mpvGetPropertyBoolean("track-list/$index/selected") == true
    }
    val prefix = selectedTrack?.let { "track-list/$it" }
    val codec = prefix?.let { mpvGetPropertyString("$it/codec") }
        ?: mpvGetPropertyString("video-codec")
        ?: ""
    val profile = prefix?.let { mpvGetPropertyInt("$it/dolby-vision-profile") }
    val width = prefix?.let { mpvGetPropertyInt("$it/demux-w") }
        ?: mpvGetPropertyInt("video-params/w")
        ?: 0
    val height = prefix?.let { mpvGetPropertyInt("$it/demux-h") }
        ?: mpvGetPropertyInt("video-params/h")
        ?: 0
    return VideoTrackEdgeInfo(codec, profile, width, height)
}
