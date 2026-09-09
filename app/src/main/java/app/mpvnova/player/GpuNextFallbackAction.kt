package app.mpvnova.player

internal enum class GpuNextFallbackAction {
    RetryWithCopyHwdec,
    FallbackToGpu,
}

private const val ERROR_WINDOW_MS = 1500L
private const val ERROR_THRESHOLD = 3
private const val ERROR_MIN_INTERVAL_MS = 50L
private const val COPY_RETRY_GRACE_MS = 1500L

/** Recovery is scoped to the current file or explicit decoder selection. */
internal class GpuNextFallbackState {
    @Volatile
    var rendererFallbackApplied = false
        private set
    private var copyRetryStartedMs: Long? = null
    private var windowStartMs: Long? = null
    private var lastErrorMs: Long? = null
    private var errorCount = 0

    @Synchronized
    fun reset() {
        rendererFallbackApplied = false
        copyRetryStartedMs = null
        clearErrors()
    }

    @Synchronized
    fun onRenderFailure(nowMs: Long, activeHwdec: String, requestedHwdec: String): GpuNextFallbackAction? {
        // Wait only while rebuilding, not indefinitely for a hardware decoder
        // that may be unavailable. Continued errors after this grace can recover.
        val retrySettling = copyRetryStartedMs?.let { nowMs - it < COPY_RETRY_GRACE_MS } == true
        if (rendererFallbackApplied || retrySettling) return null
        if (windowStartMs?.let { nowMs - it > ERROR_WINDOW_MS } != false) {
            clearErrors()
            windowStartMs = nowMs
        }
        // One failed frame can produce several libplacebo error messages.
        val distinctFailure = lastErrorMs?.let { nowMs - it >= ERROR_MIN_INTERVAL_MS } != false
        if (distinctFailure) {
            lastErrorMs = nowMs
            errorCount++
        }
        return if (distinctFailure && errorCount >= ERROR_THRESHOLD) {
            clearErrors()
            if (copyRetryStartedMs == null && activeHwdec == "mediacodec" &&
                requestedHwdec != "mediacodec-copy"
            ) {
                copyRetryStartedMs = nowMs
                GpuNextFallbackAction.RetryWithCopyHwdec
            } else {
                rendererFallbackApplied = true
                GpuNextFallbackAction.FallbackToGpu
            }
        } else null
    }

    private fun clearErrors() {
        windowStartMs = null
        lastErrorMs = null
        errorCount = 0
    }
}
