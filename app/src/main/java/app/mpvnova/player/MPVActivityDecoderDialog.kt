package app.mpvnova.player

import android.os.Build
import java.util.Locale

internal fun MPVActivity.decoderRawItems(currentMode: String): MutableList<Pair<CharSequence, String>> {
    val items = mutableListOf(
        decoderItem(MPVView.DECODER_MODE_HW, currentMode),
        decoderItem(MPVView.DECODER_MODE_SW, currentMode),
        decoderItem(MPVView.DECODER_MODE_GNEXT, currentMode),
        decoderItem(MPVView.DECODER_MODE_SHIELD_H10P, currentMode),
    )
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        items.add(0, decoderItem(MPVView.DECODER_MODE_HW_PLUS, currentMode))
    return items
}

private fun MPVActivity.decoderItem(mode: String, currentMode: String): Pair<CharSequence, String> {
    return decoderMenuLabel(mode, mode == currentMode) to mode
}

internal fun List<Pair<CharSequence, String>>.toDecoderPickerItems(
    currentMode: String
): List<MediaPickerDialog.Item> {
    return map { MediaPickerDialog.Item(it.first, it.second, it.second == currentMode) }
}

internal fun currentGpuNextPathLabel(
    useActivePath: Boolean,
    requestedHwdec: String,
    activeHwdec: String,
    currentDecoderMode: String
): String {
    val effectiveHwdec = when {
        useActivePath && activeHwdec.isNotBlank() && activeHwdec != "no" -> activeHwdec
        useActivePath && requestedHwdec == "no" -> "no"
        requestedHwdec.isNotBlank() -> requestedHwdec
        else -> activeHwdec
    }

    return when {
        effectiveHwdec == "mediacodec-copy" -> "copy"
        effectiveHwdec == "mediacodec" -> "direct"
        effectiveHwdec == "no" || currentDecoderMode == MPVView.DECODER_MODE_SHIELD_H10P -> "software"
        else -> "copy"
    }
}

internal fun normalizedHwdecOption(): String {
    return (
        mpvGetPropertyString("hwdec")
            ?: mpvGetPropertyString("options/hwdec")
            ?: ""
        ).trim().lowercase(Locale.US)
}
