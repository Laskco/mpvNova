@file:Suppress("TooManyFunctions")

package app.mpvnova.player

import android.content.SharedPreferences
import android.graphics.Typeface
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.content.res.AppCompatResources
import androidx.preference.PreferenceManager
import app.mpvnova.player.databinding.DialogPlayerTitleStyleControlBinding
import app.mpvnova.player.databinding.DialogPlayerUiControlRowBinding
import app.mpvnova.player.databinding.DialogPlayerUiCustomizationBinding
import app.mpvnova.player.preferences.SettingsChoiceItem

private enum class PlayerUiEditorTab { PRESETS, SURFACE, LAYOUT, CONTROLS }

internal fun MPVActivity.openPlayerUiCustomizationPanel() {
    val restorePlayback = keepPlaybackForDialog()
    val panel = DialogPlayerUiCustomizationBinding.inflate(layoutInflater)
    val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    lateinit var dialog: AlertDialog
    val controller = PlayerUiCustomizationPanelController(this, panel, preferences)

    dialog = AlertDialog.Builder(this).setView(panel.root).create()
    panel.playerUiDoneBtn.setOnClickListener { dialog.dismiss() }
    dialog.setOnDismissListener {
        restorePlayback()
        applyPlayerUiCustomization()
        refreshVisibleControlsTimeout()
        reopenDrawerIfPending()
    }
    controller.bind()
    UiFont.applyToViewTree(panel.root)
    showWidePlayerDialog(
        dialog,
        PlayerDialogLayout(
            widthFraction = 0.92f,
            maxWidthDp = 1400f,
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL,
            verticalOffsetDp = 10f,
            heightFraction = 0.72f,
            maxHeightDp = 650f,
        ),
        PlayerDialogChrome.CONTROLS_PREVIEW,
    )
    binding.controls.visibility = View.VISIBLE
    applyPlayerUiCustomization()
    panel.playerUiPresetTab.requestFocus()
}

