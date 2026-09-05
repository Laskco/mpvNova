package app.mpvnova.player

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat

internal fun PlayerSeekbarThumbColor.resolvePlayerBarColor(context: Context): Int = when (this) {
    PlayerSeekbarThumbColor.APP_COLOR -> AppearanceTheme.resolveColor(
        context, R.attr.mpvAccent, ContextCompat.getColor(context, R.color.tv_purple_hot),
    )
    PlayerSeekbarThumbColor.BLACK -> Color.BLACK
    else -> appearanceColorChoices.firstOrNull { it.value == appearanceValue }?.color ?: Color.WHITE
}
