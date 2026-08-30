package app.mpvnova.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FireTvVideoEdgeWorkaroundTest {
    private val affectedTrack = VideoTrackEdgeInfo(
        codec = "hevc",
        dolbyVisionProfile = 7,
        width = 3840,
        height = 2160,
    )

    @Test
    fun affectedFireTvProfile7UhdCropsFinalChromaPair() {
        assertEquals(
            "3840x2158+0+0",
            fireTvVideoEdgeCrop("AFTDCT31", affectedTrack),
        )
    }

    @Test
    fun otherDevicesAreUnchanged() {
        assertNull(fireTvVideoEdgeCrop("SHIELD Android TV", affectedTrack))
    }

    @Test
    fun otherDolbyVisionProfilesAreUnchanged() {
        assertNull(
            fireTvVideoEdgeCrop(
                "AFTDCT31",
                affectedTrack.copy(dolbyVisionProfile = 8),
            ),
        )
    }

    @Test
    fun nonUhdVideoIsUnchanged() {
        assertNull(
            fireTvVideoEdgeCrop(
                "AFTDCT31",
                affectedTrack.copy(width = 1920, height = 1080),
            ),
        )
    }
}
