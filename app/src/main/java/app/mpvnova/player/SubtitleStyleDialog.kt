package app.mpvnova.player

import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
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


    private class CachedTabGrid(
        val container: LinearLayout,
        val grid: SubtitleEditorGrid,
    )

    private lateinit var binding: DialogSubtitleStyleBinding
    private var selectedTab = SubtitleEditorTab.TEXT
    private var grid: SubtitleEditorGrid? = null
    private val tabGrids = mutableMapOf<Pair<SubtitleEditorTab, Int>, CachedTabGrid>()
    private var lastState: State? = null
    private var columns = 3
    private var tabButtons: Map<SubtitleEditorTab, Button> = emptyMap()

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
        val firstBuild = !::binding.isInitialized
        if (firstBuild) {
            binding = DialogSubtitleStyleBinding.inflate(layoutInflater)
            bindControls()
        } else {
            binding.root.detachFromParent()
        }
        val state = stateProvider?.invoke()
        if (grid == null) {
            lastState = state
            select(selectedTab)
            state?.let(::render)
        } else {
            state?.let(::render)
            binding.styleScroll.scrollTo(0, 0)
        }
        return binding.root
    }

    private fun bindControls() {
        val b = binding
        tabButtons = mapOf(
            SubtitleEditorTab.TEXT to b.styleTextTab,
            SubtitleEditorTab.EDGES to b.styleEdgesTab,
            SubtitleEditorTab.LAYOUT to b.styleLayoutTab,
            SubtitleEditorTab.ADVANCED to b.styleAdvancedTab,
        )
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
        tabButtons.forEach { (tab, button) -> button.setOnClickListener { select(tab) } }
        b.styleContent.addOnLayoutChangeListener { view, _, _, _, _, _, _, _, _ ->
            val count = if (view.width / view.resources.displayMetrics.density < 680) 2 else 3
            if (count != columns) {
                columns = count
                view.post { select(selectedTab) }
            }
        }
    }

    private fun select(tab: SubtitleEditorTab) {
        selectedTab = tab
        tabButtons.forEach { (item, button) ->
            val selected = item == tab
            if (button.isSelected != selected) button.isSelected = selected
            if (button.isActivated != selected) button.isActivated = selected
        }
        val key = tab to columns
        val cached = tabGrids.getOrPut(key) { buildTabGrid(tab) }
        tabGrids.forEach { (entryKey, entry) ->
            val targetVis = if (entryKey == key) View.VISIBLE else View.GONE
            entry.container.setVisibilityIfChanged(targetVis)
        }
        grid = cached.grid
        lastState?.let(cached.grid::render)
        binding.styleScroll.scrollTo(0, 0)
    }

    private fun buildTabGrid(tab: SubtitleEditorTab): CachedTabGrid {
        val container = LinearLayout(binding.styleContent.context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            )
            orientation = LinearLayout.VERTICAL
        }
        binding.styleContent.addView(container)
        val editor = SubtitleEditorGrid(container, columns, ::adjust).apply {
            build(
                tab,
                listOf(
                    R.string.sub_style_add_font to { onAddFont?.invoke() },
                    R.string.sub_style_remove_font to { onRemoveFont?.invoke() },
                    R.string.sub_style_edit_preset to { onEditPreset?.invoke() },
                    R.string.sub_style_delete_preset to { onDeletePreset?.invoke() },
                ),
            )
        }
        return CachedTabGrid(container, editor)
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
        binding.styleTitle.setTextIfChanged(state.title)
        if (binding.styleMaster.isChecked != state.masterOn) binding.styleMaster.isChecked = state.masterOn
        binding.stylePreview.setSpec(state.preview)
        binding.stylePresetValue.setTextIfChanged(state.presetName)
        grid?.render(state)
    }
}
