package app.mpvnova.player

import android.content.SharedPreferences
import android.os.Build

internal fun defaultVo(sharedPreferences: SharedPreferences): String {
    return if (sharedPreferences.getBoolean("gpu_next", false)) "gpu-next" else "gpu"
}

internal fun defaultHwdec(sharedPreferences: SharedPreferences): String {
    return if (sharedPreferences.getBoolean("hardware_decoding", true)) MPV_VIEW_HWDECS else "no"
}

internal fun MPVView.defaultVideoSync(sharedPreferences: SharedPreferences): String {
    return sharedPreferences.getString(
        "video_sync",
        resources.getString(R.string.pref_video_interpolation_sync_default)
    ) ?: resources.getString(R.string.pref_video_interpolation_sync_default)
}

internal fun defaultDemuxerCacheBytes(): Int {
    val cacheMegs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
        MPV_VIEW_MODERN_DEMUXER_CACHE_MIB
    } else {
        MPV_VIEW_LEGACY_DEMUXER_CACHE_MIB
    }
    return cacheMegs * MPV_VIEW_BYTES_PER_MIB
}