@Suppress("TooManyFunctions", "LargeClass")
private class PlayerUiCustomizationPanelController(
    private val activity: MPVActivity,
    private val panel: DialogPlayerUiCustomizationBinding,
    private val preferences: SharedPreferences,
) {
    private var tab = PlayerUiEditorTab.PRESETS
    private val history = PlayerAppearanceEditHistory(activity.playerUiCustomization.normalized())
    private val editorControlViews = mutableListOf<EditorControlView>()
    private var customPresets = PlayerUiCustomPresetStore.read(preferences)
    private var activeCustomPresetName = customPresets
        .firstOrNull { it.style == activity.playerUiCustomization.normalized() }
        ?.name

    fun bind() {
        panel.playerUiMoreBtn.setOnClickListener { showEditorActions() }
        panel.playerUiPresetTab.setOnClickListener { select(PlayerUiEditorTab.PRESETS) }
        panel.playerUiSurfaceTab.setOnClickListener { select(PlayerUiEditorTab.SURFACE) }
        panel.playerUiLayoutTab.setOnClickListener { select(PlayerUiEditorTab.LAYOUT) }
        panel.playerUiControlsTab.setOnClickListener { select(PlayerUiEditorTab.CONTROLS) }
        panel.playerUiPresetMinusBtn.setOnClickListener { cyclePreset(-1) }
        panel.playerUiPresetPlusBtn.setOnClickListener { cyclePreset(1) }
        panel.playerUiPresetSaveBtn.setOnClickListener { savePreset() }
        panel.playerUiPresetDeleteBtn.setOnClickListener { deletePreset() }
        panel.playerUiResetBtn.setOnClickListener {
            activeCustomPresetName = null
            applyStyle(PlayerUiCustomization.DEFAULT)
        }
        select(PlayerUiEditorTab.PRESETS)
    }

    private fun showEditorActions() {
        activity.showAppearanceEditorActions(
            history = history,
            applyStyle = { applyStyle(it) },
            copyActions = listOf(
                SettingsChoiceItem(
                    activity.getString(R.string.appearance_copy_title_surface),
                    detail = activity.getString(R.string.appearance_copy_surface_detail),
                ) {
                    applyStyle(activity.playerUiCustomization.withSurfaceFrom(activity.playerTitleStyle.titlePanel))
                },
                SettingsChoiceItem(
                    activity.getString(R.string.appearance_copy_clock_surface),
                    detail = activity.getString(R.string.appearance_copy_surface_detail),
                ) {
                    applyStyle(activity.playerUiCustomization.withSurfaceFrom(activity.playerTitleStyle.clockPanel))
                },
            ),
            exportPreset = {
                activity.presetTransfer.exportPlayerBar(
                    activeCustomPresetName ?: activity.getString(R.string.player_ui_customization_title),
                    activity.playerUiCustomization,
                )
            },
            importPreset = {
                activity.presetTransfer.importPlayerBar { preset ->
                    customPresets = PlayerUiCustomPresetStore.read(preferences)
                    activeCustomPresetName = preset.name
                    applyStyle(preset.style)
                }
            },
        )
    }

    private fun select(selected: PlayerUiEditorTab) {
        tab = selected
        mapOf(
            PlayerUiEditorTab.PRESETS to panel.playerUiPresetTab,
            PlayerUiEditorTab.SURFACE to panel.playerUiSurfaceTab,
            PlayerUiEditorTab.LAYOUT to panel.playerUiLayoutTab,
            PlayerUiEditorTab.CONTROLS to panel.playerUiControlsTab,
        ).forEach { (candidate, button) ->
            button.isSelected = candidate == selected
            button.isActivated = candidate == selected
        }
        render()
        panel.playerUiScroll.scrollTo(0, 0)
    }

    private fun render(focusTag: String? = null) {
        editorControlViews.clear()
        panel.playerUiContent.removeAllViews()
        when (tab) {
            PlayerUiEditorTab.PRESETS -> renderPresets()
            PlayerUiEditorTab.SURFACE -> renderSurface()
            PlayerUiEditorTab.LAYOUT -> renderLayout()
            PlayerUiEditorTab.CONTROLS -> renderControls()
        }
        renderPresetFooter()
        UiFont.applyToViewTree(panel.playerUiContent)
        if (focusTag != null) {
            panel.playerUiContent.findViewWithTag<View>(focusTag)?.requestFocus()
        }
    }

    private fun renderPresets() {
        addSectionHeading(R.string.player_ui_section_built_in_presets, false)
        renderBuiltInPresets()
        if (customPresets.isNotEmpty()) {
            addSectionHeading(R.string.player_ui_section_saved_presets, true)
            renderCustomPresets()
        }
    }

    private fun renderBuiltInPresets() {
        val presets = listOf(
            PlayerUiPreset.DEFAULT,
            PlayerUiPreset.MINIMAL,
            PlayerUiPreset.CINEMA,
            PlayerUiPreset.COMPACT,
            PlayerUiPreset.FLOATING,
            PlayerUiPreset.EDGE_TO_EDGE,
        )
        presets.chunked(PRESETS_PER_ROW).forEach { presetRow ->
            val row = newRow()
            presetRow.forEach { preset ->
                val button = Button(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(0, PRESET_BUTTON_HEIGHT_DP.dp(), 1f).apply {
                        marginStart = 5.dp()
                        marginEnd = 5.dp()
                    }
                    background = AppCompatResources.getDrawable(activity, R.drawable.bg_player_title_style_tab)
                    isAllCaps = false
                    setTextColor(activity.getColor(R.color.tv_text))
                    text = activity.getString(preset.labelRes()) + "\n" +
                        activity.getString(preset.summaryRes())
                    textSize = 11f
                    gravity = Gravity.CENTER
                    isSelected = playerUiPresetFor(activity.playerUiCustomization) == preset
                    isActivated = isSelected
                    tag = "preset:${preset.name}"
                    setOnClickListener { view ->
                        activeCustomPresetName = null
                        applyStyle(playerUiPresetStyle(preset), view.tag as String)
                    }
                }
                row.addView(button)
            }
        }
    }

    private fun renderCustomPresets() {
        customPresets.chunked(PRESETS_PER_ROW).forEach { presetRow ->
            val row = newRow()
            presetRow.forEach { preset ->
                val button = Button(activity).apply {
                    layoutParams = LinearLayout.LayoutParams(0, PRESET_BUTTON_HEIGHT_DP.dp(), 1f).apply {
                        marginStart = 5.dp()
                        marginEnd = 5.dp()
                    }
                    background = AppCompatResources.getDrawable(activity, R.drawable.bg_player_title_style_tab)
                    isAllCaps = false
                    setTextColor(activity.getColor(R.color.tv_text))
                    text = preset.name + "\n" + activity.getString(R.string.custom_preset_saved_summary)
                    textSize = 11f
                    gravity = Gravity.CENTER
                    isSelected = activeCustomPresetName.equals(preset.name, ignoreCase = true)
                    isActivated = isSelected
                    tag = "custom-preset:${preset.name}"
                    setOnClickListener { view ->
                        activeCustomPresetName = preset.name
                        applyStyle(preset.style, view.tag as String)
                    }
                }
                row.addView(button)
            }
            repeat(PRESETS_PER_ROW - presetRow.size) {
                row.addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f))
            }
        }
    }

    private fun renderPresetFooter() {
        val saved = customPresets
            .firstOrNull { it.name.equals(activeCustomPresetName, ignoreCase = true) }
        val label = when {
            saved != null && saved.style == activity.playerUiCustomization -> saved.name
            saved != null -> activity.getString(R.string.custom_preset_modified, saved.name)
            else -> playerUiPresetFor(activity.playerUiCustomization).takeUnless {
                it == PlayerUiPreset.CUSTOM
            }?.let { activity.getString(it.labelRes()) }
                ?: activity.getString(R.string.custom_preset_unsaved)
        }
        panel.playerUiPresetValue.text = label
        panel.playerUiPresetDeleteBtn.isEnabled = saved != null
        panel.playerUiPresetDeleteBtn.alpha = if (saved != null) 1f else DISABLED_ACTION_ALPHA
    }

    private fun cyclePreset(delta: Int) {
        val builtIns = listOf(
            PlayerUiPreset.DEFAULT,
            PlayerUiPreset.MINIMAL,
            PlayerUiPreset.CINEMA,
            PlayerUiPreset.COMPACT,
            PlayerUiPreset.FLOATING,
            PlayerUiPreset.EDGE_TO_EDGE,
        ).map { PresetChoice(playerUiPresetStyle(it), null) }
        val custom = customPresets.map {
            PresetChoice(it.style, it.name)
        }
        val choices = builtIns + custom
        if (choices.isEmpty()) return
        val current = activeCustomPresetName?.let { name ->
            choices.indexOfFirst { it.customName.equals(name, ignoreCase = true) }
        }?.takeIf { it >= 0 } ?: choices.indexOfFirst {
            it.customName == null && it.style == activity.playerUiCustomization
        }
        val next = Math.floorMod((current.takeIf { it >= 0 } ?: if (delta > 0) -1 else 0) + delta, choices.size)
        val choice = choices[next]
        activeCustomPresetName = choice.customName
        applyStyle(choice.style)
    }

    private fun savePreset() {
        activity.showCustomPresetNameDialog(
            currentName = activeCustomPresetName,
            chrome = PlayerDialogChrome.CONTROLS_PREVIEW,
        ) { name ->
            customPresets = customPresets.filterNot { preset ->
                preset.name.equals(name, ignoreCase = true) ||
                    preset.name.equals(activeCustomPresetName, ignoreCase = true)
            } + PlayerUiCustomPreset(name, activity.playerUiCustomization)
            PlayerUiCustomPresetStore.write(preferences, customPresets)
            activeCustomPresetName = name
            activity.showToast(activity.getString(R.string.custom_preset_saved), name)
            render()
        }
    }

    private fun deletePreset() {
        val name = activeCustomPresetName ?: return
        customPresets = customPresets.filterNot { it.name.equals(name, ignoreCase = true) }
        PlayerUiCustomPresetStore.write(preferences, customPresets)
        activeCustomPresetName = null
        activity.showToast(activity.getString(R.string.custom_preset_deleted), name)
        render()
    }

    private fun renderSurface() = renderEditorSections(
        listOf(
            EditorSection(R.string.player_ui_section_background, surfaceColorControls()),
            EditorSection(R.string.player_ui_section_shape, surfaceShapeControls()),
        ),
    )

    private fun surfaceColorControls() = listOf(
            EditorControl(R.string.player_ui_surface_style,
                { activity.getString(activity.playerUiCustomization.surface.labelRes()) }) { delta ->
                activity.playerUiCustomization.copy(
                    surface = cycle(PlayerPanelSurface.entries, activity.playerUiCustomization.surface, delta),
                )
            },
            EditorControl(R.string.player_ui_panel_opacity,
                { percent(activity.playerUiCustomization.panelOpacityPercent) }) { delta ->
                activity.playerUiCustomization.copy(
                    panelOpacityPercent = stepPlayerUiValue(
                        activity.playerUiCustomization.panelOpacityPercent,
                        delta,
                        PERCENT_STEP,
                    ),
                )
            },
            EditorControl(R.string.player_ui_scrim_strength,
                { percent(activity.playerUiCustomization.scrimStrengthPercent) }) { delta ->
                activity.playerUiCustomization.copy(
                    scrimStrengthPercent = stepPlayerUiValue(
                        activity.playerUiCustomization.scrimStrengthPercent,
                        delta,
                        PERCENT_STEP,
                    ),
                )
            },
            EditorControl(R.string.player_ui_gradient,
                { toggleLabel(activity.playerUiCustomization.gradientEnabled) }) {
                activity.playerUiCustomization.copy(
                    gradientEnabled = !activity.playerUiCustomization.gradientEnabled,
                )
            },
    )

    private fun surfaceShapeControls() = listOf(
            EditorControl(R.string.player_ui_panel_outline,
                { toggleLabel(activity.playerUiCustomization.panelOutlineEnabled) }) {
                activity.playerUiCustomization.copy(
                    panelOutlineEnabled = !activity.playerUiCustomization.panelOutlineEnabled,
                )
            },
            EditorControl(R.string.player_ui_panel_outline_width,
                { dp(activity.playerUiCustomization.panelOutlineWidthDp) }) { delta ->
                activity.playerUiCustomization.copy(
                    panelOutlineWidthDp = activity.playerUiCustomization.panelOutlineWidthDp + delta,
                )
            },
            EditorControl(R.string.player_ui_corner_radius,
                { dp(activity.playerUiCustomization.cornerRadiusDp) }) { delta ->
                activity.playerUiCustomization.copy(
                    cornerRadiusDp = stepPlayerUiValue(
                        activity.playerUiCustomization.cornerRadiusDp,
                        delta,
                        RADIUS_STEP_DP,
                    ),
                )
            },
            EditorControl(R.string.player_ui_panel_elevation,
                { dp(activity.playerUiCustomization.panelElevationDp) }) { delta ->
                activity.playerUiCustomization.copy(
                    panelElevationDp = stepPlayerUiValue(
                        activity.playerUiCustomization.panelElevationDp,
                        delta,
                        ELEVATION_STEP_DP,
                    ),
                )
            },
            EditorControl(R.string.player_ui_button_treatment,
                { activity.getString(activity.playerUiCustomization.buttonTreatment.labelRes()) }) { delta ->
                activity.playerUiCustomization.copy(
                    buttonTreatment = cycle(
                        PlayerButtonTreatment.entries,
                        activity.playerUiCustomization.buttonTreatment,
                        delta,
                    ),
                )
            },
            EditorControl(R.string.player_ui_icon_text_outline,
                { toggleLabel(activity.playerUiCustomization.iconTextOutlineEnabled) }) {
                activity.playerUiCustomization.copy(
                    iconTextOutlineEnabled = !activity.playerUiCustomization.iconTextOutlineEnabled,
                )
            },
    )

    private fun renderLayout() = renderEditorSections(
        listOf(
            EditorSection(R.string.player_ui_section_panel_layout, layoutGeometryControls()),
            EditorSection(R.string.player_ui_section_panel_spacing, layoutSpacingControls()),
            EditorSection(R.string.player_ui_section_seekbar, seekbarControls()),
            EditorSection(R.string.player_bar_colors, barColorControls()),
            EditorSection(R.string.player_bar_chapters, chapterStyleControls()),
            EditorSection(R.string.player_ui_section_thumb, thumbControls()),
            EditorSection(R.string.player_ui_section_time, timeControls()),
            EditorSection(R.string.player_ui_section_button_row, controlRowControls()),
            EditorSection(R.string.player_bar_button_focus, buttonFocusControls()),
        ),
    )

    private fun layoutGeometryControls() = listOf(
            EditorControl(R.string.player_ui_panel_width,
                { percent(activity.playerUiCustomization.widthPercent) }) { delta ->
                activity.playerUiCustomization.copy(
                    widthPercent = stepPlayerUiValue(
                        activity.playerUiCustomization.widthPercent,
                        delta,
                        WIDTH_STEP_PERCENT,
                    ),
                )
            },
            EditorControl(R.string.player_ui_density,
                { activity.getString(activity.playerUiCustomization.density.labelRes()) }) { delta ->
                activity.playerUiCustomization.copy(
                    density = cycle(PlayerPanelDensity.entries, activity.playerUiCustomization.density, delta),
                )
            },
            EditorControl(R.string.player_ui_vertical_offset,
                { dp(activity.playerUiCustomization.verticalOffsetDp) }) { delta ->
                activity.playerUiCustomization.copy(
                    verticalOffsetDp = stepPlayerUiValue(
                        activity.playerUiCustomization.verticalOffsetDp,
                        delta,
                        OFFSET_STEP_DP,
                    ),
                )
            },
    )

    private fun layoutSpacingControls() = listOf(
            EditorControl(R.string.player_ui_horizontal_padding,
                { dp(activity.playerUiCustomization.horizontalPaddingDp) }) { delta ->
                activity.playerUiCustomization.copy(
                    horizontalPaddingDp = stepPlayerUiValue(
                        activity.playerUiCustomization.horizontalPaddingDp,
                        delta,
                        PADDING_STEP_DP,
                    ),
                )
            },
            EditorControl(R.string.player_ui_top_padding,
                { dp(activity.playerUiCustomization.topPaddingDp) }) { delta ->
                activity.playerUiCustomization.copy(
                    topPaddingDp = stepPlayerUiValue(
                        activity.playerUiCustomization.topPaddingDp,
                        delta,
                        PADDING_STEP_DP,
                    ),
                )
            },
            EditorControl(R.string.player_ui_bottom_padding,
                { dp(activity.playerUiCustomization.bottomPaddingDp) }) { delta ->
                activity.playerUiCustomization.copy(
                    bottomPaddingDp = stepPlayerUiValue(
                        activity.playerUiCustomization.bottomPaddingDp,
                        delta,
                        PADDING_STEP_DP,
                    ),
                )
            },
            EditorControl(R.string.player_ui_row_spacing,
                { dp(activity.playerUiCustomization.rowSpacingDp) }) { delta ->
                activity.playerUiCustomization.copy(
                    rowSpacingDp = stepPlayerUiValue(
                        activity.playerUiCustomization.rowSpacingDp,
                        delta,
                        ROW_SPACING_STEP_DP,
                    ),
                )
            },
    )

    private fun seekbarControls() = listOf(
        EditorControl(R.string.player_ui_seekbar_size,
            { activity.getString(activity.playerUiCustomization.seekbarSize.labelRes()) }) { delta ->
            activity.playerUiCustomization.copy(
                seekbarSize = cycle(PlayerSeekbarSize.entries, activity.playerUiCustomization.seekbarSize, delta),
            )
        },
        EditorControl(R.string.player_ui_seekbar_position,
            { activity.getString(activity.playerUiCustomization.seekbarPosition.labelRes()) }) { delta ->
            activity.playerUiCustomization.copy(
                seekbarPosition = cycle(
                    PlayerSeekbarPosition.entries,
                    activity.playerUiCustomization.seekbarPosition,
                    delta,
                ),
            )
        },
        EditorControl(R.string.player_ui_seekbar_inset,
            { dp(activity.playerUiCustomization.seekbarInsetDp) }) { delta ->
            activity.playerUiCustomization.copy(
                seekbarInsetDp = stepPlayerUiValue(
                    activity.playerUiCustomization.seekbarInsetDp,
                    delta,
                    PADDING_STEP_DP,
                ),
            )
        },
        EditorControl(R.string.player_ui_chapter_markers,
            { toggleLabel(activity.playerUiCustomization.chapterMarkersVisible) }) {
            activity.playerUiCustomization.copy(
                chapterMarkersVisible = !activity.playerUiCustomization.chapterMarkersVisible,
            )
        },
        EditorControl(R.string.player_ui_seekbar_visibility,
            { toggleLabel(activity.playerUiCustomization.seekbarVisible) }) {
            activity.playerUiCustomization.copy(
                seekbarVisible = !activity.playerUiCustomization.seekbarVisible,
            )
        },
    )

    private fun thumbControls() = listOf(
        EditorControl(R.string.player_ui_seekbar_thumb_size,
            { activity.getString(activity.playerUiCustomization.seekbarThumbSize.labelRes()) }) { delta ->
            activity.playerUiCustomization.copy(
                seekbarThumbSize = cycle(
                    PlayerSeekbarThumbSize.entries,
                    activity.playerUiCustomization.seekbarThumbSize,
                    delta,
                ),
            )
        },
        EditorControl(R.string.player_ui_seekbar_thumb_shape,
            { activity.getString(activity.playerUiCustomization.seekbarThumbShape.labelRes()) }) { delta ->
            activity.playerUiCustomization.copy(
                seekbarThumbShape = cycle(
                    PlayerSeekbarThumbShape.entries,
                    activity.playerUiCustomization.seekbarThumbShape,
                    delta,
                ),
            )
        },
        EditorControl(R.string.player_ui_seekbar_thumb_glow,
            { toggleLabel(activity.playerUiCustomization.seekbarThumbGlowEnabled) }) {
            activity.playerUiCustomization.copy(
                seekbarThumbGlowEnabled = !activity.playerUiCustomization.seekbarThumbGlowEnabled,
            )
        },
        EditorControl(R.string.player_ui_seekbar_thumb_color,
            { activity.playerSeekbarThumbColorLabel(activity.playerUiCustomization.seekbarThumbColor) }) { delta ->
            activity.playerUiCustomization.copy(
                seekbarThumbColor = cycle(
                    PlayerSeekbarThumbColor.entries,
                    activity.playerUiCustomization.seekbarThumbColor,
                    delta,
                ),
            )
        },
    )

    private fun timeControls() = listOf(
            EditorControl(R.string.player_bar_time_mode,
                { activity.getString(activity.playerUiCustomization.timeMode.labelRes()) }) { delta ->
                activity.playerUiCustomization.copy(
                    timeMode = cycle(PlayerTimeMode.entries, activity.playerUiCustomization.timeMode, delta),
                )
            },
            EditorControl(R.string.player_ui_time_visibility,
                { toggleLabel(activity.playerUiCustomization.timeVisible) }) {
                activity.playerUiCustomization.copy(
                    timeVisible = !activity.playerUiCustomization.timeVisible,
                )
            },
            EditorControl(R.string.player_ui_time_text_size,
                { sp(activity.playerUiCustomization.timeTextSizeSp) }) { delta ->
                activity.playerUiCustomization.copy(
                    timeTextSizeSp = activity.playerUiCustomization.timeTextSizeSp + delta,
                )
            },
            EditorControl(R.string.player_ui_time_position,
                { activity.getString(activity.playerUiCustomization.timePosition.labelRes()) }) { delta ->
                activity.playerUiCustomization.copy(
                    timePosition = cycle(
                        PlayerTimePosition.entries,
                        activity.playerUiCustomization.timePosition,
                        delta,
                    ),
                )
            },
            EditorControl(R.string.player_ui_time_presentation,
                { activity.getString(activity.playerUiCustomization.timePresentation.labelRes()) }) { delta ->
                activity.playerUiCustomization.copy(
                    timePresentation = cycle(
                        PlayerTimePresentation.entries,
                        activity.playerUiCustomization.timePresentation,
                        delta,
                    ),
                )
            },
            EditorControl(R.string.player_ui_time_control_gap,
                { dp(activity.playerUiCustomization.timeControlGapDp) }) { delta ->
                activity.playerUiCustomization.copy(
                    timeControlGapDp = stepPlayerUiValue(
                        activity.playerUiCustomization.timeControlGapDp,
                        delta,
                        PADDING_STEP_DP,
                    ),
                )
            },
    )

    private fun controlRowControls() = listOf(
            EditorControl(R.string.player_bar_play_icon_size,
                { scalePercent(activity.playerUiCustomization.primaryPlayIconSizePercent) }) { delta ->
                activity.playerUiCustomization.copy(
                    primaryPlayIconSizePercent = stepPlayerUiValue(
                        activity.playerUiCustomization.primaryPlayIconSizePercent, delta, PERCENT_STEP,
                    ),
                )
            },
            EditorControl(R.string.player_bar_other_icon_size,
                { scalePercent(activity.playerUiCustomization.otherIconSizePercent) }) { delta ->
                activity.playerUiCustomization.copy(
                    otherIconSizePercent = stepPlayerUiValue(
                        activity.playerUiCustomization.otherIconSizePercent, delta, PERCENT_STEP,
                    ),
                )
            },
            EditorControl(R.string.player_ui_control_alignment,
                { activity.getString(activity.playerUiCustomization.controlAlignment.labelRes()) }) { delta ->
                activity.playerUiCustomization.copy(
                    controlAlignment = cycle(
                        PlayerControlAlignment.entries,
                        activity.playerUiCustomization.controlAlignment,
                        delta,
                    ),
                )
            },
            EditorControl(R.string.player_ui_control_size,
                { activity.getString(activity.playerUiCustomization.controlSize.labelRes()) }) { delta ->
                activity.playerUiCustomization.copy(
                    controlSize = cycle(
                        PlayerControlSize.entries,
                        activity.playerUiCustomization.controlSize,
                        delta,
                    ),
                )
            },
            EditorControl(R.string.player_ui_control_spacing,
                { dp(activity.playerUiCustomization.controlSpacingDp) }) { delta ->
                activity.playerUiCustomization.copy(
                    controlSpacingDp = activity.playerUiCustomization.controlSpacingDp + delta,
                )
            },
    )

    private fun barColorControls() = listOf(
        barColorControl(R.string.player_bar_played_color,
            { it.seekbarPlayedColor }, { style, color -> style.copy(seekbarPlayedColor = color) }),
        barColorControl(R.string.player_bar_buffered_color,
            { it.seekbarBufferedColor }, { style, color -> style.copy(seekbarBufferedColor = color) }),
        barColorControl(R.string.player_bar_unplayed_color,
            { it.seekbarUnplayedColor }, { style, color -> style.copy(seekbarUnplayedColor = color) }),
        barColorControl(R.string.player_bar_marker_color,
            { it.chapterMarkerColor }, { style, color -> style.copy(chapterMarkerColor = color) }),
    )

    private fun barColorControl(
        @StringRes label: Int,
        color: (PlayerUiCustomization) -> PlayerSeekbarThumbColor?,
        update: (PlayerUiCustomization, PlayerSeekbarThumbColor?) -> PlayerUiCustomization,
    ) = EditorControl(label, {
        color(activity.playerUiCustomization)?.let(activity::playerSeekbarThumbColorLabel)
            ?: activity.getString(R.string.player_bar_theme_default)
    }) { delta ->
        val style = activity.playerUiCustomization
        update(style, cycle(listOf(null) + PlayerSeekbarThumbColor.entries, color(style), delta))
    }

    private fun chapterStyleControls() = listOf(
        EditorControl(R.string.player_bar_marker_shape,
            { activity.getString(activity.playerUiCustomization.chapterMarkerShape.labelRes()) }) { delta ->
            activity.playerUiCustomization.copy(
                chapterMarkerShape = cycle(
                    PlayerChapterMarkerShape.entries, activity.playerUiCustomization.chapterMarkerShape, delta,
                ),
            )
        },
        EditorControl(R.string.player_bar_marker_size,
            { scalePercent(activity.playerUiCustomization.chapterMarkerSizePercent) }) { delta ->
            activity.playerUiCustomization.copy(
                chapterMarkerSizePercent = stepPlayerUiValue(
                    activity.playerUiCustomization.chapterMarkerSizePercent, delta, PERCENT_STEP,
                ),
            )
        },
        EditorControl(R.string.player_bar_current_chapter,
            { toggleLabel(activity.playerUiCustomization.currentChapterEmphasis) }) {
            activity.playerUiCustomization.copy(
                currentChapterEmphasis = !activity.playerUiCustomization.currentChapterEmphasis,
            )
        },
    )

    private fun buttonFocusControls() = listOf(
        EditorControl(R.string.player_bar_focus_outline,
            { dp(activity.playerUiCustomization.buttonFocusOutlineWidthDp) }) { delta ->
            activity.playerUiCustomization.copy(
                buttonFocusOutlineWidthDp = activity.playerUiCustomization.buttonFocusOutlineWidthDp + delta,
            )
        },
        EditorControl(R.string.player_bar_focus_opacity,
            { percent(activity.playerUiCustomization.buttonFocusHighlightOpacityPercent) }) { delta ->
            activity.playerUiCustomization.copy(
                buttonFocusHighlightOpacityPercent = stepPlayerUiValue(
                    activity.playerUiCustomization.buttonFocusHighlightOpacityPercent, delta, PERCENT_STEP,
                ),
            )
        },
        EditorControl(R.string.player_bar_focus_enlargement,
            { scalePercent(activity.playerUiCustomization.buttonFocusEnlargementPercent) }) { delta ->
            activity.playerUiCustomization.copy(
                buttonFocusEnlargementPercent = stepPlayerUiValue(
                    activity.playerUiCustomization.buttonFocusEnlargementPercent, delta, PERCENT_STEP,
                ),
            )
        },
    )

    private fun scalePercent(value: Int) = activity.getString(R.string.player_ui_value_percent, value)

    private fun renderEditorSections(sections: List<EditorSection>) {
        sections.forEachIndexed { index, section ->
            addSectionHeading(section.labelRes, index > 0)
            renderEditorControls(section.controls)
        }
    }

    private fun renderEditorControls(controls: List<EditorControl>) {
        controls.chunked(CONTROLS_PER_ROW).forEach { chunk ->
            val row = newRow()
            chunk.forEach { control ->
                val binding = DialogPlayerTitleStyleControlBinding.inflate(
                    LayoutInflater.from(activity), row, false,
                )
                binding.titleStyleControlLabel.setText(control.labelRes)
                binding.titleStyleControlValue.text = control.value()
                binding.titleStyleControlPrevious.tag = "editor:${control.labelRes}:previous"
                binding.titleStyleControlNext.tag = "editor:${control.labelRes}:next"
                binding.titleStyleControlPrevious.setOnClickListener { view ->
                    applyStyle(control.adjust(-1), view.tag as String)
                }
                binding.titleStyleControlNext.setOnClickListener { view ->
                    applyStyle(control.adjust(1), view.tag as String)
                }
                row.addView(binding.root)
                editorControlViews += EditorControlView(control, binding)
            }
            repeat(CONTROLS_PER_ROW - chunk.size) {
                row.addView(View(activity), LinearLayout.LayoutParams(0, 1, 1f))
            }
        }
    }

    private fun renderControls() {
        addSectionHeading(R.string.player_ui_section_button_order, false)
        activity.playerUiCustomization.controlOrder.forEach { control ->
            val row = DialogPlayerUiControlRowBinding.inflate(
                LayoutInflater.from(activity), panel.playerUiContent, false,
            )
            row.playerUiControlLabel.setText(control.labelRes)
            row.playerUiControlVisibility.text = activity.getString(
                when {
                    !control.canHide -> R.string.player_ui_control_protected
                    activity.playerUiCustomization.isControlVisible(control) -> R.string.player_ui_control_shown
                    else -> R.string.player_ui_control_hidden
                },
            )
            row.playerUiControlVisibility.isEnabled = true
            row.playerUiControlVisibility.isClickable = control.canHide
            row.playerUiControlVisibility.isFocusable = true
            row.playerUiControlVisibility.isChecked =
                activity.playerUiCustomization.isControlVisible(control)
            row.playerUiControlVisibility.alpha = if (control.canHide) {
                1f
            } else {
                PROTECTED_CONTROL_ALPHA
            }
            row.playerUiControlVisibility.tag = "control:${control.prefValue}:visibility"
            row.playerUiControlLeft.tag = "control:${control.prefValue}:up"
            row.playerUiControlRight.tag = "control:${control.prefValue}:down"
            row.playerUiControlVisibility.setOnClickListener(
                if (control.canHide) {
                    View.OnClickListener { view ->
                        val hidden = activity.playerUiCustomization.hiddenControls.toMutableSet()
                        if (!hidden.add(control)) hidden.remove(control)
                        applyStyle(
                            activity.playerUiCustomization.copy(hiddenControls = hidden),
                            view.tag as String,
                        )
                    }
                } else {
                    null
                },
            )
            val position = activity.playerUiCustomization.controlOrder.indexOf(control)
            row.playerUiControlLeft.isEnabled = position > 0
            row.playerUiControlRight.isEnabled = position < activity.playerUiCustomization.controlOrder.lastIndex
            row.playerUiControlLeft.setOnClickListener { view ->
                moveControl(control, -1, view.tag as String)
            }
            row.playerUiControlRight.setOnClickListener { view ->
                moveControl(control, 1, view.tag as String)
            }
            panel.playerUiContent.addView(row.root)
        }
    }

    private fun moveControl(control: PlayerBarControl, delta: Int, focusTag: String) {
        val order = activity.playerUiCustomization.controlOrder.toMutableList()
        val from = order.indexOf(control)
        val to = (from + delta).coerceIn(0, order.lastIndex)
        if (from == to) return
        order.removeAt(from)
        order.add(to, control)
        applyStyle(activity.playerUiCustomization.copy(controlOrder = order), focusTag)
    }

    private fun applyStyle(style: PlayerUiCustomization, focusTag: String? = null) {
        activity.playerUiCustomization = style.normalized()
        history.record(activity.playerUiCustomization)
        PlayerUiCustomizationStore.write(preferences, activity.playerUiCustomization)
        activity.applyPlayerUiCustomization()
        if (focusTag?.startsWith(EDITOR_FOCUS_TAG_PREFIX) == true) {
            editorControlViews.forEach { controlView ->
                controlView.binding.titleStyleControlValue.text = controlView.control.value()
            }
            renderPresetFooter()
        } else {
            render(focusTag)
        }
    }

    private fun newRow() = LinearLayout(activity).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
        )
        orientation = LinearLayout.HORIZONTAL
        setPadding(0, ROW_PADDING_DP.dp(), 0, ROW_PADDING_DP.dp())
        panel.playerUiContent.addView(this)
    }

    private fun addSectionHeading(@StringRes labelRes: Int, hasTopGap: Boolean) {
        panel.playerUiContent.addView(TextView(activity).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT,
            ).apply {
                topMargin = if (hasTopGap) SECTION_TOP_GAP_DP.dp() else 0
                bottomMargin = SECTION_BOTTOM_GAP_DP.dp()
            }
            setText(labelRes)
            setTextColor(activity.themedColor(R.attr.mpvAccentHot, R.color.tv_purple_hot))
            setTypeface(typeface, Typeface.BOLD)
            textSize = SECTION_TEXT_SIZE_SP
        })
    }

    private fun percent(value: Int) = activity.getString(
        R.string.player_ui_value_percent,
        value.coerceIn(MIN_PERCENT, MAX_PERCENT),
    )
    private fun dp(value: Int) = activity.getString(R.string.player_ui_value_dp, value.coerceAtLeast(0))
    private fun sp(value: Int) = activity.getString(R.string.player_ui_value_sp, value.coerceAtLeast(0))
    private fun toggleLabel(value: Boolean) = activity.getString(
        if (value) R.string.player_ui_value_on else R.string.player_ui_value_off,
    )
    private fun Int.dp() = (this * activity.resources.displayMetrics.density).toInt()
}

