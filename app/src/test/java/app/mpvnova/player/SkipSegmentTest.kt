package app.mpvnova.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipSegmentTest {
    private val credits = SkipSegment("end-credits", 100.0, 150.0)
    private val scene = SkipSegment("post-credits", 150.0, 170.0)

    @Test fun movieTypesAcceptHyphenAndUnderscoreAliases() {
        for (type in listOf("end-credits", "end_credits", "movie-credits", "movie_credits", " END-CREDITS ")) {
            assertEquals("end-credits", skipSegmentType(type))
        }
        for (type in listOf("post-credits", "post_credits", " POST-CREDITS ")) {
            assertEquals("post-credits", skipSegmentType(type))
        }
    }

    @Test fun episodeTypesAreNotReclassifiedAsMovieCredits() {
        val types = listOf(
            "intro", "op", "opening", "mixed-op", "recap", "outro", "ed", "ending", "credits", "mixed-ed",
        )
        for (type in types) {
            assertEquals(type, skipSegmentType(type))
        }
    }

    @Test fun allFiveModesAreIndependentForEveryCombination() {
        val aliases = mapOf(
            SkipSegmentKind.INTRO to listOf("intro", "op", "opening", "mixed-op"),
            SkipSegmentKind.OUTRO to listOf("outro", "ed", "ending", "credits", "mixed-ed"),
            SkipSegmentKind.RECAP to listOf("recap"),
            SkipSegmentKind.END_CREDITS to listOf("end-credits"),
            SkipSegmentKind.POST_CREDITS to listOf("post-credits"),
        )
        val initial = listOf(emptyMap<SkipSegmentKind, SkipSegmentsMode>())
        val combinations = SkipSegmentKind.entries.fold(initial) { prior, kind ->
            prior.flatMap { modes -> SkipSegmentsMode.entries.map { modes + (kind to it) } }
        }
        assertEquals(243, combinations.size)
        for (modes in combinations) {
            aliases.forEach { (kind, types) ->
                types.forEach { type ->
                    assertEquals(modes.getValue(kind), SkipSegment(type, 1.0, 20.0).mode(modes))
                }
            }
        }
    }

    @Test fun adjacentMovieIntervalsKeepExactBoundaries() {
        assertEquals(listOf(credits, scene), normalizedSkipSegments(listOf(credits, scene)))
    }

    @Test fun overlappingEndCreditsStopAtSceneStart() {
        assertEquals(listOf(credits, scene), normalizedSkipSegments(listOf(credits.copy(end = 180.0), scene)))
    }

    @Test fun sceneProtectionDoesNotDependOnInputOrder() {
        assertEquals(listOf(credits, scene), normalizedSkipSegments(listOf(scene, credits.copy(end = 180.0))))
    }

    @Test fun endCreditsEntirelyInsideSceneAreDropped() {
        assertEquals(listOf(scene), normalizedSkipSegments(listOf(credits.copy(start = 155.0, end = 165.0), scene)))
    }

    @Test fun firstOfMultipleScenesIsProtected() {
        val laterScene = scene.copy(start = 190.0, end = 210.0)
        assertEquals(
            listOf(credits, scene, laterScene),
            normalizedSkipSegments(listOf(laterScene, credits.copy(end = 220.0), scene)),
        )
    }

    @Test fun creditsAfterSceneAreNotTrimmed() {
        val tail = credits.copy(start = 170.0, end = 200.0)
        assertEquals(listOf(scene, tail), normalizedSkipSegments(listOf(tail, scene)))
    }

    @Test fun creditsWithoutSceneAreSupported() {
        assertEquals(listOf(credits), normalizedSkipSegments(listOf(credits)))
    }

    @Test fun sceneWithoutCreditsIsSupported() {
        assertEquals(listOf(scene), normalizedSkipSegments(listOf(scene)))
    }

    @Test fun invalidRangesAreRejected() {
        val invalid = listOf(
            credits.copy(start = -1.0), credits.copy(start = Double.NaN),
            credits.copy(end = Double.NaN), credits.copy(start = Double.NEGATIVE_INFINITY),
            credits.copy(end = Double.POSITIVE_INFINITY), credits.copy(end = 100.0),
            credits.copy(end = 50.0),
        )
        assertTrue(normalizedSkipSegments(invalid).isEmpty())
        assertEquals(listOf(credits), normalizedSkipSegments(invalid + credits))
    }

    @Test fun zeroAndFractionalStartsAreSupported() {
        val intervals = listOf(credits.copy(start = 0.0, end = 10.25), scene.copy(start = 10.25, end = 20.5))
        assertEquals(intervals, normalizedSkipSegments(intervals))
    }

    @Test fun invalidSceneDoesNotTrimCredits() {
        assertEquals(listOf(credits), normalizedSkipSegments(listOf(credits, scene.copy(end = Double.NaN))))
    }

    @Test fun episodeOutroIsNotModifiedByMovieSceneProtection() {
        val outro = credits.copy(type = "outro", end = 180.0)
        assertEquals(listOf(outro, scene), normalizedSkipSegments(listOf(outro, scene)))
    }

    @Test fun movieSeeksClampToKnownDuration() {
        assertEquals(149.999, credits.seekTarget(150.0), 0.000001)
        assertEquals(159.999, scene.seekTarget(160.0), 0.000001)
        assertEquals(0.0, scene.seekTarget(0.0005), 0.0)
    }

    @Test fun movieSeeksKeepExactEndpointWithinDuration() {
        assertEquals(150.0, credits.seekTarget(200.0), 0.0)
        assertEquals(170.0, scene.seekTarget(200.0), 0.0)
    }

    @Test fun unavailableDurationDoesNotChangeSeek() {
        for (duration in listOf(0.0, -1.0, Double.NaN, Double.POSITIVE_INFINITY)) {
            assertEquals(170.0, scene.seekTarget(duration), 0.0)
        }
    }

    @Test fun episodeSeekBehaviorIsUnchanged() {
        assertEquals(150.0, credits.copy(type = "outro").seekTarget(120.0), 0.0)
    }

    @Test fun emptyPayloadIsSafe() {
        assertTrue(normalizedSkipSegments(emptyList()).isEmpty())
    }
}
