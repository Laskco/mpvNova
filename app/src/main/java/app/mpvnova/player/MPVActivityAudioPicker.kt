package app.mpvnova.player

internal fun MPVActivity.configureAudioPickerCallbacks(
    impl: MediaPickerDialog,
    tracks: List<MPVView.Track>,
    dismiss: () -> Unit,
) {
    impl.onItemClick = { idx ->
        val trackId = tracks[idx].mpvId
        player.aid = trackId
        saveUserTrackPick("audio", trackId)
        dismiss()
        trackSwitchNotification { TrackData(trackId, "audio") }
    }
    impl.onVoiceBoostAdjust = { delta -> adjustVoiceBoost(delta) }
    impl.onVolumeBoostAdjust = { delta -> adjustVolumeBoost(delta) }
    impl.onNightModeAdjust = { delta -> adjustNightMode(delta) }
    impl.onAudioNormAdjust = { delta -> adjustAudioNorm(delta) }
    impl.onDownmixAdjust = { delta -> adjustDownmix(delta) }
    impl.onCenterBoostAdjust = { delta -> adjustCenterBoost(delta) }
    impl.onFilterStatesRefresh = { currentFilterStates() }
    impl.onPersistClick = {
        persistAudioFilters = !persistAudioFilters
        writeSettings()
        showToast(
            getString(R.string.pref_persist_filters_title),
            getString(if (persistAudioFilters) R.string.status_on else R.string.status_off)
        )
    }
}

internal fun MPVActivity.audioPickerOptions(items: List<MediaPickerDialog.Item>): MediaPickerDialog.Options {
    return MediaPickerDialog.Options(
        title = getString(R.string.dialog_title_audio),
        items = items,
        showDelay = true,
        delayText = formatAudioDelayMs(currentAudioDelayMs()),
        showFilters = true,
        initialVoiceBoostState = currentVoiceBoostState(),
        initialVolumeBoostState = currentVolumeBoostState(),
        initialNightModeState = currentNightModeState(),
        initialAudioNormState = currentAudioNormState(),
        initialDownmixState = currentDownmixState(),
        initialCenterBoostState = currentCenterBoostState(),
        persistFiltersOn = persistAudioFilters,
    )
}
