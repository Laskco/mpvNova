@file:Suppress("MatchingDeclarationName")
package app.mpvnova.player

import android.view.Gravity
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import app.mpvnova.player.databinding.DialogPlayerDrawerBinding

/**
 * Player settings drawer: right-edge VIMU-style panel. Tabs swap by
 * visibility (no fragments) so focus stays inside one dialog.
 */

internal enum class DrawerTab { VIDEO, AUDIO, SUBTITLES, PLAYBACK, INTERFACE }

internal fun MPVActivity.openPlayerDrawer() {
    val restoreState = keepPlaybackForDialog()
    // Cache the inflated view tree across opens; only pay the inflation
    // cost once per session.
    val binding = drawerBinding ?: DialogPlayerDrawerBinding.inflate(LayoutInflater.from(this)).also {
        handleInsetsAsPadding(it.root)
        TvScrollbars.bind(it.drawerContentScroll, it.drawerContentScrollbarThumb)
        drawerBinding = it
    }
    // Detach from prior dialog's window tree if needed before setView.
    (binding.root.parent as? ViewGroup)?.removeView(binding.root)

    if (!drawerHandlersBound) {
        bindDrawerTabSwitching(binding)
        bindDrawerActionButtons(binding) { currentDrawerDialog?.dismiss() }
        drawerHandlersBound = true
    } else {
        selectDrawerTab(binding, lastDrawerTab)
    }
    // Pref rows rebind every open so external pref changes are picked up.
    bindDrawerPrefRows(binding)
    applyDrawerVisibilityForCurrentState(binding)
    TvScrollbars.revealAfterLayout(binding.drawerContentScroll, binding.drawerContentScrollbarThumb)

    val onDrawerClosed = {
        currentDrawerDialog = null
        restoreState()
        highlightTopMenuAfterDrawerClose()
    }

    val dialog: AlertDialog = with(AlertDialog.Builder(this)) {
        setView(binding.root)
        setOnCancelListener { onDrawerClosed() }
        setOnDismissListener { onDrawerClosed() }
        create()
    }
    currentDrawerDialog = dialog
    showWidePlayerDialog(
        dialog,
        PlayerDialogLayout(
            widthFraction = DRAWER_WIDTH_FRACTION,
            maxWidthDp = DRAWER_MAX_WIDTH_DP,
            heightFraction = 1f,
            gravity = Gravity.END,
        )
    )

    drawerTabButton(binding, lastDrawerTab).requestFocus()
}

private fun MPVActivity.highlightTopMenuAfterDrawerClose() {
    // Skip when a sub-dialog is about to bounce back to the drawer —
    // the highlight would flicker.
    if (drawerReopenPending) return
    // Post so the window teardown finishes before we walk dpadButtons().
    eventUiHandler.post {
        val controls = dpadButtons()
        val gearIdx = controls.indexOf(binding.topMenuBtn)
        if (gearIdx >= 0) {
            btnSelected = gearIdx
            updateSelectedDpadButton()
        }
    }
}

/**
 * Reopen the drawer if a sub-dialog flagged that it wanted to. Every
 * sub-dialog calls this from its onDismiss so picker code doesn't have
 * to know about drawer state.
 */
internal fun MPVActivity.reopenDrawerIfPending() {
    if (!drawerReopenPending) return
    drawerReopenPending = false
    // Post so the sub-dialog's window tears down before the drawer
    // slides back in — otherwise the back press that closed the
    // sub-dialog can dismiss the drawer in the same dispatch.
    eventUiHandler.post { openPlayerDrawer() }
}

private fun MPVActivity.bindDrawerTabSwitching(binding: DialogPlayerDrawerBinding) {
    val pairs = listOf(
        DrawerTab.VIDEO to binding.tabBtnVideo,
        DrawerTab.AUDIO to binding.tabBtnAudio,
        DrawerTab.SUBTITLES to binding.tabBtnSubtitles,
        DrawerTab.PLAYBACK to binding.tabBtnPlayback,
        DrawerTab.INTERFACE to binding.tabBtnInterface,
    )
    for ((tab, button) in pairs) {
        button.setOnClickListener {
            lastDrawerTab = tab
            selectDrawerTab(binding, tab)
        }
    }
    selectDrawerTab(binding, lastDrawerTab)
}

internal fun selectDrawerTab(binding: DialogPlayerDrawerBinding, tab: DrawerTab) {
    binding.tabVideo.isVisible = tab == DrawerTab.VIDEO
    binding.tabAudio.isVisible = tab == DrawerTab.AUDIO
    binding.tabSubtitles.isVisible = tab == DrawerTab.SUBTITLES
    binding.tabPlayback.isVisible = tab == DrawerTab.PLAYBACK
    binding.tabInterface.isVisible = tab == DrawerTab.INTERFACE
    val active = drawerTabButton(binding, tab)
    listOf(
        binding.tabBtnVideo,
        binding.tabBtnAudio,
        binding.tabBtnSubtitles,
        binding.tabBtnPlayback,
        binding.tabBtnInterface,
    ).forEach { it.isSelected = (it === active) }
    binding.drawerContentScroll.scrollTo(0, 0)
    TvScrollbars.revealAfterLayout(binding.drawerContentScroll, binding.drawerContentScrollbarThumb)
}

private fun drawerTabButton(binding: DialogPlayerDrawerBinding, tab: DrawerTab): Button = when (tab) {
    DrawerTab.VIDEO -> binding.tabBtnVideo
    DrawerTab.AUDIO -> binding.tabBtnAudio
    DrawerTab.SUBTITLES -> binding.tabBtnSubtitles
    DrawerTab.PLAYBACK -> binding.tabBtnPlayback
    DrawerTab.INTERFACE -> binding.tabBtnInterface
}

internal fun MPVActivity.applyDrawerVisibilityForCurrentState(binding: DialogPlayerDrawerBinding) {
    binding.drawerBackgroundBtn.isVisible = isPlayingAudio

    val hasChapters = (mpvGetPropertyInt("chapter-list/count") ?: 0) > 0
    binding.drawerChapterBtn.isVisible = hasChapters
    binding.drawerRowChapter.isVisible = hasChapters

    val hasVideo = player.vid != -1
    binding.drawerRowVideoAdjust1.isVisible = hasVideo
    binding.drawerRowVideoAdjust2.isVisible = hasVideo
    binding.drawerAspectBtn.isVisible = hasVideo

    val hasAudio = player.aid != -1
    binding.drawerAudioDelayBtn.isVisible = hasAudio && hasVideo

    val hasSub = player.sid != -1
    binding.drawerSubDelayBtn.isVisible = hasSub
    binding.drawerRowSubSeek.isVisible = hasSub
}
