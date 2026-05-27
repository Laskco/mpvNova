// MayBeConstant suppressed: promoting to const val would force UPPER_SNAKE
// names, breaking ~29 call sites. Kept camelCase for source compat.
@file:Suppress("MayBeConstant")

package app.mpvnova.player

// Audio-filter preset chains for the player UI. mpv filter slot labels
// (@-prefixed) let us add/replace/drop a single slot without rebuilding
// the whole chain.

internal val voiceBoostFilterLabel = "@voiceboost"
internal val volumeBoostFilterLabel = "@volumeboost"
internal val nightModeFilterLabel = "@nightmode"
internal val audioNormFilterLabel = "@dynaudnorm"
internal val downmixFilterLabel = "@dialoguedownmix"
internal val drcAudioStageFilterLabel = "@drcaudio"

/** DRC body shared between night-mode and the standalone DRC stage. */
internal val drcFilterBody = "dynaudnorm=f=100:p=1/sqrt(2):m=100:s=12:g=11"

internal val volumeBoostStepsDb = VOLUME_BOOST_STEPS_DB

// Preset label string resource IDs — indexed by preset level (0 = off),
// length matches the matching preset chain array.
internal val voiceBoostPresetLabelIds = intArrayOf(
    R.string.filter_value_off,
    R.string.voice_boost_preset_soft,
    R.string.voice_boost_preset_light,
    R.string.voice_boost_preset_clear,
    R.string.voice_boost_preset_speech,
    R.string.voice_boost_preset_loud,
)

internal val downmixPresetLabelIds = intArrayOf(
    R.string.filter_value_off,
    R.string.dialogue_downmix_preset_soft,
    R.string.dialogue_downmix_preset_strong,
    R.string.dialogue_downmix_preset_tv,
    R.string.dialogue_downmix_preset_focus,
    R.string.dialogue_downmix_preset_anchor,
)

internal val nightModePresetLabelIds = intArrayOf(
    R.string.filter_value_off,
    R.string.night_mode_preset_drc,
)

internal val audioNormPresetLabelIds = intArrayOf(
    R.string.filter_value_off,
    R.string.audio_norm_preset_light,
    R.string.audio_norm_preset_smooth,
    R.string.audio_norm_preset_speech,
    R.string.audio_norm_preset_balanced,
    R.string.audio_norm_preset_strong,
    R.string.audio_norm_preset_loudnorm_22,
)

// Filter chain presets — index 0 is the empty (off) state, rest map 1:1
// to the label-ID arrays above.
internal val nightModePresets: List<String> = listOf(
    "",
    "$nightModeFilterLabel:lavfi=[$drcFilterBody]",
)

