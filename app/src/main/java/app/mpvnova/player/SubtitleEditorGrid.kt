package app.mpvnova.player

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.SwitchCompat
import app.mpvnova.player.databinding.DialogPlayerTitleStyleControlBinding
import app.mpvnova.player.SubtitleStyleDialog.Control as C

internal enum class SubtitleEditorTab { TEXT, EDGES, LAYOUT, ADVANCED }

internal class SubtitleEditorGrid(
    private val container: LinearLayout,
    private val columns: Int,
    private val adjust: (C, Int) -> Unit,
) {
    private val context = container.context
    private val steppers = mutableMapOf<C, DialogPlayerTitleStyleControlBinding>()
    private val switches = mutableMapOf<C, SwitchCompat>()
    private val lastRows = mutableMapOf<C, SubtitleStyleDialog.Row>()
    private val lastToggles = mutableMapOf<C, Pair<Boolean, Boolean>>()
    private val chipDrawables = mutableMapOf<C, GradientDrawable>()
    private val decreaseLabel = context.getString(R.string.btn_decrease)
    private val increaseLabel = context.getString(R.string.btn_increase)

    fun build(tab: SubtitleEditorTab, actions: List<Pair<Int, () -> Unit>>) {
        steppers.clear()
        switches.clear()
        lastRows.clear()
        lastToggles.clear()
        chipDrawables.clear()
        container.removeAllViews()
        val controls = subtitleEditorControls(tab)
        controls.chunked(columns).forEach { chunk ->
            val row = compactRow()
            chunk.forEach { control ->
                if (control in SUBTITLE_EDITOR_TOGGLES) addToggle(control, row) else addStepper(control, row)
            }
            val stepperHeight = chunk.firstNotNullOfOrNull { steppers[it]?.root?.layoutParams?.height }
            if (stepperHeight != null) chunk.forEach { switches[it]?.minHeight = stepperHeight }
            balanceRow(row, chunk.size)
        }
        if (tab == SubtitleEditorTab.ADVANCED) {
            addNote(R.string.sub_editor_ass_note)
            actions.chunked(columns).forEach { chunk ->
                val row = compactRow()
                chunk.forEach { (label, callback) -> addAction(label, callback, row) }
                balanceRow(row, chunk.size)
            }
            addNote(R.string.sub_editor_fonts_note)
        }
    }

    fun render(state: SubtitleStyleDialog.State) {
        val swatchGapPx = dp(SWATCH_GAP_DP)
        steppers.forEach { (control, binding) ->
            renderStepper(control, binding, state.editorRow(control), swatchGapPx)
        }
        switches.forEach { (control, button) ->
            val toggle = state.editorToggle(control)
            if (lastToggles.put(control, toggle) != toggle) {
                applySubtitleToggleState(button, toggle)
            }
        }
    }

    private fun renderStepper(
        control: C,
        b: DialogPlayerTitleStyleControlBinding,
        row: SubtitleStyleDialog.Row,
        swatchGapPx: Int,
    ) {
        val previous = lastRows.put(control, row)
        if (previous == row) return
        b.titleStyleControlValue.setTextIfChanged(row.value)
        val targetAlpha = if (row.enabled) 1f else SUBTITLE_DISABLED_ALPHA
        if (b.root.alpha != targetAlpha) b.root.alpha = targetAlpha
        if (b.titleStyleControlPrevious.isEnabled != row.enabled) {
            b.titleStyleControlPrevious.isEnabled = row.enabled
        }
        if (b.titleStyleControlNext.isEnabled != row.enabled) {
            b.titleStyleControlNext.isEnabled = row.enabled
        }
        if (previous == null || previous.value != row.value) {
            val label = context.getString(subtitleEditorLabel(control))
            b.titleStyleControlPrevious.contentDescription = "$label: ${row.value}, $decreaseLabel"
            b.titleStyleControlNext.contentDescription = "$label: ${row.value}, $increaseLabel"
        }
        if (previous == null || previous.chipRgb != row.chipRgb) {
            updateStepperChip(b.titleStyleControlValue, chipDrawables, control, row.chipRgb, swatchGapPx)
        }
    }

    private fun addStepper(control: C, parent: LinearLayout) {
        val b = DialogPlayerTitleStyleControlBinding.inflate(LayoutInflater.from(context), parent, false)
        b.root.tag = control
        b.titleStyleControlLabel.setText(subtitleEditorLabel(control))
        b.titleStyleControlPrevious.setOnClickListener { adjust(control, -1) }
        b.titleStyleControlNext.setOnClickListener { adjust(control, 1) }
        parent.addView(b.root)
        steppers[control] = b
    }

    private fun addToggle(control: C, parent: LinearLayout) {
        val button = SwitchCompat(context).apply {
            tag = control
            setText(subtitleEditorLabel(control))
            textSize = CONTROL_TEXT_SP
            setTextColor(context.getColor(R.color.tv_text))
            setBackgroundResource(R.drawable.bg_player_title_style_action)
            setPadding(dp(ACTION_PADDING_DP), dp(CONTENT_PADDING_DP), dp(ACTION_PADDING_DP), dp(CONTENT_PADDING_DP))
            minHeight = dp(TOGGLE_HEIGHT_DP)
            switchPadding = dp(ACTION_PADDING_DP)
            layoutParams = compactCellParams()
            thumbTintList = ColorStateList(
                arrayOf(intArrayOf(android.R.attr.state_checked), intArrayOf()),
                intArrayOf(context.themedColor(R.attr.mpvAccentHot, R.color.tv_purple_hot), Color.LTGRAY),
            )
            setOnClickListener { adjust(control, 1) }
        }
        parent.addView(button)
        switches[control] = button
    }

    private fun addAction(label: Int, callback: () -> Unit, parent: LinearLayout) {
        parent.addView(AppCompatButton(context).apply {
            setText(label)
            isAllCaps = false
            textSize = CONTROL_TEXT_SP
            setTextColor(context.getColor(R.color.tv_text))
            setBackgroundResource(R.drawable.bg_player_title_style_action)
            layoutParams = compactCellParams().apply { height = dp(ACTION_HEIGHT_DP) }
            setOnClickListener { callback() }
        })
    }

    private fun compactRow() = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        isBaselineAligned = false
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT,
        ).apply { bottomMargin = dp(ROW_GAP_DP) }
        container.addView(this)
    }

    private fun compactCellParams() = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
        marginStart = dp(CELL_MARGIN_DP)
        marginEnd = dp(CELL_MARGIN_DP)
    }

    private fun balanceRow(row: LinearLayout, count: Int) {
        repeat(columns - count) { row.addView(View(context), compactCellParams()) }
    }

    private fun addNote(label: Int) {
        container.addView(TextView(context).apply {
            setText(label)
            textSize = NOTE_TEXT_SP
            setTextColor(context.getColor(R.color.tv_text_dim))
            setPadding(dp(CONTENT_PADDING_DP), dp(CONTENT_PADDING_DP), dp(CONTENT_PADDING_DP), dp(ACTION_PADDING_DP))
        })
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}

