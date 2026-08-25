@file:Suppress("MagicNumber", "MatchingDeclarationName")

package app.mpvnova.player

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.StringRes

internal data class AppearanceColorChoice(
    val value: String,
    @param:StringRes val labelRes: Int,
    @param:ColorInt val color: Int,
)

internal val appearanceColorChoices = listOf(
    AppearanceColorChoice("nova", R.string.appearance_theme_white, Color.WHITE),
    AppearanceColorChoice("crimson", R.string.appearance_theme_crimson, Color.rgb(244, 67, 54)),
    AppearanceColorChoice("ocean", R.string.appearance_theme_ocean, Color.rgb(33, 150, 243)),
    AppearanceColorChoice("cyan", R.string.appearance_theme_cyan, Color.rgb(0, 172, 193)),
    AppearanceColorChoice("violet", R.string.appearance_theme_violet, Color.rgb(156, 39, 176)),
    AppearanceColorChoice("emerald", R.string.appearance_theme_emerald, Color.rgb(76, 175, 80)),
    AppearanceColorChoice("lime", R.string.appearance_theme_lime, Color.rgb(158, 157, 36)),
    AppearanceColorChoice("amber", R.string.appearance_theme_amber, Color.rgb(255, 152, 0)),
    AppearanceColorChoice("gold", R.string.appearance_theme_gold, Color.rgb(253, 216, 53)),
    AppearanceColorChoice("copper", R.string.appearance_theme_copper, Color.rgb(184, 106, 44)),
    AppearanceColorChoice("indigo", R.string.appearance_theme_indigo, Color.rgb(57, 73, 171)),
    AppearanceColorChoice("rose", R.string.appearance_theme_rose, Color.rgb(216, 27, 96)),
    AppearanceColorChoice("slate", R.string.appearance_theme_slate, Color.rgb(120, 144, 156)),
    AppearanceColorChoice("chrome", R.string.appearance_theme_chrome, Color.rgb(184, 193, 204)),
    AppearanceColorChoice("oyster", R.string.appearance_theme_oyster, Color.rgb(200, 182, 166)),
    AppearanceColorChoice("ivory", R.string.appearance_theme_ivory, Color.rgb(216, 198, 144)),
)
