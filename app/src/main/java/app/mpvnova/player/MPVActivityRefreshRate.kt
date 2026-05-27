package app.mpvnova.player

import android.os.Build
import android.util.Log
import android.view.Display
import androidx.annotation.RequiresApi
import kotlin.math.abs

/**
 * Match the display refresh rate to the playing video's container fps on
 * Android TV. Eliminates 3:2 pulldown judder on 60 Hz panels for 23.976
 * content. Only swaps among modes at the current resolution.
 */

// Generous so 23.976 content folds onto a 23.97602… mode, tight enough
// that 30fps content doesn't fold onto a 60Hz mode.
private const val REFRESH_RATE_MATCH_TOLERANCE_HZ = 0.5f

@Suppress("ReturnCount")
internal fun MPVActivity.maybeApplyContentRefreshRate() {
    // Two separate ifs so lint sees both early-returns and skips NewApi
    // on the M-only helper call below.
    if (!autoRefreshRateSwitch) return
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    val containerFps = (mpvGetPropertyDouble("container-fps")
        ?: mpvGetPropertyDouble("estimated-vf-fps")
        ?: 0.0)
    if (containerFps <= 0.0) {
        Log.v(MPV_ACTIVITY_TAG, "refresh-rate: container-fps unavailable")
        return
    }
    // mpv event thread → UI thread for the display/window calls.
    val targetFps = containerFps.toFloat()
    runOnUiThread { applyClosestRefreshRate(targetFps) }
}

/** Reset to the system default mode (toggle-off path). */
internal fun MPVActivity.clearContentRefreshRate() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
    runOnUiThread {
        val attrs = window.attributes
        if (attrs.preferredDisplayModeId != 0) {
            Log.v(MPV_ACTIVITY_TAG, "refresh-rate: clearing preferredDisplayModeId")
            attrs.preferredDisplayModeId = 0
            window.attributes = attrs
        }
    }
}

@RequiresApi(Build.VERSION_CODES.M)
@Suppress("ReturnCount")
private fun MPVActivity.applyClosestRefreshRate(targetFps: Float) {
    val display: Display = window.decorView.display ?: return
    val current = display.mode
    val best = pickClosestRefreshMode(display, current, targetFps) ?: return
    if (best.modeId == current.modeId) return
    Log.v(
        MPV_ACTIVITY_TAG,
        "refresh-rate: switching to #${best.modeId} (${best.refreshRate}Hz) for ${targetFps}fps"
    )
    val attrs = window.attributes
    attrs.preferredDisplayModeId = best.modeId
    window.attributes = attrs
}

@RequiresApi(Build.VERSION_CODES.M)
private fun pickClosestRefreshMode(
    display: Display,
    current: Display.Mode,
    targetFps: Float,
): Display.Mode? {
    val candidates = display.supportedModes.filter {
        it.physicalWidth == current.physicalWidth && it.physicalHeight == current.physicalHeight
    }
    val best = candidates.minByOrNull { abs(it.refreshRate - targetFps) } ?: return null
    return best.takeIf { abs(it.refreshRate - targetFps) <= REFRESH_RATE_MATCH_TOLERANCE_HZ }
}
