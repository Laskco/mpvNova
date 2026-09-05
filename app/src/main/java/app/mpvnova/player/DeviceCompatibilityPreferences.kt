package app.mpvnova.player

import android.content.SharedPreferences

internal const val PREF_HI10P_FALLBACK = "hi10p_fallback"
internal const val PREF_MPEG2_SOFTWARE_FALLBACK = "mpeg2_software_fallback"
internal const val PREF_AUTOPAUSE_HI10P = "autopause_hi10p"

internal fun SharedPreferences.migrateDeviceCompatibilityPreferences(
    isShield: Boolean = isNvidiaShieldDevice(),
) = synchronized(this) {
    if (contains(PREF_HI10P_FALLBACK) && contains(PREF_MPEG2_SOFTWARE_FALLBACK) &&
        contains(PREF_AUTOPAUSE_HI10P)) return@synchronized

    val editor = edit()
    if (!contains(PREF_HI10P_FALLBACK)) {
        val legacyKey = if (isShield) "shield_decoder_mode" else "hi10p_fallback_other_devices"
        editor.putBoolean(PREF_HI10P_FALLBACK, getBoolean(legacyKey, isShield))
    }
    if (!contains(PREF_MPEG2_SOFTWARE_FALLBACK)) {
        val legacyKey = if (isShield) "shield_mpeg2_software_fallback"
            else "mpeg2_software_fallback_other_devices"
        editor.putBoolean(PREF_MPEG2_SOFTWARE_FALLBACK, getBoolean(legacyKey, isShield))
    }
    if (!contains(PREF_AUTOPAUSE_HI10P)) {
        editor.putBoolean(PREF_AUTOPAUSE_HI10P, isShield && getBoolean("autopause_shield_hi10p", true))
    }
    editor.apply()
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
