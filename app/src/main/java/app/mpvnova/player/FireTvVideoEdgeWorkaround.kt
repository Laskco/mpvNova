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
    val track = selectedVideoTrackEdgeInfo()
    val mode = videoEdgeCleanupValue()
    val crop = videoEdgeCleanupCrop(mode, Build.MODEL, track)
    videoEdgeCropSession.update(crop, { mpvGetPropertyString("video-crop") }) { value ->
        setRuntimeOption("video-crop", value)
        Log.i(MPV_ACTIVITY_TAG, "Video edge cleanup mode=$mode requested=$value " +
            "applied=${mpvGetPropertyString("video-crop")} size=${track.width}x${track.height} " +
            "codec=${track.codec} doviProfile=${track.dolbyVisionProfile} gamma=${track.gamma}")
    }
}

internal fun MPVActivity.clearFireTvVideoEdgeCrop() {
    videoEdgeCropSession.clear({ mpvGetPropertyString("video-crop") }) { setRuntimeOption("video-crop", it) }
}

internal fun videoEdgeCleanupCrop(mode: String, model: String, track: VideoTrackEdgeInfo): String? {
    val rows = mode.takeIf { it in VIDEO_EDGE_CLEANUP_VALUES }?.toIntOrNull()
    return when {
        mode == "auto" -> fireTvVideoEdgeCrop(model, track)
        rows != null && track.width > 0 && track.height > rows ->
            "${track.width}x${track.height - rows}+0+0"
        else -> null
    }
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
        width = firstIntProperty("video-dec-params/w", prefix?.let { "$it/demux-w" }) ?: 0,
        height = firstIntProperty("video-dec-params/h", prefix?.let { "$it/demux-h" }) ?: 0,
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
    .firstOrNull { it.isNotBlank() }
    .orEmpty()

private fun firstIntProperty(vararg names: String?): Int? = names
    .asSequence()
    .filterNotNull()
    .mapNotNull(::mpvGetPropertyInt)
    .firstOrNull { it > 0 }
