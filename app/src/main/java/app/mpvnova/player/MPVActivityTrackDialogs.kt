package app.mpvnova.player

import androidx.appcompat.app.AlertDialog
import android.os.Build
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import kotlin.math.roundToInt

internal fun MPVActivity.cycleAudio() = trackSwitchNotification {
    player.cycleAudio(); TrackData(player.aid, "audio")
}

internal fun MPVActivity.cycleSub() = trackSwitchNotification {
    player.cycleSub(); TrackData(player.sid, "sub")
}

internal fun MPVActivity.showWidePlayerDialog(
    dialog: AlertDialog,
    layout: PlayerDialogLayout = PlayerDialogLayout(),
    chrome: PlayerDialogChrome = PlayerDialogChrome.HIDE_ALL,
    animateChrome: Boolean = false,
) {
    showPlayerDialog(dialog, chrome, animateChrome)
    dialog.window?.apply {
        setBackgroundDrawableResource(android.R.color.transparent)
        decorView.setPadding(0, 0, 0, 0)
        setGravity(layout.gravity)

        val screenWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            windowManager.currentWindowMetrics.bounds.width()
        } else @Suppress("DEPRECATION") {
            val dm = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(dm)
            dm.widthPixels
        }
        val maxWidthPx = Utils.convertDp(activityContext, layout.maxWidthDp)
        val desiredWidth = (screenWidth * layout.widthFraction).roundToInt()
        val desiredHeight = layout.heightFraction?.let {
            val screenHeight = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                windowManager.currentWindowMetrics.bounds.height()
            } else @Suppress("DEPRECATION") {
                val dm = DisplayMetrics()
                windowManager.defaultDisplay.getRealMetrics(dm)
                dm.heightPixels
            }
            val fractionalHeight = (screenHeight * it).roundToInt()
            val maxHeightPx = layout.maxHeightDp?.let { dp -> Utils.convertDp(activityContext, dp) }
                ?: Int.MAX_VALUE
            minOf(fractionalHeight, maxHeightPx)
        } ?: WindowManager.LayoutParams.WRAP_CONTENT
        setLayout(minOf(desiredWidth, maxWidthPx), desiredHeight)
        if (layout.verticalOffsetDp != 0f) {
            attributes = attributes.apply {
                y = Utils.convertDp(activityContext, layout.verticalOffsetDp)
            }
        }
    }
    rehostActivePlayerToast()
}

internal fun MPVActivity.showPlayerDialog(
    dialog: AlertDialog,
    chrome: PlayerDialogChrome = PlayerDialogChrome.HIDE_ALL,
    animateChrome: Boolean = false,
) {
    playerDialogStack.removeAll { !it.isShowing }
    if (playerDialogStack.isEmpty() && playerChromeSnapshot == null) {
        playerChromeSnapshot = capturePlayerChrome()
    }
    topPlayerDialog = dialog
    // Any key inside a panel counts as activity, so the idle timer only fires when the user
    // truly stops moving; navigating a menu keeps the screensaver away.
    dialog.setOnKeyListener { _, _, _ -> noteScreensaverActivity(); false }
    dialog.show()
    if (animateChrome) {
        dialog.window?.decorView?.apply {
            alpha = 0f
            animate().alpha(1f).setDuration(PLAYER_DIALOG_FADE_DURATION_MS).start()
        }
    }
    if (dialog !in playerDialogStack) {
        playerDialogStack += dialog
    }
    applyPlayerDialogChrome(chrome, animateChrome)
    dialog.window?.decorView?.addOnAttachStateChangeListener(
        object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit

            override fun onViewDetachedFromWindow(view: View) {
                view.removeOnAttachStateChangeListener(this)
                eventUiHandler.post { onPlayerDialogDetached(dialog) }
            }
        }
    )
    rehostActivePlayerToast()
}

private const val PLAYER_DIALOG_FADE_DURATION_MS = 140L

internal fun MPVActivity.pickAudio(
    returnFocus: TrackPanelReturnFocus = TrackPanelReturnFocus.TRACK,
) {
    val restore = keepPlaybackForDialog()
    val tracks = player.tracks.getValue("audio")
    val selectedId = player.aid
    val items = tracks.map {
        MediaPickerDialog.Item(it.name, it.mpvId, it.mpvId == selectedId)
    }
    val impl = audioPickerDialog ?: MediaPickerDialog().also {
        audioPickerDialog = it
    }
    lateinit var dialog: AlertDialog
    configureAudioPickerCallbacks(impl, tracks) { dialog.dismiss() }
    impl.onDelayClick = {
        trackPanelChildTransition = true
        dialog.dismiss()
        showAudioDelayPicker(keepPlaybackForDialog()) {
            pickAudio(TrackPanelReturnFocus.DELAY)
            trackPanelChildTransition = false
        }
    }

    @Suppress("DEPRECATION")
    dialog = with(AlertDialog.Builder(this)) {
        val inflater = LayoutInflater.from(context)
        setView(impl.buildView(inflater, audioPickerOptions(items)))
        setOnDismissListener {
            restore()
            if (!trackPanelChildTransition) reopenDrawerIfPending()
        }
        create()
    }
    showWidePlayerDialog(
        dialog,
        PlayerDialogLayout(
            widthFraction = 0.78f,
            maxWidthDp = 1080f,
        )
    )
    dialog.window?.decorView?.post {
        if (returnFocus == TrackPanelReturnFocus.DELAY) impl.focusDelayRow()
    }
}

