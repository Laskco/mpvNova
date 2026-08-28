package app.mpvnova.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MpvHeldKeyTrackerTest {
    @Test
    fun repeatedDownDoesNotCreateAnotherHeldKey() {
        val tracker = MpvHeldKeyTracker()

        assertTrue(tracker.press(22, "RIGHT"))
        assertFalse(tracker.press(22, "RIGHT"))
        assertEquals("RIGHT", tracker.release(22))
        assertNull(tracker.release(22))
    }

    @Test
    fun releaseUsesChordCapturedAtKeyDown() {
        val tracker = MpvHeldKeyTracker()

        tracker.press(21, "ctrl+LEFT")

        assertEquals("ctrl+LEFT", tracker.release(21))
    }

    @Test
    fun clearReportsWhetherMpvNeedsAReleaseAllCommand() {
        val tracker = MpvHeldKeyTracker()

        assertFalse(tracker.clear())
        tracker.press(21, "LEFT")
        tracker.press(22, "RIGHT")
        assertTrue(tracker.clear())
        assertFalse(tracker.clear())
    }
}
