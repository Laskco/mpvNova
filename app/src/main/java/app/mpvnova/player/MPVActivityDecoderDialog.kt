package app.mpvnova.player

import android.os.Build
import java.util.Locale

internal fun MPVActivity.decoderRawItems(currentMode: String): MutableList<Pair<CharSequence, String>> {
    val items = mutableListOf(
        decoderItem(MPVView.DECODER_MODE_AUTO_SAFE, currentMode),
        decoderItem(MPVView.DECODER_MODE_HW, currentMode),
        decoderItem(MPVView.DECODER_MODE_SW, currentMode),
        decoderItem(MPVView.DECODER_MODE_GNEXT, currentMode),
    )
    if (supportsGpuNextDirect())
        items.add(decoderItem(MPVView.DECODER_MODE_GNEXT_DIRECT, currentMode))
    items.add(decoderItem(MPVView.DECODER_MODE_MPV_CONF, currentMode))
    if (shieldDecoderModeEnabled)
        items.add(decoderItem(MPVView.DECODER_MODE_SHIELD_H10P, currentMode))
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        items.add(1, decoderItem(MPVView.DECODER_MODE_HW_PLUS, currentMode))
    return items
}

private fun MPVActivity.decoderItem(mode: String, currentMode: String): Pair<CharSequence, String> {
    // Shield Hi10P rides the gpu-next copy path underneath — keep that row's
    // active-path word lit while Shield mode is current, without selecting it.
    val highlightLabel = mode == currentMode ||
        (mode == MPVView.DECODER_MODE_GNEXT && currentMode == MPVView.DECODER_MODE_SHIELD_H10P)
    val label = if (mode == MPVView.DECODER_MODE_GNEXT_DIRECT && !canSelectGpuNextDirect()) {
        getString(R.string.decoder_mode_gnext_direct) + "\n" +
            getString(R.string.decoder_mode_gnext_direct_shield_warning)
    } else {
        decoderMenuLabel(mode, highlightLabel)
    }
    return label to mode
}

internal fun List<Pair<CharSequence, String>>.toDecoderPickerItems(
    currentMode: String
): List<MediaPickerDialog.Item> {
    return map {
        MediaPickerDialog.Item(
            label = it.first,
            tag = it.second,
            selected = it.second == currentMode,
            enabled = it.second != MPVView.DECODER_MODE_GNEXT_DIRECT || canSelectGpuNextDirect(),
        )
    }
}

internal fun currentGpuNextPathLabel(
    useActivePath: Boolean,
    requestedHwdec: String,
    activeHwdec: String
): String {
    val effectiveHwdec = when {
        useActivePath && activeHwdec.isNotBlank() -> activeHwdec
        useActivePath && requestedHwdec == "no" -> "no"
        requestedHwdec.isNotBlank() -> requestedHwdec
        else -> activeHwdec
    }

    return when {
        effectiveHwdec == "mediacodec-copy" -> "copy"
        effectiveHwdec == "mediacodec" -> "direct"
        effectiveHwdec == "no" -> "software"
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
