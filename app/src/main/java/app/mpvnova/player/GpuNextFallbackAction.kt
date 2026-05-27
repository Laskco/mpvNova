package app.mpvnova.player

import android.os.SystemClock
import java.util.Locale

internal enum class GpuNextFallbackAction {
    RetryWithCopyHwdec,
    WaitForCopyRetry,
    KeepGpuNext,
    FallbackToGpu,
}

// A single transient libplacebo error must not flip the renderer mid-
// playback — that rebuilds the VO while audio keeps draining its buffer
// → A/V/sub desync (Hi10p+g-next).
private const val GPU_NEXT_ERROR_WINDOW_MS = 1500L
private const val GPU_NEXT_ERROR_WINDOW_THRESHOLD = 3

internal fun MPVActivity.canApplyGpuNextRenderFallback(level: Int): Boolean {
    // Gates: auto-fallback on, error-level log, VO is gpu-next, user didn't
    // explicitly pick gpu-next/Shield-Hi10P (else we'd override their choice).
    val chosen = sessionDecoderMode ?: preferredDecoderMode
    val userPickedGpuNextMode =
        chosen == MPVView.DECODER_MODE_GNEXT || chosen == MPVView.DECODER_MODE_SHIELD_H10P
    val gatesPassed = autoDecoderFallback &&
        level <= MpvLogLevel.MPV_LOG_LEVEL_ERROR &&
        player.requestedVideoOutput.trim().lowercase(Locale.US).startsWith("gpu-next") &&
        !userPickedGpuNextMode
    if (!gatesPassed) return false
    // Sliding window: ≥THRESHOLD errors inside WINDOW_MS = sustained failure.
    // Single OSD blips (common on Tegra) don't trip the rebuild.
    val now = SystemClock.uptimeMillis()
    if (now - gpuNextErrorWindowStartMs > GPU_NEXT_ERROR_WINDOW_MS) {
        gpuNextErrorWindowStartMs = now
        gpuNextErrorWindowCount = 0
    }
    gpuNextErrorWindowCount += 1
    return gpuNextErrorWindowCount >= GPU_NEXT_ERROR_WINDOW_THRESHOLD
}

internal fun MPVActivity.gpuNextFallbackAction(): GpuNextFallbackAction {
    val activeHwdec = player.hwdecActive.trim().lowercase(Locale.US)
    val requestedHwdec = normalizedHwdecOption()
    val shouldRetryWithCopyHwdec = gpuNextRenderFallbackStage == 0 &&
        activeHwdec != "mediacodec-copy" &&
        requestedHwdec != "mediacodec-copy"
    val copyRetryFinished = gpuNextCopyRetryConfirmed && gpuNextCopyRetryDisplayedFrame
    return when {
        shouldRetryWithCopyHwdec -> GpuNextFallbackAction.RetryWithCopyHwdec
        gpuNextRenderFallbackStage == 1 && !copyRetryFinished -> GpuNextFallbackAction.WaitForCopyRetry
        gpuNextRenderFallbackStage in GPU_NEXT_RETRY_STAGES && copyRetryFinished -> GpuNextFallbackAction.KeepGpuNext
        else -> GpuNextFallbackAction.FallbackToGpu
    }
}
