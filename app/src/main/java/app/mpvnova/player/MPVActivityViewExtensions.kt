package app.mpvnova.player

import android.content.res.ColorStateList
import android.text.TextUtils
import androidx.core.content.ContextCompat
import android.widget.ImageButton
import android.widget.TextView

internal fun TextView.setTextIfChanged(newText: CharSequence?) {
    val resolvedText = newText ?: ""
    if (!TextUtils.equals(text, resolvedText))
        text = resolvedText
}

internal fun ImageButton.setImageTintColorIfChanged(color: Int) {
    if (imageTintList?.defaultColor != color)
        imageTintList = ColorStateList.valueOf(color)
}

internal fun MPVActivity.activeFilterColor(): Int {
    cachedActiveFilterColor?.let { return it }
    val color = AppearanceTheme.resolveColor(
        this,
        R.attr.mpvAccentHot,
        ContextCompat.getColor(this, R.color.tv_purple_hot)
    )
    cachedActiveFilterColor = color
    return color
}

internal fun MPVActivity.refreshFilterTint(btn: ImageButton, active: Boolean) {
    val color = if (active)
        activeFilterColor()
    else
        ContextCompat.getColor(this, R.color.tv_text)
    btn.setImageTintColorIfChanged(color)
}

internal fun MPVActivity.isVoiceBoostOn() = voiceBoostLevel > 0

internal fun MPVActivity.isVolumeBoostOn() = volumeBoostDb > 0

internal fun MPVActivity.isNightModeOn() = nightModeLevel > 0

internal fun MPVActivity.isAudioNormOn() = audioNormLevel > 0
