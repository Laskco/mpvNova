package app.mpvnova.player

import android.content.SharedPreferences

internal enum class PlayerPanelSurface(val prefValue: String) {
    GLASS("glass"),
    FLAT("flat"),
    TRANSPARENT("transparent");

    companion object {
        fun fromPref(value: String?): PlayerPanelSurface = entries.firstOrNull {
            it.prefValue == value
        } ?: GLASS
    }
}

internal enum class PlayerPanelDensity(val prefValue: String) {
    COMPACT("compact"),
    STANDARD("standard"),
    COMFORTABLE("comfortable");

    companion object {
        fun fromPref(value: String?): PlayerPanelDensity = entries.firstOrNull {
            it.prefValue == value
        } ?: STANDARD
    }
}

internal enum class PlayerSeekbarSize(val prefValue: String) {
    THIN("thin"),
    STANDARD("standard"),
    THICK("thick");

    companion object {
        fun fromPref(value: String?): PlayerSeekbarSize = entries.firstOrNull {
            it.prefValue == value
        } ?: STANDARD
    }
}

internal enum class PlayerSeekbarPosition(val prefValue: String) {
    ABOVE("above"),
    BELOW("below");

    companion object {
        fun fromPref(value: String?): PlayerSeekbarPosition =
            entries.firstOrNull { it.prefValue == value } ?: ABOVE
    }
}

internal enum class PlayerSeekbarThumbSize(val prefValue: String) {
    SMALL("small"),
    STANDARD("standard"),
    LARGE("large");

    companion object {
        fun fromPref(value: String?): PlayerSeekbarThumbSize = entries.firstOrNull {
            it.prefValue == value
        } ?: STANDARD
    }
}

internal enum class PlayerSeekbarThumbShape(val prefValue: String) {
    RING("ring"),
    SOLID("solid"),
    DIAMOND("diamond"),
    PILL("pill");

    companion object {
        fun fromPref(value: String?): PlayerSeekbarThumbShape = entries.firstOrNull {
            it.prefValue == value
        } ?: RING
    }
}

internal enum class PlayerSeekbarThumbColor(val appearanceValue: String?) {
    APP_COLOR(null),
    WHITE("nova"),
    BLACK("black"),
    CRIMSON("crimson"),
    OCEAN("ocean"),
    CYAN("cyan"),
    VIOLET("violet"),
    EMERALD("emerald"),
    LIME("lime"),
    AMBER("amber"),
    GOLD("gold"),
    COPPER("copper"),
    INDIGO("indigo"),
    ROSE("rose"),
    SLATE("slate"),
    CHROME("chrome"),
    OYSTER("oyster"),
    IVORY("ivory"),
}

internal enum class PlayerTimePosition(val prefValue: String) {
    START("start"),
    END("end");

    companion object {
        fun fromPref(value: String?): PlayerTimePosition = entries.firstOrNull {
            it.prefValue == value
        } ?: END
    }
}

internal enum class PlayerTimePresentation(val prefValue: String) {
    PILL("pill"),
    OUTLINE("outline"),
    PLAIN("plain");

    companion object {
        fun fromPref(value: String?): PlayerTimePresentation = entries.firstOrNull {
            it.prefValue == value
        } ?: PILL
    }
}

internal enum class PlayerButtonTreatment(val prefValue: String) {
    MINIMAL("minimal"),
    SOFT("soft"),
    BLOCK("block");

    companion object {
        fun fromPref(value: String?): PlayerButtonTreatment = entries.firstOrNull {
            it.prefValue == value
        } ?: MINIMAL
    }
}

internal enum class PlayerControlAlignment(val prefValue: String) {
    START("start"),
    CENTER("center"),
    END("end");

    companion object {
        fun fromPref(value: String?): PlayerControlAlignment = entries.firstOrNull {
            it.prefValue == value
        } ?: START
    }
}

internal enum class PlayerControlSize(val prefValue: String) {
    COMPACT("compact"),
    STANDARD("standard"),
    LARGE("large");

    companion object {
        fun fromPref(value: String?): PlayerControlSize = entries.firstOrNull {
            it.prefValue == value
        } ?: STANDARD
    }
}

