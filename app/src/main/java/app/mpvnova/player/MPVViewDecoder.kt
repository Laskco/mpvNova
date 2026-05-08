package app.mpvnova.player

import android.content.SharedPreferences
import androidx.preference.PreferenceManager

internal val MPVView.currentDecoderMode: String
    get() {
        val requestedHwdec = getOptionString("hwdec").trim().lowercase()
        val requestedVo = requestedVideoOutput.trim().lowercase()
        return when {
            isShieldH10pModeActive() -> MPVView.DECODER_MODE_SHIELD_H10P
            requestedVo.startsWith("gpu-next") && requestedHwdec == "mediacodec-copy" -> MPVView.DECODER_MODE_GNEXT
            requestedHwdec == "mediacodec" -> MPVView.DECODER_MODE_HW_PLUS
            requestedHwdec == "mediacodec-copy" -> MPVView.DECODER_MODE_HW
            requestedHwdec == MPV_VIEW_HWDECS -> if (hwdecActive == "mediacodec") {
                MPVView.DECODER_MODE_HW_PLUS
            } else {
                MPVView.DECODER_MODE_HW
            }
            requestedHwdec == "no" && requestedVo.startsWith("gpu-next") -> MPVView.DECODER_MODE_GNEXT
            requestedHwdec == "no" -> MPVView.DECODER_MODE_SW
            hwdecActive == "mediacodec" -> MPVView.DECODER_MODE_HW_PLUS
            hwdecActive == "mediacodec-copy" -> MPVView.DECODER_MODE_HW
            else -> MPVView.DECODER_MODE_SW
        }
    }

internal fun MPVView.isHi10pH264Video(): Boolean {
    val codec = selectedVideoTrackString("codec").ifBlank {
        mpvGetPropertyString("video-codec") ?: ""
    }.trim().lowercase()
    val profile = selectedVideoTrackString("codec-profile").trim().lowercase()
    val pixelFormat = getOptionString("video-params/pixelformat").trim().lowercase()
    return codec == "h264" && (
            profile.contains("10") ||
            profile.contains("hi10") ||
            pixelFormat.contains("p10") ||
            pixelFormat.contains("10le")
    )
}

internal fun MPVView.applyDecoderMode(mode: String) {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    when (mode) {
        MPVView.DECODER_MODE_HW_PLUS -> {
            applyStandardDecoderTuning(sharedPreferences, "gpu")
            setRuntimeOption("hwdec", "mediacodec")
        }
        MPVView.DECODER_MODE_HW -> {
            applyStandardDecoderTuning(sharedPreferences, "gpu")
            setRuntimeOption("hwdec", "mediacodec-copy")
        }
        MPVView.DECODER_MODE_SW -> {
            applyStandardDecoderTuning(sharedPreferences, "gpu")
            setRuntimeOption("hwdec", "no")
        }
        MPVView.DECODER_MODE_GNEXT -> {
            applyStandardDecoderTuning(sharedPreferences, "gpu-next")
            setRuntimeVo("gpu-next")
            setRuntimeOption("hwdec", "mediacodec-copy")
        }
        MPVView.DECODER_MODE_SHIELD_H10P -> {
            setRuntimeVo("gpu-next")
            setRuntimeOption("hwdec", "no")
            setRuntimeOption("vd-lavc-threads", "6")
            setRuntimeOption("vd-lavc-fast", "yes")
            setRuntimeOption("vd-lavc-skiploopfilter", "nonref")
            setRuntimeOption("framedrop", "vo")
            setRuntimeOption("gpu-api", "opengl")
            setRuntimeOption("video-sync", "display-resample")
            setRuntimeOption("cache", "no")
            setRuntimeOption("demuxer-max-bytes", MPV_VIEW_SHIELD_H10P_DEMUXER_BYTES.toString())
            setRuntimeOption("demuxer-max-back-bytes", MPV_VIEW_SHIELD_H10P_DEMUXER_BYTES.toString())
        }
    }
}

internal fun MPVView.fallbackGpuNextToGpu() {
    if (!requestedVideoOutput.trim().lowercase().startsWith("gpu-next"))
        return
    setRuntimeVo("gpu")
}

internal fun MPVView.fallbackGpuNextToCopyHwdec() {
    if (!requestedVideoOutput.trim().lowercase().startsWith("gpu-next"))
        return
    setRuntimeVo("gpu-next")
    setRuntimeOption("hwdec", "mediacodec-copy")
}

private fun MPVView.applyStandardDecoderTuning(sharedPreferences: SharedPreferences, vo: String) {
    setRuntimeVo(vo)
    setRuntimeOption("video-sync", defaultVideoSync(sharedPreferences))
    if (sharedPreferences.getBoolean("video_fastdecode", false)) {
        setRuntimeOption("vd-lavc-fast", "yes")
        setRuntimeOption("vd-lavc-skiploopfilter", "nonkey")
    } else {
        setRuntimeOption("vd-lavc-fast", "no")
        setRuntimeOption("vd-lavc-skiploopfilter", "default")
    }
    setRuntimeOption("vd-lavc-threads", "0")
    setRuntimeOption("framedrop", "no")
    setRuntimeOption("gpu-api", "auto")
    setRuntimeOption("cache", "auto")
    val cacheBytes = defaultDemuxerCacheBytes().toString()
    setRuntimeOption("demuxer-max-bytes", cacheBytes)
    setRuntimeOption("demuxer-max-back-bytes", cacheBytes)
}

private fun MPVView.setRuntimeVo(vo: String) {
    setVo(vo)
    mpvSetPropertyString("vo", vo)
}

private fun setRuntimeOption(name: String, value: String) {
    mpvSetOptionString(name, value)
    mpvSetPropertyString(name, value)
}

private fun selectedVideoTrackString(name: String): String {
    val count = mpvGetPropertyInt("track-list/count") ?: 0
    val selectedTrack = (0 until count).firstOrNull { index ->
        mpvGetPropertyString("track-list/$index/type") == "video" &&
            mpvGetPropertyBoolean("track-list/$index/selected") == true
    }
    return selectedTrack?.let { mpvGetPropertyString("track-list/$it/$name") } ?: ""
}

private fun MPVView.isShieldH10pModeActive(): Boolean {
    return matchesOption("vd-lavc-threads", "6") &&
        matchesOption("vd-lavc-fast", "yes") &&
        matchesOption("vd-lavc-skiploopfilter", "nonref") &&
        matchesOption("framedrop", "vo") &&
        matchesOption("gpu-api", "opengl") &&
        matchesOption("video-sync", "display-resample") &&
        matchesOption("cache", "no") &&
        matchesOption("demuxer-max-bytes", MPV_VIEW_SHIELD_H10P_DEMUXER_BYTES.toString(), "50mib")
}

private fun MPVView.matchesOption(name: String, vararg expected: String): Boolean {
    val value = getOptionString(name).trim().lowercase()
    return expected.any { value == it.lowercase() }
}
