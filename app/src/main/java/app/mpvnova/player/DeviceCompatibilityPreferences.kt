package app.mpvnova.player

import android.content.SharedPreferences

internal const val PREF_HI10P_FALLBACK = "hi10p_fallback"
internal const val PREF_MPEG2_SOFTWARE_FALLBACK = "mpeg2_software_fallback"
internal const val PREF_AUTOPAUSE_HI10P = "autopause_hi10p"

private const val OLD_SHIELD_HI10P = "shield_decoder_mode"
private const val OLD_OTHER_HI10P = "hi10p_fallback_other_devices"
private const val OLD_SHIELD_MPEG2 = "shield_mpeg2_software_fallback"
private const val OLD_OTHER_MPEG2 = "mpeg2_software_fallback_other_devices"
private const val OLD_SHIELD_AUTOPAUSE = "autopause_shield_hi10p"

internal val legacyDeviceCompatibilityKeys = setOf(
    OLD_SHIELD_HI10P, OLD_OTHER_HI10P, OLD_SHIELD_MPEG2, OLD_OTHER_MPEG2, OLD_SHIELD_AUTOPAUSE,
)

internal fun SharedPreferences.migrateDeviceCompatibilityPreferences(
    isShield: Boolean = isNvidiaShieldDevice(),
) = synchronized(this) {
    if (contains(PREF_HI10P_FALLBACK) && contains(PREF_MPEG2_SOFTWARE_FALLBACK) &&
        contains(PREF_AUTOPAUSE_HI10P)) return@synchronized

    edit().apply {
        deviceCompatibilityUpdates(all, isShield).forEach { (key, value) -> putBoolean(key, value) }
    }.apply()
}

internal fun deviceCompatibilityUpdates(values: Map<String, *>, isShield: Boolean): Map<String, Boolean> =
    buildMap {
        if (!values.containsKey(PREF_HI10P_FALLBACK)) {
            val legacyKey = if (isShield) OLD_SHIELD_HI10P else OLD_OTHER_HI10P
            put(PREF_HI10P_FALLBACK, values[legacyKey] as? Boolean ?: isShield)
        }
        if (!values.containsKey(PREF_MPEG2_SOFTWARE_FALLBACK)) {
            val legacyKey = if (isShield) OLD_SHIELD_MPEG2 else OLD_OTHER_MPEG2
            put(PREF_MPEG2_SOFTWARE_FALLBACK, values[legacyKey] as? Boolean ?: isShield)
        }
        if (!values.containsKey(PREF_AUTOPAUSE_HI10P)) {
            put(PREF_AUTOPAUSE_HI10P, isShield && (values[OLD_SHIELD_AUTOPAUSE] as? Boolean ?: true))
        }
    }

internal fun SharedPreferences.hi10pFallbackEnabled(): Boolean {
    migrateDeviceCompatibilityPreferences()
    return getBoolean(PREF_HI10P_FALLBACK, isNvidiaShieldDevice())
}

internal fun SharedPreferences.mpeg2SoftwareFallbackEnabled(): Boolean {
    migrateDeviceCompatibilityPreferences()
    return getBoolean(PREF_MPEG2_SOFTWARE_FALLBACK, isNvidiaShieldDevice())
}

internal fun SharedPreferences.autoPauseHi10pEnabled(): Boolean {
    migrateDeviceCompatibilityPreferences()
    return getBoolean(PREF_AUTOPAUSE_HI10P, isNvidiaShieldDevice())
}
