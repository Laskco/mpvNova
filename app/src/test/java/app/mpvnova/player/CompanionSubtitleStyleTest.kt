package app.mpvnova.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompanionSubtitleStyleTest {
    @Test
    fun disabledNeverBypassesStyle() {
        listOf("Signs & Songs", "Forced", "Full Subtitles", "With English Audio").forEach { title ->
            assertFalse(shouldPreserveCompanionSubtitleStyle(false, TrackMeta(1, title, "en", true)))
        }
    }

    @Test
    fun companionLabelsAndForcedFlagsBypassStyle() {
        listOf(
            "Signs & Songs [Cait-Sidhe]", "SIGNS AND SONGS", "Signs_&_Songs", "S&S",
            "Signs only", "Sign + Song", "Forced", "English (Forced)", "With English Audio",
        ).forEach { title ->
            assertTrue(title, shouldPreserveCompanionSubtitleStyle(true, TrackMeta(1, title, "en")))
        }
        assertTrue(shouldPreserveCompanionSubtitleStyle(true, TrackMeta(2, "", "fr", true)))
    }

    @Test
    fun fullSubtitlesAndCodecLabelsKeepCustomStyle() {
        listOf("Full Subtitles [Cait-Sidhe]", "Dialogue + Signs", "SDH", "ASS", "SSA",
            "Non-forced", "Not forced", "Forced: false", "With Japanese Audio").forEach { title ->
            assertFalse(title, shouldPreserveCompanionSubtitleStyle(true, TrackMeta(1, title, "en")))
        }
    }

    @Test
    fun detectionDoesNotRequireEnglishOrAValidLanguageTag() {
        listOf("en", "enm", "ja", "fr", "de", "es", "ar", "", "und").forEach { language ->
            assertTrue(shouldPreserveCompanionSubtitleStyle(true, TrackMeta(1, "Signs & Songs", language)))
            assertTrue(shouldPreserveCompanionSubtitleStyle(true, TrackMeta(1, "", language, true)))
        }
    }

    @Test
    fun switchingTracksOrTurningSettingOffRestoresStyle() {
        val signs = TrackMeta(1, "Signs & Songs", "en")
        val full = TrackMeta(2, "Full Subtitles", "en")
        assertTrue(shouldPreserveCompanionSubtitleStyle(true, signs))
        assertFalse(shouldPreserveCompanionSubtitleStyle(true, full))
        assertTrue(shouldPreserveCompanionSubtitleStyle(true, signs))
        assertFalse(shouldPreserveCompanionSubtitleStyle(false, signs))
        assertFalse(shouldPreserveCompanionSubtitleStyle(true, null))
    }
}
