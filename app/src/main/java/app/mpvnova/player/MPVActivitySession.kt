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
    readAudioFilterSettings(prefs)
    clampAudioFilterState()
    readSubFilterSettings(prefs)
    clampSubFilterState()
}

internal fun MPVActivity.writeSettings() {
    val prefs = getDefaultSharedPreferences(applicationContext)

    with (prefs.edit()) {
        putBoolean("use_time_remaining", useTimeRemaining)
        putBoolean("persist_audio_filters", persistAudioFilters)
        putBoolean("voice_boost_on", if (persistAudioFilters) isVoiceBoostOn() else false)
        putInt("voice_boost_level", if (persistAudioFilters) voiceBoostLevel else 0)
        putInt("volume_boost_db", if (persistAudioFilters) volumeBoostDb else 0)
        putBoolean("night_mode_on", if (persistAudioFilters) isNightModeOn() else false)
        putInt("night_mode_level", if (persistAudioFilters) nightModeLevel else 0)
        putBoolean("audio_norm_on", if (persistAudioFilters) isAudioNormOn() else false)
        putInt("audio_norm_level", if (persistAudioFilters) audioNormLevel else 0)
        putBoolean("downmix_on", if (persistAudioFilters) isDownmixOn() else false)
        putInt("downmix_level", if (persistAudioFilters) downmixLevel else 0)

        putBoolean("persist_sub_filters", persistSubFilters)
        putInt("sub_scale_level", if (persistSubFilters) subScaleLevel else DEFAULT_SUB_SCALE_INDEX)
        putInt(
            "sub_pos_pct",
            if (persistSubFilters) subPosSteps[subPosLevel] else DEFAULT_SUB_POSITION_PERCENT
        )
        putInt(
            "secondary_sub_pos_pct",
            if (persistSubFilters) {
                secondaryPosSteps[secondaryPosLevel]
            } else {
                DEFAULT_SECONDARY_SUB_POSITION_PERCENT
            }
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
