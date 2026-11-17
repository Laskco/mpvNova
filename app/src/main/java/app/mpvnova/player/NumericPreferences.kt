package app.mpvnova.player

internal fun Map<String, *>.numericInt(key: String, fallback: Int): Int =
    (this[key] as? Number)?.toInt() ?: fallback

internal fun Map<String, *>.numericFloat(key: String, fallback: Float): Float =
    (this[key] as? Number)?.toFloat() ?: fallback
