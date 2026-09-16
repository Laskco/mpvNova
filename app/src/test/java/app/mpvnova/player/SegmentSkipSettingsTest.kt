package app.mpvnova.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SegmentSkipSettingsTest {
    private val episodeKinds = listOf(SkipSegmentKind.INTRO, SkipSegmentKind.OUTRO, SkipSegmentKind.RECAP)

    @Test fun freshSettingsPreserveEpisodeAndMovieDefaults() {
        val modes = readSegmentSkipModes(emptyMap<String, Any>())
        episodeKinds.forEach { assertEquals(SkipSegmentsMode.AUTO, modes[it]) }
        assertEquals(SkipSegmentsMode.BUTTON, modes[SkipSegmentKind.END_CREDITS])
        assertEquals(SkipSegmentsMode.BUTTON, modes[SkipSegmentKind.POST_CREDITS])
    }

    @Test fun everyLegacyCombinedChoiceSeedsAllEpisodeKinds() {
        SkipSegmentsMode.entries.forEach { legacy ->
            val modes = readSegmentSkipModes(mapOf("skip_segments_mode" to legacy.prefValue))
            episodeKinds.forEach { assertEquals(legacy, modes[it]) }
            assertEquals(SkipSegmentsMode.BUTTON, modes[SkipSegmentKind.END_CREDITS])
            assertEquals(SkipSegmentsMode.BUTTON, modes[SkipSegmentKind.POST_CREDITS])
        }
    }

    @Test fun oldestBooleanPreferenceIsStillRespected() {
        listOf(true, false).forEach { auto ->
            val modes = readSegmentSkipModes(mapOf("auto_skip_segments" to auto))
            episodeKinds.forEach { assertEquals(if (auto) SkipSegmentsMode.AUTO else SkipSegmentsMode.OFF, modes[it]) }
        }
    }

    @Test fun combinedPreferenceTakesPrecedenceOverOldBoolean() {
        val modes = readSegmentSkipModes(mapOf("skip_segments_mode" to "button", "auto_skip_segments" to false))
        episodeKinds.forEach { assertEquals(SkipSegmentsMode.BUTTON, modes[it]) }
    }

    @Test fun individualChoicesOverrideLegacyWithoutChangingOthers() {
        episodeKinds.forEach { changed ->
            val modes = readSegmentSkipModes(mapOf("skip_segments_mode" to "off", changed.preferenceKey to "button"))
            episodeKinds.forEach {
                assertEquals(if (it == changed) SkipSegmentsMode.BUTTON else SkipSegmentsMode.OFF, modes[it])
            }
        }
    }

    @Test fun existingMovieChoicesSurvive() {
        val modes = readSegmentSkipModes(mapOf("movie_credits_mode" to "auto", "post_credits_mode" to "off"))
        assertEquals(SkipSegmentsMode.AUTO, modes[SkipSegmentKind.END_CREDITS])
        assertEquals(SkipSegmentsMode.OFF, modes[SkipSegmentKind.POST_CREDITS])
    }

    @Test fun persistedModesSurviveReopeningSettings() {
        val initial = readSegmentSkipModes(mapOf("skip_segments_mode" to "button", "outro_skip_mode" to "off"))
        val saved = initial.mapKeys { it.key.preferenceKey }.mapValues { it.value.prefValue }
        assertEquals(initial, readSegmentSkipModes(saved))
    }

    @Test fun invalidNewModesUseSafeCategoryFallbacks() {
        val values = mapOf(
            "skip_segments_mode" to "off", "intro_skip_mode" to "invalid", "movie_credits_mode" to "invalid",
        )
        val modes = readSegmentSkipModes(values)
        assertEquals(SkipSegmentsMode.OFF, modes[SkipSegmentKind.INTRO])
        assertEquals(SkipSegmentsMode.BUTTON, modes[SkipSegmentKind.END_CREDITS])
    }

    @Test fun settingsDoNotAlterButtonDurationOrOtherPreferences() {
        val values = mapOf("skip_button_display" to "10000", "unrelated" to true)
        val snapshot = values.toMap()
        readSegmentSkipModes(values)
        assertEquals(snapshot, values)
    }
}
