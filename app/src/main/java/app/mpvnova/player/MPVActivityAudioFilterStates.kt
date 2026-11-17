package app.mpvnova.player


internal fun MPVActivity.getNightModeLabel(): String = getString(nightModePresetLabelIds[nightModeLevel])

internal fun MPVActivity.getAudioNormLabel(): String = getString(audioNormPresetLabelIds[audioNormLevel])

internal fun MPVActivity.getCenterBoostLabel(): String {
    return if (isCenterBoostOn()) centerBoostMixLevelLabel() else getString(R.string.filter_value_off)
}

internal fun MPVActivity.currentVoiceBoostState(): MediaPickerDialog.ValueState {
    val maxLevel = voiceBoostPresets.lastIndex
    return MediaPickerDialog.ValueState(
        label = getVoiceBoostLabel(),
        active = isVoiceBoostOn(),
        canDecrease = voiceBoostLevel > 0,
        canIncrease = voiceBoostLevel < maxLevel
    )
}

internal fun MPVActivity.currentVolumeBoostState(): MediaPickerDialog.ValueState {
    val currentIndex = volumeBoostStepsDb.indexOf(volumeBoostDb).takeIf { it >= 0 } ?: 0
    val maxIndex = volumeBoostStepsDb.lastIndex
    return MediaPickerDialog.ValueState(
        label = getVolumeBoostLabel(),
        active = isVolumeBoostOn(),
        canDecrease = currentIndex > 0,
        canIncrease = currentIndex < maxIndex
    )
}

internal fun MPVActivity.currentDownmixState(): MediaPickerDialog.ValueState {
    val maxLevel = downmixPresetLabelIds.lastIndex
    val channels = if (isDownmixOn()) currentAudioChannelCount() else null
    return MediaPickerDialog.ValueState(
        label = getDownmixLabel(channels),
        active = channels != null && channels >= MIN_SURROUND_CHANNELS,
        canDecrease = downmixLevel > 0,
        canIncrease = downmixLevel < maxLevel
    )
}

internal fun MPVActivity.currentCenterBoostState(): MediaPickerDialog.ValueState {
    val maxLevel = centerBoostMixLevels.lastIndex
    return MediaPickerDialog.ValueState(
        label = getCenterBoostLabel(),
        active = isCenterBoostOn(),
        canDecrease = centerBoostLevel > 0,
        canIncrease = centerBoostLevel < maxLevel
    )
}

internal fun MPVActivity.currentNightModeState(): MediaPickerDialog.ValueState {
    if (isAudioNormOn()) {
        return MediaPickerDialog.ValueState(
            label = getString(R.string.filter_blocked_by_audio_norm),
            active = false,
            enabled = false,
            canDecrease = false,
            canIncrease = false
        )
    }
    val maxLevel = nightModePresets.lastIndex
    return MediaPickerDialog.ValueState(
        label = getNightModeLabel(),
        active = isNightModeOn(),
        enabled = true,
        canDecrease = nightModeLevel > 0,
        canIncrease = nightModeLevel < maxLevel
    )
}

internal fun MPVActivity.currentAudioNormState(): MediaPickerDialog.ValueState {
    if (isNightModeOn()) {
        return MediaPickerDialog.ValueState(
            label = getString(R.string.filter_blocked_by_drc),
            active = false,
            enabled = false,
            canDecrease = false,
            canIncrease = false
        )
    }
    val maxLevel = audioNormPresets.lastIndex
    return MediaPickerDialog.ValueState(
        label = getAudioNormLabel(),
        active = isAudioNormOn(),
        enabled = true,
        canDecrease = audioNormLevel > 0,
        canIncrease = audioNormLevel < maxLevel
    )
}

internal fun MPVActivity.currentFilterStates(): MediaPickerDialog.FilterStates {
    return MediaPickerDialog.FilterStates(
        voiceBoost = currentVoiceBoostState(),
        volumeBoost = currentVolumeBoostState(),
        nightMode = currentNightModeState(),
        audioNorm = currentAudioNormState(),
        downmix = currentDownmixState(),
        centerBoost = currentCenterBoostState()
    )
}