private data class EditorControl(
    val labelRes: Int,
    val value: () -> String,
    val adjust: (Int) -> PlayerUiCustomization,
)

private data class EditorSection(
    @StringRes val labelRes: Int,
    val controls: List<EditorControl>,
)

private data class EditorControlView(
    val control: EditorControl,
    val binding: DialogPlayerTitleStyleControlBinding,
)

private data class PresetChoice(
    val style: PlayerUiCustomization,
    val customName: String?,
)

private fun PlayerUiPreset.labelRes() = when (this) {
    PlayerUiPreset.DEFAULT -> R.string.player_ui_preset_default
    PlayerUiPreset.MINIMAL -> R.string.player_ui_preset_minimal
    PlayerUiPreset.CINEMA -> R.string.player_ui_preset_cinema
    PlayerUiPreset.COMPACT -> R.string.player_ui_preset_compact
    PlayerUiPreset.FLOATING -> R.string.player_ui_preset_floating
    PlayerUiPreset.EDGE_TO_EDGE -> R.string.player_ui_preset_edge_to_edge
    PlayerUiPreset.CUSTOM -> R.string.player_ui_preset_custom
}

private fun PlayerUiPreset.summaryRes() = when (this) {
    PlayerUiPreset.DEFAULT -> R.string.player_ui_preset_default_summary
    PlayerUiPreset.MINIMAL -> R.string.player_ui_preset_minimal_summary
    PlayerUiPreset.CINEMA -> R.string.player_ui_preset_cinema_summary
    PlayerUiPreset.COMPACT -> R.string.player_ui_preset_compact_summary
    PlayerUiPreset.FLOATING -> R.string.player_ui_preset_floating_summary
    PlayerUiPreset.EDGE_TO_EDGE -> R.string.player_ui_preset_edge_to_edge_summary
    PlayerUiPreset.CUSTOM -> R.string.player_ui_preset_default_summary
}

