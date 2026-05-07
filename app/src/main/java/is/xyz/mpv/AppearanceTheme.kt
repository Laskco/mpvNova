package app.mpvnova.player

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import androidx.annotation.StyleRes
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager

object AppearanceTheme {
    const val PREF_KEY = "ui_color_theme"
    const val PREF_AMOLED_MODE = "ui_amoled_mode"
    const val PREF_PURE_BLACK_SURFACES = "ui_pure_black_surfaces"
    const val DEFAULT_VALUE = "nova"
    private const val LEGACY_OLED_VALUE = "oled"

    private enum class Target {
        Player,
        Preference,
        FilePicker,
        FilePickerSpecial
    }

    fun currentValue(context: Context): String {
        return preferences(context).getString(PREF_KEY, DEFAULT_VALUE) ?: DEFAULT_VALUE
    }

    fun migrateLegacyOled(context: Context) {
        val prefs = preferences(context)
        if (prefs.getString(PREF_KEY, DEFAULT_VALUE) != LEGACY_OLED_VALUE) return
        prefs.edit()
            .putString(PREF_KEY, DEFAULT_VALUE)
            .putBoolean(PREF_AMOLED_MODE, true)
            .putBoolean(PREF_PURE_BLACK_SURFACES, true)
            .commit()
    }

    fun applyPlayer(activity: Activity) {
        migrateLegacyOled(activity)
        activity.setTheme(styleFor(activity, Target.Player))
        applySurfaceOverlays(activity)
    }

    fun applyPreferences(activity: Activity) {
        migrateLegacyOled(activity)
        activity.setTheme(styleFor(activity, Target.Preference))
        applySurfaceOverlays(activity)
    }

    fun applyFilePicker(activity: Activity) {
        migrateLegacyOled(activity)
        activity.setTheme(styleFor(activity, Target.FilePicker))
        applySurfaceOverlays(activity)
    }

    fun applySpecialFilePicker(activity: Activity) {
        migrateLegacyOled(activity)
        activity.setTheme(styleFor(activity, Target.FilePickerSpecial))
        applySurfaceOverlays(activity)
    }

    @ColorInt
    fun resolveColor(context: Context, @AttrRes attr: Int, @ColorInt fallback: Int): Int {
        val value = TypedValue()
        return if (context.theme.resolveAttribute(attr, value, true)) {
            if (value.resourceId != 0)
                ContextCompat.getColor(context, value.resourceId)
            else
                value.data
        } else {
            fallback
        }
    }

    private fun preferences(context: Context): SharedPreferences {
        return PreferenceManager.getDefaultSharedPreferences(context)
    }

    private fun applySurfaceOverlays(activity: Activity) {
        val prefs = preferences(activity)
        if (prefs.getBoolean(PREF_AMOLED_MODE, false))
            activity.theme.applyStyle(R.style.MpvThemeOverlay_Amoled, true)
        if (prefs.getBoolean(PREF_PURE_BLACK_SURFACES, false))
            activity.theme.applyStyle(R.style.MpvThemeOverlay_PureBlackSurfaces, true)
    }

    @StyleRes
    private fun styleFor(context: Context, target: Target): Int {
        return when (currentValue(context)) {
            "ocean" -> when (target) {
                Target.Player -> R.style.AppTheme_Ocean
                Target.Preference -> R.style.AppTheme_Preference_Ocean
                Target.FilePicker -> R.style.FilePickerTheme_Ocean
                Target.FilePickerSpecial -> R.style.FilePickerThemeSpecial_Ocean
            }
            "crimson" -> when (target) {
                Target.Player -> R.style.AppTheme_Crimson
                Target.Preference -> R.style.AppTheme_Preference_Crimson
                Target.FilePicker -> R.style.FilePickerTheme_Crimson
                Target.FilePickerSpecial -> R.style.FilePickerThemeSpecial_Crimson
            }
            "violet" -> when (target) {
                Target.Player -> R.style.AppTheme_Violet
                Target.Preference -> R.style.AppTheme_Preference_Violet
                Target.FilePicker -> R.style.FilePickerTheme_Violet
                Target.FilePickerSpecial -> R.style.FilePickerThemeSpecial_Violet
            }
            "emerald" -> when (target) {
                Target.Player -> R.style.AppTheme_Emerald
                Target.Preference -> R.style.AppTheme_Preference_Emerald
                Target.FilePicker -> R.style.FilePickerTheme_Emerald
                Target.FilePickerSpecial -> R.style.FilePickerThemeSpecial_Emerald
            }
            "amber" -> when (target) {
                Target.Player -> R.style.AppTheme_Amber
                Target.Preference -> R.style.AppTheme_Preference_Amber
                Target.FilePicker -> R.style.FilePickerTheme_Amber
                Target.FilePickerSpecial -> R.style.FilePickerThemeSpecial_Amber
            }
            "rose" -> when (target) {
                Target.Player -> R.style.AppTheme_Rose
                Target.Preference -> R.style.AppTheme_Preference_Rose
                Target.FilePicker -> R.style.FilePickerTheme_Rose
                Target.FilePickerSpecial -> R.style.FilePickerThemeSpecial_Rose
            }
            else -> when (target) {
                Target.Player -> R.style.AppTheme
                Target.Preference -> R.style.AppTheme_Preference
                Target.FilePicker -> R.style.FilePickerTheme
                Target.FilePickerSpecial -> R.style.FilePickerThemeSpecial
            }
        }
    }
}
