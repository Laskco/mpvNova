@file:Suppress("TooManyFunctions")

package app.mpvnova.player

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.updateLayoutParams
import androidx.preference.PreferenceManager

internal fun MPVActivity.applyPlayerUiCustomization() {
    val style = playerUiCustomization.normalized()
    playerUiCustomization = style
    enforceMinimalSeekOverlayForHiddenSeekbar(style)
    applyPlayerPanelSurface(style)
    applyPlayerPanelLayout(style)
    applyPlayerControlOrderAndVisibility(style)
    applyPlayerControlLayout(style)
    applyPlayerTimeLayout(style)
    applyPlayerControlDecoration(style.iconTextOutlineEnabled)
}

internal fun MPVActivity.refreshPlayerUiTheme() {
    binding.playbackSeekbar.refreshTheme(this)
    binding.seekOverlayBar.refreshTheme(this)
    binding.seekOverlayTime.background = themedPlayerDrawable(R.drawable.bg_player_toast)
    applyPlayerUiCustomization()
}

private fun MPVActivity.enforceMinimalSeekOverlayForHiddenSeekbar(style: PlayerUiCustomization) {
    if (style.seekbarVisible || (minimalSeekbarWhileSeeking && !hideControlsWhileSeeking)) return
    minimalSeekbarWhileSeeking = true
    hideControlsWhileSeeking = false
    PreferenceManager.getDefaultSharedPreferences(applicationContext)
        .edit()
        .putBoolean(PREF_MINIMAL_SEEKBAR_WHILE_SEEKING, true)
        .putBoolean(PREF_HIDE_CONTROLS_WHILE_SEEKING, false)
        .apply()
}

internal fun MPVActivity.applyPlayerPanelLayout(style: PlayerUiCustomization = playerUiCustomization) {
    val density = resources.displayMetrics.density
    val metrics = style.density.layoutMetrics()
    val thumb = style.seekbarThumbSize.thumbStyle()
    applySeekbarPosition(style.seekbarPosition)
    binding.controls.setPadding(
        (style.horizontalPaddingDp * density).toInt(),
        (style.topPaddingDp * density).toInt(),
        (style.horizontalPaddingDp * density).toInt(),
        (style.bottomPaddingDp * density).toInt(),
    )
    binding.controls.elevation = style.panelElevationDp * density
    applySeekbarGeometry(style, density, metrics, thumb)
    binding.playbackTimeGroup.setVisibilityIfChanged(
        if (style.timeVisible) View.VISIBLE else View.GONE,
    )
    binding.playbackTimeGroup.applyTextSizeRecursively(style.timeTextSizeSp.toFloat())
    updatePlaybackTimeReservedWidths()

    binding.controls.updateLayoutParams<android.widget.RelativeLayout.LayoutParams> {
        val screenWidth = resources.displayMetrics.widthPixels
        width = if (style.widthPercent >= MAX_WIDTH_PERCENT) {
            ViewGroup.LayoutParams.MATCH_PARENT
        } else {
            (screenWidth * style.widthPercent / MAX_PERCENT.toFloat()).toInt()
        }
        marginStart = 0
        marginEnd = 0
        val floatingBase = if (controlsAtBottom) 0f else FLOATING_CONTROLS_BOTTOM_MARGIN_DP
        bottomMargin = ((floatingBase + style.verticalOffsetDp) * density).toInt()
        addRule(android.widget.RelativeLayout.CENTER_HORIZONTAL, android.widget.RelativeLayout.TRUE)
    }
    updateSkipButtonPlacement()
    scheduleSubtitleControlsPositionUpdate()
}

