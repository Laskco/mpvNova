package app.mpvnova.player

import android.util.Log
import java.util.Locale

internal const val AUDIO_FILTER_OUTPUT_SAMPLE_FORMAT = "flt"

internal fun stableAudioAresampleOptions(
    controlledDownmixActive: Boolean,
    centerBoostMixLevel: String?,
): String {
    val options = mutableListOf<String>()
    if (controlledDownmixActive) {
        options += "in_chlayout=stereo"
        options += "out_chlayout=stereo"
    } else if (centerBoostMixLevel != null) {
        options += "out_chlayout=stereo"
    }
    options += "out_sample_fmt=$AUDIO_FILTER_OUTPUT_SAMPLE_FORMAT"
    centerBoostMixLevel?.let { options += "center_mix_level=$it" }
    return options.joinToString(":")
}

internal fun MPVActivity.centerBoostMixLevelLabel(): String {
    return String.format(Locale.US, "%.1f", centerBoostMixLevels[centerBoostLevel])
}

private fun MPVActivity.buildAudioAresampleFilter(owner: String): String {
    val sourceChannels = currentAudioChannelCount()
    val controlledDownmixActive = isDownmixOn() && sourceChannels >= MIN_SURROUND_CHANNELS
    val centerBoostMixLevel = if (isCenterBoostOn()) centerBoostMixLevelLabel() else null
    val options = stableAudioAresampleOptions(controlledDownmixActive, centerBoostMixLevel)
    Log.i(
        MPV_ACTIVITY_TAG,
        if (controlledDownmixActive)
            "$owner using controlled Channel Downmix output: ${sourceChannels}ch -> stereo"
        else
            "$owner active without forced center downmix: ${sourceChannels}ch source"
    )
    return "aresample=$options"
}

internal fun MPVActivity.buildDrcAresampleFilter(): String = buildAudioAresampleFilter("DRC")

internal fun MPVActivity.buildCenterBoostAudioStageFilter(): String {
    return "$centerBoostFilterLabel:lavfi=[${buildAudioAresampleFilter("Center Boost")}]"
}
