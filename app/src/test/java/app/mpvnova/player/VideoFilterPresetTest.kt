package app.mpvnova.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoFilterPresetTest {
    @Test
    fun includesEveryMpvRxColorPreset() {
        assertEquals(12, VideoFilterPreset.entries.size)
        assertEquals(VideoFilterPreset.VIVID, VideoFilterPreset.fromPref("vivid"))
        assertEquals(VideoFilterPreset.DEEP_BLACK, VideoFilterPreset.fromPref("deep_black"))
        assertNull(VideoFilterPreset.fromPref(VIDEO_FILTER_PRESET_CUSTOM))
    }

    @Test
    fun presetValuesStayInsideMpvColorPropertyRange() {
        VideoFilterPreset.entries.forEach { preset ->
            VIDEO_FILTER_ADJUSTMENTS.forEach { spec ->
                assertTrue(preset.valueFor(spec) in VIDEO_ADJUSTMENT_MIN_INT..VIDEO_ADJUSTMENT_MAX_INT)
            }
        }
    }

    @Test
    fun vividAndDeepBlackMatchSourceDefinitions() {
        assertEquals(5, VideoFilterPreset.VIVID.brightness)
        assertEquals(25, VideoFilterPreset.VIVID.saturation)
        assertEquals(15, VideoFilterPreset.VIVID.contrast)
        assertEquals(-15, VideoFilterPreset.DEEP_BLACK.brightness)
        assertEquals(25, VideoFilterPreset.DEEP_BLACK.contrast)
        assertEquals(-15, VideoFilterPreset.DEEP_BLACK.gamma)
    }
}