private fun MPVActivity.applySeekbarGeometry(
    style: PlayerUiCustomization,
    density: Float,
    metrics: PlayerPanelLayoutMetrics,
    thumb: PlayerSeekbarThumbStyle,
) {
    binding.controlsContentRow.updateLayoutParams<LinearLayout.LayoutParams> {
        topMargin = if (style.seekbarPosition == PlayerSeekbarPosition.ABOVE) {
            (style.rowSpacingDp * density).toInt()
        } else {
            0
        }
    }
    binding.playbackSeekbar.updateLayoutParams<LinearLayout.LayoutParams> {
        height = (maxOf(metrics.seekbarHeightDp, thumb.viewHeightDp) * density).toInt()
        marginStart = (style.seekbarInsetDp * density).toInt()
        marginEnd = (style.seekbarInsetDp * density).toInt()
        topMargin = if (style.seekbarPosition == PlayerSeekbarPosition.BELOW) {
            (style.rowSpacingDp * density).toInt()
        } else {
            0
        }
    }
    binding.playbackSeekbar.scaleY = 1f
    val track = style.seekbarSize.trackStyle()
    binding.playbackSeekbar.setTrackStyle(track.heightDp, track.drawableRes, this)
    binding.playbackSeekbar.setThumbStyle(
        this,
        style.seekbarThumbShape,
        thumb.sizeDp,
        thumb.offsetDp,
        style.seekbarThumbGlowEnabled,
        style.seekbarThumbColor,
    )
    binding.playbackSeekbar.setChapterMarkersVisible(style.chapterMarkersVisible)
    binding.playbackSeekbar.setVisibilityIfChanged(
        if (style.seekbarVisible) View.VISIBLE else View.GONE,
    )
}

private fun MPVActivity.applySeekbarPosition(position: PlayerSeekbarPosition) {
    val parent = binding.controls
    val seekbar = binding.playbackSeekbar
    val content = binding.controlsContentRow
    val currentAbove = parent.indexOfChild(seekbar) < parent.indexOfChild(content)
    if (currentAbove == (position == PlayerSeekbarPosition.ABOVE)) return
    parent.removeView(seekbar)
    val contentIndex = parent.indexOfChild(content)
    val targetIndex = if (position == PlayerSeekbarPosition.ABOVE) contentIndex else contentIndex + 1
    parent.addView(seekbar, targetIndex.coerceIn(0, parent.childCount))
}

private fun MPVActivity.applyPlayerControlLayout(style: PlayerUiCustomization) {
    binding.controlsButtonGroup.gravity = Gravity.CENTER_VERTICAL or when (style.controlAlignment) {
        PlayerControlAlignment.START -> Gravity.START
        PlayerControlAlignment.CENTER -> Gravity.CENTER_HORIZONTAL
        PlayerControlAlignment.END -> Gravity.END
    }
    val metrics = style.controlSize.controlMetrics()
    val density = resources.displayMetrics.density
    val spacing = (style.controlSpacingDp * density).toInt()
    for (index in 0 until binding.controlsButtonGroup.childCount) {
        val child = binding.controlsButtonGroup.getChildAt(index)
        val isIcon = child is ShadowImageButton
        child.minimumWidth = ((if (isIcon) metrics.iconWidthDp else metrics.textWidthDp) * density).toInt()
        child.minimumHeight = (metrics.heightDp * density).toInt()
        val padding = (metrics.paddingDp * density).toInt()
        child.setPadding(padding, padding, padding, padding)
        child.background = themedPlayerDrawable(style.buttonTreatment.backgroundRes(isIcon))
        (child.layoutParams as? ViewGroup.MarginLayoutParams)?.let { params ->
            params.marginStart = spacing
            params.marginEnd = spacing
            child.layoutParams = params
        }
    }
}

private fun MPVActivity.applyPlayerTimeLayout(style: PlayerUiCustomization) {
    val row = binding.controlsContentRow
    val controls = binding.controlsButtonGroup
    val time = binding.playbackTimeGroup
    val expected = if (style.timePosition == PlayerTimePosition.START) {
        listOf(time, controls)
    } else {
        listOf(controls, time)
    }
    val current = (0 until row.childCount).map(row::getChildAt)
    if (current != expected) {
        row.removeAllViews()
        expected.forEach(row::addView)
    }

    val gap = (style.timeControlGapDp * resources.displayMetrics.density).toInt()
    controls.updateLayoutParams<LinearLayout.LayoutParams> {
        marginStart = if (style.timePosition == PlayerTimePosition.START) gap else 0
        marginEnd = if (style.timePosition == PlayerTimePosition.END) gap else 0
    }
    applyTimePresentation(style.timePresentation)
}

