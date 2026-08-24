package app.mpvnova.player

import app.mpvnova.player.databinding.DialogSubtitleStyleBinding
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageButton
import android.widget.TextView

internal class SubtitleStyleDialog {

    enum class Control {
        MASTER,
        IMAGE_SUB_GRAYSCALE,
        TEXT_COLOR,
        TEXT_OPACITY,
        EDGE,
        OUTLINE_COLOR,
        OUTLINE_SIZE,
        BLUR,
        SHADOW_SIZE,
        SHADOW_COLOR,
        BG_OPACITY,
        BG_COLOR,
        FONT,
        SPACING,
        JUSTIFY,
        BOLD,
        ITALIC,
        OVERRIDE_ASS,
        SELECTIVE_ASS,
        FORCE_ALL_ASS,
    }

    data class Row(val value: String, val enabled: Boolean = true, val chipRgb: Int? = null)

    data class State(
        val title: String,
        val masterOn: Boolean,
        val imageSubtitleGrayOn: Boolean,
        val textColor: Row,
        val textOpacity: Row,
        val edge: Row,
        val outlineColor: Row,
        val outlineSize: Row,
        val blur: Row,
        val shadowSize: Row,
        val shadowColor: Row,
        val bgOpacity: Row,
        val bgColor: Row,
        val font: Row,
        val spacing: Row,
        val justify: Row,
        val boldOn: Boolean,
        val italicOn: Boolean,
        val overrideOn: Boolean,
        val overrideEnabled: Boolean,
        val selectiveOn: Boolean,
        val selectiveEnabled: Boolean,
        val forceAllOn: Boolean,
        val forceAllEnabled: Boolean,
        val preview: SubtitleStylePreviewView.Spec,
    )

    private lateinit var binding: DialogSubtitleStyleBinding

    // delta is -1/+1; toggles ignore it and flip. Returns the new full state.
    var onAdjust: ((Control, Int) -> State)? = null
    var stateProvider: (() -> State)? = null
    var onAddFont: (() -> Unit)? = null
    var onRemoveFont: (() -> Unit)? = null
    var onSavePreset: (() -> Unit)? = null
    var onApplyPreset: (() -> Unit)? = null
    var onEditPreset: (() -> Unit)? = null
    var onDeletePreset: (() -> Unit)? = null

    fun buildView(layoutInflater: LayoutInflater): View {
        if (!::binding.isInitialized) {
            binding = DialogSubtitleStyleBinding.inflate(layoutInflater)
            bindControls()
        } else {
            binding.root.detachFromParent()
            binding.styleScroll.scrollTo(0, 0)
        }
        stateProvider?.invoke()?.let { render(it) }
        return binding.root
    }

    private fun bindControls() {
        val b = binding
        b.masterRow.setOnClickListener { adjust(Control.MASTER, 1) }
        b.imageSubtitleGrayRow.setOnClickListener { adjust(Control.IMAGE_SUB_GRAYSCALE, 1) }
        b.textColorMinusBtn.setOnClickListener { adjust(Control.TEXT_COLOR, -1) }
        b.textColorPlusBtn.setOnClickListener { adjust(Control.TEXT_COLOR, 1) }
        b.textOpacityMinusBtn.setOnClickListener { adjust(Control.TEXT_OPACITY, -1) }
        b.textOpacityPlusBtn.setOnClickListener { adjust(Control.TEXT_OPACITY, 1) }
        b.edgeMinusBtn.setOnClickListener { adjust(Control.EDGE, -1) }
        b.edgePlusBtn.setOnClickListener { adjust(Control.EDGE, 1) }
        b.outlineColorMinusBtn.setOnClickListener { adjust(Control.OUTLINE_COLOR, -1) }
        b.outlineColorPlusBtn.setOnClickListener { adjust(Control.OUTLINE_COLOR, 1) }
        b.outlineSizeMinusBtn.setOnClickListener { adjust(Control.OUTLINE_SIZE, -1) }
        b.outlineSizePlusBtn.setOnClickListener { adjust(Control.OUTLINE_SIZE, 1) }
        b.blurMinusBtn.setOnClickListener { adjust(Control.BLUR, -1) }
        b.blurPlusBtn.setOnClickListener { adjust(Control.BLUR, 1) }
        b.shadowSizeMinusBtn.setOnClickListener { adjust(Control.SHADOW_SIZE, -1) }
        b.shadowSizePlusBtn.setOnClickListener { adjust(Control.SHADOW_SIZE, 1) }
        b.shadowColorMinusBtn.setOnClickListener { adjust(Control.SHADOW_COLOR, -1) }
        b.shadowColorPlusBtn.setOnClickListener { adjust(Control.SHADOW_COLOR, 1) }
        b.spacingMinusBtn.setOnClickListener { adjust(Control.SPACING, -1) }
        b.spacingPlusBtn.setOnClickListener { adjust(Control.SPACING, 1) }
        b.justifyMinusBtn.setOnClickListener { adjust(Control.JUSTIFY, -1) }
        b.justifyPlusBtn.setOnClickListener { adjust(Control.JUSTIFY, 1) }
        b.bgOpacityMinusBtn.setOnClickListener { adjust(Control.BG_OPACITY, -1) }
        b.bgOpacityPlusBtn.setOnClickListener { adjust(Control.BG_OPACITY, 1) }
        b.bgColorMinusBtn.setOnClickListener { adjust(Control.BG_COLOR, -1) }
        b.bgColorPlusBtn.setOnClickListener { adjust(Control.BG_COLOR, 1) }
        b.fontMinusBtn.setOnClickListener { adjust(Control.FONT, -1) }
        b.fontPlusBtn.setOnClickListener { adjust(Control.FONT, 1) }
        b.boldRow.setOnClickListener { adjust(Control.BOLD, 1) }
        b.italicRow.setOnClickListener { adjust(Control.ITALIC, 1) }
        b.addFontRow.setOnClickListener { onAddFont?.invoke() }
        b.removeFontRow.setOnClickListener { onRemoveFont?.invoke() }
        b.savePresetRow.setOnClickListener { onSavePreset?.invoke() }
        b.applyPresetRow.setOnClickListener { onApplyPreset?.invoke() }
        b.editPresetRow.setOnClickListener { onEditPreset?.invoke() }
        b.deletePresetRow.setOnClickListener { onDeletePreset?.invoke() }
        b.overrideAssRow.setOnClickListener { adjust(Control.OVERRIDE_ASS, 1) }
        b.selectiveAssRow.setOnClickListener { adjust(Control.SELECTIVE_ASS, 1) }
        b.forceAllAssRow.setOnClickListener { adjust(Control.FORCE_ALL_ASS, 1) }
    }

