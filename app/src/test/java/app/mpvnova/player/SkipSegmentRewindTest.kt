package app.mpvnova.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SkipSegmentRewindTest {
    private val intro = SkipSegment(type = "intro", start = 10.0, end = 90.0)

    @Test
    fun rewindFromPastEndIntoSegmentRearmsButton() {
        assertTrue(rewoundIntoOrBeforeSkippedSegment(95.0, 60.0, intro))
    }

    @Test
    fun rewindFromPastEndToBeforeSegmentRearmsButton() {
        assertTrue(rewoundIntoOrBeforeSkippedSegment(95.0, 5.0, intro))
    }

    @Test
    fun backwardLandingInsideHandledSegmentRearmsButton() {
        assertTrue(rewoundIntoOrBeforeSkippedSegment(88.0, 60.0, intro))
    }

    @Test
    fun forwardPlaybackDoesNotRearmButton() {
        assertFalse(rewoundIntoOrBeforeSkippedSegment(60.0, 95.0, intro))
    }

    @Test
    fun rewindThatStaysPastSegmentDoesNotRearmButton() {
        assertFalse(rewoundIntoOrBeforeSkippedSegment(120.0, 100.0, intro))
    }

    @Test
    fun rewindBeforeSegmentWithoutCrossingItDoesNotRearmButton() {
        assertFalse(rewoundIntoOrBeforeSkippedSegment(8.0, 5.0, intro))
    }

    @Test
    fun timestampJitterDoesNotRearmButton() {
        assertFalse(rewoundIntoOrBeforeSkippedSegment(90.1, 89.5, intro))
    }
}
