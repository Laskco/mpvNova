package app.mpvnova.player

import android.view.LayoutInflater
import android.view.View
import androidx.appcompat.app.AlertDialog
import app.mpvnova.player.databinding.DialogStatsPickerBinding

private const val STATS_PICKER_WIDTH_FRACTION = 0.38f
private const val STATS_PICKER_MAX_WIDTH_DP = 500f

internal fun MPVActivity.toggleStatsFromButton() {
    val page = activeStatsPage.takeIf { it in STATS_PAGE_FIRST..STATS_PAGE_LAST }
        ?: STATS_PAGE_FIRST
    toggleStatsPage(page)
}

internal fun MPVActivity.toggleStatsPage(page: Int) {
    val statsPage = page.coerceIn(STATS_PAGE_FIRST, STATS_PAGE_LAST)
    if (activeStatsPage == statsPage) {
        mpvCommand(arrayOf("script-binding", "stats/display-page-$statsPage-toggle"))
        activeStatsPage = 0
        refreshTimeInfoPanelVisibility()
        return
    }
    val previousPage = activeStatsPage.takeIf { it in STATS_PAGE_FIRST..STATS_PAGE_LAST }
    if (previousPage != null)
        mpvCommand(arrayOf("script-binding", "stats/display-page-$previousPage-toggle"))
    mpvCommand(arrayOf("script-binding", "stats/display-page-$statsPage-toggle"))
    activeStatsPage = statsPage
    refreshTimeInfoPanelVisibility()
}

internal fun MPVActivity.showConfiguredStatsPage() {
    val statsPage = statsLuaMode.takeIf { it in STATS_PAGE_FIRST..STATS_PAGE_LAST } ?: return
    mpvCommand(arrayOf("script-binding", "stats/display-page-$statsPage-toggle"))
    activeStatsPage = statsPage
    refreshTimeInfoPanelVisibility()
}

internal fun MPVActivity.showStatsPickerDialog() {
    val restore = keepPlaybackForDialog()
    lateinit var dialog: AlertDialog

    @Suppress("DEPRECATION")
    dialog = with(AlertDialog.Builder(this)) {
        val inflater = LayoutInflater.from(context)
        val binding = DialogStatsPickerBinding.inflate(inflater)
        fun View.openStatsPageOnClick(page: Int) = setOnClickListener {
            toggleStatsPage(page)
            dialog.dismiss()
        }
        binding.statsPage1Row.openStatsPageOnClick(STATS_PAGE_FIRST)
        binding.statsPage2Row.openStatsPageOnClick(STATS_PAGE_FIRST + 1)
        binding.statsPage3Row.openStatsPageOnClick(STATS_PAGE_LAST)
        binding.cancelBtn.setOnClickListener { dialog.cancel() }

        val selected = activeStatsPage.takeIf { it in STATS_PAGE_FIRST..STATS_PAGE_LAST }
        binding.statsPage1Row.isActivated = selected == STATS_PAGE_FIRST
        binding.statsPage2Row.isActivated = selected == STATS_PAGE_FIRST + 1
        binding.statsPage3Row.isActivated = selected == STATS_PAGE_LAST
        binding.statsPage1Row.post { binding.statsPage1Row.requestFocus() }

        setView(binding.root)
        setOnDismissListener { restore(); reopenDrawerIfPending() }
        create()
    }
    showWidePlayerDialog(
        dialog,
        PlayerDialogLayout(
            widthFraction = STATS_PICKER_WIDTH_FRACTION,
            maxWidthDp = STATS_PICKER_MAX_WIDTH_DP,
        )
    )
}
