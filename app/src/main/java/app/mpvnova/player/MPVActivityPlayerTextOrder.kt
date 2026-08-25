package app.mpvnova.player

import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout

internal fun MPVActivity.applyPlayerTextOrder() {
    reorderTextRows(
        binding.playerTitleOverlay,
        playerTitleStyle.titleOrder.map(::titleUnitView),
    )
    reorderTextRows(
        binding.timeInfoPanel,
        playerTitleStyle.clockOrder.map(::clockUnitView),
    )
}

private fun MPVActivity.titleUnitView(unit: PlayerTitleUnit): View = when (unit) {
    PlayerTitleUnit.CONTEXT -> binding.playerTitleContextRow
    PlayerTitleUnit.TITLE -> binding.playerTitlePrimary
    PlayerTitleUnit.EPISODE_TITLE -> binding.playerTitleSecondary
}

private fun MPVActivity.clockUnitView(unit: PlayerClockUnit): View = when (unit) {
    PlayerClockUnit.DATE -> binding.dateTextView
    PlayerClockUnit.CLOCK -> binding.clockTextView
    PlayerClockUnit.ENDS_AT -> binding.endsAtTextView
}

private fun MPVActivity.reorderTextRows(parent: ViewGroup, orderedViews: List<View>) {
    orderedViews.forEachIndexed { index, view ->
        if (parent.indexOfChild(view) != index) {
            parent.removeView(view)
            parent.addView(view, index)
        }
        updateTextRowMargins(view, index)
    }
}

private fun MPVActivity.updateTextRowMargins(view: View, index: Int) {
    val params = view.layoutParams as? LinearLayout.LayoutParams ?: return
    val topMargin = if (index == 0) 0 else Utils.convertDp(this, TEXT_ROW_GAP_DP)
    if (params.topMargin == topMargin && params.bottomMargin == 0) return
    params.topMargin = topMargin
    params.bottomMargin = 0
    view.layoutParams = params
}

private const val TEXT_ROW_GAP_DP = 4f