private fun PlayerPanelSurface.labelRes() = when (this) {
    PlayerPanelSurface.GLASS -> R.string.player_ui_surface_glass
    PlayerPanelSurface.FLAT -> R.string.player_ui_surface_flat
    PlayerPanelSurface.TRANSPARENT -> R.string.player_ui_surface_transparent
}

private fun PlayerPanelDensity.labelRes() = when (this) {
    PlayerPanelDensity.COMPACT -> R.string.player_ui_density_compact
    PlayerPanelDensity.STANDARD -> R.string.player_ui_density_standard
    PlayerPanelDensity.COMFORTABLE -> R.string.player_ui_density_comfortable
}

private fun PlayerSeekbarSize.labelRes() = when (this) {
    PlayerSeekbarSize.THIN -> R.string.player_ui_seekbar_thin
    PlayerSeekbarSize.STANDARD -> R.string.player_ui_seekbar_standard
    PlayerSeekbarSize.THICK -> R.string.player_ui_seekbar_thick
}

private fun PlayerSeekbarPosition.labelRes() = when (this) {
    PlayerSeekbarPosition.ABOVE -> R.string.player_ui_seekbar_position_above
    PlayerSeekbarPosition.BELOW -> R.string.player_ui_seekbar_position_below
}

private fun PlayerSeekbarThumbSize.labelRes() = when (this) {
    PlayerSeekbarThumbSize.SMALL -> R.string.player_ui_thumb_small
    PlayerSeekbarThumbSize.STANDARD -> R.string.player_ui_thumb_standard
    PlayerSeekbarThumbSize.LARGE -> R.string.player_ui_thumb_large
}