internal enum class PlayerBarControl(
    val prefValue: String,
    val viewId: Int,
    val labelRes: Int,
    val canHide: Boolean,
) {
    PLAY("play", R.id.playBtn, R.string.btn_play, false),
    CHAPTERS("chapters", R.id.nextChapterBtn, R.string.chapter_button, false),
    SUBTITLES("subtitles", R.id.cycleSubsBtn, R.string.track_subs, false),
    AUDIO("audio", R.id.cycleAudioBtn, R.string.track_audio, false),
    SETTINGS("settings", R.id.topMenuBtn, R.string.action_settings, false),
    PREVIOUS("previous", R.id.prevBtn, R.string.dialog_prev, true),
    NEXT("next", R.id.nextBtn, R.string.dialog_next, true),
    SPEED("speed", R.id.cycleSpeedBtn, R.string.btn_play_speed, true),
    DECODER("decoder", R.id.cycleDecoderBtn, R.string.btn_decoder, true),
    FILTERS("filters", R.id.videoFiltersBtn, R.string.video_filter_presets_title, true),
    STATS("stats", R.id.statsToggleBtn, R.string.toggle_stats, true),
    VOICE_BOOST("voice_boost", R.id.voiceBoostBtn, R.string.btn_voice_boost, true),
    VOLUME_BOOST("volume_boost", R.id.volumeBoostBtn, R.string.btn_volume_boost, true),
    NIGHT_MODE("night_mode", R.id.nightModeBtn, R.string.btn_night_mode, true),
    AUDIO_NORMALIZATION("audio_norm", R.id.audioNormBtn, R.string.btn_audio_norm, true),
    PICTURE_IN_PICTURE("pip", R.id.topPiPBtn, R.string.action_pip, true);

    companion object {
        fun fromPref(value: String): PlayerBarControl? = entries.firstOrNull { it.prefValue == value }
    }
}

private val DEFAULT_HIDDEN_PLAYER_CONTROLS = setOf(
    PlayerBarControl.FILTERS,
    PlayerBarControl.PICTURE_IN_PICTURE,
)

internal data class PlayerUiCustomization(
    val surface: PlayerPanelSurface = PlayerPanelSurface.GLASS,
    val panelOpacityPercent: Int = 80,
    val scrimStrengthPercent: Int = 0,
    val gradientEnabled: Boolean = true,
    val panelOutlineEnabled: Boolean = true,
    val panelOutlineWidthDp: Int = 1,
    val cornerRadiusDp: Int = 28,
    val widthPercent: Int = 100,
    val density: PlayerPanelDensity = PlayerPanelDensity.STANDARD,
    val verticalOffsetDp: Int = 0,
    val horizontalPaddingDp: Int = 18,
    val topPaddingDp: Int = 10,
    val bottomPaddingDp: Int = 8,
    val rowSpacingDp: Int = 4,
    val panelElevationDp: Int = 0,
    val seekbarSize: PlayerSeekbarSize = PlayerSeekbarSize.STANDARD,
    val seekbarPosition: PlayerSeekbarPosition = PlayerSeekbarPosition.ABOVE,
    val seekbarInsetDp: Int = 2,
    val seekbarThumbSize: PlayerSeekbarThumbSize = PlayerSeekbarThumbSize.STANDARD,
    val seekbarThumbShape: PlayerSeekbarThumbShape = PlayerSeekbarThumbShape.RING,
    val seekbarThumbGlowEnabled: Boolean = true,
    val seekbarThumbColor: PlayerSeekbarThumbColor = PlayerSeekbarThumbColor.WHITE,
    val seekbarVisible: Boolean = true,
    val chapterMarkersVisible: Boolean = true,
    val timeVisible: Boolean = true,
    val timeTextSizeSp: Int = 13,
    val timePosition: PlayerTimePosition = PlayerTimePosition.END,
    val timePresentation: PlayerTimePresentation = PlayerTimePresentation.PILL,
    val timeControlGapDp: Int = 12,
    val controlAlignment: PlayerControlAlignment = PlayerControlAlignment.START,
    val controlSize: PlayerControlSize = PlayerControlSize.STANDARD,
    val controlSpacingDp: Int = 2,
    val buttonTreatment: PlayerButtonTreatment = PlayerButtonTreatment.MINIMAL,
    val iconTextOutlineEnabled: Boolean = true,
    val hiddenControls: Set<PlayerBarControl> = DEFAULT_HIDDEN_PLAYER_CONTROLS,
    val controlOrder: List<PlayerBarControl> = PlayerBarControl.entries,
) {
    fun normalized(): PlayerUiCustomization = copy(
        panelOpacityPercent = panelOpacityPercent.coerceIn(MIN_PERCENT, MAX_PERCENT),
        scrimStrengthPercent = scrimStrengthPercent.coerceIn(MIN_PERCENT, MAX_PERCENT),
        panelOutlineWidthDp = panelOutlineWidthDp.coerceIn(MIN_OUTLINE_WIDTH_DP, MAX_OUTLINE_WIDTH_DP),
        cornerRadiusDp = cornerRadiusDp.coerceIn(MIN_CORNER_RADIUS_DP, MAX_CORNER_RADIUS_DP),
        widthPercent = widthPercent.coerceIn(MIN_WIDTH_PERCENT, MAX_WIDTH_PERCENT),
        verticalOffsetDp = verticalOffsetDp.coerceIn(MIN_VERTICAL_OFFSET_DP, MAX_VERTICAL_OFFSET_DP),
        horizontalPaddingDp = horizontalPaddingDp.coerceIn(MIN_PANEL_PADDING_DP, MAX_PANEL_PADDING_DP),
        topPaddingDp = topPaddingDp.coerceIn(MIN_PANEL_PADDING_DP, MAX_PANEL_PADDING_DP),
        bottomPaddingDp = bottomPaddingDp.coerceIn(MIN_PANEL_PADDING_DP, MAX_PANEL_PADDING_DP),
        rowSpacingDp = rowSpacingDp.coerceIn(MIN_ROW_SPACING_DP, MAX_ROW_SPACING_DP),
        panelElevationDp = panelElevationDp.coerceIn(MIN_ELEVATION_DP, MAX_ELEVATION_DP),
        seekbarInsetDp = seekbarInsetDp.coerceIn(MIN_SEEKBAR_INSET_DP, MAX_SEEKBAR_INSET_DP),
        timeTextSizeSp = timeTextSizeSp.coerceIn(MIN_TIME_TEXT_SIZE_SP, MAX_TIME_TEXT_SIZE_SP),
        timeControlGapDp = timeControlGapDp.coerceIn(MIN_TIME_CONTROL_GAP_DP, MAX_TIME_CONTROL_GAP_DP),
        controlSpacingDp = controlSpacingDp.coerceIn(MIN_CONTROL_SPACING_DP, MAX_CONTROL_SPACING_DP),
        hiddenControls = hiddenControls.filterTo(mutableSetOf()) { it.canHide },
        controlOrder = normalizeControlOrder(controlOrder),
    )

    fun isControlVisible(control: PlayerBarControl): Boolean = !control.canHide || control !in hiddenControls

    companion object {
        val DEFAULT = PlayerUiCustomization()
    }
}

