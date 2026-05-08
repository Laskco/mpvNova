package app.mpvnova.player

import java.util.Locale

internal enum class GpuNextFallbackAction {
    RetryWithCopyHwdec,
    WaitForCopyRetry,
    KeepGpuNext,
    FallbackToGpu,
}

internal fun MPVActivity.canApplyGpuNextRenderFallback(level: Int): Boolean {
    return autoDecoderFallback &&
        level <= MpvLogLevel.MPV_LOG_LEVEL_ERROR &&
        player.requestedVideoOutput.trim().lowercase(Locale.US).startsWith("gpu-next")
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
