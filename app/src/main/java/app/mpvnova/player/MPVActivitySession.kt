package app.mpvnova.player

import android.app.ForegroundServiceStartNotAllowedException
import android.content.Intent
import android.util.Log
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager.getDefaultSharedPreferences

internal fun MPVActivity.isPlayingAudioOnly(): Boolean {
    if (!isPlayingAudio)
        return false
    val image = mpvGetPropertyString("current-tracks/video/image")
    return image.isNullOrEmpty() || image == "yes"
}

internal fun MPVActivity.shouldBackground(): Boolean {
    if (isFinishing) // about to exit?
        return false
    return when (backgroundPlayMode) {
        "always" -> true
        "audio-only" -> isPlayingAudioOnly()
        else -> false // "never"
    }
}

internal fun MPVActivity.tryStartForegroundService(intent: Intent): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        try {
            ContextCompat.startForegroundService(this, intent)
        } catch (e: ForegroundServiceStartNotAllowedException) {
            Log.w(MPV_ACTIVITY_TAG, e)
            return false
        }
    } else {
        ContextCompat.startForegroundService(this, intent)
    }
    return true
}

internal fun MPVActivity.readSettings() {
    val prefs = getDefaultSharedPreferences(applicationContext)
    val getString: (String, Int) -> String = { key, defaultRes ->
        prefs.getString(key, resources.getString(defaultRes)) ?: resources.getString(defaultRes)
    }

    readPlaybackSettings(prefs, getString)
    playerUiCustomization = PlayerUiCustomizationStore.read(prefs)
    migrateRetiredPlayerUiSettings(prefs)
    readAudioFilterSettings(prefs)
    clampAudioFilterState()
    readSubFilterSettings(prefs)
    clampSubFilterState()
    readSubtitleStyleSettings(prefs)
}

internal fun MPVActivity.writeSettings() {
    val prefs = getDefaultSharedPreferences(applicationContext)

    with (prefs.edit()) {
        putBoolean("use_time_remaining", useTimeRemaining)
        putBoolean("persist_audio_filters", persistAudioFilters)
        putBoolean("voice_boost_on", persistedAudioFilterEnabled(isVoiceBoostOn()))
        putInt("voice_boost_level", persistedAudioFilterLevel(voiceBoostLevel))
        putInt("volume_boost_db", persistedAudioFilterLevel(volumeBoostDb))
        putBoolean("night_mode_on", persistedAudioFilterEnabled(isNightModeOn()))
        putInt("night_mode_level", persistedAudioFilterLevel(nightModeLevel))
        putBoolean("audio_norm_on", persistedAudioFilterEnabled(isAudioNormOn()))
        putInt("audio_norm_level", persistedAudioFilterLevel(audioNormLevel))
        putBoolean("downmix_on", persistedAudioFilterEnabled(isDownmixOn()))
        putInt("downmix_level", persistedAudioFilterLevel(downmixLevel))
        putBoolean("center_boost_on", persistedAudioFilterEnabled(isCenterBoostOn()))
        putInt("center_boost_level", persistedAudioFilterLevel(centerBoostLevel))

        putBoolean("persist_sub_filters", persistSubFilters)
        putInt("sub_scale_level", persistedSubFilterLevel(subScaleLevel, DEFAULT_SUB_SCALE_INDEX))
        putInt("sub_scale_steps_version", SUB_SCALE_STEPS_VERSION)
        putInt(
            "sub_pos_pct",
            persistedSubPositionLevel(subPosSteps[subPosLevel], DEFAULT_SUB_POSITION_PERCENT)
        )
        putInt(
            "secondary_sub_pos_pct",
            persistedSubPositionLevel(
                secondaryPosSteps[secondaryPosLevel],
                DEFAULT_SECONDARY_SUB_POSITION_PERCENT
            )
        )
        apply()
    }
}

internal fun MPVActivity.clampAudioFilterState() {
    voiceBoostLevel = voiceBoostLevel.coerceIn(0, voiceBoostPresets.lastIndex)
    nightModeLevel = nightModeLevel.coerceIn(0, nightModePresets.lastIndex)
    audioNormLevel = audioNormLevel.coerceIn(0, audioNormPresets.lastIndex)
    if (nightModeLevel > 0 && audioNormLevel > 0)
        audioNormLevel = 0
    downmixLevel = downmixLevel.coerceIn(0, downmixPresetLabelIds.lastIndex)
    centerBoostLevel = centerBoostLevel.coerceIn(0, centerBoostMixLevels.lastIndex)
    val volumeIndex = volumeBoostStepsDb.indexOf(volumeBoostDb)
    volumeBoostDb = if (volumeIndex >= 0) {
        volumeBoostDb
    } else {
        volumeBoostStepsDb.first()
    }
}

internal fun MPVActivity.savePosition() {
    val eofReached = mpvGetPropertyBoolean("eof-reached")
    val shouldWrite = shouldSavePosition &&
        resumeIdentityFromSource(currentResumeSource) == null &&
        eofReached != true
    if (shouldWrite) {
        mpvCommand(arrayOf("write-watch-later-config"))
    } else if (eofReached ?: true) {
        Log.d(MPV_ACTIVITY_TAG, "player indicates EOF, not saving watch-later config")
    }
}