private fun MPVActivity.applyTimePresentation(presentation: PlayerTimePresentation) {
    val horizontalPadding: Int
    val verticalPadding: Int
    when (presentation) {
        PlayerTimePresentation.PILL -> {
            binding.playbackTimeGroup.background = themedPlayerDrawable(R.drawable.bg_pill_purple)
            horizontalPadding = TIME_PILL_HORIZONTAL_PADDING_DP
            verticalPadding = TIME_PILL_VERTICAL_PADDING_DP
        }
        PlayerTimePresentation.OUTLINE -> {
            binding.playbackTimeGroup.background = GradientDrawable().apply {
                setColor(Color.TRANSPARENT)
                cornerRadius = resources.displayMetrics.density * TIME_OUTLINE_RADIUS_DP
                setStroke(
                    resources.displayMetrics.density.toInt().coerceAtLeast(1),
                    themedColor(R.attr.mpvStrokeStrong, R.color.tv_stroke_strong),
                )
            }
            horizontalPadding = TIME_OUTLINE_HORIZONTAL_PADDING_DP
            verticalPadding = TIME_OUTLINE_VERTICAL_PADDING_DP
        }
        PlayerTimePresentation.PLAIN -> {
            binding.playbackTimeGroup.background = null
            horizontalPadding = TIME_PLAIN_HORIZONTAL_PADDING_DP
            verticalPadding = TIME_PLAIN_VERTICAL_PADDING_DP
        }
    }
    val density = resources.displayMetrics.density
    binding.playbackTimeGroup.setPadding(
        (horizontalPadding * density).toInt(),
        (verticalPadding * density).toInt(),
        (horizontalPadding * density).toInt(),
        (verticalPadding * density).toInt(),
    )
}

private fun View.applyTextSizeRecursively(sizeSp: Float) {
    when (this) {
        is TextView -> setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
        is ViewGroup -> for (index in 0 until childCount) {
            getChildAt(index).applyTextSizeRecursively(sizeSp)
        }
    }
}

internal fun MPVActivity.applyPlayerControlOrderAndVisibility(
    style: PlayerUiCustomization = playerUiCustomization,
) {
    updateTopActionPlacement()
    val group = binding.controlsButtonGroup
    val currentChildren = (0 until group.childCount).map(group::getChildAt)
    val childrenById = currentChildren.associateBy(View::getId)
    val orderedChildren = buildList {
        style.controlOrder.forEach { control -> childrenById[control.viewId]?.let(::add) }
        currentChildren.filterTo(this) { child -> child !in this }
    }
    if (currentChildren != orderedChildren) {
        group.removeAllViews()
        orderedChildren.forEach(group::addView)
    }

    PlayerBarControl.entries.forEach { control ->
        val view = binding.root.findViewById<View>(control.viewId) ?: return@forEach
        val baseVisible = isControlAvailableForCurrentMedia(control)
        val obeysPlayerbarVisibility = topActionsInPlayerBar ||
            (control != PlayerBarControl.SETTINGS && control != PlayerBarControl.PICTURE_IN_PICTURE)
        view.setVisibilityIfChanged(
            if (baseVisible && (!obeysPlayerbarVisibility || style.isControlVisible(control))) {
                View.VISIBLE
            } else {
                View.GONE
            },
        )
    }
    val controls = dpadButtons()
    if (btnSelected >= controls.size) {
        btnSelected = controls.lastIndex
        updateSelectedDpadButton()
    }
}