internal enum class PlayerUiPreset {
    DEFAULT,
    MINIMAL,
    CINEMA,
    COMPACT,
    FLOATING,
    EDGE_TO_EDGE,
    CUSTOM,
}

private val MINIMAL_PLAYER_UI_PRESET = PlayerUiCustomization(
    surface = PlayerPanelSurface.FLAT,
    panelOpacityPercent = 72,
    gradientEnabled = false,
    panelOutlineEnabled = false,
    cornerRadiusDp = 18,
    widthPercent = 76,
    density = PlayerPanelDensity.COMPACT,
    horizontalPaddingDp = 10,
    topPaddingDp = 4,
    bottomPaddingDp = 2,
    rowSpacingDp = 0,
    seekbarSize = PlayerSeekbarSize.THIN,
    seekbarInsetDp = 16,
    seekbarThumbSize = PlayerSeekbarThumbSize.SMALL,
    seekbarThumbShape = PlayerSeekbarThumbShape.SOLID,
    seekbarThumbGlowEnabled = false,
    chapterMarkersVisible = false,
    timeVisible = false,
    controlAlignment = PlayerControlAlignment.CENTER,
    controlSize = PlayerControlSize.COMPACT,
    controlSpacingDp = 0,
    iconTextOutlineEnabled = false,
    hiddenControls = PlayerBarControl.entries.filterTo(mutableSetOf()) { it.canHide },
)

