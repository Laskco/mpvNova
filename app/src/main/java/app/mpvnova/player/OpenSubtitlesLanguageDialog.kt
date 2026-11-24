package app.mpvnova.player

import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.app.AlertDialog
import androidx.core.graphics.ColorUtils
import androidx.core.view.doOnNextLayout
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.MaterialColors

internal class OpenSubtitlesLanguageDialog(
    private val activity: MPVActivity,
    languages: List<OpenSubtitlesLanguage>,
    selectedCode: String,
    onSelected: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    private var closed = false
    private val selectedPosition = languages.indexOfFirst { it.code == selectedCode }.coerceAtLeast(0)
    private val picker = OptionPickerDialog(R.string.os_search_title, R.string.os_search_language,
        languages.map { OptionPickerDialog.Item(it.code, it.name, it.code, it.code == selectedCode) })
    private val content = picker.buildView(LayoutInflater.from(activity))
    private val list = content.findViewById<RecyclerView>(R.id.list)
    private val viewport = list.parent as View
    private val rowExtent = languageRowExtent()
    private val dialog = AlertDialog.Builder(activity).setView(content)
        .setOnDismissListener { closed = true; onDismiss() }.create()
    private val desiredListHeight = languages.size.coerceAtMost(VISIBLE_CHOICES) * rowExtent +
        list.paddingTop + list.paddingBottom

    init {
        val opaqueColors = intArrayOf(
            ColorUtils.setAlphaComponent(MaterialColors.getColor(content, R.attr.mpvOverlayStart), OPAQUE_ALPHA),
            ColorUtils.setAlphaComponent(MaterialColors.getColor(content, R.attr.mpvOverlayEnd), OPAQUE_ALPHA),
        )
        (content.background?.mutate() as? GradientDrawable)?.colors = opaqueColors
        list.layoutParams = list.layoutParams.apply { height = ViewGroup.LayoutParams.MATCH_PARENT }
        list.setPadding(list.paddingLeft, list.paddingTop,
            Utils.convertDp(activity, LANGUAGE_SCROLLBAR_GUTTER_DP), list.paddingBottom)
        picker.onCancelClick = { close() }
        picker.onItemPicked = { item -> onSelected(item.id); close() }
    }

    fun show() {
        if (closed) return
        activity.showWidePlayerDialog(dialog, PlayerDialogLayout(
            widthFraction = 0.60f, maxWidthDp = 600f,
            heightFraction = 0.92f, maxHeightDp = 540f,
        ))
        val maxHeight = dialog.window?.attributes?.height ?: return
        fitOpenSubtitlesPanel(dialog, content, viewport, maxHeight, desiredListHeight,
            rowExtent = rowExtent, rowPadding = list.paddingTop + list.paddingBottom)
        content.post {
            if (!closed && dialog.isShowing) {
                list.scrollToPosition(selectedPosition)
                list.post { focusSelectedRow() }
            }
        }
    }

    private fun focusSelectedRow() {
        if (closed || !dialog.isShowing) return
        val row = list.findViewHolderForAdapterPosition(selectedPosition)?.itemView
        if (row != null) row.requestFocus()
        else {
            list.doOnNextLayout {
                if (!closed && dialog.isShowing)
                    list.findViewHolderForAdapterPosition(selectedPosition)?.itemView?.requestFocus()
            }
            list.requestLayout()
        }
    }

    fun close() { closed = true; dialog.dismiss() }

    private fun languageRowExtent(): Int {
        val row = activity.layoutInflater.inflate(R.layout.dialog_setting_option_item, list, false)
        row.findViewById<android.widget.TextView>(R.id.optionTitleText).text = "M"
        row.findViewById<android.widget.TextView>(R.id.optionDetailText).text = "en"
        val width = Utils.convertDp(activity, LANGUAGE_MEASURE_WIDTH_DP)
        row.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        val margins = row.layoutParams as ViewGroup.MarginLayoutParams
        return row.measuredHeight + margins.topMargin + margins.bottomMargin
    }

    private companion object {
        const val OPAQUE_ALPHA = 255
        const val VISIBLE_CHOICES = 5
        const val LANGUAGE_MEASURE_WIDTH_DP = 400f
        const val LANGUAGE_SCROLLBAR_GUTTER_DP = 20f
    }
}

/** The AlertDialog host wraps custom content; explicitly bound both content and scroll viewport. */
internal fun fitOpenSubtitlesPanel(
    dialog: AlertDialog, content: View, scroll: View, maxHeight: Int, desiredScrollHeight: Int,
    rowExtent: Int = 0, rowPadding: Int = 0, rowDivider: Int = 0,
    flexibleForm: MaxHeightScrollView? = null,
) {
    val window = dialog.window ?: return
    val width = window.attributes.width
    if (width <= 0 || maxHeight <= 0) return
    scroll.visibility = if (desiredScrollHeight > 0) View.VISIBLE else View.GONE
    scroll.layoutParams = scroll.layoutParams.apply { height = 0 }
    flexibleForm?.maxHeightPx = 0
    content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
        View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
    var chromeHeight = content.measuredHeight
    if (flexibleForm != null && chromeHeight + desiredScrollHeight > maxHeight) {
        // Large UI/font sizes keep whole results; the form remains reachable by normal focus scrolling.
        flexibleForm.maxHeightPx = Utils.convertDp(content.context, FORM_ROW_HEIGHT_DP)
        content.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED))
        chromeHeight = content.measuredHeight
    }
    val available = desiredScrollHeight.coerceIn(0, (maxHeight - chromeHeight).coerceAtLeast(0))
    val rows = if (rowExtent > 0) ((available - rowPadding + rowDivider) / rowExtent).coerceAtLeast(0) else 0
    val scrollHeight = if (rowExtent > 0) {
        if (rows > 0) rowPadding + rows * rowExtent - rowDivider else 0
    } else available
    scroll.layoutParams = scroll.layoutParams.apply { height = scrollHeight }
    val height = (chromeHeight + scrollHeight).coerceAtMost(maxHeight)
    content.layoutParams = content.layoutParams.apply { this.height = height }
    window.setLayout(width, height)
}

private const val FORM_ROW_HEIGHT_DP = 48f
