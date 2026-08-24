package app.mpvnova.player

import android.util.Log


internal fun MPVActivity.refreshAllFilterTints() {
    refreshFilterTint(binding.voiceBoostBtn, isVoiceBoostOn())
    refreshFilterTint(binding.volumeBoostBtn, isVolumeBoostOn())
    refreshFilterTint(binding.nightModeBtn, isNightModeOn())
    refreshFilterTint(binding.audioNormBtn, isAudioNormOn())
}

internal fun MPVActivity.buildAudioFilterChain(): String {
    val filters = mutableListOf<String>()
    if (isNightModeOn()) {
        if (isDownmixOn())
            surroundDialogueDownmixFilter()?.let { filters += it }
        filters += buildNightModeAudioStageFilter()
        if (isVoiceBoostOn())
            filters += drcVoiceBoostPresets[voiceBoostLevel]
    } else {
        if (isDownmixOn())
            surroundDialogueDownmixFilter()?.let { filters += it }
        if (isCenterBoostOn())
            filters += buildCenterBoostAudioStageFilter()
        if (isAudioNormOn())
            filters += audioNormPresets[audioNormLevel]
        if (isVoiceBoostOn())
            filters += voiceBoostPresets[voiceBoostLevel]
        if (isVolumeBoostOn())
            filters += volumeBoostFilter()
    }
    return filters.joinToString(",")
}

internal fun MPVActivity.applyInitialAudioFilterDefaults() {
    applyNightModeDecoderDrcScale()
    val filterChain = if (persistAudioFilters) buildAudioFilterChain() else ""
    val result = mpvSetOptionString("af", filterChain)
    if (result < 0) {
        Log.w(MPV_ACTIVITY_TAG, "Failed to configure initial audio filters: $result")
    }
    // Track metadata is unavailable before initialization. The post-load pass
    // must still reconcile codec- and channel-dependent filters at runtime.
    lastAppliedAudioFilterChain = null
}

internal fun MPVActivity.applySavedAudioFilterDefaults() {
    applyNightModeDecoderDrcScale()
    val filterChain = if (persistAudioFilters) buildAudioFilterChain() else ""
    mpvSetPropertyString("af", filterChain)
    lastAppliedAudioFilterChain = filterChain
}

internal fun MPVActivity.applyAudioFilterState(force: Boolean = false) {
    applyNightModeDecoderDrcScale()
    val filterChain = buildAudioFilterChain()
    if (!force && filterChain == lastAppliedAudioFilterChain) return
    mpvSetPropertyString("af", filterChain)
    lastAppliedAudioFilterChain = filterChain
}

internal fun MPVActivity.rebuildAudioFilters(force: Boolean = false) {
    applyAudioFilterState(force)
}

internal fun MPVActivity.adjustVoiceBoost(delta: Int, wrap: Boolean = false): MediaPickerDialog.ValueState {
    val maxLevel = voiceBoostPresets.lastIndex
    val nextLevel = when {
        wrap -> {
            when {
                voiceBoostLevel + delta > maxLevel -> 0
                voiceBoostLevel + delta < 0 -> maxLevel
                else -> voiceBoostLevel + delta
            }
        }
        else -> (voiceBoostLevel + delta).coerceIn(0, maxLevel)
    }
    voiceBoostLevel = nextLevel
    rebuildAudioFilters()
    refreshAllFilterTints()
    writeSettings()
    showToast(
        getString(R.string.btn_voice_boost),
        if (isVoiceBoostOn()) getVoiceBoostLabel() else getString(R.string.status_off)
    )
    return currentVoiceBoostState()
}

internal fun MPVActivity.adjustVolumeBoost(delta: Int, wrap: Boolean = false): MediaPickerDialog.ValueState {
    val currentIndex = volumeBoostStepsDb.indexOf(volumeBoostDb).takeIf { it >= 0 } ?: 0
    val maxIndex = volumeBoostStepsDb.lastIndex
    val nextIndex = when {
        wrap -> {
            when {
                currentIndex + delta > maxIndex -> 0
                currentIndex + delta < 0 -> maxIndex
                else -> currentIndex + delta
            }
        }
        else -> (currentIndex + delta).coerceIn(0, maxIndex)
    }
    volumeBoostDb = volumeBoostStepsDb[nextIndex]
    rebuildAudioFilters()
    refreshAllFilterTints()
    writeSettings()
    showToast(
        getString(R.string.btn_volume_boost),
        if (isVolumeBoostOn()) getVolumeBoostLabel() else getString(R.string.status_off)
    )
    return currentVolumeBoostState()
}

internal fun MPVActivity.adjustDownmix(delta: Int, wrap: Boolean = false): MediaPickerDialog.ValueState {
    val maxLevel = downmixPresetLabelIds.lastIndex
    val nextLevel = when {
        wrap -> {
            when {
                downmixLevel + delta > maxLevel -> 0
                downmixLevel + delta < 0 -> maxLevel
                else -> downmixLevel + delta
            }
        }
        else -> (downmixLevel + delta).coerceIn(0, maxLevel)
    }
    downmixLevel = nextLevel
    rebuildAudioFilters()
    writeSettings()
    showToast(
        getString(R.string.btn_dialogue_downmix),
        getDownmixLabel()
    )
    return currentDownmixState()
}

internal fun MPVActivity.adjustCenterBoost(delta: Int, wrap: Boolean = false): MediaPickerDialog.ValueState {
    val maxLevel = centerBoostMixLevels.lastIndex
    val nextLevel = when {
        wrap -> {
            when {
                centerBoostLevel + delta > maxLevel -> 0
                centerBoostLevel + delta < 0 -> maxLevel
                else -> centerBoostLevel + delta
            }
        }
        else -> (centerBoostLevel + delta).coerceIn(0, maxLevel)
    }
    centerBoostLevel = nextLevel
    rebuildAudioFilters()
    writeSettings()
    showToast(
        getString(R.string.btn_center_boost),
        if (isCenterBoostOn()) getCenterBoostLabel() else getString(R.string.status_off)
    )
    return currentCenterBoostState()
}