private val CINEMA_PLAYER_UI_PRESET = PlayerUiCustomization(
    surface = PlayerPanelSurface.GLASS,
    panelOpacityPercent = 58,
    scrimStrengthPercent = 35,
    panelOutlineEnabled = false,
    cornerRadiusDp = 22,
    widthPercent = 92,
    density = PlayerPanelDensity.COMPACT,
    verticalOffsetDp = 10,
    horizontalPaddingDp = 24,
    topPaddingDp = 8,
    bottomPaddingDp = 6,
    rowSpacingDp = 2,
    seekbarSize = PlayerSeekbarSize.THIN,
    seekbarInsetDp = 28,
    seekbarThumbSize = PlayerSeekbarThumbSize.SMALL,
    seekbarThumbShape = PlayerSeekbarThumbShape.PILL,
    seekbarThumbGlowEnabled = false,
    timePresentation = PlayerTimePresentation.PLAIN,
    timeControlGapDp = 20,
    controlAlignment = PlayerControlAlignment.CENTER,
    controlSpacingDp = 4,
    hiddenControls = setOf(
        PlayerBarControl.PREVIOUS,
        PlayerBarControl.NEXT,
        PlayerBarControl.FILTERS,
        PlayerBarControl.STATS,
        PlayerBarControl.VOICE_BOOST,
        PlayerBarControl.VOLUME_BOOST,
        PlayerBarControl.NIGHT_MODE,
        PlayerBarControl.AUDIO_NORMALIZATION,
        PlayerBarControl.PICTURE_IN_PICTURE,
    ),
)

private val COMPACT_PLAYER_UI_PRESET = PlayerUiCustomization(
    surface = PlayerPanelSurface.FLAT,
    panelOpacityPercent = 88,
    gradientEnabled = false,
    cornerRadiusDp = 14,
    widthPercent = 70,
    density = PlayerPanelDensity.COMPACT,
    horizontalPaddingDp = 8,
    topPaddingDp = 4,
    bottomPaddingDp = 2,
    rowSpacingDp = 0,
    seekbarSize = PlayerSeekbarSize.THIN,
    seekbarPosition = PlayerSeekbarPosition.BELOW,
    seekbarInsetDp = 10,
    seekbarThumbSize = PlayerSeekbarThumbSize.SMALL,
    seekbarThumbShape = PlayerSeekbarThumbShape.DIAMOND,
    seekbarThumbGlowEnabled = false,
    chapterMarkersVisible = false,
    timeVisible = false,
    controlAlignment = PlayerControlAlignment.CENTER,
    controlSize = PlayerControlSize.COMPACT,
    controlSpacingDp = 0,
    hiddenControls = PlayerBarControl.entries.filterTo(mutableSetOf()) { it.canHide },
)

private val DOCK_PLAYER_UI_PRESET = PlayerUiCustomization(
    surface = PlayerPanelSurface.GLASS,
    panelOpacityPercent = 82,
    scrimStrengthPercent = 0,
    gradientEnabled = true,
    panelOutlineEnabled = true,
    panelOutlineWidthDp = 1,
    cornerRadiusDp = 28,
    widthPercent = 48,
    density = PlayerPanelDensity.COMPACT,
    verticalOffsetDp = 20,
    horizontalPaddingDp = 16,
    topPaddingDp = 8,
    bottomPaddingDp = 8,
    rowSpacingDp = 0,
    panelElevationDp = 8,
    seekbarVisible = false,
    chapterMarkersVisible = false,
    timeTextSizeSp = 13,
    timePosition = PlayerTimePosition.END,
    timePresentation = PlayerTimePresentation.PLAIN,
    timeControlGapDp = 6,
    controlAlignment = PlayerControlAlignment.CENTER,
    controlSize = PlayerControlSize.STANDARD,
    controlSpacingDp = 3,
    buttonTreatment = PlayerButtonTreatment.MINIMAL,
    iconTextOutlineEnabled = true,
    hiddenControls = PlayerBarControl.entries.filterTo(mutableSetOf()) {
        it.canHide && it != PlayerBarControl.DECODER
    },
)

private val EDGE_TO_EDGE_PLAYER_UI_PRESET = PlayerUiCustomization(
    surface = PlayerPanelSurface.FLAT,
    panelOpacityPercent = 92,
    gradientEnabled = false,
    panelOutlineEnabled = false,
    cornerRadiusDp = 0,
    widthPercent = 100,
    density = PlayerPanelDensity.COMPACT,
    horizontalPaddingDp = 24,
    topPaddingDp = 8,
    bottomPaddingDp = 6,
    seekbarSize = PlayerSeekbarSize.THICK,
    seekbarPosition = PlayerSeekbarPosition.BELOW,
    seekbarInsetDp = 0,
    seekbarThumbSize = PlayerSeekbarThumbSize.LARGE,
    seekbarThumbShape = PlayerSeekbarThumbShape.DIAMOND,
    seekbarThumbGlowEnabled = false,
    timeTextSizeSp = 14,
    timePresentation = PlayerTimePresentation.PLAIN,
    timeControlGapDp = 18,
    controlAlignment = PlayerControlAlignment.START,
    controlSize = PlayerControlSize.STANDARD,
    controlSpacingDp = 3,
    buttonTreatment = PlayerButtonTreatment.BLOCK,
)

