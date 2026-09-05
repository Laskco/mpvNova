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

    fun build(tab: SubtitleEditorTab, actions: List<Pair<Int, () -> Unit>>) {
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
        steppers.forEach { (control, b) ->
            val row = state.editorRow(control)
            b.titleStyleControlValue.text = row.value
            b.root.alpha = if (row.enabled) 1f else SUBTITLE_DISABLED_ALPHA
            b.titleStyleControlPrevious.isEnabled = row.enabled
            b.titleStyleControlNext.isEnabled = row.enabled
            val label = context.getString(subtitleEditorLabel(control))
            b.titleStyleControlPrevious.contentDescription =
                "$label: ${row.value}, ${context.getString(R.string.btn_decrease)}"
            b.titleStyleControlNext.contentDescription =
                "$label: ${row.value}, ${context.getString(R.string.btn_increase)}"
            val chip = row.chipRgb?.let { rgb -> GradientDrawable().apply {
                setColor(Color.rgb(Color.red(rgb), Color.green(rgb), Color.blue(rgb)))
                setStroke(dp(1), Color.GRAY)
                setBounds(0, 0, dp(ACTION_PADDING_DP), dp(ACTION_PADDING_DP))
            } }
            b.titleStyleControlValue.setCompoundDrawablesRelative(chip, null, null, null)
            b.titleStyleControlValue.compoundDrawablePadding = dp(SWATCH_GAP_DP)
        }
        switches.forEach { (control, button) ->
            val (on, enabled) = state.editorToggle(control)
            button.isChecked = on
            button.isEnabled = enabled
            button.alpha = if (enabled) 1f else SUBTITLE_DISABLED_ALPHA
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
