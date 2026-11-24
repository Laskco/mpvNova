package app.mpvnova.player

import android.view.View
import android.view.KeyEvent
import android.view.MotionEvent
import android.widget.AbsListView
import android.widget.AdapterView
import android.widget.ListView
import kotlin.math.roundToInt

internal class OpenSubtitlesResultsScroll(private val list: ListView, private val thumb: View) :
    AbsListView.OnScrollListener, AdapterView.OnItemSelectedListener {
    var rowHeight = 0
    private var closed = false
    private var windowStart = 0
    private var keySelection = -1
    private var hovering = false
    private var hoverX = 0f
    private var hoverY = 0f
    private val minThumbHeightPx = Utils.convertDp(list.context, MIN_THUMB_HEIGHT_DP)
    private val layoutReady get() = rowHeight > 0 && list.height > 0 && list.count > 0
    private val layoutListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        updateThumb()
        updateRowStates()
    }

    init {
        list.setOnScrollListener(this)
        list.onItemSelectedListener = this
        list.onFocusChangeListener = View.OnFocusChangeListener { _, _ -> updateRowStates() }
        list.setOnHoverListener { _, event ->
            hovering = event.actionMasked != MotionEvent.ACTION_HOVER_EXIT
            hoverX = event.x
            hoverY = event.y
            updateRowStates()
            false
        }
        list.addOnLayoutChangeListener(layoutListener)
        list.setOnKeyListener { _, code, event -> moveSelection(code, event) }
    }

    override fun onScroll(view: AbsListView?, firstVisibleItem: Int, visibleItemCount: Int, totalItemCount: Int) {
        updateThumb()
        updateRowStates()
    }

    override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) = updateRowStates()
    override fun onNothingSelected(parent: AdapterView<*>?) = updateRowStates()

    private fun updateRowStates() {
        if (closed) return
        val hoverPosition = if (hovering) list.pointToPosition(hoverX.toInt(), hoverY.toInt())
            else AdapterView.INVALID_POSITION
        val showKeyboardSelection = list.isEnabled && list.hasFocus() && hoverPosition == AdapterView.INVALID_POSITION
        for (index in 0 until list.childCount) {
            val position = list.firstVisiblePosition + index
            list.getChildAt(index).apply {
                isActivated = showKeyboardSelection && position == list.selectedItemPosition
                isHovered = list.isEnabled && position == hoverPosition
            }
        }
    }

    override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) {
        if (scrollState != AbsListView.OnScrollListener.SCROLL_STATE_IDLE) keySelection = -1
    }

    private fun restoreConfirmationHighlight(code: Int, event: KeyEvent) {
        if (event.action == KeyEvent.ACTION_DOWN && (
                code == KeyEvent.KEYCODE_DPAD_CENTER ||
                    code == KeyEvent.KEYCODE_ENTER ||
                    code == KeyEvent.KEYCODE_NUMPAD_ENTER
                )
        ) {
            hovering = false
            updateRowStates()
        }
    }

    private fun moveSelection(code: Int, event: KeyEvent): Boolean {
        if (closed || !layoutReady || !list.isEnabled) return false
        restoreConfirmationHighlight(code, event)
        val delta = when (code) {
            KeyEvent.KEYCODE_DPAD_UP -> -1
            KeyEvent.KEYCODE_DPAD_DOWN -> 1
            else -> 0
        }
        val selected = if (keySelection >= 0) keySelection else list.selectedItemPosition
        val next = selected + delta
        val consumed = event.action == KeyEvent.ACTION_DOWN && delta != 0 && selected >= 0
        if (consumed) {
            hovering = false
            if (next in 0 until list.count) selectRow(next)
            else {
                val target = if (delta < 0) list.nextFocusUpId else list.nextFocusDownId
                list.rootView.findViewById<View>(target)?.requestFocus()
                resetSelection()
            }
        }
        return consumed
    }

    fun resetSelection() {
        keySelection = -1
        windowStart = 0
    }

    private fun selectRow(next: Int) {
        if (keySelection < 0) windowStart = list.firstVisiblePosition
        val extent = rowHeight + list.dividerHeight
        val visibleRows = ((list.height + list.dividerHeight) / extent).coerceAtLeast(1)
        windowStart = windowStart.coerceIn((next - visibleRows + 1).coerceAtLeast(0), next)
            .coerceAtMost((list.count - visibleRows).coerceAtLeast(0))
        keySelection = next
        // One position update per key, never a frame-by-frame correction fighting ListView.
        list.setSelectionFromTop(next, (next - windowStart) * extent)
    }

    private fun updateThumb() {
        if (closed || !layoutReady) {
            thumb.setVisibilityIfChanged(View.INVISIBLE)
        } else {
            val extent = rowHeight + list.dividerHeight
            val range = list.count * extent - list.dividerHeight
            val travelRange = range - list.height
            thumb.setVisibilityIfChanged(if (travelRange > 0) View.VISIBLE else View.INVISIBLE)
            if (travelRange > 0) {
                val height = maxOf(
                    minThumbHeightPx,
                    (list.height.toFloat() * list.height / range).roundToInt(),
                ).coerceAtMost(list.height)
                if (thumb.layoutParams.height != height) {
                    thumb.layoutParams = thumb.layoutParams.apply { this.height = height }
                }
                val offset = (list.firstVisiblePosition * extent - (list.getChildAt(0)?.top ?: 0))
                    .coerceIn(0, travelRange)
                val targetY = (list.height - height) * offset.toFloat() / travelRange
                if (thumb.translationY != targetY) {
                    thumb.translationY = targetY
                }
            }
        }
    }

    fun close() {
        closed = true
        list.setOnScrollListener(null)
        list.onItemSelectedListener = null
        list.onFocusChangeListener = null
        list.setOnHoverListener(null)
        list.setOnKeyListener(null)
        list.removeOnLayoutChangeListener(layoutListener)
    }
}

private const val MIN_THUMB_HEIGHT_DP = 48f
