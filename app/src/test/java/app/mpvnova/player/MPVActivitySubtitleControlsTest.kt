package app.mpvnova.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MPVActivitySubtitleControlsTest {
    @Test
    fun compactPlayerBarUsesItsRenderedTopEdge() {
        val offset = calculateSubtitleControlsOffsetPercent(
            playerHeightPx = 1080,
            controlsTopPx = 980,
            clearancePx = 6,
            baseMarginScaledPx = 34,
        )

        assertEquals(5, offset)
    }

    @Test
    fun tallerPlayerBarProducesLargerMargin() {
        val compactOffset = calculateSubtitleControlsOffsetPercent(1080, 980, 6, 34)
        val tallOffset = calculateSubtitleControlsOffsetPercent(1080, 800, 6, 34)

        assertTrue(tallOffset > compactOffset)
        assertEquals(22, tallOffset)
    }

    @Test
    fun existingSubtitleMarginIsNotStackedOnTop() {
        val offset = calculateSubtitleControlsOffsetPercent(
            playerHeightPx = 720,
            controlsTopPx = 650,
            clearancePx = 4,
            baseMarginScaledPx = 34,
        )

        assertEquals(6, offset)
    }

    @Test
    fun invalidLayoutNeedsNoTemporaryMargin() {
        assertEquals(0, calculateSubtitleControlsOffsetPercent(0, 0, 0, 34))
        assertEquals(0, calculateSubtitleControlsOffsetPercent(1080, 1080, 0, 34))
    }
}
