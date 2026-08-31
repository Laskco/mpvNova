package app.mpvnova.player

import org.json.JSONObject

internal fun JSONObject.nullableString(key: String): String? =
    optString(key).takeIf { has(key) && it.isNotEmpty() }
