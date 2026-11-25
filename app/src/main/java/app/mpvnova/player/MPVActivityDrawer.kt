package app.mpvnova.player

import android.content.res.ColorStateList
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.TextViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import app.mpvnova.player.databinding.DialogPlayerDrawerBinding
import app.mpvnova.player.preferences.AppUpdateAvailability

/**
 * Player settings drawer: right-edge panel. Tabs swap the
 * RecyclerView rows in place so focus stays inside one dialog.
 */

private const val FIRST_FOCUSABLE_DRAWER_ROW_POSITION = 0
private const val DRAWER_LIST_VIEW_CACHE_SIZE = 10

internal fun MPVActivity.openPlayerDrawer() {
    val restoreState = keepPlaybackForDialog()
    // Cache the inflated view tree across opens; only pay the inflation
    // cost once per session.
    val binding = drawerBinding ?: DialogPlayerDrawerBinding.inflate(LayoutInflater.from(this)).also {
        handleInsetsAsPadding(it.root)
        it.drawerContentList.itemAnimator = null
        it.drawerContentList.setHasFixedSize(true)
        it.drawerContentList.setItemViewCacheSize(DRAWER_LIST_VIEW_CACHE_SIZE)
        it.drawerContentList.adapter = PlayerDrawerAdapter(this) { currentDrawerDialog?.dismiss() }
        TvScrollbars.bind(it.drawerContentList, it.drawerContentScrollbarThumb)
        drawerBinding = it
    }
    // Detach from prior dialog's window tree if needed before setView.
    binding.root.detachFromParent()

    if (!drawerHandlersBound) {
        bindDrawerTabSwitching(binding)
        drawerHandlersBound = true
    }
    syncUpdateTabVisibility(binding)
    triggerSilentAppUpdateCheckNow()
    selectDrawerTab(binding, lastDrawerTab, revealScrollbar = false)

    var drawerCloseHandled = false
    val onDrawerClosed: () -> Unit = {
        if (!drawerCloseHandled) {
            drawerCloseHandled = true
            currentDrawerDialog = null
            restoreState()
            highlightTopMenuAfterDrawerClose()
        }
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
        ),
        animateChrome = true,
    )
    revealDrawerContentScrollbar(binding)

    drawerTabButton(binding, lastDrawerTab).requestFocus()
}

private fun MPVActivity.highlightTopMenuAfterDrawerClose() {
    // Skip when a sub-dialog is about to bounce back to the drawer:
    // the highlight would flicker.
    if (drawerReopenPending) return
    // Post so the window teardown finishes before we walk dpadButtons().
    eventUiHandler.post {
        // Dialog focus can otherwise remain on the cog while btnSelected moves
        // through the player bar, producing two simultaneous highlights.
        parkPlayerFrameworkFocus()
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
    if (!drawerReopenPending || drawerReopenScheduled) return
    drawerReopenScheduled = true
    // Post so the sub-dialog's window tears down before the drawer
    // slides back in; otherwise the back press that closed the
    // sub-dialog can dismiss the drawer in the same dispatch.
    eventUiHandler.post {
        drawerReopenScheduled = false
        if (!drawerReopenPending) return@post
        openPlayerDrawer()
        drawerReopenPending = false
    }
}

private fun MPVActivity.bindDrawerTabSwitching(binding: DialogPlayerDrawerBinding) {
    val pairs = listOf(
        DrawerTab.VIDEO to binding.tabBtnVideo,
        DrawerTab.PROCESSING to binding.tabBtnProcessing,
        DrawerTab.AUDIO to binding.tabBtnAudio,
        DrawerTab.SUBTITLES to binding.tabBtnSubtitles,
        DrawerTab.PLAYBACK to binding.tabBtnPlayback,
        DrawerTab.NETWORK to binding.tabBtnNetwork,
        DrawerTab.INTERFACE to binding.tabBtnInterface,
    )
    for ((tab, button) in pairs) {
        button.setOnClickListener {
            lastDrawerTab = tab
            selectDrawerTab(binding, tab)
        }
        button.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
                focusFirstDrawerContentRow(binding)
            } else {
                false
            }
        }
    }
    binding.tabBtnUpdate.setOnClickListener {
        val release = AppUpdateAvailability.availableUpdate(this) ?: return@setOnClickListener
        drawerReopenPending = true
        currentDrawerDialog?.dismiss()
        showInPlayerAppUpdatePanel(release)
    }
    binding.tabBtnUpdate.setOnKeyListener { _, keyCode, event ->
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT && event.action == KeyEvent.ACTION_DOWN) {
            focusFirstDrawerContentRow(binding)
        } else {
            false
        }
    }
}

