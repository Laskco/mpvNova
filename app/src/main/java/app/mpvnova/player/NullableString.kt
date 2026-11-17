package app.mpvnova.player

import android.content.SharedPreferences
import org.json.JSONObject

internal fun SharedPreferences.getStringIfTyped(key: String): String? = try {
    getString(key, null)
} catch (_: ClassCastException) {
    null
}

internal fun JSONObject.nullableString(key: String): String? =
    optString(key).takeIf { has(key) && it.isNotEmpty() }
