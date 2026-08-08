package app.mpvnova.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubtitleStyleSpecTest {
    @Test
    fun fullOpacityMapsToOpaqueAlphaFf() {
        // mpv uses standard alpha: 100% opacity -> alpha FF.
        assertEquals("#FFFFFFFF", mpvSubtitleColor(0xFFFFFF, 100))
    }

    @Test
    fun zeroOpacityMapsToTransparentAlphaZero() {
        assertEquals("#00000000", mpvSubtitleColor(0x000000, 0))
    }

    @Test
    fun halfOpacityRoundsToMidAlpha() {
        // 50% opacity -> alpha ~0x80 (128).
        assertEquals("#80FF453A", mpvSubtitleColor(0xFF453A, 50))
    }

    @Test
    fun opacityIsClampedIntoRange() {
        assertEquals("#FF0A84FF", mpvSubtitleColor(0x0A84FF, 150))
        assertEquals("#000A84FF", mpvSubtitleColor(0x0A84FF, -20))
    }

    @Test
    fun colorStringIgnoresHighBitsOfRgb() {
        // Callers may pass an ARGB int; only the low 24 bits are colour.
        assertEquals("#FF30D158", mpvSubtitleColor(0xFF30D158.toInt(), 100))
    }

    @Test
    fun assColorUsesInvertedAlphaAndBgrOrder() {
        assertEquals("&H0000840A", assSubtitleColor(0x0A8400, 100))
        assertEquals("&H7F58D130", assSubtitleColor(0x30D158, 50))
        assertEquals("&HFF000000", assSubtitleColor(0x000000, 0))
    }

    @Test
    fun assOverridesApplyToEveryNamedStyle() {
        val overrides = buildAssStyleOverrides(
            AssStyleOverrideSpec(
                fontFamily = "sans-serif",
                textRgb = 0xFFFFFF,
                textOpacity = 100,
                borderRgb = 0x000000,
                borderSize = 3.0,
                backgroundRgb = 0x000000,
                backgroundOpacity = 0,
                shadowRgb = 0x000000,
                shadowSize = 2.0,
                edge = SubtitleEdgeStyle.OUTLINE,
                bold = true,
                italic = true,
                spacing = 0.0,
                blur = 0.0,
            ),
        )

        assertTrue(overrides.none { it.substringBefore('=').contains('.') })
        assertTrue("Bold=-1" in overrides)
        assertTrue("Italic=-1" in overrides)
        assertTrue("Shadow=0.0" in overrides)
    }

    @Test
    fun assAttributeOverridesDoNotAssumeDefaultStyleName() {
        assertEquals(
            listOf("Bold=-1", "Italic=0"),
            buildAssAttributeOverrides(bold = true, italic = false),
        )
    }

    @Test
    fun defaultColorIdsResolveToKnownSwatches() {
        assertEquals("white", SUBTITLE_COLOR_OPTIONS[subtitleColorOptionIndex(SUBTITLE_TEXT_COLOR_DEFAULT_ID)].id)
        assertEquals("black", SUBTITLE_COLOR_OPTIONS[subtitleColorOptionIndex(SUBTITLE_BORDER_COLOR_DEFAULT_ID)].id)
    }

    @Test
    fun unknownColorIdFallsBackToFirstSwatch() {
        assertEquals(0, subtitleColorOptionIndex("not-a-real-color"))
    }

    @Test
    fun nearestOpacityIndexSnapsToTenPercentSteps() {
        assertEquals(0, nearestOpacityIndex(2))
        assertEquals(5, nearestOpacityIndex(48))
        assertEquals(10, nearestOpacityIndex(97))
    }
}