internal val audioNormPresets: List<String> = listOf(
    "",
    "$audioNormFilterLabel:lavfi=[" +
        "dynaudnorm=framelen=500:gausssize=9:peak=0.94:maxgain=3.0:coupling=1," +
        "equalizer=f=240:t=q:w=1.0:g=-0.5," +
        "equalizer=f=2600:t=q:w=0.9:g=0.5," +
        "acompressor=threshold=-20dB:ratio=1.35:attack=22:release=280:knee=2.5:" +
        "link=average:detection=rms:makeup=1.02," +
        "alimiter=limit=0.98:attack=2:release=24]",
    "$audioNormFilterLabel:lavfi=[" +
        "dynaudnorm=framelen=460:gausssize=9:peak=0.94:maxgain=4.5:coupling=1," +
        "equalizer=f=235:t=q:w=1.0:g=-0.7," +
        "equalizer=f=2700:t=q:w=0.9:g=0.7," +
        "acompressor=threshold=-21dB:ratio=1.55:attack=20:release=300:knee=2.8:" +
        "link=average:detection=rms:makeup=1.05," +
        "alimiter=limit=0.97:attack=2:release=22]",
    "$audioNormFilterLabel:lavfi=[" +
        "dynaudnorm=framelen=420:gausssize=7:peak=0.93:maxgain=6.0:coupling=1," +
        "equalizer=f=230:t=q:w=1.0:g=-0.9," +
        "equalizer=f=2800:t=q:w=0.9:g=0.9," +
        "acompressor=threshold=-22dB:ratio=1.75:attack=18:release=320:knee=3.0:" +
        "link=average:detection=rms:makeup=1.08," +
        "alimiter=limit=0.96:attack=2:release=20]",
    "$audioNormFilterLabel:lavfi=[" +
        "dynaudnorm=framelen=380:gausssize=7:peak=0.93:maxgain=7.5:coupling=1," +
        "equalizer=f=225:t=q:w=1.0:g=-1.1," +
        "equalizer=f=2900:t=q:w=0.9:g=1.1," +
        "acompressor=threshold=-23dB:ratio=1.95:attack=16:release=340:knee=3.2:" +
        "link=average:detection=rms:makeup=1.10," +
        "alimiter=limit=0.95:attack=2:release=18]",
    "$audioNormFilterLabel:lavfi=[" +
        "dynaudnorm=framelen=340:gausssize=5:peak=0.92:maxgain=9.0:coupling=1," +
        "equalizer=f=220:t=q:w=1.0:g=-1.3," +
        "equalizer=f=3000:t=q:w=0.9:g=1.3," +
        "acompressor=threshold=-24dB:ratio=2.15:attack=14:release=360:knee=3.5:" +
        "link=average:detection=rms:makeup=1.12," +
        "alimiter=limit=0.94:attack=2:release=18]",
    "$audioNormFilterLabel:lavfi=[loudnorm=I=-22:TP=-1.5:LRA=2]",
)

internal val voiceBoostPresets: List<String> = listOf(
    "",
    "$voiceBoostFilterLabel:lavfi=[" +
        "highpass=f=72:p=2," +
        "equalizer=f=180:t=q:w=0.8:g=-0.5," +
        "equalizer=f=360:t=q:w=1.0:g=-0.6," +
        "equalizer=f=1250:t=q:w=1.1:g=0.8," +
        "equalizer=f=2300:t=q:w=0.9:g=1.3," +
        "equalizer=f=3400:t=q:w=0.9:g=1.0," +
        "equalizer=f=6500:t=q:w=1.0:g=-0.2]",
    "$voiceBoostFilterLabel:lavfi=[" +
        "highpass=f=76:p=2," +
        "equalizer=f=180:t=q:w=0.8:g=-0.8," +
        "equalizer=f=360:t=q:w=1.0:g=-0.9," +
        "equalizer=f=1300:t=q:w=1.1:g=1.2," +
        "equalizer=f=2400:t=q:w=0.9:g=1.8," +
        "equalizer=f=3500:t=q:w=0.9:g=1.4," +
        "equalizer=f=6600:t=q:w=1.0:g=-0.3]",
    "$voiceBoostFilterLabel:lavfi=[" +
        "highpass=f=80:p=2," +
        "equalizer=f=175:t=q:w=0.8:g=-1.1," +
        "equalizer=f=360:t=q:w=1.0:g=-1.2," +
        "equalizer=f=1400:t=q:w=1.1:g=1.6," +
        "equalizer=f=2550:t=q:w=0.9:g=2.4," +
        "equalizer=f=3650:t=q:w=0.9:g=1.8," +
        "equalizer=f=6800:t=q:w=1.0:g=-0.4]",
    "$voiceBoostFilterLabel:lavfi=[" +
        "highpass=f=84:p=2," +
        "equalizer=f=170:t=q:w=0.8:g=-1.4," +
        "equalizer=f=360:t=q:w=1.0:g=-1.5," +
        "equalizer=f=1500:t=q:w=1.1:g=2.0," +
        "equalizer=f=2700:t=q:w=0.9:g=3.0," +
        "equalizer=f=3800:t=q:w=0.9:g=2.2," +
        "equalizer=f=7000:t=q:w=1.0:g=-0.5]",
    "$voiceBoostFilterLabel:lavfi=[" +
        "highpass=f=88:p=2," +
        "equalizer=f=165:t=q:w=0.8:g=-1.8," +
        "equalizer=f=360:t=q:w=1.0:g=-1.9," +
        "equalizer=f=1600:t=q:w=1.1:g=2.4," +
        "equalizer=f=2900:t=q:w=0.9:g=3.6," +
        "equalizer=f=4000:t=q:w=0.9:g=2.6," +
        "equalizer=f=7200:t=q:w=1.0:g=-0.6]",
)

