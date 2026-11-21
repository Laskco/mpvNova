package app.mpvnova.player

import java.util.Locale

internal fun MPVActivity.adjustNightMode(delta: Int, wrap: Boolean = false): MediaPickerDialog.ValueState {
    val maxLevel = nightModePresets.lastIndex
    val nextLevel = when {
        wrap -> {
            when {
                nightModeLevel + delta > maxLevel -> 0
                nightModeLevel + delta < 0 -> maxLevel
                else -> nightModeLevel + delta
            }
        }
        else -> (nightModeLevel + delta).coerceIn(0, maxLevel)
    }
    nightModeLevel = nextLevel
    if (nightModeLevel > 0 && audioNormLevel > 0)
        audioNormLevel = 0
    rebuildAudioFilters()
    refreshAllFilterTints()
    writeSettings()
    showToast(
        getString(R.string.btn_night_mode),
        if (isNightModeOn()) getNightModeLabel() else getString(R.string.status_off)
    )
    return currentNightModeState()
}

internal fun MPVActivity.adjustAudioNorm(delta: Int, wrap: Boolean = false): MediaPickerDialog.ValueState {
    val maxLevel = audioNormPresets.lastIndex
    val nextLevel = when {
        wrap -> {
            when {
                audioNormLevel + delta > maxLevel -> 0
                audioNormLevel + delta < 0 -> maxLevel
                else -> audioNormLevel + delta
            }
        }
        else -> (audioNormLevel + delta).coerceIn(0, maxLevel)
    }
    audioNormLevel = nextLevel
    if (audioNormLevel > 0 && nightModeLevel > 0)
        nightModeLevel = 0
    rebuildAudioFilters()
    refreshAllFilterTints()
    writeSettings()
    showToast(
        getString(R.string.btn_audio_norm),
        if (isAudioNormOn()) getAudioNormLabel() else getString(R.string.status_off)
    )
    return currentAudioNormState()
}

internal fun MPVActivity.isSubScaleOn() = subScaleSteps[subScaleLevel] != DEFAULT_SUB_SCALE

internal fun MPVActivity.isSubPosOn() = subPosSteps[subPosLevel] != DEFAULT_SUB_POSITION_PERCENT

internal fun MPVActivity.isSecondaryPosOn() =
    secondaryPosSteps[secondaryPosLevel] != DEFAULT_SECONDARY_SUB_POSITION_PERCENT

internal fun MPVActivity.isSubWidthOn() = subWidthSteps[subWidthLevel] != DEFAULT_SUB_WIDTH_PERCENT

internal fun MPVActivity.getSubScaleLabel(): String =
    if (isSubScaleOn()) String.format(Locale.US, "%.2fx", subScaleSteps[subScaleLevel])
    else getString(R.string.sub_scale_default)

internal fun MPVActivity.getSubPosLabel(): String =
    if (isSubPosOn()) "${subPosSteps[subPosLevel]}%"
    else getString(R.string.sub_pos_default)

internal fun MPVActivity.getSecondaryPosLabel(): String =
    if (isSecondaryPosOn()) "${secondaryPosSteps[secondaryPosLevel]}%"
    else getString(R.string.sub_pos_default)

internal fun MPVActivity.getSubWidthLabel(): String =
    if (isSubWidthOn()) "${subWidthSteps[subWidthLevel]}%"
    else getString(R.string.sub_width_default)
