package app.mpvnova.player

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.res.Resources
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
        applyTheme(activity, Target.Player)
    }

    fun applyPreferences(activity: Activity) {
        applyTheme(activity, Target.Preference)
    }

    fun applyFilePicker(activity: Activity) {
        applyTheme(activity, Target.FilePicker)
    }

    fun applySpecialFilePicker(activity: Activity) {
        applyTheme(activity, Target.FilePickerSpecial)
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

    private fun applyTheme(activity: Activity, target: Target) {
        migrateLegacyOled(activity)
        val baseStyle = styleFor(activity, target)
        activity.setTheme(baseStyle)
        val replacement = activity.resources.newTheme().apply {
            applyStyle(baseStyle, true)
            applySurfaceOverlays(this, preferences(activity))
        }
        activity.theme.setTo(replacement)
    }

    private fun applySurfaceOverlays(theme: Resources.Theme, prefs: SharedPreferences) {
        if (prefs.getBoolean(PREF_AMOLED_MODE, false))
            theme.applyStyle(R.style.MpvThemeOverlay_Amoled, true)
        if (prefs.getBoolean(PREF_PURE_BLACK_SURFACES, false))
            theme.applyStyle(R.style.MpvThemeOverlay_PureBlackSurfaces, true)
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
        "cyan" to StyleSet(
            R.style.AppTheme_Cyan,
            R.style.AppTheme_Preference_Cyan,
            R.style.FilePickerTheme_Cyan,
            R.style.FilePickerThemeSpecial_Cyan
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
        "lime" to StyleSet(
            R.style.AppTheme_Lime,
            R.style.AppTheme_Preference_Lime,
            R.style.FilePickerTheme_Lime,
            R.style.FilePickerThemeSpecial_Lime
        ),
        "gold" to StyleSet(
            R.style.AppTheme_Gold,
            R.style.AppTheme_Preference_Gold,
            R.style.FilePickerTheme_Gold,
            R.style.FilePickerThemeSpecial_Gold
        ),
        "amber" to StyleSet(
            R.style.AppTheme_Amber,
            R.style.AppTheme_Preference_Amber,
            R.style.FilePickerTheme_Amber,
            R.style.FilePickerThemeSpecial_Amber
        ),
        "copper" to StyleSet(
            R.style.AppTheme_Copper,
            R.style.AppTheme_Preference_Copper,
            R.style.FilePickerTheme_Copper,
            R.style.FilePickerThemeSpecial_Copper
        ),
        "indigo" to StyleSet(
            R.style.AppTheme_Indigo,
            R.style.AppTheme_Preference_Indigo,
            R.style.FilePickerTheme_Indigo,
            R.style.FilePickerThemeSpecial_Indigo
        ),
        "rose" to StyleSet(
            R.style.AppTheme_Rose,
            R.style.AppTheme_Preference_Rose,
            R.style.FilePickerTheme_Rose,
            R.style.FilePickerThemeSpecial_Rose
        ),
        "slate" to StyleSet(
            R.style.AppTheme_Slate,
            R.style.AppTheme_Preference_Slate,
            R.style.FilePickerTheme_Slate,
            R.style.FilePickerThemeSpecial_Slate
        ),
        "chrome" to StyleSet(
            R.style.AppTheme_Chrome,
            R.style.AppTheme_Preference_Chrome,
            R.style.FilePickerTheme_Chrome,
            R.style.FilePickerThemeSpecial_Chrome
        ),
        "oyster" to StyleSet(
            R.style.AppTheme_Oyster,
            R.style.AppTheme_Preference_Oyster,
            R.style.FilePickerTheme_Oyster,
            R.style.FilePickerThemeSpecial_Oyster
        ),
        "ivory" to StyleSet(
            R.style.AppTheme_Ivory,
            R.style.AppTheme_Preference_Ivory,
            R.style.FilePickerTheme_Ivory,
            R.style.FilePickerThemeSpecial_Ivory
        )
    )
}