    private fun adjust(control: Control, delta: Int) {
        onAdjust?.invoke(control, delta)?.let { render(it) }
    }

    private fun render(state: State) {
        val b = binding
        b.styleTitle.text = state.title
        b.stylePreview.setSpec(state.preview)
        b.masterCheck.visibility = checkVisibility(state.masterOn)
        renderToggle(
            b.imageSubtitleGrayRow,
            b.imageSubtitleGrayCheck,
            state.imageSubtitleGrayOn,
            enabled = true,
        )

        renderStepper(
            b.textColorRow, b.textColorValue, b.textColorMinusBtn, b.textColorPlusBtn, state.textColor,
        )
        applyChip(b.textColorChip, state.textColor)
        renderStepper(
            b.textOpacityRow, b.textOpacityValue, b.textOpacityMinusBtn, b.textOpacityPlusBtn, state.textOpacity,
        )
        renderStepper(
            b.edgeRow, b.edgeValue, b.edgeMinusBtn, b.edgePlusBtn, state.edge,
        )
        renderStepper(
            b.outlineColorRow, b.outlineColorValue, b.outlineColorMinusBtn, b.outlineColorPlusBtn, state.outlineColor,
        )
        applyChip(b.outlineColorChip, state.outlineColor)
        renderStepper(
            b.outlineSizeRow, b.outlineSizeValue, b.outlineSizeMinusBtn, b.outlineSizePlusBtn, state.outlineSize,
        )
        renderStepper(
            b.blurRow, b.blurValue, b.blurMinusBtn, b.blurPlusBtn, state.blur,
        )
        renderStepper(
            b.shadowSizeRow, b.shadowSizeValue, b.shadowSizeMinusBtn, b.shadowSizePlusBtn, state.shadowSize,
        )
        renderStepper(
            b.shadowColorRow, b.shadowColorValue, b.shadowColorMinusBtn, b.shadowColorPlusBtn, state.shadowColor,
        )
        applyChip(b.shadowColorChip, state.shadowColor)
        renderStepper(
            b.bgOpacityRow, b.bgOpacityValue, b.bgOpacityMinusBtn, b.bgOpacityPlusBtn, state.bgOpacity,
        )
        renderStepper(
            b.bgColorRow, b.bgColorValue, b.bgColorMinusBtn, b.bgColorPlusBtn, state.bgColor,
        )
        applyChip(b.bgColorChip, state.bgColor)
        renderStepper(
            b.fontRow, b.fontValue, b.fontMinusBtn, b.fontPlusBtn, state.font,
        )
        renderStepper(
            b.spacingRow, b.spacingValue, b.spacingMinusBtn, b.spacingPlusBtn, state.spacing,
        )
        renderStepper(
            b.justifyRow, b.justifyValue, b.justifyMinusBtn, b.justifyPlusBtn, state.justify,
        )

        renderToggle(b.boldRow, b.boldCheck, state.boldOn, state.masterOn)
        renderToggle(b.italicRow, b.italicCheck, state.italicOn, state.masterOn)
        renderToggle(b.overrideAssRow, b.overrideAssCheck, state.overrideOn, state.overrideEnabled)
        renderToggle(b.selectiveAssRow, b.selectiveAssCheck, state.selectiveOn, state.selectiveEnabled)
        renderToggle(b.forceAllAssRow, b.forceAllAssCheck, state.forceAllOn, state.forceAllEnabled)
    }

    private fun renderToggle(row: View, check: View, on: Boolean, enabled: Boolean) {
        row.alpha = rowAlpha(enabled)
        row.isClickable = enabled
        check.visibility = checkVisibility(on && enabled)
    }

    private fun renderStepper(row: View, value: TextView, minus: ImageButton, plus: ImageButton, state: Row) {
        value.text = state.value
        row.alpha = rowAlpha(state.enabled)
        // Keep buttons focusable so D-pad focus doesn't jump when a row dims.
        minus.isClickable = state.enabled
        plus.isClickable = state.enabled
    }

    private fun applyChip(chip: View, state: Row) {
        val rgb = state.chipRgb
        if (rgb == null) {
            chip.visibility = View.GONE
            return
        }
        chip.visibility = View.VISIBLE
        chip.setBackgroundColor(OPAQUE_ALPHA or (rgb and RGB_MASK))
    }

    private fun checkVisibility(on: Boolean) = if (on) View.VISIBLE else View.INVISIBLE

    private fun rowAlpha(enabled: Boolean) = if (enabled) 1f else DISABLED_ALPHA

    companion object {
        private const val DISABLED_ALPHA = 0.4f
        private const val OPAQUE_ALPHA = -0x1000000 // 0xFF000000 as Int
        private const val RGB_MASK = 0xFFFFFF
    }
}
