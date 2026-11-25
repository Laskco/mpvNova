package app.mpvnova.player

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.RelativeLayout

internal fun MPVActivity.applyPlayerTitleCombinedPanel() {
    val root = binding.playerTitleOverlay.parent as? RelativeLayout ?: return
    val existing = root.getTag(R.id.player_title_combined_panel) as? PlayerTitleCombinedPanel
    if (!playerTitleStyle.combinedPanels) {
        existing?.disable()
        return
    }
    val combined = existing ?: PlayerTitleCombinedPanel(
        root, binding.playerTitleOverlay, binding.timeInfoPanel, { playerTitleStyle },
    ).also {
        root.setTag(R.id.player_title_combined_panel, it)
    }
    combined.applyStyle()
}

internal fun MPVActivity.syncPlayerTitleCombinedPanel() {
    if (!playerTitleStyle.combinedPanels) return
    val root = binding.playerTitleOverlay.parent as? RelativeLayout ?: return
    val combined = root.getTag(R.id.player_title_combined_panel) as? PlayerTitleCombinedPanel ?: return
    combined.syncLayout()
}

internal fun MPVActivity.playerTitleCombinedSurface(): View? {
    if (!playerTitleStyle.combinedPanels) return null
    val root = binding.playerTitleOverlay.parent as? RelativeLayout ?: return null
    return (root.getTag(R.id.player_title_combined_panel) as? PlayerTitleCombinedPanel)?.surface
}

internal fun MPVActivity.playerTitlePlacementTarget(part: PlayerTitlePart): View {
    val root = binding.playerTitleOverlay.parent as? View
    val combined = root?.getTag(R.id.player_title_combined_panel) as? PlayerTitleCombinedPanel
    return if (playerTitleStyle.combinedPanels && combined != null) combined.surface
    else if (part.isTitlePart()) binding.playerTitleOverlay else binding.timeInfoPanel
}

