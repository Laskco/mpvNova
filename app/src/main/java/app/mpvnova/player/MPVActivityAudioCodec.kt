package app.mpvnova.player

import java.util.Locale

private val AUDIO_CODEC_FIELDS = arrayOf(
    "codec",
    "demux-codec",
    "codec-name",
    "decoder-desc",
)

internal fun MPVActivity.selectedAudioTrackCodec(): String? {
    if (mpvGetPropertyInt("current-tracks/audio/id") != null) {
        return audioTrackCodec("current-tracks/audio")
    }
    val trackCount = mpvGetPropertyInt("track-list/count") ?: return null
    return (0 until trackCount)
        .firstOrNull { index ->
            mpvGetPropertyString("track-list/$index/type") == "audio" &&
                mpvGetPropertyBoolean("track-list/$index/selected") == true
        }
        ?.let(::audioTrackCodec)
}

internal fun MPVActivity.audioTrackCodec(index: Int): String? =
    audioTrackCodec("track-list/$index")

private fun MPVActivity.audioTrackCodec(prefix: String): String? =
    AUDIO_CODEC_FIELDS.firstNotNullOfOrNull { field ->
        mpvGetPropertyString("$prefix/$field")?.takeIf { it.isNotBlank() }
    }

internal fun MPVActivity.currentAudioCodecName(): String? {
    return selectedAudioTrackCodec()
        ?: mpvGetPropertyString("audio-codec-name")
        ?: mpvGetPropertyString("audio-codec")
}

internal fun String?.isEac3CodecName(): Boolean {
    val normalized = this
        ?.trim()
        ?.lowercase(Locale.US)
        ?: return false
    return normalized == "eac3" ||
        normalized == "e-ac-3" ||
        normalized == "e-ac3" ||
        normalized.contains("eac3") ||
        normalized.contains("e-ac-3")
}