internal fun MPVActivity.selectDrawerTab(
    binding: DialogPlayerDrawerBinding,
    tab: DrawerTab,
    revealScrollbar: Boolean = true,
) {
    val active = drawerTabButton(binding, tab)
    listOf(
        binding.tabBtnVideo,
        binding.tabBtnProcessing,
        binding.tabBtnAudio,
        binding.tabBtnSubtitles,
        binding.tabBtnPlayback,
        binding.tabBtnNetwork,
        binding.tabBtnInterface,
    ).forEach { it.isSelected = (it === active) }

    val titleRes = when (tab) {
        DrawerTab.VIDEO -> R.string.drawer_section_video
        DrawerTab.PROCESSING -> R.string.drawer_section_processing
        DrawerTab.AUDIO -> R.string.drawer_section_audio
        DrawerTab.SUBTITLES -> R.string.drawer_section_subtitles
        DrawerTab.PLAYBACK -> R.string.drawer_section_playback
        DrawerTab.NETWORK -> R.string.network_title
        DrawerTab.INTERFACE -> R.string.drawer_section_interface
    }
    binding.drawerStaticHeader.drawerRowEyebrow.setText(R.string.mpv_activity)
    binding.drawerStaticHeader.drawerRowTitle.setText(titleRes)

    val adapter = binding.drawerContentList.adapter as? PlayerDrawerAdapter ?: return
    binding.drawerContentList.stopScroll()
    binding.drawerContentList.clearFocus()
    adapter.submitRows(buildPlayerDrawerRows(tab))
    resetDrawerContentPosition(binding, revealScrollbar)
}

internal fun MPVActivity.refreshDrawerRowsIfVisible(tab: DrawerTab? = null) {
    val binding = drawerBinding ?: return
    if (currentDrawerDialog?.isShowing == true && (tab == null || lastDrawerTab == tab)) {
        selectDrawerTab(binding, lastDrawerTab, revealScrollbar = false)
    }
}

internal fun MPVActivity.refreshDrawerUpdateState() {
    val binding = drawerBinding ?: return
    if (currentDrawerDialog?.isShowing != true) return
    val updateFocused = binding.tabBtnUpdate.hasFocus()
    syncUpdateTabVisibility(binding)
    if (updateFocused && binding.tabBtnUpdate.visibility != View.VISIBLE) {
        drawerTabButton(binding, lastDrawerTab).requestFocus()
    }
}

private fun MPVActivity.syncUpdateTabVisibility(binding: DialogPlayerDrawerBinding) {
    val release = AppUpdateAvailability.availableUpdate(this)
    binding.tabBtnUpdate.setVisibilityIfChanged(if (release != null) View.VISIBLE else View.GONE)
    if (release != null && !AppUpdateAvailability.isIgnored(this, release)) {
        binding.tabBtnUpdate.setCompoundDrawablesRelativeWithIntrinsicBounds(
            0, 0, R.drawable.ic_update_dot_6dp, 0,
        )
        TextViewCompat.setCompoundDrawableTintList(
            binding.tabBtnUpdate,
            ColorStateList.valueOf(activeFilterColor()),
        )
    } else {
        binding.tabBtnUpdate.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0)
    }
}

private fun drawerTabButton(binding: DialogPlayerDrawerBinding, tab: DrawerTab): Button = when (tab) {
    DrawerTab.VIDEO -> binding.tabBtnVideo
    DrawerTab.PROCESSING -> binding.tabBtnProcessing
    DrawerTab.AUDIO -> binding.tabBtnAudio
    DrawerTab.SUBTITLES -> binding.tabBtnSubtitles
    DrawerTab.PLAYBACK -> binding.tabBtnPlayback
    DrawerTab.NETWORK -> binding.tabBtnNetwork
    DrawerTab.INTERFACE -> binding.tabBtnInterface
}

private fun resetDrawerContentPosition(
    binding: DialogPlayerDrawerBinding,
    revealScrollbar: Boolean,
) {
    val list = binding.drawerContentList
    val layoutManager = list.layoutManager as? LinearLayoutManager
    layoutManager?.scrollToPositionWithOffset(0, 0)
    list.post {
        (list.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
        list.post {
            (list.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
            if (revealScrollbar) {
                revealDrawerContentScrollbar(binding)
            }
        }
    }
}

private fun revealDrawerContentScrollbar(binding: DialogPlayerDrawerBinding) {
    val list = binding.drawerContentList
    list.post {
        (list.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
        list.post {
            TvScrollbars.refresh(list, binding.drawerContentScrollbarThumb)
        }
    }
}

private fun focusFirstDrawerContentRow(binding: DialogPlayerDrawerBinding): Boolean {
    val list = binding.drawerContentList
    list.stopScroll()
    (list.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
    list.post {
        (list.layoutManager as? LinearLayoutManager)?.scrollToPositionWithOffset(0, 0)
        list.post {
            val target = list.findViewHolderForAdapterPosition(FIRST_FOCUSABLE_DRAWER_ROW_POSITION)
                ?.itemView
                ?.takeIf(View::isFocusable)
            if (target?.requestFocus() != true) {
                list.requestFocus()
            }
            TvScrollbars.refresh(list, binding.drawerContentScrollbarThumb)
        }
    }
    return true
}
