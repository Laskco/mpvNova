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

    private data class StyleSet(
        val player: Int,
        val preference: Int,
        val filePicker: Int,
        val filePickerSpecial: Int
    ) {
        @StyleRes
        fun styleFor(target: Target): Int {
            return when (target) {
                Target.Player -> player
                Target.Preference -> preference
                Target.FilePicker -> filePicker
                Target.FilePickerSpecial -> filePickerSpecial
            }
        }
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
            .apply()
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
        return (STYLE_SETS[currentValue(context)] ?: DEFAULT_STYLE_SET).styleFor(target)
    }

    private val DEFAULT_STYLE_SET = StyleSet(
        player = R.style.AppTheme,
        preference = R.style.AppTheme_Preference,
        filePicker = R.style.FilePickerTheme,
        filePickerSpecial = R.style.FilePickerThemeSpecial
    )
    private val STYLE_SETS = mapOf(
        "ocean" to StyleSet(
            R.style.AppTheme_Ocean,
            R.style.AppTheme_Preference_Ocean,
            R.style.FilePickerTheme_Ocean,
            R.style.FilePickerThemeSpecial_Ocean
        ),
        "crimson" to StyleSet(
            R.style.AppTheme_Crimson,
            R.style.AppTheme_Preference_Crimson,
            R.style.FilePickerTheme_Crimson,
            R.style.FilePickerThemeSpecial_Crimson
        ),
        "violet" to StyleSet(
            R.style.AppTheme_Violet,
            R.style.AppTheme_Preference_Violet,
            R.style.FilePickerTheme_Violet,
            R.style.FilePickerThemeSpecial_Violet
        ),
        "emerald" to StyleSet(
            R.style.AppTheme_Emerald,
            R.style.AppTheme_Preference_Emerald,
            R.style.FilePickerTheme_Emerald,
            R.style.FilePickerThemeSpecial_Emerald
        ),
        "amber" to StyleSet(
            R.style.AppTheme_Amber,
            R.style.AppTheme_Preference_Amber,
            R.style.FilePickerTheme_Amber,
            R.style.FilePickerThemeSpecial_Amber
        ),
        "rose" to StyleSet(
            R.style.AppTheme_Rose,
            R.style.AppTheme_Preference_Rose,
            R.style.FilePickerTheme_Rose,
            R.style.FilePickerThemeSpecial_Rose
        )
    )
}
