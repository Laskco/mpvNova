package app.mpvnova.player

import android.content.res.ColorStateList
import android.view.Choreographer
import android.view.KeyEvent
import android.view.View
import android.widget.FrameLayout
import androidx.core.view.updateLayoutParams
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.random.Random

// A DVD-screensaver style bouncing logo shown over a black overlay after the video
// has been paused and idle for the configured timeout. Paused-only, so the (idle)
// decoder is never starved by the animation. A faint scanline + vignette pass over
// the overlay gives it an old-CRT look.

private const val DVD_LOGO_WIDTH_FRACTION = 0.14f
private const val DVD_SPEED_DP_PER_SEC = 90f
private const val DVD_CORNER_HIT_CHANCE = 0.01f
internal const val SCREENSAVER_FADE_MS = 900L
private const val MAX_FRAME_DELTA_SEC = 0.05f

internal const val PREF_SCREENSAVER_LOGO_URI = "screensaver_logo_uri"
internal const val PREF_SCREENSAVER_TINT = "screensaver_tint"

private val DVD_TINTS = intArrayOf(
    0xFFFF4136.toInt(), 0xFFFF851B.toInt(), 0xFFFFDC00.toInt(), 0xFF2ECC40.toInt(),
    0xFF0074D9.toInt(), 0xFF7FDBFF.toInt(), 0xFFB10DC9.toInt(), 0xFFFFFFFF.toInt(),
)

internal fun MPVActivity.onScreensaverPauseChanged(paused: Boolean) {
    if (paused) scheduleScreensaver() else cancelScreensaver()
}

internal fun MPVActivity.scheduleScreensaver() {
    eventUiHandler.removeCallbacks(screensaverStartRunnable)
    if (screensaverActive || screensaverMode == ScreensaverMode.OFF || !psc.pause) return
    if (inPictureInPicture()) return
    eventUiHandler.postDelayed(screensaverStartRunnable, screensaverTimeoutMs)
}

// Reset the idle timer on user input (only matters while paused and not yet active).
internal fun MPVActivity.noteScreensaverActivity() {
    if (screensaverActive) return
    if (psc.pause) scheduleScreensaver() else eventUiHandler.removeCallbacks(screensaverStartRunnable)
}

// Consumes the key that wakes the screensaver so it only dismisses (like a real screensaver).
internal fun MPVActivity.consumeScreensaverKey(ev: KeyEvent): Boolean {
    if (!screensaverActive && !screensaverWaking) return false
    when (ev.action) {
        KeyEvent.ACTION_DOWN -> { screensaverWaking = true; wakeFromScreensaver() }
        KeyEvent.ACTION_UP -> screensaverWaking = false
    }
    return true
}

internal fun MPVActivity.startScreensaver() {
    if (screensaverActive || !psc.pause || screensaverMode == ScreensaverMode.OFF) return
    if (inPictureInPicture()) return
    screensaverActive = true
    // Being idle inside a menu/panel still triggers it: close any open player dialogs (without
    // bouncing the drawer back) so the overlay sits on top of everything.
    drawerReopenPending = false
    drawerReopenScheduled = false
    playerDialogStack.toList().forEach { it.dismiss() }
    playerDialogStack.clear()
    hideControls()
    if (screensaverMode == ScreensaverMode.DIM) startDimScreensaver() else startLogoScreensaver()
}

private fun MPVActivity.startLogoScreensaver() {
    val overlay = binding.screensaverOverlay
    val logo = binding.screensaverLogo
    applyScreensaverLogo(logo)
    val screenW = overlay.width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
    val screenH = overlay.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels

    val logoW = (screenW * DVD_LOGO_WIDTH_FRACTION).toInt().coerceAtLeast(1)
    val drawable = logo.drawable
    val ratio = if (drawable != null && drawable.intrinsicWidth > 0) {
        drawable.intrinsicHeight.toFloat() / drawable.intrinsicWidth
    } else {
        0.5f
    }
    val logoH = (logoW * ratio).toInt().coerceAtLeast(1)
    logo.updateLayoutParams<FrameLayout.LayoutParams> {
        width = logoW
        height = logoH
    }

    dvdX = Random.nextInt(0, (screenW - logoW).coerceAtLeast(1)).toFloat()
    dvdY = Random.nextInt(0, (screenH - logoH).coerceAtLeast(1)).toFloat()
    val speed = Utils.convertDp(this, DVD_SPEED_DP_PER_SEC).toFloat().coerceAtLeast(1f)
    dvdVx = if (Random.nextBoolean()) speed else -speed
    dvdVy = if (Random.nextBoolean()) speed else -speed
    logo.x = dvdX
    logo.y = dvdY
    if (screensaverTintEnabled) tintScreensaverLogo()

    overlay.alpha = 0f
    overlay.setVisibilityIfChanged(View.VISIBLE)
    overlay.animate().alpha(1f).setDuration(SCREENSAVER_FADE_MS).start()

    screensaverLastFrameNanos = 0L
    Choreographer.getInstance().postFrameCallback(screensaverFrameCallback)
}

