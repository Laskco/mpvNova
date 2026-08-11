package app.mpvnova.player

import android.graphics.Color
import android.view.View
import kotlin.math.PI
import kotlin.math.sin

// A translucent grey scrim so the paused frame stays faintly visible underneath.
private const val SCREENSAVER_DIM_SCRIM_COLOR = 0xE6101010.toInt()
private const val SCREENSAVER_CLOCK_Z_DP = 8f

// Subtle CRT brightness flicker on the scanline pass.
private const val CRT_FLICKER_BASE_ALPHA = 0.85f
private const val CRT_FLICKER_AMPLITUDE = 0.06f
private const val CRT_FLICKER_HZ = 1.6f

// Gentle sine flicker on the scanline layer so the CRT look "breathes" a little.
internal fun MPVActivity.flickerScreensaverCrt(frameTimeNanos: Long) {
    val t = frameTimeNanos / 1_000_000_000.0
    val wobble = sin(t * 2.0 * PI * CRT_FLICKER_HZ).toFloat() * CRT_FLICKER_AMPLITUDE
    binding.screensaverScanlines.alpha = (CRT_FLICKER_BASE_ALPHA + wobble).coerceIn(0f, 1f)
}

internal fun MPVActivity.startDimScreensaver() {
    val overlay = binding.screensaverOverlay
    binding.screensaverLogo.setVisibilityIfChanged(View.GONE)
    binding.screensaverScanlines.setVisibilityIfChanged(View.GONE)
    binding.screensaverVignette.setVisibilityIfChanged(View.GONE)
    overlay.setBackgroundColor(SCREENSAVER_DIM_SCRIM_COLOR)
    raiseDimClock()
    overlay.alpha = 0f
    overlay.setVisibilityIfChanged(View.VISIBLE)
    overlay.animate().alpha(1f).setDuration(SCREENSAVER_FADE_MS).start()
}

// Lift the clock panel above the scrim and keep it ticking, if the user shows the clock on pause.
private fun MPVActivity.raiseDimClock() {
    if (!shouldShowClockWhileControlsHidden()) return
    val panel = binding.timeInfoPanel
    panel.translationZ = Utils.convertDp(this, SCREENSAVER_CLOCK_Z_DP).toFloat()
    panel.animate().cancel()
    panel.alpha = 1f
    updateClockInfo(force = true)
    panel.setVisibilityIfChanged(View.VISIBLE)
    clockHandler.removeCallbacks(clockRunnable)
    clockHandler.post(clockRunnable)
}

// Undo the dim-mode overlay/clock changes so the next activation (any mode) is clean.
internal fun MPVActivity.teardownDimScreensaver() {
    binding.timeInfoPanel.translationZ = 0f
    binding.screensaverLogo.setVisibilityIfChanged(View.VISIBLE)
    binding.screensaverScanlines.setVisibilityIfChanged(View.VISIBLE)
    binding.screensaverVignette.setVisibilityIfChanged(View.VISIBLE)
    binding.screensaverOverlay.setBackgroundColor(Color.BLACK)
    refreshTimeInfoPanelVisibility()
}

internal fun MPVActivity.setScreensaverMode(mode: ScreensaverMode) {
    screensaverMode = mode
    androidx.preference.PreferenceManager.getDefaultSharedPreferences(applicationContext).edit()
        .putString("screensaver_mode", mode.pref).apply()
    refreshDrawerRowsIfVisible(DrawerTab.PLAYBACK)
    if (screensaverActive) wakeFromScreensaver() else noteScreensaverActivity()
}
