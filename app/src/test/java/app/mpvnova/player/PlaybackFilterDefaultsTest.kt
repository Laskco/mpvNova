package app.mpvnova.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackFilterDefaultsTest {
    @Test
    fun plainAudioProcessingUsesStableFloatFormatWithoutForcingRateOrLayout() {
        assertEquals(
            "out_sample_fmt=flt",
            stableAudioAresampleOptions(
                controlledDownmixActive = false,
                centerBoostMixLevel = null,
            ),
        )
    }

    @Test
    fun controlledDownmixKeepsStereoLayout() {
        assertEquals(
            "in_chlayout=stereo:out_chlayout=stereo:out_sample_fmt=flt",
            stableAudioAresampleOptions(
                controlledDownmixActive = true,
                centerBoostMixLevel = null,
            ),
        )
    }

    @Test
    fun centerBoostCreatesStableStereoDownmix() {
        assertEquals(
            "out_chlayout=stereo:out_sample_fmt=flt:center_mix_level=4.0",
            stableAudioAresampleOptions(
                controlledDownmixActive = false,
                centerBoostMixLevel = "4.0",
            ),
        )
    }

    @Test
    fun centerBoostKeepsControlledDownmixInputStable() {
        assertEquals(
            "in_chlayout=stereo:out_chlayout=stereo:out_sample_fmt=flt:center_mix_level=3.5",
            stableAudioAresampleOptions(
                controlledDownmixActive = true,
                centerBoostMixLevel = "3.5",
            ),
        )
    }

    @Test
    fun interpolationOffAlwaysRestoresAudioSync() {
        assertEquals(
            "audio",
            resolveVideoSync(
                interpolationEnabled = false,
                configuredSync = "display-resample",
                defaultSync = "audio",
            ),
        )
    }

    @Test
    fun interpolationOnKeepsSelectedSyncMode() {
        assertEquals(
            "display-resample-vdrop",
            resolveVideoSync(
                interpolationEnabled = true,
                configuredSync = "display-resample-vdrop",
                defaultSync = "audio",
            ),
        )
    }

    @Test
    fun interpolationOnFallsBackFromBlankSyncMode() {
        assertEquals(
            "audio",
            resolveVideoSync(
                interpolationEnabled = true,
                configuredSync = "",
                defaultSync = "audio",
            ),
        )
    }
}