private fun MPVActivity.updateTopActionPlacement() {
    val target = if (topActionsInPlayerBar) binding.controlsButtonGroup else binding.topControls
    val actions = if (topActionsInPlayerBar) {
        listOf(binding.topMenuBtn, binding.topPiPBtn)
    } else {
        listOf(binding.topPiPBtn, binding.topMenuBtn)
    }
    actions.forEach { action ->
        if (action.parent !== target) {
            (action.parent as? ViewGroup)?.removeView(action)
            action.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
            target.addView(action)
        }
        if (!topActionsInPlayerBar) {
            val padding = (TOP_ACTION_PADDING_DP * resources.displayMetrics.density).toInt()
            action.minimumWidth = (TOP_ACTION_SIZE_DP * resources.displayMetrics.density).toInt()
            action.minimumHeight = (TOP_ACTION_SIZE_DP * resources.displayMetrics.density).toInt()
            action.setPadding(padding, padding, padding, padding)
            action.background = themedPlayerDrawable(R.drawable.bg_tv_player_icon_button)
        }
    }
    binding.topControls.setVisibilityIfChanged(
        if (!topActionsInPlayerBar && binding.controls.visibility == View.VISIBLE) View.VISIBLE else View.GONE,
    )
}

private fun MPVActivity.isControlAvailableForCurrentMedia(control: PlayerBarControl): Boolean {
    if (useAudioUI) {
        return when (control) {
            PlayerBarControl.PLAY,
            PlayerBarControl.CHAPTERS,
            PlayerBarControl.AUDIO,
            PlayerBarControl.SETTINGS,
            PlayerBarControl.PREVIOUS,
            PlayerBarControl.NEXT,
            PlayerBarControl.SPEED,
            -> true
            else -> false
        } && dynamicPlayerControlAvailability(control)
    }
    return dynamicPlayerControlAvailability(control)
}

private fun MPVActivity.dynamicPlayerControlAvailability(control: PlayerBarControl): Boolean = when (control) {
    PlayerBarControl.CHAPTERS -> cachedChapters.isNotEmpty()
    PlayerBarControl.PICTURE_IN_PICTURE -> isPictureInPictureActionAvailable()
    PlayerBarControl.PREVIOUS,
    PlayerBarControl.NEXT,
    -> useAudioUI || psc.playlistCount != 1
    else -> true
}

private fun MPVActivity.applyPlayerPanelSurface(style: PlayerUiCustomization) {
    val start = themedColor(R.attr.mpvOverlayStart, R.color.tv_surface)
    val center = themedColor(R.attr.mpvOverlayCenter, R.color.tv_surface)
    val end = themedColor(R.attr.mpvOverlayEnd, R.color.tv_surface)
    val flat = themedColor(R.attr.mpvSurfaceStrongEnd, R.color.tv_surface)
    val colors = when (style.surface) {
        PlayerPanelSurface.GLASS -> if (style.gradientEnabled) {
            intArrayOf(start, center, end)
        } else {
            intArrayOf(center, center)
        }
        PlayerPanelSurface.FLAT -> intArrayOf(flat, flat)
        PlayerPanelSurface.TRANSPARENT -> intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT)
    }.map { color -> color.withOpacity(style.panelOpacityPercent) }.toIntArray()
    val stroke = themedColor(R.attr.mpvStrokeStrong, R.color.tv_stroke_strong)
    binding.controls.background = GradientDrawable(
        GradientDrawable.Orientation.TOP_BOTTOM,
        colors,
    ).apply {
        cornerRadius = resources.displayMetrics.density * style.cornerRadiusDp
        if (style.panelOutlineEnabled) {
            setStroke(
                (resources.displayMetrics.density * style.panelOutlineWidthDp).toInt().coerceAtLeast(1),
                stroke,
            )
        }
    }
    binding.controlsScrim.background = GradientDrawable(
        GradientDrawable.Orientation.BOTTOM_TOP,
        intArrayOf(
            Color.BLACK.scaleAlpha(style.scrimStrengthPercent),
            Color.TRANSPARENT,
        ),
    )
    binding.controlsScrim.setVisibilityIfChanged(
        if (style.scrimStrengthPercent > 0 && binding.controls.visibility == View.VISIBLE) {
            View.VISIBLE
        } else {
            View.GONE
        },
    )
}

