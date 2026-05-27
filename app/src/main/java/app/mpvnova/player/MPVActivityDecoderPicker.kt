package app.mpvnova.player

import androidx.appcompat.app.AlertDialog
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.os.Build
import android.view.LayoutInflater
import android.view.View
import java.util.Locale

internal fun MPVActivity.pickDecoder() {
    val restore = keepPlaybackForDialog()
    val currentMode = player.currentDecoderMode
    val rawItems = decoderRawItems(currentMode)
    val items = rawItems.toDecoderPickerItems(currentMode)
    val impl = MediaPickerDialog()
    lateinit var dialog: AlertDialog
    impl.onItemClick = { idx ->
        sessionDecoderMode = rawItems[idx].second
        player.applyDecoderMode(rawItems[idx].second)
        updateDecoderButton()
        dialog.dismiss()
    }

    @Suppress("DEPRECATION")
    dialog = with(AlertDialog.Builder(this)) {
        val inflater = LayoutInflater.from(context)
        setView(impl.buildView(
            inflater,
            MediaPickerDialog.Options(
                title = getString(R.string.dialog_title_decoder),
                items = items,
            )
        ))
        setOnDismissListener { restore(); reopenDrawerIfPending() }
        create()
    }
    showWidePlayerDialog(
        dialog,
        PlayerDialogLayout(
            widthFraction = 0.62f,
            maxWidthDp = 760f,
        )
    )
}

internal fun MPVActivity.cycleDecoderMode() {
    val modes = mutableListOf(
        MPVView.DECODER_MODE_HW,
        MPVView.DECODER_MODE_SW,
        MPVView.DECODER_MODE_GNEXT,
    )
    if (shieldDecoderModeEnabled)
        modes.add(MPVView.DECODER_MODE_SHIELD_H10P)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
        modes.add(0, MPVView.DECODER_MODE_HW_PLUS)
    val currentMode = player.currentDecoderMode
    val currentIndex = modes.indexOf(currentMode).takeIf { it >= 0 } ?: 0
    val nextMode = modes[(currentIndex + 1) % modes.size]
    sessionDecoderMode = nextMode
    player.applyDecoderMode(nextMode)
    updateDecoderButton()
}

internal fun MPVActivity.cycleSpeed() {
    player.cycleSpeed()
}

internal fun MPVActivity.currentGpuNextPathLabel(useActivePath: Boolean): String {
    val requestedHwdec = normalizedHwdecOption()
    val activeHwdec = player.hwdecActive.trim().lowercase(Locale.US)
    return currentGpuNextPathLabel(
        useActivePath,
        requestedHwdec,
        activeHwdec
    )
}

internal fun MPVActivity.currentGpuNextBadge(): String {
    val requestedHwdec = (
        mpvGetPropertyString("hwdec")
            ?: mpvGetPropertyString("options/hwdec")
            ?: ""
        ).trim().lowercase(Locale.US)
    val activeHwdec = player.hwdecActive.trim().lowercase(Locale.US)
    val effectiveHwdec = when {
        activeHwdec == "mediacodec-copy" -> "mediacodec-copy"
        activeHwdec == "mediacodec" -> "mediacodec"
        requestedHwdec == "no" -> "no"
        requestedHwdec.isNotBlank() -> requestedHwdec
        else -> activeHwdec
    }

    return when (effectiveHwdec) {
        "mediacodec-copy" -> "G+CPY"
        "mediacodec" -> "G+HW"
        "no" -> "G+SW"
        else -> "G-NXT"
    }
}

internal fun MPVActivity.highlightDecoderLabel(
    label: String,
    activeWord: String?,
    isCurrentMode: Boolean
): CharSequence {
    val word = activeWord.orEmpty()
    val start = label.indexOf(word, ignoreCase = true)
    return if (!isCurrentMode || word.isBlank() || start < 0) {
        label
    } else SpannableString(label).apply {
        val end = start + word.length
        setSpan(
            ForegroundColorSpan(activeFilterColor()),
            start,
            end,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
    }
}

internal fun MPVActivity.decoderMenuLabel(mode: String, isCurrentMode: Boolean): CharSequence {
    return when (mode) {
        MPVView.DECODER_MODE_HW_PLUS ->
            highlightDecoderLabel(getString(R.string.decoder_mode_hw_plus), "direct", isCurrentMode)
        MPVView.DECODER_MODE_HW ->
            highlightDecoderLabel(getString(R.string.decoder_mode_hw), "copy", isCurrentMode)
        MPVView.DECODER_MODE_SW ->
            highlightDecoderLabel(getString(R.string.decoder_mode_sw), "software", isCurrentMode)
        MPVView.DECODER_MODE_GNEXT ->
            highlightDecoderLabel(
                getString(R.string.decoder_mode_gnext_paths),
                currentGpuNextPathLabel(useActivePath = true),
                isCurrentMode,
            )
        MPVView.DECODER_MODE_SHIELD_H10P ->
            highlightDecoderLabel(getString(R.string.decoder_mode_shield_h10p), "Hi10P", isCurrentMode)
        else -> mode
    }
}

internal fun View.setVisibilityIfChanged(newVisibility: Int) {
    if (visibility != newVisibility)
        visibility = newVisibility
}
