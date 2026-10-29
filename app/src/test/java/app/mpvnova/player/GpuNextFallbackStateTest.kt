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

    private fun failFrames(state: GpuNextFallbackState, start: Long, hwdec: String): GpuNextFallbackAction? {
        assertNull(state.onRenderFailure(start, hwdec, hwdec))
        assertNull(state.onRenderFailure(start + 100, hwdec, hwdec))
        return state.onRenderFailure(start + 200, hwdec, hwdec)
    }
}