private fun PlayerSeekbarThumbShape.labelRes() = when (this) {
    PlayerSeekbarThumbShape.RING -> R.string.player_ui_thumb_ring
    PlayerSeekbarThumbShape.SOLID -> R.string.player_ui_thumb_solid
    PlayerSeekbarThumbShape.DIAMOND -> R.string.player_ui_thumb_diamond
    PlayerSeekbarThumbShape.PILL -> R.string.player_ui_thumb_pill
}

private fun MPVActivity.playerSeekbarThumbColorLabel(color: PlayerSeekbarThumbColor): String = when (color) {
    PlayerSeekbarThumbColor.APP_COLOR -> getString(R.string.player_title_style_value_app_color)
    PlayerSeekbarThumbColor.BLACK -> getString(R.string.player_title_style_value_black)
    else -> appearanceColorChoices.firstOrNull { it.value == color.appearanceValue }
        ?.let { choice -> getString(choice.labelRes) }
        ?: getString(R.string.appearance_theme_white)
}

private fun PlayerTimePosition.labelRes() = when (this) {
    PlayerTimePosition.START -> R.string.player_ui_time_position_start
    PlayerTimePosition.END -> R.string.player_ui_time_position_end
}

private fun PlayerChapterMarkerShape.labelRes() = when (this) {
    PlayerChapterMarkerShape.TICKS -> R.string.player_bar_marker_ticks
    PlayerChapterMarkerShape.DOTS -> R.string.player_bar_marker_dots
}