/**
 * Voice-boost variant tuned to sit on top of the DRC stage. Used when DRC
 * (the recovered native dynaudnorm) is active so Voice Boost doesn't fight
 * it. Same length / level mapping as [voiceBoostPresets].
 */
internal val drcVoiceBoostPresets: List<String> = listOf(
    "",
    "$voiceBoostFilterLabel:lavfi=[" +
        "highpass=f=68:p=2," +
        "equalizer=f=220:t=q:w=0.9:g=-0.4," +
        "equalizer=f=520:t=q:w=1.0:g=-0.5," +
        "equalizer=f=1050:t=q:w=1.1:g=0.8," +
        "equalizer=f=1550:t=q:w=1.0:g=1.6," +
        "equalizer=f=2400:t=q:w=0.95:g=1.8," +
        "equalizer=f=3600:t=q:w=1.0:g=0.6," +
        "equalizer=f=6200:t=q:w=1.0:g=-0.8]",
    "$voiceBoostFilterLabel:lavfi=[" +
        "highpass=f=70:p=2," +
        "equalizer=f=220:t=q:w=0.9:g=-0.7," +
        "equalizer=f=520:t=q:w=1.0:g=-0.8," +
        "equalizer=f=1100:t=q:w=1.1:g=1.2," +
        "equalizer=f=1650:t=q:w=1.0:g=2.2," +
        "equalizer=f=2500:t=q:w=0.95:g=2.4," +
        "equalizer=f=3600:t=q:w=1.0:g=0.8," +
        "equalizer=f=6400:t=q:w=1.0:g=-1.0]",
    "$voiceBoostFilterLabel:lavfi=[" +
        "highpass=f=72:p=2," +
        "equalizer=f=210:t=q:w=0.9:g=-1.0," +
        "equalizer=f=500:t=q:w=1.0:g=-1.1," +
        "equalizer=f=1150:t=q:w=1.1:g=1.6," +
        "equalizer=f=1750:t=q:w=1.0:g=2.8," +
        "equalizer=f=2600:t=q:w=0.95:g=3.0," +
        "equalizer=f=3650:t=q:w=1.0:g=1.0," +
        "equalizer=f=6600:t=q:w=1.0:g=-1.2]",
    "$voiceBoostFilterLabel:lavfi=[" +
        "highpass=f=74:p=2," +
        "equalizer=f=200:t=q:w=0.9:g=-1.3," +
        "equalizer=f=480:t=q:w=1.0:g=-1.4," +
        "equalizer=f=1200:t=q:w=1.1:g=2.0," +
        "equalizer=f=1850:t=q:w=1.0:g=3.4," +
        "equalizer=f=2750:t=q:w=0.95:g=3.6," +
        "equalizer=f=3700:t=q:w=1.0:g=1.1," +
        "equalizer=f=6800:t=q:w=1.0:g=-1.4]",
    "$voiceBoostFilterLabel:lavfi=[" +
        "highpass=f=76:p=2," +
        "equalizer=f=190:t=q:w=0.9:g=-1.6," +
        "equalizer=f=460:t=q:w=1.0:g=-1.7," +
        "equalizer=f=1250:t=q:w=1.1:g=2.4," +
        "equalizer=f=1950:t=q:w=1.0:g=4.0," +
        "equalizer=f=2900:t=q:w=0.95:g=4.2," +
        "equalizer=f=3800:t=q:w=1.0:g=1.2," +
        "equalizer=f=7000:t=q:w=1.0:g=-1.6]",
)
