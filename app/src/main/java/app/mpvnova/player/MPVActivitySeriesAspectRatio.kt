package app.mpvnova.player

import android.content.Context
import androidx.preference.PreferenceManager.getDefaultSharedPreferences

internal const val PREF_SERIES_ASPECT_RATIO = "remember_series_aspect_ratio"
private const val SERIES_ASPECT_RATIO_STORE = "series_aspect_ratios_v1"

internal fun MPVActivity.saveSeriesAspectRatio(ratio: String) {
    if (!getDefaultSharedPreferences(applicationContext).getBoolean(PREF_SERIES_ASPECT_RATIO, false) ||
        ratio !in resources.getStringArray(R.array.aspect_ratios)
    ) return
    val key = currentAspectSeriesKey ?: resolveTrackSeriesKey()?.also { currentAspectSeriesKey = it } ?: return
    getSharedPreferences(SERIES_ASPECT_RATIO_STORE, Context.MODE_PRIVATE)
        .edit().putString(key, ratio).apply()
}

internal fun MPVActivity.applySeriesAspectRatio() {
    val enabled = getDefaultSharedPreferences(applicationContext).getBoolean(PREF_SERIES_ASPECT_RATIO, false)
    if (!enabled) return
    currentAspectSeriesKey = resolveTrackSeriesKey()
    val stored = currentAspectSeriesKey?.let {
        getSharedPreferences(SERIES_ASPECT_RATIO_STORE, Context.MODE_PRIVATE).all[it] as? String
    }
    val ratio = rememberedSeriesAspectRatio(enabled, stored, resources.getStringArray(R.array.aspect_ratios).toList())
    if (ratio != null) applyFileAspectRatio(ratio)
}

internal fun applyFileAspectRatio(ratio: String) {
    // File-local options prevent a series choice leaking into the next series or movie.
    aspectRatioMpvOptions(ratio).forEach { (name, value) ->
        mpvSetPropertyString("file-local-options/$name", value)
    }
}

internal fun aspectRatioMpvOptions(ratio: String): Map<String, String> = buildMap {
    val useContainerAspect = ratio == "-1" || ratio == "panscan"
    // Keep the stored Auto value compatible while avoiding mpv's deprecated -1 override.
    if (useContainerAspect) put("video-aspect-method", "container")
    put("video-aspect-override", if (useContainerAspect) "no" else ratio)
    put("panscan", if (ratio == "panscan") "1" else "0")
}
