package app.mpvnova.player

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import androidx.preference.PreferenceManager
import kotlin.math.roundToInt

/**
 * Forces a consistent UI scale across devices, adjustable by the user.
 *
 * The couch UI is designed around a ~1170dp-wide canvas (a Shield at 2560x1440 /
 * 350dpi). Stock TVs report fewer dp across the screen (a 1080p set at 320dpi is
 * only 960dp wide), so the same layout renders much larger there. We override the
 * app's density so the screen is always treated as the design width, regardless
 * of the device's configured DPI. The "UI scale" preference widens/narrows that
 * design width: 100% is the baseline, higher = bigger UI, lower = smaller.
 */
internal object UiScale {
    const val PREF_KEY = "ui_scale_percent"
    const val DEFAULT_SCALE_PERCENT = 100

    private const val DESIGN_WIDTH_DP = 1170f
    private const val SCALE_BASE = 100f
    private const val MIN_SCALE = 50
    private const val MAX_SCALE = 200

    fun currentScalePercent(context: Context): Int {
        val preferences = PreferenceManager.getDefaultSharedPreferences(context)
        val raw = preferences.all[PREF_KEY]
        val value = when (raw) {
            is String -> raw.toIntOrNull()
            is Int -> raw
            is Long -> raw.takeIf { it in Int.MIN_VALUE..Int.MAX_VALUE }?.toInt()
            else -> null
        }
        val scale = (value ?: DEFAULT_SCALE_PERCENT).coerceIn(MIN_SCALE, MAX_SCALE)
        // Repair old malformed imports before ListPreference also reads this value as a String.
        if (raw != null && raw !is String) preferences.edit().putString(PREF_KEY, scale.toString()).apply()
        return scale
    }

    fun wrap(base: Context): Context {
        val metrics = base.resources.displayMetrics
        val widthPx = metrics.widthPixels
        val designWidth = DESIGN_WIDTH_DP * SCALE_BASE / currentScalePercent(base)
        val targetDensityDpi = (widthPx / designWidth * DisplayMetrics.DENSITY_DEFAULT).roundToInt()
        // Couch UI is landscape-only; leave portrait/odd configs and no-op changes alone.
        val unchanged = widthPx <= 0 ||
            widthPx < metrics.heightPixels ||
            base.resources.configuration.densityDpi == targetDensityDpi
        if (unchanged)
            return base
        val config = Configuration(base.resources.configuration).apply { densityDpi = targetDensityDpi }
        return base.createConfigurationContext(config)
    }
}