private fun PlayerTimeMode.labelRes() = when (this) {
    PlayerTimeMode.PLAYER_DEFAULT -> R.string.player_bar_player_default
    PlayerTimeMode.ELAPSED_TOTAL -> R.string.player_bar_elapsed_total
    PlayerTimeMode.ELAPSED_REMAINING -> R.string.player_bar_elapsed_remaining
    PlayerTimeMode.REMAINING_ONLY -> R.string.player_bar_remaining_only
}

private fun PlayerTimePresentation.labelRes() = when (this) {
    PlayerTimePresentation.PILL -> R.string.player_ui_time_presentation_pill
    PlayerTimePresentation.OUTLINE -> R.string.player_ui_time_presentation_outline
    PlayerTimePresentation.PLAIN -> R.string.player_ui_time_presentation_plain
}

private fun PlayerButtonTreatment.labelRes() = when (this) {
    PlayerButtonTreatment.MINIMAL -> R.string.player_ui_buttons_minimal
    PlayerButtonTreatment.SOFT -> R.string.player_ui_buttons_soft
    PlayerButtonTreatment.BLOCK -> R.string.player_ui_buttons_block
}

private fun PlayerControlAlignment.labelRes() = when (this) {
    PlayerControlAlignment.START -> R.string.player_ui_alignment_start
    PlayerControlAlignment.CENTER -> R.string.player_ui_alignment_center
    PlayerControlAlignment.END -> R.string.player_ui_alignment_end
}