internal fun playerUiPresetStyle(preset: PlayerUiPreset): PlayerUiCustomization = when (preset) {
    PlayerUiPreset.DEFAULT -> PlayerUiCustomization.DEFAULT
    PlayerUiPreset.MINIMAL -> MINIMAL_PLAYER_UI_PRESET
    PlayerUiPreset.CINEMA -> CINEMA_PLAYER_UI_PRESET
    PlayerUiPreset.COMPACT -> COMPACT_PLAYER_UI_PRESET
    PlayerUiPreset.FLOATING -> DOCK_PLAYER_UI_PRESET
    PlayerUiPreset.EDGE_TO_EDGE -> EDGE_TO_EDGE_PLAYER_UI_PRESET
    PlayerUiPreset.CUSTOM -> PlayerUiCustomization.DEFAULT
}

internal fun playerUiPresetFor(style: PlayerUiCustomization): PlayerUiPreset =
    PlayerUiPreset.entries.firstOrNull { preset ->
        preset != PlayerUiPreset.CUSTOM && playerUiPresetStyle(preset) == style.normalized()
    } ?: PlayerUiPreset.CUSTOM

internal object PlayerUiCustomizationStore {
    private const val PREFIX = "player_ui_custom"
    private const val SURFACE = "${PREFIX}_surface"
    private const val PANEL_OPACITY = "${PREFIX}_panel_opacity"
    private const val SCRIM_STRENGTH = "${PREFIX}_scrim_strength"
    private const val GRADIENT = "${PREFIX}_gradient"
    private const val PANEL_OUTLINE = "${PREFIX}_panel_outline"
    private const val PANEL_OUTLINE_WIDTH = "${PREFIX}_panel_outline_width"
    private const val CORNER_RADIUS = "${PREFIX}_corner_radius"
    private const val WIDTH = "${PREFIX}_width"
    private const val DENSITY = "${PREFIX}_density"
    private const val VERTICAL_OFFSET = "${PREFIX}_vertical_offset"
    private const val HORIZONTAL_PADDING = "${PREFIX}_horizontal_padding"
    private const val TOP_PADDING = "${PREFIX}_top_padding"
    private const val BOTTOM_PADDING = "${PREFIX}_bottom_padding"
    private const val ROW_SPACING = "${PREFIX}_row_spacing"
    private const val PANEL_ELEVATION = "${PREFIX}_panel_elevation"
    private const val SEEKBAR_SIZE = "${PREFIX}_seekbar_size"
    private const val SEEKBAR_POSITION = "${PREFIX}_seekbar_position"
    private const val SEEKBAR_INSET = "${PREFIX}_seekbar_inset"
    private const val SEEKBAR_THUMB_SIZE = "${PREFIX}_seekbar_thumb_size"
    private const val SEEKBAR_THUMB_SHAPE = "${PREFIX}_seekbar_thumb_shape"
    private const val SEEKBAR_THUMB_GLOW = "${PREFIX}_seekbar_thumb_glow"
    private const val SEEKBAR_THUMB_COLOR = "${PREFIX}_seekbar_thumb_color"
    private const val SEEKBAR_VISIBLE = "${PREFIX}_seekbar_visible"
    private const val CHAPTER_MARKERS_VISIBLE = "${PREFIX}_chapter_markers_visible"
    private const val TIME_VISIBLE = "${PREFIX}_time_visible"
    private const val TIME_TEXT_SIZE = "${PREFIX}_time_text_size"
    private const val TIME_POSITION = "${PREFIX}_time_position"
    private const val TIME_PRESENTATION = "${PREFIX}_time_presentation"
    private const val TIME_CONTROL_GAP = "${PREFIX}_time_control_gap"
    private const val CONTROL_ALIGNMENT = "${PREFIX}_control_alignment"
    private const val CONTROL_SIZE = "${PREFIX}_control_size"
    private const val CONTROL_SPACING = "${PREFIX}_control_spacing"
    private const val BUTTON_TREATMENT = "${PREFIX}_button_treatment"
    private const val ICON_TEXT_OUTLINE = "${PREFIX}_icon_text_outline"
    private const val HIDDEN_CONTROLS = "${PREFIX}_hidden_controls"
    private const val CONTROL_ORDER = "${PREFIX}_control_order"

