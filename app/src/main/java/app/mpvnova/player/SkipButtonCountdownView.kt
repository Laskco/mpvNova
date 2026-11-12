package app.mpvnova.player

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.os.SystemClock
import android.util.AttributeSet
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils

/** A decorative timer driven by the same uptime deadline as the button's hide callback. */
class SkipButtonCountdownView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : View(context, attrs) {
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var deadlineMs = 0L
    private var durationMs = 0L

    fun startCountdown(hideAtMs: Long, delayMs: Long) {
        val accent = AppearanceTheme.resolveColor(
            context, R.attr.mpvAccentHot, ContextCompat.getColor(context, R.color.tv_purple_hot),
        )
        fillPaint.color = accent
        trackPaint.color = ColorUtils.setAlphaComponent(accent, COUNTDOWN_TRACK_ALPHA)
        deadlineMs = hideAtMs
        durationMs = delayMs
        visibility = VISIBLE
        invalidate()
    }

    fun clearCountdown() {
        deadlineMs = 0L
        durationMs = 0L
        visibility = INVISIBLE
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (durationMs <= 0L) return
        val remainingMs = (deadlineMs - SystemClock.uptimeMillis()).coerceAtLeast(0L)
        val fraction = (remainingMs.toFloat() / durationMs).coerceIn(0f, 1f)
        val radius = height / 2f
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, trackPaint)
        val fillWidth = width * fraction
        val left = if (layoutDirection == LAYOUT_DIRECTION_RTL) width - fillWidth else 0f
        canvas.drawRoundRect(left, 0f, left + fillWidth, height.toFloat(), radius, radius, fillPaint)
        // Redraw only this small strip; no layout or per-frame player-state updates.
        if (remainingMs > 0L && isShown) postInvalidateDelayed(COUNTDOWN_REFRESH_MS)
    }
}

private const val COUNTDOWN_TRACK_ALPHA = 40
private const val COUNTDOWN_REFRESH_MS = 50L
