package app.mpvnova.player

import android.content.pm.ActivityInfo

internal fun fixedOrientationForMode(mode: String): Int {
    return when (mode) {
        "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    }
}

internal fun autoOrientationForAspect(ratio: Float): Int {
    return when {
        ratio == 0f || ratio in (1f / ASPECT_RATIO_MIN)..ASPECT_RATIO_MIN ->
            ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        ratio > 1f -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }
}