    @Suppress("LongMethod")
    fun read(prefs: SharedPreferences): PlayerUiCustomization = PlayerUiCustomization(
        surface = PlayerPanelSurface.fromPref(prefs.getString(SURFACE, null)),
        panelOpacityPercent = prefs.getInt(PANEL_OPACITY, PlayerUiCustomization.DEFAULT.panelOpacityPercent),
        scrimStrengthPercent = prefs.getInt(SCRIM_STRENGTH, PlayerUiCustomization.DEFAULT.scrimStrengthPercent),
        gradientEnabled = prefs.getBoolean(GRADIENT, PlayerUiCustomization.DEFAULT.gradientEnabled),
        panelOutlineEnabled = prefs.getBoolean(
            PANEL_OUTLINE,
            PlayerUiCustomization.DEFAULT.panelOutlineEnabled,
        ),
        panelOutlineWidthDp = prefs.getInt(
            PANEL_OUTLINE_WIDTH,
            PlayerUiCustomization.DEFAULT.panelOutlineWidthDp,
        ),
        cornerRadiusDp = prefs.getInt(CORNER_RADIUS, PlayerUiCustomization.DEFAULT.cornerRadiusDp),
        widthPercent = prefs.getInt(WIDTH, PlayerUiCustomization.DEFAULT.widthPercent),
        density = PlayerPanelDensity.fromPref(prefs.getString(DENSITY, null)),
        verticalOffsetDp = prefs.getInt(
            VERTICAL_OFFSET,
            PlayerUiCustomization.DEFAULT.verticalOffsetDp,
        ),
        horizontalPaddingDp = prefs.getInt(
            HORIZONTAL_PADDING,
            PlayerUiCustomization.DEFAULT.horizontalPaddingDp,
        ),
        topPaddingDp = prefs.getInt(TOP_PADDING, PlayerUiCustomization.DEFAULT.topPaddingDp),
        bottomPaddingDp = prefs.getInt(BOTTOM_PADDING, PlayerUiCustomization.DEFAULT.bottomPaddingDp),
        rowSpacingDp = prefs.getInt(ROW_SPACING, PlayerUiCustomization.DEFAULT.rowSpacingDp),
        panelElevationDp = prefs.getInt(PANEL_ELEVATION, PlayerUiCustomization.DEFAULT.panelElevationDp),
        seekbarSize = PlayerSeekbarSize.fromPref(prefs.getString(SEEKBAR_SIZE, null)),
        seekbarPosition = PlayerSeekbarPosition.fromPref(prefs.getString(SEEKBAR_POSITION, null)),
        seekbarInsetDp = prefs.getInt(SEEKBAR_INSET, PlayerUiCustomization.DEFAULT.seekbarInsetDp),
        seekbarThumbSize = PlayerSeekbarThumbSize.fromPref(prefs.getString(SEEKBAR_THUMB_SIZE, null)),
        seekbarThumbShape = PlayerSeekbarThumbShape.fromPref(prefs.getString(SEEKBAR_THUMB_SHAPE, null)),
        seekbarThumbGlowEnabled = prefs.getBoolean(
            SEEKBAR_THUMB_GLOW,
            PlayerUiCustomization.DEFAULT.seekbarThumbGlowEnabled,
        ),
        seekbarThumbColor = readThumbColor(prefs),
        seekbarVisible = prefs.getBoolean(SEEKBAR_VISIBLE, PlayerUiCustomization.DEFAULT.seekbarVisible),
        chapterMarkersVisible = prefs.getBoolean(
            CHAPTER_MARKERS_VISIBLE,
            PlayerUiCustomization.DEFAULT.chapterMarkersVisible,
        ),
        timeVisible = prefs.getBoolean(TIME_VISIBLE, PlayerUiCustomization.DEFAULT.timeVisible),
        timeTextSizeSp = prefs.getInt(TIME_TEXT_SIZE, PlayerUiCustomization.DEFAULT.timeTextSizeSp),
        timePosition = PlayerTimePosition.fromPref(prefs.getString(TIME_POSITION, null)),
        timePresentation = PlayerTimePresentation.fromPref(prefs.getString(TIME_PRESENTATION, null)),
        timeControlGapDp = prefs.getInt(
            TIME_CONTROL_GAP,
            PlayerUiCustomization.DEFAULT.timeControlGapDp,
        ),
        controlAlignment = PlayerControlAlignment.fromPref(prefs.getString(CONTROL_ALIGNMENT, null)),
        controlSize = PlayerControlSize.fromPref(prefs.getString(CONTROL_SIZE, null)),
        controlSpacingDp = prefs.getInt(CONTROL_SPACING, PlayerUiCustomization.DEFAULT.controlSpacingDp),
        buttonTreatment = PlayerButtonTreatment.fromPref(prefs.getString(BUTTON_TREATMENT, null)),
        iconTextOutlineEnabled = prefs.getBoolean(
            ICON_TEXT_OUTLINE,
            PlayerUiCustomization.DEFAULT.iconTextOutlineEnabled,
        ),
        hiddenControls = readHiddenControls(prefs),
        controlOrder = readControlOrder(prefs),
    ).normalized()