internal fun MPVActivity.stepScreensaver(frameTimeNanos: Long) {
    if (!screensaverActive) return
    val overlay = binding.screensaverOverlay
    val logo = binding.screensaverLogo
    val maxX = (overlay.width - logo.width).toFloat()
    val maxY = (overlay.height - logo.height).toFloat()
    if (maxX <= 0f || maxY <= 0f) {
        Choreographer.getInstance().postFrameCallback(screensaverFrameCallback)
        return
    }

    val last = screensaverLastFrameNanos
    screensaverLastFrameNanos = frameTimeNanos
    val dt = if (last == 0L) 0f else ((frameTimeNanos - last) / 1_000_000_000f).coerceAtMost(MAX_FRAME_DELTA_SEC)
    dvdX += dvdVx * dt
    dvdY += dvdVy * dt

    var bounced = false
    if (dvdX <= 0f) { dvdX = 0f; dvdVx = abs(dvdVx); bounced = true }
    else if (dvdX >= maxX) { dvdX = maxX; dvdVx = -abs(dvdVx); bounced = true }
    if (dvdY <= 0f) { dvdY = 0f; dvdVy = abs(dvdVy); bounced = true }
    else if (dvdY >= maxY) { dvdY = maxY; dvdVy = -abs(dvdVy); bounced = true }

    if (bounced) {
        if (screensaverTintEnabled) tintScreensaverLogo()
        if (Random.nextFloat() < DVD_CORNER_HIT_CHANCE) aimForCorner(maxX, maxY)
    }

    logo.x = dvdX
    logo.y = dvdY
    flickerScreensaverCrt(frameTimeNanos)
    Choreographer.getInstance().postFrameCallback(screensaverFrameCallback)
}


// 1% chance on a wall bounce: re-aim straight at the corner in the current direction
// of travel so the logo visibly goes for the satisfying corner hit, keeping its speed.
private fun MPVActivity.aimForCorner(maxX: Float, maxY: Float) {
    val speed = hypot(dvdVx.toDouble(), dvdVy.toDouble()).toFloat()
    val targetX = if (dvdVx >= 0f) maxX else 0f
    val targetY = if (dvdVy >= 0f) maxY else 0f
    val dx = targetX - dvdX
    val dy = targetY - dvdY
    val len = hypot(dx.toDouble(), dy.toDouble()).toFloat().coerceAtLeast(1f)
    dvdVx = dx / len * speed
    dvdVy = dy / len * speed
}

private fun MPVActivity.tintScreensaverLogo() {
    // Always advance to a different colour so a bounce never looks like "no change".
    var next = Random.nextInt(DVD_TINTS.size)
    if (next == screensaverTintIndex) next = (next + 1) % DVD_TINTS.size
    screensaverTintIndex = next
    binding.screensaverLogo.imageTintList = ColorStateList.valueOf(DVD_TINTS[next])
}

internal fun MPVActivity.wakeFromScreensaver() {
    if (!screensaverActive) return
    screensaverActive = false
    Choreographer.getInstance().removeFrameCallback(screensaverFrameCallback)
    val overlay = binding.screensaverOverlay
    overlay.animate().cancel()
    overlay.setVisibilityIfChanged(View.GONE)
    teardownDimScreensaver()
    scheduleScreensaver()
}

internal fun MPVActivity.cancelScreensaver() {
    eventUiHandler.removeCallbacks(screensaverStartRunnable)
    screensaverWaking = false
    if (!screensaverActive) return
    screensaverActive = false
    Choreographer.getInstance().removeFrameCallback(screensaverFrameCallback)
    val overlay = binding.screensaverOverlay
    overlay.animate().cancel()
    overlay.setVisibilityIfChanged(View.GONE)
    teardownDimScreensaver()
}
