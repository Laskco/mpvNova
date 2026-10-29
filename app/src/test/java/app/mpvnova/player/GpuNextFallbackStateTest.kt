package app.mpvnova.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GpuNextFallbackStateTest {
    @Test
    fun directFailureRetriesCopyBeforeChangingRenderer() {
        val state = GpuNextFallbackState()
        assertEquals(GpuNextFallbackAction.RetryWithCopyHwdec, failFrames(state, 0, "mediacodec"))
        assertTrue(state.recoveryApplied)
        assertFalse(state.rendererFallbackApplied)
    }

    @Test
    fun copyGetsGraceThenSustainedFailuresChangeRenderer() {
        val state = GpuNextFallbackState()
        failFrames(state, 0, "mediacodec")
        assertNull(state.onRenderFailure(1000, "mediacodec-copy", "mediacodec-copy"))
        assertEquals(GpuNextFallbackAction.FallbackToGpu, failFrames(state, 2000, "mediacodec-copy"))
        assertTrue(state.rendererFallbackApplied)
        assertNull(state.onRenderFailure(4000, "mediacodec-copy", "mediacodec-copy"))
    }

    @Test
    fun duplicateMessagesFromOneFrameDoNotTriggerRecovery() {
        val state = GpuNextFallbackState()
        repeat(20) { assertNull(state.onRenderFailure(100, "mediacodec", "mediacodec")) }
        assertFalse(state.recoveryApplied)
    }

    @Test
    fun isolatedFailuresDoNotTriggerRecovery() {
        val state = GpuNextFallbackState()
        listOf(0L, 2000L, 4000L).forEach {
            assertNull(state.onRenderFailure(it, "mediacodec", "mediacodec"))
        }
        assertFalse(state.recoveryApplied)
    }

    @Test
    fun nextFileOrExplicitSelectionClearsCopyRecovery() {
        val state = GpuNextFallbackState()
        failFrames(state, 0, "mediacodec")
        state.reset()
        assertFalse(state.recoveryApplied)
        assertEquals(GpuNextFallbackAction.RetryWithCopyHwdec, failFrames(state, 4000, "mediacodec"))
    }

    @Test
    fun nextFileClearsRendererRecovery() {
        val state = GpuNextFallbackState()
        failFrames(state, 0, "mediacodec-copy")
        state.reset()
        assertFalse(state.recoveryApplied)
        assertFalse(state.rendererFallbackApplied)
    }

    @Test
    fun recoveryPreservesPreloadedHi10pForIncomingFile() {
        val state = GpuNextFallbackState()
        failFrames(state, 0, "mediacodec")
        assertFalse(state.shouldRestoreForFile("hi10p.mkv", null, "hi10p.mkv"))
    }

    @Test
    fun previousFilesHi10pPreloadDoesNotPreventRecovery() {
        val state = GpuNextFallbackState()
        failFrames(state, 0, "mediacodec")
        assertTrue(state.shouldRestoreForFile("normal.mkv", null, "hi10p.mkv"))
        assertTrue(state.shouldRestoreForFile("normal.mkv", null, null))
        assertTrue(state.shouldRestoreForFile(null, null, null))
    }

    @Test
    fun recoveryDoesNotOverrideExplicitDecoderSelection() {
        val state = GpuNextFallbackState()
        failFrames(state, 0, "mediacodec")
        assertFalse(state.shouldRestoreForFile("normal.mkv", "g_next_direct", null))
    }

    @Test
    fun normalFileLoadDoesNotRestoreWithoutRecovery() {
        assertFalse(GpuNextFallbackState().shouldRestoreForFile("normal.mkv", null, null))
    }

    private fun failFrames(state: GpuNextFallbackState, start: Long, hwdec: String): GpuNextFallbackAction? {
        assertNull(state.onRenderFailure(start, hwdec, hwdec))
        assertNull(state.onRenderFailure(start + 100, hwdec, hwdec))
        return state.onRenderFailure(start + 200, hwdec, hwdec)
    }

    @Test
    fun disabledFallbacksEnforceEveryExplicitHardwareMode() {
        listOf("auto_safe", "hw_plus", "hw", "g_next", "g_next_direct").forEach { mode ->
            assertEquals("no", nativeSoftwareFallback(mode, false, false, "3"))
        }
    }

    @Test
    fun eitherEnabledFallbackRestoresConfiguredNativeBehavior() {
        listOf(true to false, false to true, true to true).forEach { (auto, hi10p) ->
            assertEquals("7", nativeSoftwareFallback("g_next_direct", auto, hi10p, "7"))
        }
    }

    @Test
    fun explicitSoftwareSelectionIsNeverBlockedByStrictHardwareMode() {
        listOf("sw", "shield_h10p").forEach { mode ->
            assertEquals("3", nativeSoftwareFallback(mode, false, false, "3"))
            assertEquals("3", nativeSoftwareFallback(mode, false, false, "no"))
        }
    }

    @Test
    fun configurationModePreservesNativeFallbackSetting() {
        assertEquals("7", nativeSoftwareFallback("mpv_conf", false, false, "7"))
        assertEquals("no", nativeSoftwareFallback("mpv_conf", false, false, "no"))
        assertEquals("3", nativeSoftwareFallback(null, false, false, "3"))
    }

    @Test
    fun missingDecoderIsNotReportedAsSoftware() {
        assertEquals("G-NXT", gpuNextDecoderBadge(null, false))
        assertEquals("G-NXT", gpuNextDecoderBadge("", false))
        assertEquals("G+ERR", gpuNextDecoderBadge(null, true))
    }

    @Test
    fun activeDecoderDeterminesTheBadge() {
        assertEquals("G+SW", gpuNextDecoderBadge("no", false))
        assertEquals("G+HW", gpuNextDecoderBadge("mediacodec", false))
        assertEquals("G+CPY", gpuNextDecoderBadge("mediacodec-copy", false))
    }

    @Test
    fun hardwareRejectionIsOneShotAndResets() {
        val state = GpuNextFallbackState()
        assertTrue(state.markHardwareDecoderRejected())
        assertFalse(state.markHardwareDecoderRejected())
        state.clearHardwareDecoderRejected()
        assertFalse(state.hardwareDecoderRejected)
        state.markHardwareDecoderRejected()
        state.reset()
        assertFalse(state.hardwareDecoderRejected)
    }

    @Test
    fun normalHardwareBadgesReportRejectedDecoders() {
        listOf("HW", "HW+", "AUTO").forEach { badge ->
            assertEquals("HW-ERR", hardwareDecoderBadge(badge, null, true))
            assertEquals("HW-ERR", hardwareDecoderBadge(badge, "", true))
            assertEquals(badge, hardwareDecoderBadge(badge, null, false))
        }
    }

    @Test
    fun activeDecoderClearsNormalHardwareBadgeError() {
        assertEquals("HW+", hardwareDecoderBadge("HW+", "mediacodec", true))
        assertEquals("HW", hardwareDecoderBadge("HW", "mediacodec-copy", true))
        assertEquals("SW", hardwareDecoderBadge("SW", "no", true))
    }
}
