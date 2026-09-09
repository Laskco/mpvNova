package app.mpvnova.player

import android.util.Log
import java.util.Locale

internal fun MPVActivity.retryGpuNextWithCopyHwdec(prefix: String, text: String) {
    Log.w(
        MPV_ACTIVITY_TAG,
        "gpu-next render failure detected, retrying with mediacodec-copy ($prefix: $text)"
    )
    player.fallbackGpuNextToCopyHwdec()
    eventUiHandler.post {
        updateDecoderButton()
        if (activityIsForeground) {
            showToast(
                getString(R.string.pref_gpu_next_title),
                getString(R.string.toast_gpu_next_copy_fallback),
                durationMs = GPU_NEXT_FALLBACK_TOAST_MS
            )
        }
    }
}

internal fun MPVActivity.fallbackGpuNextToGpu(prefix: String, text: String) {
    Log.w(MPV_ACTIVITY_TAG, "Sustained gpu-next render failure, falling back to gpu ($prefix: $text)")
    player.fallbackGpuNextToGpu()
    eventUiHandler.post {
        updateDecoderButton()
        if (activityIsForeground) {
            showToast(
                getString(R.string.pref_gpu_next_title),
                getString(R.string.toast_gpu_next_fallback),
                durationMs = GPU_NEXT_FALLBACK_TOAST_MS
            )
        }
    }
}

internal fun isGpuNextRenderFailure(prefix: String, text: String): Boolean {
    val normalizedPrefix = prefix.trim().lowercase(Locale.US)
    val normalizedText = text.trim().lowercase(Locale.US)
    return normalizedPrefix.contains("gpu-next") &&
        GPU_NEXT_RENDER_FAILURE_TEXT.any { normalizedText.contains(it) } ||
        GPU_NEXT_GENERAL_FAILURE_TEXT.any { normalizedText.contains(it) }
}

internal fun MPVActivity.parseControlsTimeout(value: String?): Long {
    return when (value) {
        "never" -> -1L
        else -> value?.toLongOrNull()?.takeIf { it > 0L }
            ?: DEFAULT_CONTROLS_DISPLAY_TIMEOUT
    }
}