private fun PlayerControlSize.labelRes() = when (this) {
    PlayerControlSize.COMPACT -> R.string.player_ui_control_size_compact
    PlayerControlSize.STANDARD -> R.string.player_ui_control_size_standard
    PlayerControlSize.LARGE -> R.string.player_ui_control_size_large
}

private fun <T> cycle(values: List<T>, current: T, delta: Int): T {
    val index = values.indexOf(current).coerceAtLeast(0)
    return values[(index + delta).floorMod(values.size)]
}

private fun Int.floorMod(divisor: Int): Int = ((this % divisor) + divisor) % divisor

private const val PRESET_BUTTON_HEIGHT_DP = 70
private const val PRESETS_PER_ROW = 3
private const val PERCENT_STEP = 5
private const val RADIUS_STEP_DP = 2
private const val WIDTH_STEP_PERCENT = 2
private const val OFFSET_STEP_DP = 4
private const val PADDING_STEP_DP = 2
private const val ROW_SPACING_STEP_DP = 2
private const val ELEVATION_STEP_DP = 2
private const val CONTROLS_PER_ROW = 4
private const val ROW_PADDING_DP = 3
private const val EDITOR_FOCUS_TAG_PREFIX = "editor:"
private const val PROTECTED_CONTROL_ALPHA = 0.68f
private const val DISABLED_ACTION_ALPHA = 0.45f
private const val SECTION_TOP_GAP_DP = 12
private const val SECTION_BOTTOM_GAP_DP = 6
private const val SECTION_TEXT_SIZE_SP = 11f