    fun write(prefs: SharedPreferences, style: PlayerUiCustomization) {
        val normalized = style.normalized()
        prefs.edit()
            .putString(SURFACE, normalized.surface.prefValue)
            .putInt(PANEL_OPACITY, normalized.panelOpacityPercent)
            .putInt(SCRIM_STRENGTH, normalized.scrimStrengthPercent)
            .putBoolean(GRADIENT, normalized.gradientEnabled)
            .putBoolean(PANEL_OUTLINE, normalized.panelOutlineEnabled)
            .putInt(PANEL_OUTLINE_WIDTH, normalized.panelOutlineWidthDp)
            .putInt(CORNER_RADIUS, normalized.cornerRadiusDp)
            .putInt(WIDTH, normalized.widthPercent)
            .putString(DENSITY, normalized.density.prefValue)
            .putInt(VERTICAL_OFFSET, normalized.verticalOffsetDp)
            .putInt(HORIZONTAL_PADDING, normalized.horizontalPaddingDp)
            .putInt(TOP_PADDING, normalized.topPaddingDp)
            .putInt(BOTTOM_PADDING, normalized.bottomPaddingDp)
            .putInt(ROW_SPACING, normalized.rowSpacingDp)
            .putInt(PANEL_ELEVATION, normalized.panelElevationDp)
            .putString(SEEKBAR_SIZE, normalized.seekbarSize.prefValue)
            .putString(SEEKBAR_POSITION, normalized.seekbarPosition.prefValue)
            .putInt(SEEKBAR_INSET, normalized.seekbarInsetDp)
            .putString(SEEKBAR_THUMB_SIZE, normalized.seekbarThumbSize.prefValue)
            .putString(SEEKBAR_THUMB_SHAPE, normalized.seekbarThumbShape.prefValue)
            .putBoolean(SEEKBAR_THUMB_GLOW, normalized.seekbarThumbGlowEnabled)
            .putString(SEEKBAR_THUMB_COLOR, normalized.seekbarThumbColor.name)
            .putBoolean(SEEKBAR_VISIBLE, normalized.seekbarVisible)
            .putBoolean(CHAPTER_MARKERS_VISIBLE, normalized.chapterMarkersVisible)
            .putBoolean(TIME_VISIBLE, normalized.timeVisible)
            .putInt(TIME_TEXT_SIZE, normalized.timeTextSizeSp)
            .putString(TIME_POSITION, normalized.timePosition.prefValue)
            .putString(TIME_PRESENTATION, normalized.timePresentation.prefValue)
            .putInt(TIME_CONTROL_GAP, normalized.timeControlGapDp)
            .putString(CONTROL_ALIGNMENT, normalized.controlAlignment.prefValue)
            .putString(CONTROL_SIZE, normalized.controlSize.prefValue)
            .putInt(CONTROL_SPACING, normalized.controlSpacingDp)
            .putString(BUTTON_TREATMENT, normalized.buttonTreatment.prefValue)
            .putBoolean(ICON_TEXT_OUTLINE, normalized.iconTextOutlineEnabled)
            .putStringSet(HIDDEN_CONTROLS, normalized.hiddenControls.mapTo(mutableSetOf()) { it.prefValue })
            .putString(CONTROL_ORDER, normalized.controlOrder.joinToString(",") { it.prefValue })
            .apply()
    }

    fun reset(prefs: SharedPreferences) = write(prefs, PlayerUiCustomization.DEFAULT)

    private fun readThumbColor(prefs: SharedPreferences): PlayerSeekbarThumbColor =
        prefs.getString(SEEKBAR_THUMB_COLOR, null)
            ?.let { raw -> PlayerSeekbarThumbColor.entries.firstOrNull { it.name == raw } }
            ?: PlayerUiCustomization.DEFAULT.seekbarThumbColor

