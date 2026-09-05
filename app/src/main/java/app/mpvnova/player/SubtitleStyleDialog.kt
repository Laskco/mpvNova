package app.mpvnova.player

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import app.mpvnova.player.databinding.DialogSubtitleStyleBinding

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
        FONT_SIZE, LINE_SPACING, SIDE_MARGIN, ALIGNMENT, OUTLINE_OPACITY, SHADOW_OPACITY, SCALE, POSITION, FONT_HINTING,
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
        val presetName: String,
        val extraRows: Map<Control, Row>,
    )


    private lateinit var binding: DialogSubtitleStyleBinding
    private var selectedTab = SubtitleEditorTab.TEXT
    private var grid: SubtitleEditorGrid? = null
    private var lastState: State? = null
    private var columns = 3

    var onAdjust: ((Control, Int) -> State)? = null
    var stateProvider: (() -> State)? = null
    var onAddFont: (() -> Unit)? = null
    var onRemoveFont: (() -> Unit)? = null
    var onSavePreset: (() -> Unit)? = null
    var onApplyPreset: (() -> Unit)? = null
    var onEditPreset: (() -> Unit)? = null
    var onDeletePreset: (() -> Unit)? = null
    var onDone: (() -> Unit)? = null
    var onReset: (() -> Unit)? = null
    var onCyclePreset: ((Int) -> Unit)? = null
    var onPreviewHeightChanged: ((Int) -> Int)? = null
        set(value) {
            field = value
            if (::binding.isInitialized && value != null) binding.stylePreview.reportContentHeight()
        }

    fun buildView(layoutInflater: LayoutInflater): View {
        if (!::binding.isInitialized) {
            binding = DialogSubtitleStyleBinding.inflate(layoutInflater)
            bindControls()
        } else {
            binding.root.detachFromParent()
        }
        stateProvider?.invoke()?.let { render(it) }
        select(selectedTab)
        return binding.root
    }

    private fun bindControls() {
        val b = binding
        b.stylePreview.onContentHeightChanged = { desiredHeight ->
            val viewportHeight = onPreviewHeightChanged?.invoke(desiredHeight) ?: desiredHeight
            if (b.stylePreviewPanel.layoutParams.height != viewportHeight) {
                b.stylePreviewPanel.layoutParams = b.stylePreviewPanel.layoutParams.apply { height = viewportHeight }
            }
        }
        b.styleMaster.setOnClickListener { adjust(Control.MASTER, 1) }
        b.styleDoneBtn.setOnClickListener { onDone?.invoke() }
        b.styleSaveBtn.setOnClickListener { onSavePreset?.invoke() }
        b.styleResetBtn.setOnClickListener { onReset?.invoke() }
        b.stylePresetValue.setOnClickListener { onApplyPreset?.invoke() }
        b.stylePresetMinus.setOnClickListener { cyclePreset(-1) }
        b.stylePresetPlus.setOnClickListener { cyclePreset(1) }
        tabs().forEach { (tab, button) -> button.setOnClickListener { select(tab) } }
        b.styleContent.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            val count = if (view.width / view.resources.displayMetrics.density < 680) 2 else 3
            if (count != columns) {
                columns = count
                select(selectedTab)
            }
        }
    }

    private fun tabs(): Map<SubtitleEditorTab, Button> = mapOf(
        SubtitleEditorTab.TEXT to binding.styleTextTab,
        SubtitleEditorTab.EDGES to binding.styleEdgesTab,
        SubtitleEditorTab.LAYOUT to binding.styleLayoutTab,
        SubtitleEditorTab.ADVANCED to binding.styleAdvancedTab,
    )

    private fun select(tab: SubtitleEditorTab) {
        selectedTab = tab
        tabs().forEach { (item, button) ->
            button.isSelected = item == tab
            button.isActivated = item == tab
        }
        grid = SubtitleEditorGrid(binding.styleContent, columns, ::adjust).also { editor ->
            editor.build(tab, listOf(
                R.string.sub_style_add_font to { onAddFont?.invoke(); Unit },
                R.string.sub_style_remove_font to { onRemoveFont?.invoke(); Unit },
                R.string.sub_style_edit_preset to { onEditPreset?.invoke(); Unit },
                R.string.sub_style_delete_preset to { onDeletePreset?.invoke(); Unit },
            ))
            lastState?.let(editor::render)
        }
        binding.styleScroll.scrollTo(0, 0)
    }

    private fun cyclePreset(delta: Int) {
        onCyclePreset?.invoke(delta)
        stateProvider?.invoke()?.let(::render)
    }

    fun refresh() {
        stateProvider?.invoke()?.let(::render)
    }

    private fun adjust(control: Control, delta: Int) {
        onAdjust?.invoke(control, delta)?.let(::render)
    }

    private fun render(state: State) {
        lastState = state
        binding.styleTitle.text = state.title
        binding.styleMaster.isChecked = state.masterOn
        binding.stylePreview.setSpec(state.preview)
        binding.stylePresetValue.text = state.presetName
        grid?.render(state)
    }
}
