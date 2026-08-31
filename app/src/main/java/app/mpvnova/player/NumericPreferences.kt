package app.mpvnova.player

import android.content.SharedPreferences

internal fun SharedPreferences.numericInt(key: String, fallback: Int): Int =
    (all[key] as? Number)?.toInt() ?: fallback

internal fun SharedPreferences.numericFloat(key: String, fallback: Float): Float =
    (all[key] as? Number)?.toFloat() ?: fallback
