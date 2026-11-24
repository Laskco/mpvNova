package app.mpvnova.player

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Shared critically-damped spring physics engine used by both [TvSmoothScrollView] and
 * [TvWeightedLinearLayoutManager] so every scrolled panel and list across the app shares
 * the exact same weight, inertia, and velocity continuity.
 */
internal class TvScrollSpring {
    var remainingDistance: Float = 0f
        private set
    var velocity: Float = 0f
        private set
    var isRunning: Boolean = false
        private set

    private var lastFrameTimeMs: Long = 0L
    private var fractionalCarry: Float = 0f

    fun retarget(newRemainingDistance: Float, nowMs: Long): Boolean {
        if (!isRunning && abs(newRemainingDistance) < SETTLE_DISTANCE_PX) {
            return false
        }
        if (!isRunning) {
            velocity = 0f
            fractionalCarry = 0f
            lastFrameTimeMs = nowMs
            isRunning = true
        }
        remainingDistance = newRemainingDistance
        return true
    }

    fun step(nowMs: Long): Int {
        if (!isRunning) return 0
        val dt = (nowMs - lastFrameTimeMs).coerceIn(1L, MAX_FRAME_DT_MS) / MILLIS_PER_SECOND
        lastFrameTimeMs = nowMs

        // Critically-damped spring closed form where displacement d = current - target = -remainingDistance.
        val d0 = -remainingDistance
        val v0 = velocity
        val decay = exp(-SPRING_OMEGA * dt)
        val coeff = v0 + SPRING_OMEGA * d0
        val d1 = (d0 + coeff * dt) * decay
        val v1 = (v0 - SPRING_OMEGA * coeff * dt) * decay

        val settled = abs(d1) < SETTLE_DISTANCE_PX && abs(v1) < SETTLE_VELOCITY_PX_PER_SEC
        val stepFloat = if (settled) {
            remainingDistance + fractionalCarry
        } else {
            (d1 - d0) + fractionalCarry
        }
        val stepPx = stepFloat.roundToInt()
        if (settled) {
            stop()
        } else {
            remainingDistance = -d1
            velocity = v1
            fractionalCarry = stepFloat - stepPx
        }
        return stepPx
    }

    fun stop() {
        isRunning = false
        remainingDistance = 0f
        velocity = 0f
        fractionalCarry = 0f
    }

    companion object {
        // Critically-damped angular frequency (rad/s): balanced, responsive TV weight across all panels.
        const val SPRING_OMEGA = 14.8f
        private const val MAX_FRAME_DT_MS = 32L
        private const val MILLIS_PER_SECOND = 1000f
        private const val SETTLE_DISTANCE_PX = 0.5f
        private const val SETTLE_VELOCITY_PX_PER_SEC = 10f
    }
}