internal fun MPVActivity.playerControlsScrimEnabled(): Boolean =
    playerUiCustomization.scrimStrengthPercent > 0

private fun MPVActivity.applyPlayerControlDecoration(enabled: Boolean) {
    binding.controls.applyControlDecorationRecursively(enabled)
}

private fun View.applyControlDecorationRecursively(enabled: Boolean) {
    when (this) {
        is ShadowImageButton -> outlineAndShadowEnabled = enabled
        is TextView -> {
            if (enabled) {
                val density = resources.displayMetrics.density
                setShadowLayer(2f * density, 0f, density, 0xCC000000.toInt())
            } else {
                setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
            }
        }
        is ViewGroup -> for (index in 0 until childCount) {
            getChildAt(index).applyControlDecorationRecursively(enabled)
        }
    }
}

private fun Int.scaleAlpha(percent: Int): Int {
    val scaled = Color.alpha(this) * percent.coerceIn(MIN_PERCENT, MAX_PERCENT) / MAX_PERCENT
    return Color.argb(scaled, Color.red(this), Color.green(this), Color.blue(this))
}

private fun Int.withOpacity(percent: Int): Int = Color.argb(
    MAX_COLOR_CHANNEL * percent.coerceIn(MIN_PERCENT, MAX_PERCENT) / MAX_PERCENT,
    Color.red(this),
    Color.green(this),
    Color.blue(this),
)

private fun MPVActivity.themedPlayerDrawable(@DrawableRes drawableRes: Int) =
    ResourcesCompat.getDrawable(resources, drawableRes, theme)

private fun PlayerPanelDensity.layoutMetrics(): PlayerPanelLayoutMetrics = when (this) {
    PlayerPanelDensity.COMPACT -> COMPACT_LAYOUT_METRICS
    PlayerPanelDensity.STANDARD -> STANDARD_LAYOUT_METRICS
    PlayerPanelDensity.COMFORTABLE -> COMFORTABLE_LAYOUT_METRICS
}

private data class PlayerPanelLayoutMetrics(
    val seekbarHeightDp: Int,
)

private data class PlayerSeekbarThumbStyle(
    val sizeDp: Int,
    val offsetDp: Float,
    val viewHeightDp: Int,
)

private fun PlayerSeekbarThumbSize.thumbStyle() = when (this) {
    PlayerSeekbarThumbSize.SMALL -> PlayerSeekbarThumbStyle(
        SMALL_THUMB_SIZE_DP,
        SMALL_THUMB_OFFSET_DP,
        SMALL_THUMB_VIEW_HEIGHT_DP,
    )
    PlayerSeekbarThumbSize.STANDARD -> PlayerSeekbarThumbStyle(
        STANDARD_THUMB_SIZE_DP,
        STANDARD_THUMB_OFFSET_DP,
        STANDARD_THUMB_VIEW_HEIGHT_DP,
    )
    PlayerSeekbarThumbSize.LARGE -> PlayerSeekbarThumbStyle(
        LARGE_THUMB_SIZE_DP,
        LARGE_THUMB_OFFSET_DP,
        LARGE_THUMB_VIEW_HEIGHT_DP,
    )
}

private data class PlayerSeekbarTrackStyle(
    val drawableRes: Int,
    val heightDp: Float,
)

