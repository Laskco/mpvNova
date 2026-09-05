package app.mpvnova.player

import android.view.Gravity
import androidx.appcompat.app.AlertDialog
import kotlin.math.roundToInt

internal fun MPVActivity.bindSubtitlePreviewWindow(dialog: AlertDialog, editor: SubtitleStyleDialog) {
    val window = dialog.window ?: return
    val screenHeight = binding.root.height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
    val sizing = SubtitlePreviewWindowSizing(screenHeight, window.attributes.height, resources.displayMetrics.density)
    editor.onPreviewHeightChanged = { desired ->
        val size = sizing.forPreview(desired)
        if (dialog.isShowing) {
            window.attributes = window.attributes.apply {
                gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                y = sizing.bottomGap
                height = size.windowHeight
            }
        }
        size.previewHeight
    }
}

internal data class SubtitlePreviewWindowSize(val windowHeight: Int, val previewHeight: Int)

internal class SubtitlePreviewWindowSizing(screenHeight: Int, baselineHeight: Int, density: Float) {
    val bottomGap = ((screenHeight - baselineHeight) / 2).coerceAtLeast(0)
    private val minimumPreview = (PREVIEW_HEIGHT_DP * density).roundToInt()
    private val gap = (PANEL_GAP_DP * density).roundToInt()
    private val editorHeight = (baselineHeight - minimumPreview - gap).coerceAtLeast(1)
    private val minimumEditor = minOf(editorHeight, (MIN_EDITOR_HEIGHT_DP * density).roundToInt())
    private val maximumHeight = maxOf(baselineHeight,
        screenHeight - bottomGap - (TOP_INSET_DP * density).roundToInt())

    fun forPreview(desiredHeight: Int): SubtitlePreviewWindowSize {
        val previewHeight = desiredHeight.coerceIn(minimumPreview, maximumHeight - minimumEditor - gap)
        return SubtitlePreviewWindowSize(minOf(maximumHeight, editorHeight + previewHeight + gap), previewHeight)
    }
}

private const val PREVIEW_HEIGHT_DP = 84f
private const val PANEL_GAP_DP = 10f
private const val MIN_EDITOR_HEIGHT_DP = 280f
private const val TOP_INSET_DP = 24f
