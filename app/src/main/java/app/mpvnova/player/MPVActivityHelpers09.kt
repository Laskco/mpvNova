package app.mpvnova.player

import app.mpvnova.player.databinding.PlayerBinding
import app.mpvnova.player.MpvEvent
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.annotation.SuppressLint
import android.app.ForegroundServiceStartNotAllowedException
import androidx.appcompat.app.AlertDialog
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.DisplayMetrics
import android.util.Rational
import androidx.core.content.ContextCompat
import android.view.Gravity
import android.view.InputDevice
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageButton
import android.widget.SeekBar
import android.widget.TextView
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.annotation.IdRes
import androidx.annotation.LayoutRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.IntentCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.isVisible
import androidx.core.view.updateLayoutParams
import androidx.media.AudioAttributesCompat
import androidx.media.AudioFocusRequestCompat
import androidx.media.AudioManagerCompat
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.io.File
import java.io.FileNotFoundException
import java.lang.IllegalArgumentException
import kotlin.math.roundToInt
import kotlin.math.roundToLong

internal fun MPVActivity.cycleAudio() = trackSwitchNotification {
    player.cycleAudio(); TrackData(player.aid, "audio")
}

internal fun MPVActivity.cycleSub() = trackSwitchNotification {
    player.cycleSub(); TrackData(player.sid, "sub")
}

internal fun MPVActivity.showWidePlayerDialog(dialog: AlertDialog, layout: PlayerDialogLayout = PlayerDialogLayout()) {
    dialog.show()
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
}

internal fun MPVActivity.pickAudio() {
    val restore = keepPlaybackForDialog()
    val tracks = player.tracks.getValue("audio")
    val selectedId = player.aid
    val items = tracks.map {
        MediaPickerDialog.Item(it.name, it.mpvId, it.mpvId == selectedId)
    }
    val impl = MediaPickerDialog()
    lateinit var dialog: AlertDialog
    impl.onItemClick = { idx ->
        val trackId = tracks[idx].mpvId
        player.aid = trackId
        saveUserTrackPick("audio", trackId)
        dialog.dismiss()
        trackSwitchNotification { TrackData(trackId, "audio") }
    }
    impl.onVoiceBoostAdjust = { delta -> adjustVoiceBoost(delta) }
    impl.onVolumeBoostAdjust = { delta -> adjustVolumeBoost(delta) }
    impl.onNightModeAdjust = { delta -> adjustNightMode(delta) }
    impl.onAudioNormAdjust = { delta -> adjustAudioNorm(delta) }
    impl.onDownmixAdjust = { delta -> adjustDownmix(delta) }
    impl.onFilterStatesRefresh = { currentFilterStates() }
    impl.onPersistClick    = {
        persistAudioFilters = !persistAudioFilters
        writeSettings()
        showToast(
            getString(R.string.pref_persist_filters_title),
            getString(if (persistAudioFilters) R.string.status_on else R.string.status_off)
        )
    }

    @Suppress("DEPRECATION")
    dialog = with(AlertDialog.Builder(this)) {
        val inflater = LayoutInflater.from(context)
        setView(impl.buildView(
            inflater,
            MediaPickerDialog.Options(
                title = getString(R.string.dialog_title_audio),
                items = items,
                showFilters = true,
                initialVoiceBoostState = currentVoiceBoostState(),
                initialVolumeBoostState = currentVolumeBoostState(),
                initialNightModeState = currentNightModeState(),
                initialAudioNormState = currentAudioNormState(),
                initialDownmixState = currentDownmixState(),
                persistFiltersOn = persistAudioFilters,
            )
        ))
        setOnDismissListener { restore() }
        create()
    }
    showWidePlayerDialog(
        dialog,
        PlayerDialogLayout(
            widthFraction = 0.78f,
            maxWidthDp = 1080f,
        )
    )
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

internal fun MPVActivity.pickSub() {
    val restore = keepPlaybackForDialog()
    val impl = MediaPickerDialog()
    lateinit var dialog: AlertDialog
    configureSubPickerCallbacks(impl) { dialog.dismiss() }
    dialog = createSubPickerDialog(impl, restore)
    showWidePlayerDialog(
        dialog,
        PlayerDialogLayout(
            widthFraction = 0.78f,
            maxWidthDp = 1080f,
        )
    )
}

internal fun MPVActivity.openSubDelayDialog() {
    val restore = keepPlaybackForDialog()
    val picker = SubDelayDialog(SUB_DELAY_MIN_SEC, SUB_DELAY_MAX_SEC)
    val dialog = with(AlertDialog.Builder(this)) {
        setTitle(R.string.sub_delay)
        val inflater = LayoutInflater.from(context)
        setView(picker.buildView(inflater))
        setPositiveButton(R.string.dialog_ok) { _, _ ->
            picker.delay1?.let { player.subDelay = it }
            picker.delay2?.let { player.secondarySubDelay = it }
        }
        setNegativeButton(R.string.dialog_cancel) { d, _ -> d.cancel() }
        setOnDismissListener { restore() }
        create()
    }
    picker.delay1 = player.subDelay ?: 0.0
    picker.delay2 = if (player.secondarySid != -1) player.secondarySubDelay else null
    showWidePlayerDialog(
        dialog,
        PlayerDialogLayout(
            widthFraction = 0.82f,
            maxWidthDp = 980f,
            heightFraction = 0.82f,
            maxHeightDp = 760f,
        )
    )
}

internal fun MPVActivity.openPlaylistMenu(restore: StateRestoreCallback) {
    val impl = PlaylistDialog(player)
    lateinit var dialog: AlertDialog

    impl.listeners = object : PlaylistDialog.Listeners {
        private fun openFilePicker(skip: Int) {
            openFilePickerFor("", skip) { result, data ->
                if (result == RESULT_OK) {
                    val path = data!!.getStringExtra("path")!!
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
                create().show()
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
        setOnDismissListener { restore() }
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
