package app.mpvnova.player

import android.content.SharedPreferences
import androidx.preference.PreferenceManager

internal val MPVView.currentDecoderMode: String
    get() {
        val requestedHwdec = getOptionString("hwdec").trim().lowercase()
        val requestedVo = requestedVideoOutput.primaryVideoOutput()
        val effectiveHwdec = when {
            requestedHwdec == MPV_VIEW_HWDECS -> hwdecActive.trim().lowercase()
            requestedHwdec.isNotBlank() -> requestedHwdec
            else -> hwdecActive.trim().lowercase()
        }
        return when {
            isShieldH10pSoftwareModeActive() -> MPVView.DECODER_MODE_SHIELD_H10P
            requestedVo.startsWith(MPV_VIEW_VO_GPU_NEXT) &&
                requestedHwdec == MPV_VIEW_HWDEC_MEDIACODEC_COPY -> MPVView.DECODER_MODE_GNEXT
            requestedHwdec == MPV_VIEW_HWDEC_MEDIACODEC -> MPVView.DECODER_MODE_HW_PLUS
            requestedHwdec == MPV_VIEW_HWDEC_MEDIACODEC_COPY -> MPVView.DECODER_MODE_HW
            requestedHwdec == MPV_VIEW_HWDECS -> if (hwdecActive == MPV_VIEW_HWDEC_MEDIACODEC) {
                MPVView.DECODER_MODE_HW_PLUS
            } else {
                MPVView.DECODER_MODE_HW
            }
            requestedHwdec == MPV_VIEW_HWDEC_NONE &&
                requestedVo.startsWith(MPV_VIEW_VO_GPU_NEXT) -> MPVView.DECODER_MODE_GNEXT
            requestedHwdec == MPV_VIEW_HWDEC_NONE -> MPVView.DECODER_MODE_SW
            hwdecActive == MPV_VIEW_HWDEC_MEDIACODEC -> MPVView.DECODER_MODE_HW_PLUS
            hwdecActive == MPV_VIEW_HWDEC_MEDIACODEC_COPY -> MPVView.DECODER_MODE_HW
            requestedVo.isNotBlank() || requestedHwdec.isNotBlank() -> MPVView.DECODER_MODE_MPV_CONF
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
            applyStandardDecoderTuning(sharedPreferences, MPV_VIEW_VO_GPU)
            setRuntimeOption("hwdec", MPV_VIEW_HWDEC_MEDIACODEC)
        }
        MPVView.DECODER_MODE_HW -> {
            applyStandardDecoderTuning(sharedPreferences, MPV_VIEW_VO_GPU)
            setRuntimeOption("hwdec", MPV_VIEW_HWDEC_MEDIACODEC_COPY)
        }
        MPVView.DECODER_MODE_SW -> {
            applyStandardDecoderTuning(sharedPreferences, MPV_VIEW_VO_GPU)
            setRuntimeOption("hwdec", MPV_VIEW_HWDEC_NONE)
        }
        MPVView.DECODER_MODE_GNEXT -> {
            applyStandardDecoderTuning(sharedPreferences, MPV_VIEW_VO_GPU_NEXT)
            setRuntimeVo(MPV_VIEW_VO_GPU_NEXT)
            setRuntimeOption("hwdec", MPV_VIEW_HWDEC_MEDIACODEC_COPY)
        }
        MPVView.DECODER_MODE_SHIELD_H10P -> {
            applyShieldHi10pFallback(sharedPreferences)
        }
        MPVView.DECODER_MODE_MPV_CONF -> {
            applyMpvConfDecoderOptions()
        }
    }
}

internal fun MPVView.fallbackGpuNextToGpu() {
    if (!requestedVideoOutput.trim().lowercase().startsWith(MPV_VIEW_VO_GPU_NEXT))
        return
    setRuntimeVo(MPV_VIEW_VO_GPU)
}

internal fun MPVView.fallbackGpuNextToCopyHwdec() {
    if (!requestedVideoOutput.trim().lowercase().startsWith(MPV_VIEW_VO_GPU_NEXT))
        return
    setRuntimeVo(MPV_VIEW_VO_GPU_NEXT)
    setRuntimeOption("hwdec", MPV_VIEW_HWDEC_MEDIACODEC_COPY)
}

internal fun MPVView.applyStandardDecoderTuning(sharedPreferences: SharedPreferences, vo: String) {
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

internal fun MPVView.setRuntimeVo(vo: String) {
    setVo(vo)
    mpvSetPropertyString("vo", vo)
}

internal fun setRuntimeOption(name: String, value: String) {
    mpvSetOptionString(name, value)
    mpvSetPropertyString(name, value)
}

internal fun MPVView.applyMpvConfDecoderOptions() {
    setVo(null)
    context.mpvConfOption("vo")?.let(::setRuntimeVo)
    context.mpvConfOption("hwdec")?.let { setRuntimeOption("hwdec", it) }
}

private fun String.primaryVideoOutput(): String {
    return trim()
        .lowercase()
        .substringBefore(",")
        .trim()
}

private fun selectedVideoTrackString(name: String): String {
    val count = mpvGetPropertyInt("track-list/count") ?: 0
    val selectedTrack = (0 until count).firstOrNull { index ->
        mpvGetPropertyString("track-list/$index/type") == "video" &&
            mpvGetPropertyBoolean("track-list/$index/selected") == true
    }
    return selectedTrack?.let { mpvGetPropertyString("track-list/$it/$name") } ?: ""
}