private fun PlayerSeekbarSize.trackStyle() = when (this) {
    PlayerSeekbarSize.THIN -> PlayerSeekbarTrackStyle(
        R.drawable.seekbar_progress_tv_thin,
        SEEKBAR_TRACK_THIN_DP,
    )
    PlayerSeekbarSize.STANDARD -> PlayerSeekbarTrackStyle(
        R.drawable.seekbar_progress_tv,
        SEEKBAR_TRACK_STANDARD_DP,
    )
    PlayerSeekbarSize.THICK -> PlayerSeekbarTrackStyle(
        R.drawable.seekbar_progress_tv_thick,
        SEEKBAR_TRACK_THICK_DP,
    )
}

private fun PlayerButtonTreatment.backgroundRes(isIcon: Boolean): Int = when (this) {
    PlayerButtonTreatment.MINIMAL -> if (isIcon) {
        R.drawable.bg_tv_player_icon_button
    } else {
        R.drawable.bg_tv_player_button
    }
    PlayerButtonTreatment.SOFT -> if (isIcon) {
        R.drawable.bg_player_control_soft_icon
    } else {
        R.drawable.bg_player_control_soft_text
    }
    PlayerButtonTreatment.BLOCK -> R.drawable.bg_player_control_block
}

private fun PlayerControlSize.controlMetrics(): PlayerControlMetrics = when (this) {
    PlayerControlSize.COMPACT -> COMPACT_CONTROL_METRICS
    PlayerControlSize.STANDARD -> STANDARD_CONTROL_METRICS
    PlayerControlSize.LARGE -> LARGE_CONTROL_METRICS
}

private data class PlayerControlMetrics(
    val iconWidthDp: Int,
    val textWidthDp: Int,
    val heightDp: Int,
    val paddingDp: Int,
)

private const val SEEKBAR_TRACK_THIN_DP = 4f
private const val SEEKBAR_TRACK_STANDARD_DP = 8f
private const val SEEKBAR_TRACK_THICK_DP = 14f
private const val MAX_COLOR_CHANNEL = 255
private const val TIME_PILL_HORIZONTAL_PADDING_DP = 12
private const val TIME_PILL_VERTICAL_PADDING_DP = 8
private const val TIME_OUTLINE_HORIZONTAL_PADDING_DP = 10
private const val TIME_OUTLINE_VERTICAL_PADDING_DP = 6
private const val TIME_PLAIN_HORIZONTAL_PADDING_DP = 4
private const val TIME_PLAIN_VERTICAL_PADDING_DP = 2
private const val PREF_MINIMAL_SEEKBAR_WHILE_SEEKING = "minimal_seekbar_while_seeking"
private const val PREF_HIDE_CONTROLS_WHILE_SEEKING = "hide_controls_while_seeking"
private const val TIME_OUTLINE_RADIUS_DP = 999
private const val TOP_ACTION_SIZE_DP = 48
private const val TOP_ACTION_PADDING_DP = 8
private const val SMALL_THUMB_OFFSET_DP = 12f
private const val STANDARD_THUMB_OFFSET_DP = 16f
private const val LARGE_THUMB_OFFSET_DP = 20f
private const val SMALL_THUMB_SIZE_DP = 24
private const val STANDARD_THUMB_SIZE_DP = 32
private const val LARGE_THUMB_SIZE_DP = 40
private const val SMALL_THUMB_VIEW_HEIGHT_DP = 30
private const val STANDARD_THUMB_VIEW_HEIGHT_DP = 38
private const val LARGE_THUMB_VIEW_HEIGHT_DP = 48
private val COMPACT_LAYOUT_METRICS = PlayerPanelLayoutMetrics(30)
private val STANDARD_LAYOUT_METRICS = PlayerPanelLayoutMetrics(36)
private val COMFORTABLE_LAYOUT_METRICS = PlayerPanelLayoutMetrics(44)
private val COMPACT_CONTROL_METRICS = PlayerControlMetrics(40, 48, 40, 6)
private val STANDARD_CONTROL_METRICS = PlayerControlMetrics(48, 56, 48, 8)
private val LARGE_CONTROL_METRICS = PlayerControlMetrics(58, 68, 56, 10)