    private fun readHiddenControls(prefs: SharedPreferences): Set<PlayerBarControl> {
        val controls = prefs.getStringSet(
            HIDDEN_CONTROLS,
            DEFAULT_HIDDEN_PLAYER_CONTROLS.mapTo(mutableSetOf()) { it.prefValue },
        ).orEmpty().mapNotNullTo(mutableSetOf()) { PlayerBarControl.fromPref(it) }
        val savedOrder = prefs.getString(CONTROL_ORDER, null)

        // New optional controls stay hidden for users with a layout saved by an older version.
        if (savedOrder != null && PlayerBarControl.FILTERS.prefValue !in savedOrder.split(',')) {
            controls += PlayerBarControl.FILTERS
        }
        if (savedOrder != null && PlayerBarControl.PICTURE_IN_PICTURE.prefValue !in savedOrder.split(',')) {
            controls += PlayerBarControl.PICTURE_IN_PICTURE
        }
        return controls
    }

    private fun readControlOrder(prefs: SharedPreferences): List<PlayerBarControl> {
        val saved = prefs.getString(CONTROL_ORDER, null)
            ?.split(',')
            ?.mapNotNull { PlayerBarControl.fromPref(it) }
            ?: return PlayerBarControl.entries
        val migrated = saved.toMutableList()
        if (PlayerBarControl.SETTINGS !in migrated) {
            val audioIndex = migrated.indexOf(PlayerBarControl.AUDIO)
            migrated.add((audioIndex + 1).coerceAtLeast(0), PlayerBarControl.SETTINGS)
        }
        if (PlayerBarControl.PICTURE_IN_PICTURE !in migrated) {
            migrated += PlayerBarControl.PICTURE_IN_PICTURE
        }
        return migrated
    }
}

private fun normalizeControlOrder(order: List<PlayerBarControl>): List<PlayerBarControl> {
    val normalized = LinkedHashSet(order).toMutableList()
    PlayerBarControl.entries.forEach { control ->
        if (
            control !in normalized &&
            control != PlayerBarControl.SETTINGS &&
            control != PlayerBarControl.PICTURE_IN_PICTURE
        ) {
            normalized += control
        }
    }
    if (PlayerBarControl.SETTINGS !in normalized) {
        normalized.add(normalized.indexOf(PlayerBarControl.AUDIO) + 1, PlayerBarControl.SETTINGS)
    }
    if (PlayerBarControl.PICTURE_IN_PICTURE !in normalized) {
        normalized += PlayerBarControl.PICTURE_IN_PICTURE
    }
    return normalized
}

internal fun stepPlayerUiValue(value: Int, delta: Int, step: Int): Int {
    if (delta == 0 || step <= 0) return value
    val remainder = Math.floorMod(value, step)
    return if (delta > 0) {
        value + if (remainder == 0) step else step - remainder
    } else {
        value - if (remainder == 0) step else remainder
    }
}

internal const val MIN_PERCENT = 0
internal const val MAX_PERCENT = 100
internal const val MIN_OUTLINE_WIDTH_DP = 1
internal const val MAX_OUTLINE_WIDTH_DP = 4
internal const val MIN_CORNER_RADIUS_DP = 0
internal const val MAX_CORNER_RADIUS_DP = 36
internal const val MIN_WIDTH_PERCENT = 40
internal const val MAX_WIDTH_PERCENT = 100
internal const val MIN_VERTICAL_OFFSET_DP = 0
internal const val MAX_VERTICAL_OFFSET_DP = 80
internal const val MIN_PANEL_PADDING_DP = 0
internal const val MAX_PANEL_PADDING_DP = 32
internal const val MIN_ROW_SPACING_DP = 0
internal const val MAX_ROW_SPACING_DP = 16
internal const val MIN_ELEVATION_DP = 0
internal const val MAX_ELEVATION_DP = 24
internal const val MIN_TIME_TEXT_SIZE_SP = 10
internal const val MAX_TIME_TEXT_SIZE_SP = 20
internal const val MIN_CONTROL_SPACING_DP = 0
internal const val MAX_CONTROL_SPACING_DP = 12
internal const val MIN_SEEKBAR_INSET_DP = 0
internal const val MAX_SEEKBAR_INSET_DP = 48
internal const val MIN_TIME_CONTROL_GAP_DP = 0
internal const val MAX_TIME_CONTROL_GAP_DP = 32
