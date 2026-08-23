package app.mpvnova.player

import android.os.Bundle

internal data class ExternalStartPositionRequest(
    val positionMs: Long,
    val isExplicit: Boolean,
)

internal data class ExternalStartPositionExtras(
    val forceFromStart: Boolean = false,
    val startFromMs: Long? = null,
    val positionMs: Long? = null,
    val extraPositionMs: Long? = null,
    val resumePositionMs: Long? = null,
)

@Suppress("DEPRECATION")
internal fun Bundle.externalStartPositionRequest(): ExternalStartPositionRequest {
    val positionMs: (String) -> Long? = { key ->
        if (!containsKey(key)) {
            null
        } else when (val value = get(key)) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
        }
    }
    return externalStartPositionRequest(
        ExternalStartPositionExtras(
            forceFromStart = getBoolean("from_start", false),
            startFromMs = positionMs("startfrom"),
            positionMs = positionMs("position"),
            extraPositionMs = positionMs("extra_position"),
            resumePositionMs = positionMs("resume_position"),
        )
    )
}

internal fun externalStartPositionRequest(extras: ExternalStartPositionExtras): ExternalStartPositionRequest {
    val startFromMs = extras.startFromMs ?: 0L
    val positionMs = extras.positionMs ?: 0L
    val extraPositionMs = extras.extraPositionMs ?: 0L
    val resumePositionMs = extras.resumePositionMs ?: 0L
    val startPosition = when {
        extras.forceFromStart -> 0L
        startFromMs > 1L -> startFromMs
        positionMs > 0L -> positionMs
        extraPositionMs > 0L -> extraPositionMs
        else -> resumePositionMs.coerceAtLeast(0L)
    }
    return ExternalStartPositionRequest(
        positionMs = startPosition,
        isExplicit = extras.forceFromStart || extras.hasPositionExtra,
    )
}

private val ExternalStartPositionExtras.hasPositionExtra: Boolean
    get() = startFromMs != null || positionMs != null ||
        extraPositionMs != null || resumePositionMs != null

@Suppress("DEPRECATION")
internal fun Bundle.externalDurationMs(): Long {
    val durationMs: (String) -> Long = { key ->
        when (val value = get(key)) {
            is Number -> value.toLong()
            is String -> value.toLongOrNull() ?: 0L
            else -> 0L
        }
    }
    return durationMs("duration")
        .takeIf { it > 0L }
        ?: durationMs("extra_duration")
}

internal fun effectiveIntentStartPosition(
    request: ExternalStartPositionRequest,
    savedPositionMs: Long?,
    durationMs: Long,
): Long {
    val intentNearEnd = durationMs > 0L &&
        request.positionMs >= durationMs - RESUME_NEAR_END_MS
    return when {
        request.positionMs >= RESUME_MIN_POSITION_MS && !intentNearEnd -> request.positionMs
        request.isExplicit -> 0L
        request.positionMs <= 0L -> savedPositionMs ?: 0L
        else -> 0L
    }
}
