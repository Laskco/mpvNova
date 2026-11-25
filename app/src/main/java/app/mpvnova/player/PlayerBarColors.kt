package app.mpvnova.player

import android.content.Context
import android.graphics.Color

internal fun PlayerSeekbarThumbColor.resolvePlayerBarColor(context: Context): Int = when (this) {
    PlayerSeekbarThumbColor.APP_COLOR -> AppearanceTheme.resolveColorRes(
        context, R.attr.mpvAccent, R.color.tv_purple_hot,
    )
    PlayerSeekbarThumbColor.BLACK -> Color.BLACK
    else -> appearanceColorChoices.firstOrNull { it.value == appearanceValue }?.color ?: Color.WHITE
}
