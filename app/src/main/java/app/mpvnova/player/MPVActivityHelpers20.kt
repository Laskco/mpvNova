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

internal fun MPVActivity.clampSubFilterState() {
    subScaleLevel = subScaleLevel.coerceIn(0, subScaleSteps.lastIndex)
    subPosLevel = subPosLevel.coerceIn(0, subPosSteps.lastIndex)
    secondaryPosLevel = secondaryPosLevel.coerceIn(0, secondaryPosSteps.lastIndex)
}

internal fun MPVActivity.pickSpeed() {
    val picker = SpeedPickerDialog()

    val restore = keepPlaybackForDialog()
    genericPickerDialog(picker, R.string.title_speed_dialog, "speed") {
        restore()
    }
}

internal fun MPVActivity.goIntoPiP() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
        return
    enterPictureInPictureMode(buildPiPParams())
}

internal fun MPVActivity.lockUI() {
    lockedUI = true
    hideControlsFade()
}

internal fun MPVActivity.unlockUI() {
    binding.unlockBtn.visibility = View.GONE
    lockedUI = false
    showControls()
}

internal fun MPVActivity.genericMenu(
        @LayoutRes layoutRes: Int, buttons: List<MenuItem>, hiddenButtons: Set<Int>,
        restoreState: StateRestoreCallback) {
    lateinit var dialog: AlertDialog

    val builder = AlertDialog.Builder(this)
    val dialogView = LayoutInflater.from(builder.context).inflate(layoutRes, null)

    for (button in buttons) {
        val buttonView = dialogView.findViewById<Button>(button.idRes)
        buttonView.setOnClickListener {
            val ret = button.handler()
            if (ret) // restore state immediately
                restoreState()
            dialog.dismiss()
        }
    }

    hiddenButtons.forEach { dialogView.findViewById<View>(it).isVisible = false }

    if (visibleChildren(dialogView) == 0) {
        Log.w(MPV_ACTIVITY_TAG, "Not showing menu because it would be empty")
        restoreState()
        return
    }

    handleInsetsAsPadding(dialogView)

    with (builder) {
        setView(dialogView)
        setOnCancelListener { restoreState() }
        dialog = create()
    }
    showWidePlayerDialog(
        dialog,
        PlayerDialogLayout(
            widthFraction = 0.56f,
            maxWidthDp = 620f,
            heightFraction = 0.72f,
            maxHeightDp = 520f,
        )
    )
}

internal fun MPVActivity.openTopMenu() {
    val restoreState = keepPlaybackForDialog()
    genericMenu(
        R.layout.dialog_top_menu,
        topMenuItems(restoreState),
        topMenuHiddenButtons(),
        restoreState
    )
}

internal fun MPVActivity.genericPickerDialog(
    picker: PickerDialog, @StringRes titleRes: Int, property: String,
    restoreState: StateRestoreCallback
) {
    val dialog = with(AlertDialog.Builder(this)) {
        setTitle(titleRes)
        val inflater = LayoutInflater.from(context)
        setView(picker.buildView(inflater))
        setPositiveButton(R.string.dialog_ok) { _, _ ->
            picker.number?.let {
                if (picker.isInteger())
                    mpvSetPropertyInt(property, it.toInt())
                else
                    mpvSetPropertyDouble(property, it)
            }
        }
        setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.cancel() }
        setOnDismissListener { restoreState() }
        create()
    }

    picker.number = mpvGetPropertyDouble(property)
    dialog.show()
}