// Keep the original sibling views: activity visibility/animation code still owns each independently.
internal class PlayerTitleCombinedPanel(
    private val root: RelativeLayout,
    private val title: ViewGroup,
    private val clock: ViewGroup,
    private val styleProvider: () -> PlayerTitleStyle,
) : View.OnLayoutChangeListener {
    val surface = View(root.context).apply {
        isFocusable = false
        isClickable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private var enabled = false
    private val scratchClip = Rect()
    private val currentClip = Rect()

    init {
        root.addView(surface, minOf(root.indexOfChild(title), root.indexOfChild(clock)),
            RelativeLayout.LayoutParams(0, 0))
    }

    fun applyStyle() {
        val style = styleProvider().titlePanel
        clock.applyThemedPanelStyle(style, root.context)
        val background = title.background
        // Detach before transferring ownership; clearing the old view otherwise clears the new callback.
        title.background = null
        clock.background = null
        surface.background = background
        surface.elevation = title.elevation
        if (!enabled) {
            enabled = true
            root.addOnLayoutChangeListener(this)
            title.addOnLayoutChangeListener(this)
            clock.addOnLayoutChangeListener(this)
        }
        syncLayout()
    }

    fun disable() {
        if (!enabled) return
        enabled = false
        root.removeOnLayoutChangeListener(this)
        title.removeOnLayoutChangeListener(this)
        clock.removeOnLayoutChangeListener(this)
        surface.visibility = View.GONE
        title.translationX = 0f
        title.translationY = 0f
        clock.translationX = 0f
        clock.translationY = 0f
        title.clipBounds = null
        clock.clipBounds = null
    }

    override fun onLayoutChange(
        v: View?,
        left: Int,
        top: Int,
        right: Int,
        bottom: Int,
        oldLeft: Int,
        oldTop: Int,
        oldRight: Int,
        oldBottom: Int,
    ) {
        syncLayout()
    }

    fun syncLayout() {
        if (!enabled || root.width <= 0 || root.height <= 0) return
        if (title.visibility != View.VISIBLE && clock.visibility != View.VISIBLE) {
            if (surface.visibility != View.GONE) surface.visibility = View.GONE
            return
        }
        val visibleTitle = title.takeIf { it.visibility == View.VISIBLE && it.hasVisibleText() }
        val visibleClock = clock.takeIf { it.visibility == View.VISIBLE && it.hasVisibleText() }
        val first = visibleTitle ?: visibleClock
        val targetVis = if (first == null) View.GONE else View.VISIBLE
        if (surface.visibility != targetVis) {
            surface.visibility = targetVis
        }
        if (first != null) {
            layoutPanels(first, if (visibleTitle != null) visibleClock else null)
        }
    }

    private fun layoutPanels(first: ViewGroup, second: ViewGroup?) {
        val style = styleProvider().titlePanel
        val density = root.resources.displayMetrics.density
        val width = maxOf(first.width, second?.width ?: first.width).coerceAtMost(root.width)
        val height = (first.height + (second?.height ?: 0)).coerceAtMost(root.height)
        val left = if (style.manualPosition) (style.horizontalOffsetDp * density).toInt() else {
            when (style.alignment) {
                PlayerTitlePanelAlignment.START -> (SHARED_PANEL_EDGE_MARGIN_DP * density).toInt()
                PlayerTitlePanelAlignment.CENTER -> (root.width - width) / 2
                PlayerTitlePanelAlignment.END -> root.width - width - (SHARED_PANEL_EDGE_MARGIN_DP * density).toInt()
            }
        }.coerceIn(0, (root.width - width).coerceAtLeast(0))
        val top = (style.verticalOffsetDp * density).toInt()
            .coerceIn(0, (root.height - height).coerceAtLeast(0))
        val clampedLeft = left.coerceIn(0, (root.width - width).coerceAtLeast(0))
        updateSurfaceLayout(clampedLeft, top, width, height)
        val targetAlpha = maxOf(first.alpha, second?.alpha ?: first.alpha)
        if (surface.alpha != targetAlpha) {
            surface.alpha = targetAlpha
        }
        layoutPanel(first, style.contentAlignment, clampedLeft, top, width, top + height)
        if (second != null) {
            layoutPanel(second, style.contentAlignment, clampedLeft, top + first.height, width, top + height)
        }
    }

    private fun layoutPanel(
        panel: ViewGroup,
        alignment: PlayerTitlePanelAlignment,
        left: Int,
        top: Int,
        width: Int,
        bottom: Int,
    ) {
        val x = left + when (alignment) {
            PlayerTitlePanelAlignment.START -> 0
            PlayerTitlePanelAlignment.CENTER -> (width - panel.width) / 2
            PlayerTitlePanelAlignment.END -> width - panel.width
        }
        val targetTx = (x - panel.left).toFloat()
        val targetTy = (top - panel.top).toFloat()
        if (panel.translationX != targetTx) panel.translationX = targetTx
        if (panel.translationY != targetTy) panel.translationY = targetTy
        scratchClip.set(
            0, 0, panel.width.coerceAtMost(width), (bottom - top).coerceIn(0, panel.height),
        )
        val hasClip = panel.getClipBounds(currentClip)
        if (!hasClip || currentClip != scratchClip) {
            panel.clipBounds = scratchClip
        }
    }

    private fun updateSurfaceLayout(left: Int, top: Int, width: Int, height: Int): Boolean {
        val params = surface.layoutParams as RelativeLayout.LayoutParams
        val sameSize = params.width == width && params.height == height
        val samePosition = params.leftMargin == left && params.topMargin == top
        if (sameSize && samePosition) return false
        params.width = width
        params.height = height
        params.leftMargin = left
        params.topMargin = top
        params.addRule(RelativeLayout.ALIGN_PARENT_LEFT)
        params.addRule(RelativeLayout.ALIGN_PARENT_TOP)
        surface.layoutParams = params
        // Wait for measurement and outline updates before displaying the resized shared surface.
        return true
    }
}

private const val SHARED_PANEL_EDGE_MARGIN_DP = 20

private fun ViewGroup.hasVisibleText(): Boolean {
    for (index in 0 until childCount) {
        val child = getChildAt(index)
        if (child.visibility == View.VISIBLE && (child !is ViewGroup || child.hasVisibleText())) {
            return true
        }
    }
    return false
}