internal fun MPVActivity.buildSubItems(): List<MediaPickerDialog.Item> {
    val rawTracks = player.tracks.getValue("sub")
    val primaryId = player.sid
    val secondaryId = player.secondarySid

    val noneTrack = rawTracks.firstOrNull { it.mpvId == -1 }
    val primaryTrack = rawTracks.firstOrNull { it.mpvId != -1 && it.mpvId == primaryId }
    val secondaryTrack = rawTracks.firstOrNull { it.mpvId != -1 && it.mpvId == secondaryId }
    val pinnedIds = setOfNotNull(primaryTrack?.mpvId, secondaryTrack?.mpvId)

    val orderedTracks = buildList {
        noneTrack?.let { add(it) }
        primaryTrack?.let { add(it) }
        secondaryTrack?.let { add(it) }
        addAll(rawTracks.filter { it.mpvId != -1 && it.mpvId !in pinnedIds })
    }

    val hasSecondary = secondaryTrack != null
    return orderedTracks.map { t ->
        val label: CharSequence = when {
            t.mpvId == -1 -> t.name
            t.mpvId == primaryId && hasSecondary -> "▾  BOTTOM  ·  ${t.name}"
            t.mpvId == secondaryId -> "▴  TOP  ·  ${t.name}"
            else -> t.name
        }
        val checked = t.mpvId == primaryId ||
                (t.mpvId != -1 && t.mpvId == secondaryId)
        MediaPickerDialog.Item(label, t.mpvId, checked)
    }
}

internal fun MPVActivity.pickSub(
    returnFocus: TrackPanelReturnFocus = TrackPanelReturnFocus.TRACK,
) {
    val restore = keepPlaybackForDialog()
    val impl = subtitlePickerDialog ?: MediaPickerDialog().also {
        subtitlePickerDialog = it
    }
    lateinit var dialog: AlertDialog
    configureSubPickerCallbacks(impl) { dialog.dismiss() }
    impl.onDelayClick = {
        trackPanelChildTransition = true
        dialog.dismiss()
        openSubDelayDialog()
    }
    impl.onSubStyleClick = {
        trackPanelChildTransition = true
        dialog.dismiss()
        openSubtitleStyleDialog {
            pickSub(TrackPanelReturnFocus.SUBTITLE_STYLE)
            trackPanelChildTransition = false
        }
    }
    dialog = createSubPickerDialog(impl, restore)
    showWidePlayerDialog(
        dialog,
        PlayerDialogLayout(
            widthFraction = 0.78f,
            maxWidthDp = 1080f,
        )
    )
    dialog.window?.decorView?.post {
        when (returnFocus) {
            TrackPanelReturnFocus.DELAY -> impl.focusDelayRow()
            TrackPanelReturnFocus.SUBTITLE_STYLE -> impl.focusSubtitleStyleRow()
            TrackPanelReturnFocus.TRACK -> Unit
        }
    }
}

internal fun MPVActivity.openSubDelayDialog() {
    val restore = keepPlaybackForDialog()
    showSubDelayPicker(restore) {
        pickSub(TrackPanelReturnFocus.DELAY)
        trackPanelChildTransition = false
    }
}

internal fun MPVActivity.openPlaylistMenu(restore: StateRestoreCallback) {
    val impl = playlistDialog ?: PlaylistDialog(player).also {
        playlistDialog = it
    }
    lateinit var dialog: AlertDialog

    impl.listeners = object : PlaylistDialog.Listeners {
        private fun openFilePicker(skip: Int) {
            openFilePickerFor("", skip) { result, data ->
                if (result == RESULT_OK) {
                    val path = data?.getStringExtra("path") ?: return@openFilePickerFor
                    mpvCommand(arrayOf("loadfile", path, "append"))
                    impl.refresh()
                }
            }
        }
        override fun pickFile() = openFilePicker(FilePickerActivity.FILE_PICKER)

        override fun openUrl() {
            val helper = Utils.OpenUrlDialog(activityContext)
            with (helper) {
                builder.setPositiveButton(R.string.dialog_ok) { _, _ ->
                    mpvCommand(arrayOf("loadfile", helper.text, "append"))
                    impl.refresh()
                }
                builder.setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.cancel() }
                showPlayerDialog(create())
            }
        }

        override fun onItemPicked(item: MPVView.PlaylistItem) {
            mpvSetPropertyInt("playlist-pos", item.index)
            dialog.dismiss()
        }
    }

    dialog = with (AlertDialog.Builder(this)) {
        val inflater = LayoutInflater.from(context)
        setView(impl.buildView(inflater))
        setOnDismissListener { restore(); reopenDrawerIfPending() }
        create()
    }
    showWidePlayerDialog(
        dialog,
        PlayerDialogLayout(
            widthFraction = 0.62f,
            maxWidthDp = 720f,
            heightFraction = 0.82f,
            maxHeightDp = 760f,
        )
    )
}
