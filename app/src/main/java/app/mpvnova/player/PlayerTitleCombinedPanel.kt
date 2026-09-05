package app.mpvnova.player

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
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
) : ViewTreeObserver.OnPreDrawListener {
    val surface = View(root.context).apply {
        isFocusable = false
        isClickable = false
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    }
    private var enabled = false

    init {
        root.addView(surface, minOf(root.indexOfChild(title), root.indexOfChild(clock)),
            RelativeLayout.LayoutParams(0, 0))
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) {
                if (enabled) root.viewTreeObserver.addOnPreDrawListener(this@PlayerTitleCombinedPanel)
            }

            override fun onViewDetachedFromWindow(view: View) {
                if (root.viewTreeObserver.isAlive) {
                    root.viewTreeObserver.removeOnPreDrawListener(this@PlayerTitleCombinedPanel)
                }
            }
        })
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
            root.viewTreeObserver.addOnPreDrawListener(this)
        }
        root.invalidate()
    }

    fun disable() {
        if (!enabled) return
        enabled = false
        if (root.viewTreeObserver.isAlive) root.viewTreeObserver.removeOnPreDrawListener(this)
        surface.visibility = View.GONE
        title.translationX = 0f
        title.translationY = 0f
        clock.translationX = 0f
        clock.translationY = 0f
        title.clipBounds = null
        clock.clipBounds = null
    }

    override fun onPreDraw(): Boolean {
        if (!enabled || root.width <= 0 || root.height <= 0) return true
        val panels = listOf(title, clock).filter { it.visibility == View.VISIBLE && it.hasVisibleText() }
        surface.visibility = if (panels.isEmpty()) View.GONE else View.VISIBLE
        return panels.isEmpty() || layoutPanels(panels)
    }

    private fun layoutPanels(panels: List<ViewGroup>): Boolean {
        val style = styleProvider().titlePanel
        val density = root.resources.displayMetrics.density
        val width = panels.maxOf { it.width }.coerceAtMost(root.width)
        val height = panels.sumOf { it.height }.coerceAtMost(root.height)
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
        if (updateSurfaceLayout(clampedLeft, top, width, height)) return false
        surface.alpha = panels.maxOf { it.alpha }
        var y = top
        panels.forEach { panel ->
            val x = clampedLeft + when (style.contentAlignment) {
                PlayerTitlePanelAlignment.START -> 0
                PlayerTitlePanelAlignment.CENTER -> (width - panel.width) / 2
                PlayerTitlePanelAlignment.END -> width - panel.width
            }
            panel.translationX = (x - panel.left).toFloat()
            panel.translationY = (y - panel.top).toFloat()
            panel.clipBounds = Rect(
                0, 0, panel.width.coerceAtMost(width), (top + height - y).coerceIn(0, panel.height),
            )
            y += panel.height
        }
        return true
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

private fun ViewGroup.hasVisibleText(): Boolean = (0 until childCount).any { index ->
    val child = getChildAt(index)
    child.visibility == View.VISIBLE && (child !is ViewGroup || child.hasVisibleText())
}
