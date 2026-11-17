package app.mpvnova.player

// Match SharedPreferences' strict types for values that were not read through legacy coercion.
internal fun Map<String, *>.getString(key: String, fallback: String?): String? =
    (this[key] as String?) ?: fallback

internal fun Map<String, *>.getInt(key: String, fallback: Int): Int =
    (this[key] as Int?) ?: fallback

internal fun Map<String, *>.getBoolean(key: String, fallback: Boolean): Boolean =
    (this[key] as Boolean?) ?: fallback

@Suppress("UNCHECKED_CAST")
internal fun Map<String, *>.getStringSet(key: String, fallback: Set<String>): Set<String> =
    (this[key] as Set<String>?) ?: fallback
