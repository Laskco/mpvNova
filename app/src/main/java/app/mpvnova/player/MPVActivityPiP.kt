package app.mpvnova.player

import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import android.util.Rational
import androidx.annotation.DrawableRes
import androidx.annotation.RequiresApi
import androidx.annotation.StringRes

/** Picture-in-picture activity-side glue. */

internal fun MPVActivity.onPiPModeChangedImpl(state: Boolean) {
    Log.v(MPV_ACTIVITY_TAG, "onPiPModeChanged($state)")
    if (state) {
        hideControls()
        return
    }

    // No clean PiP-exit signal — finish here or the activity sticks around
    // unreachable from recents. <https://stackoverflow.com/a/56127742>
    // On Android ≤12 the result gets delivered on the *next* launch, which
    // makes the file picker look broken.
    if (activityIsStopped) {
        finishWithResult(RESULT_OK, true)
    }
}

@RequiresApi(Build.VERSION_CODES.O)
internal fun MPVActivity.makeRemoteAction(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    intentAction: String
): RemoteAction {
    val intent = NotificationButtonReceiver.createIntent(this, intentAction)
    return RemoteAction(
        Icon.createWithResource(this, icon),
        getString(title),
        REMOTE_ACTION_EMPTY_TEXT,
        intent
    )
}

internal fun MPVActivity.updatePiPParams(force: Boolean = false) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
        return
    if (!isInPictureInPictureMode && !force)
        return

    try {
        setPictureInPictureParams(buildPiPParams())
    } catch (ignored: IllegalArgumentException) {
        // Aspect ratio out of Android's bounds — fall back to square.
        setPictureInPictureParams(buildPiPParams(Rational(SQUARE_ASPECT_RATIO, SQUARE_ASPECT_RATIO)))
    }
}

@RequiresApi(Build.VERSION_CODES.O)
internal fun MPVActivity.buildPiPParams(fallbackAspectRatio: Rational? = null): PictureInPictureParams {
    val playPauseAction = if (psc.pause)
        makeRemoteAction(R.drawable.ic_play_arrow_black_24dp, R.string.btn_play, "PLAY_PAUSE")
    else
        makeRemoteAction(R.drawable.ic_pause_black_24dp, R.string.btn_pause, "PLAY_PAUSE")
    val actions = mutableListOf<RemoteAction>()
    if (psc.playlistCount > 1) {
        actions.add(makeRemoteAction(
            R.drawable.ic_skip_previous_black_24dp, R.string.dialog_prev, "ACTION_PREV"
        ))
        actions.add(playPauseAction)
        actions.add(makeRemoteAction(
            R.drawable.ic_skip_next_black_24dp, R.string.dialog_next, "ACTION_NEXT"
        ))
    } else {
        actions.add(playPauseAction)
    }

    return with(PictureInPictureParams.Builder()) {
        val aspect = fallbackAspectRatio ?: Rational(
            (player.getVideoAspect() ?: 0.0).times(PIP_ASPECT_RATIO_SCALE).toInt(),
            PIP_ASPECT_RATIO_SCALE
        )
        setAspectRatio(aspect)
        setActions(actions)
        build()
    }
}