private fun updateStepperChip(
    valueView: TextView,
    chipDrawables: MutableMap<C, GradientDrawable>,
    control: C,
    chipRgb: Int?,
    swatchGapPx: Int,
) {
    val density = valueView.resources.displayMetrics.density
    val chip = chipRgb?.let { rgb ->
        val drawable = chipDrawables.getOrPut(control) {
            val sizePx = (ACTION_PADDING_DP * density).toInt()
            GradientDrawable().apply {
                setStroke(density.toInt().coerceAtLeast(1), Color.GRAY)
                setBounds(0, 0, sizePx, sizePx)
            }
        }
        drawable.setColor(Color.rgb(Color.red(rgb), Color.green(rgb), Color.blue(rgb)))
        drawable
    }
    valueView.setCompoundDrawablesRelative(chip, null, null, null)
    if (valueView.compoundDrawablePadding != swatchGapPx) {
        valueView.compoundDrawablePadding = swatchGapPx
    }
}

private fun applySubtitleToggleState(button: SwitchCompat, toggle: Pair<Boolean, Boolean>) {
    val (on, enabled) = toggle
    if (button.isChecked != on) button.isChecked = on
    if (button.isEnabled != enabled) button.isEnabled = enabled
    val targetAlpha = if (enabled) 1f else SUBTITLE_DISABLED_ALPHA
    if (button.alpha != targetAlpha) button.alpha = targetAlpha
}

private const val SUBTITLE_DISABLED_ALPHA = 0.4f
private const val SWATCH_GAP_DP = 4
private const val CONTENT_PADDING_DP = 8
private const val ACTION_PADDING_DP = 12
private const val CONTROL_TEXT_SP = 12f
private const val NOTE_TEXT_SP = 11f
private const val ACTION_HEIGHT_DP = 42
private const val ROW_GAP_DP = 6
private const val CELL_MARGIN_DP = 4
private const val TOGGLE_HEIGHT_DP = 64
