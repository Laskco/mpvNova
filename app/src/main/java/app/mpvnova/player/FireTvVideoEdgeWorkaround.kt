package app.mpvnova.player

import android.os.Build
import android.util.Log

private const val FIRE_TV_STICK_4K_2019_MODEL = "AFTDCT31"
private const val UHD_WIDTH = 3840
private const val UHD_HEIGHT = 2160
private const val EDGE_CROP_ROWS = 16
private const val DOLBY_VISION_GAMMA = "dolbyvision"

internal data class VideoTrackEdgeInfo(
    val codec: String,
    val dolbyVisionProfile: Int?,
    val gamma: String,
    val width: Int,
    val height: Int,
)

internal fun fireTvVideoEdgeCrop(
    model: String,
    track: VideoTrackEdgeInfo,
): String? {
    val isHevc = track.codec.equals("hevc", ignoreCase = true) ||
        track.codec.equals("h265", ignoreCase = true)
    val isDolbyVision = track.dolbyVisionProfile == 7 ||
        track.gamma.equals(DOLBY_VISION_GAMMA, ignoreCase = true)
    val affected = model.equals(FIRE_TV_STICK_4K_2019_MODEL, ignoreCase = true) &&
        isHevc &&
        isDolbyVision &&
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
    val track = selectedVideoTrackEdgeInfo()
    if (Build.MODEL.equals(FIRE_TV_STICK_4K_2019_MODEL, ignoreCase = true)) {
        Log.i(
            MPV_ACTIVITY_TAG,
            "Fire TV decoder edge probe codec=${track.codec} " +
                "doviProfile=${track.dolbyVisionProfile} gamma=${track.gamma} " +
                "size=${track.width}x${track.height}",
        )
    }
    val crop = fireTvVideoEdgeCrop(Build.MODEL, track) ?: return
    setRuntimeOption("video-crop", crop)
    fireTvVideoEdgeCropApplied = true
    val appliedCrop = mpvGetPropertyString("video-crop")
    Log.i(
        MPV_ACTIVITY_TAG,
        "Fire TV decoder edge crop requested=$crop applied=$appliedCrop " +
            "codec=${track.codec} doviProfile=${track.dolbyVisionProfile} gamma=${track.gamma}",
    )
}

internal fun MPVActivity.clearFireTvVideoEdgeCrop() {
    if (!fireTvVideoEdgeCropApplied)
        return
    setRuntimeOption("video-crop", "")
    fireTvVideoEdgeCropApplied = false
}

private fun selectedVideoTrackEdgeInfo(): VideoTrackEdgeInfo {
    val prefix = selectedVideoTrackPrefix()
    return VideoTrackEdgeInfo(
        codec = firstStringProperty(prefix?.let { "$it/codec" }, "video-codec"),
        dolbyVisionProfile = firstIntProperty(
            prefix?.let { "$it/dolby-vision-profile" },
            "current-tracks/video/dolby-vision-profile",
        ),
        gamma = firstStringProperty("video-params/gamma", "video-dec-params/gamma"),
        width = firstIntProperty(prefix?.let { "$it/demux-w" }, "video-params/w") ?: 0,
        height = firstIntProperty(prefix?.let { "$it/demux-h" }, "video-params/h") ?: 0,
    )
}

private fun selectedVideoTrackPrefix(): String? {
    val count = mpvGetPropertyInt("track-list/count") ?: 0
    val selectedTrack = (0 until count).firstOrNull { index ->
        mpvGetPropertyString("track-list/$index/type") == "video" &&
            mpvGetPropertyBoolean("track-list/$index/selected") == true
    }
    return selectedTrack?.let { "track-list/$it" }
}

private fun firstStringProperty(vararg names: String?): String = names
    .asSequence()
    .filterNotNull()
    .mapNotNull(::mpvGetPropertyString)
    .firstOrNull()
    .orEmpty()

private fun firstIntProperty(vararg names: String?): Int? = names
    .asSequence()
    .filterNotNull()
    .mapNotNull(::mpvGetPropertyInt)
    .firstOrNull()
