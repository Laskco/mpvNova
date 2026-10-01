package app.mpvnova.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SeriesAspectRatioTest {
    private val choices = listOf("-1", "panscan", "16:9", "16:10", "4:3", "2.35")

    @Test fun disabledDoesNotOverridePlayback() {
        (choices + listOf(null, "invalid")).forEach {
            assertNull(rememberedSeriesAspectRatio(false, it, choices))
        }
    }

    @Test fun allSupportedChoicesRestoreIncludingOriginalAndPanscan() {
        choices.forEach { assertEquals(it, rememberedSeriesAspectRatio(true, it, choices)) }
    }

    @Test fun unknownSeriesAndInvalidChoicesUseOriginal() {
        listOf(null, "", "invalid", "NaN", "999:1").forEach {
            assertEquals("-1", rememberedSeriesAspectRatio(true, it, choices))
        }
    }

    @Test fun episodesShareAKeyButDifferentSeriesDoNot() {
        val first = trackSeriesKey(PlayerTitlePresentation("Example Show", 1, 1))
        val next = trackSeriesKey(PlayerTitlePresentation("Example Show", 2, 4))
        val other = trackSeriesKey(PlayerTitlePresentation("Another Show", 1, 1))
        val saved = mapOf(first to "4:3")
        assertEquals(first, next)
        assertEquals("4:3", rememberedSeriesAspectRatio(true, saved[next], choices))
        assertEquals("-1", rememberedSeriesAspectRatio(true, saved[other], choices))
    }

    @Test fun movieOrMissingTitleDoesNotHaveASeriesKey() {
        assertNull(trackSeriesKey(PlayerTitlePresentation("Example Movie")))
        assertNull(trackSeriesKey(null))
    }

    @Test fun seriesKeysNormalizeCaseAndWhitespace() {
        assertEquals(
            trackSeriesKey(PlayerTitlePresentation("Example Show", 1, 1)),
            trackSeriesKey(PlayerTitlePresentation("  EXAMPLE   SHOW  ", 1, 2)),
        )
    }
}
