package app.mpvnova.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AssRenderingModePreferencesTest {

    @Test
    fun defaultsToPerformanceWhenUnset() {
        assertEquals(ASS_RENDERING_MODE_PERFORMANCE, assRenderingModeFromValues(emptyMap<String, Any>()))
    }

    @Test
    fun migratesLegacyBooleanPreference() {
        val enabled = mutableMapOf<String, Any>(LEGACY_PREF_EXPERIMENTAL_ASS_EDGES to true)
        enabled.migrateRetiredPreferences(isShield = false)
        assertEquals(ASS_RENDERING_MODE_PERFORMANCE, enabled[PREF_ASS_RENDERING_MODE])
        assertFalse(enabled.containsKey(LEGACY_PREF_EXPERIMENTAL_ASS_EDGES))

        val disabled = mutableMapOf<String, Any>(LEGACY_PREF_EXPERIMENTAL_ASS_EDGES to false)
        disabled.migrateRetiredPreferences(isShield = false)
        assertEquals(ASS_RENDERING_MODE_OFF, disabled[PREF_ASS_RENDERING_MODE])
        assertFalse(disabled.containsKey(LEGACY_PREF_EXPERIMENTAL_ASS_EDGES))
    }

    @Test
    fun preservesExplicitAssRenderingModeOverLegacyValue() {
        val values = mutableMapOf<String, Any>(
            LEGACY_PREF_EXPERIMENTAL_ASS_EDGES to false,
            PREF_ASS_RENDERING_MODE to ASS_RENDERING_MODE_ULTRA,
        )
        values.migrateRetiredPreferences(isShield = false)
        assertEquals(ASS_RENDERING_MODE_ULTRA, values[PREF_ASS_RENDERING_MODE])
        assertFalse(values.containsKey(LEGACY_PREF_EXPERIMENTAL_ASS_EDGES))
    }

    @Test
    fun normalizesUnknownValuesToPerformance() {
        assertEquals(ASS_RENDERING_MODE_OFF, normalizedAssRenderingMode("0"))
        assertEquals(ASS_RENDERING_MODE_PERFORMANCE, normalizedAssRenderingMode("1"))
        assertEquals(ASS_RENDERING_MODE_ULTRA, normalizedAssRenderingMode("2"))
        assertEquals(ASS_RENDERING_MODE_PERFORMANCE, normalizedAssRenderingMode("99"))
        assertEquals(ASS_RENDERING_MODE_PERFORMANCE, normalizedAssRenderingMode(null))
    }
}
