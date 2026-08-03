package app.mpvnova.player

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.media.session.MediaSession
import android.util.Log
import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.PendingIntentCompat
import androidx.core.app.ServiceCompat
import app.mpvnova.player.MpvEvent

fun createBackgroundPlaybackNotificationChannel(context: Context) {
    val manager = NotificationManagerCompat.from(context)
    val builder = NotificationChannelCompat.Builder(
        NOTIFICATION_CHANNEL_ID,
        NotificationManagerCompat.IMPORTANCE_MIN
    )
    manager.createNotificationChannel(with(builder) {
        setName(context.getString(R.string.pref_background_play_title))
        build()
    })
}

private fun Service.buildNotificationAction(
    @DrawableRes icon: Int,
    @StringRes title: Int,
    intentAction: String,
): Notification.Action {
    val intent = NotificationButtonReceiver.createIntent(this, intentAction)

    val builder = Notification.Action.Builder(Icon.createWithResource(this, icon), getString(title), intent)
    return builder.build()
}

private fun Service.notificationBuilder(): Notification.Builder {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
    } else {
        legacyNotificationBuilder()
    }
}

@Suppress("DEPRECATION")
private fun Service.legacyNotificationBuilder(): Notification.Builder {
    return Notification.Builder(this)
}

private fun Service.buildBackgroundNotification(
    metadata: Utils.AudioMetadata,
    paused: Boolean,
    shouldShowPrevNext: Boolean
): Notification {
    val notificationIntent = Intent(this, MPVActivity::class.java)
    notificationIntent.setFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
    val pendingIntent = PendingIntentCompat.getActivity(
        this,
        0,
        notificationIntent,
        PendingIntent.FLAG_UPDATE_CURRENT,
        false
    )

    val builder = notificationBuilder()
    with(builder) {
        setVisibility(Notification.VISIBILITY_PUBLIC)
        setContentTitle(metadata.formatTitle())
        setContentText(metadata.formatArtistAlbum())
        setSmallIcon(R.drawable.ic_mpv_symbolic)
        setContentIntent(pendingIntent)
        setOngoing(true)
    }

    // With an active media session, the media style will override everything
    // (including the thumbnail) and we can skip doing this.
    if (BackgroundPlaybackService.mediaToken == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
        BackgroundPlaybackService.thumbnail?.let {
            builder.setLargeIcon(it)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                builder.setColorized(true)
                val b1 = Bitmap.createScaledBitmap(it, THUMBNAIL_SAMPLE_SIZE, THUMBNAIL_SAMPLE_SIZE, true)
                val b2 = Bitmap.createScaledBitmap(b1, 1, 1, true)
                builder.setColor(b2.getPixel(0, 0))
                b2.recycle(); b1.recycle()
            }
        }
    }

    val playPauseAction = if (paused) {
        buildNotificationAction(R.drawable.ic_play_arrow_black_24dp, R.string.btn_play, "PLAY_PAUSE")
    } else {
        buildNotificationAction(R.drawable.ic_pause_black_24dp, R.string.btn_pause, "PLAY_PAUSE")
    }

    val style = Notification.MediaStyle()
    BackgroundPlaybackService.mediaToken?.let { style.setMediaSession(it) }
    if (shouldShowPrevNext) {
        builder.addAction(buildNotificationAction(
            R.drawable.ic_skip_previous_black_24dp, R.string.dialog_prev, "ACTION_PREV"
        ))
        builder.addAction(playPauseAction)
        builder.addAction(buildNotificationAction(
            R.drawable.ic_skip_next_black_24dp, R.string.dialog_next, "ACTION_NEXT"
        ))
        style.setShowActionsInCompactView(0, 1, 2) // all
    } else {
        builder.addAction(playPauseAction)
    }
    builder.setStyle(style)

    return builder.build()
}

@SuppressLint("NotificationPermission")
private fun Service.notifyBackgroundPlayback(
    metadata: Utils.AudioMetadata,
    paused: Boolean,
    shouldShowPrevNext: Boolean
) {
    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    notificationManager.notify(
        NOTIFICATION_ID,
        buildBackgroundNotification(metadata, paused, shouldShowPrevNext)
    )
}

class BackgroundPlaybackService : Service(), MpvEventObserver {
    override fun onCreate() {
        super.onCreate()
        thumbnailHandler = Handler(mainLooper)
        addMpvObserver(this)
    }

    private lateinit var thumbnailHandler: Handler
    private val thumbnailRunnable = Runnable {
        grabThumbnail()
        // Let the activity refresh the media-session artwork to match the new thumbnail.
        thumbnailChanged?.let { it() }
        notifyBackgroundPlayback(cachedMetadata, paused, shouldShowPrevNext)
    }

    private var cachedMetadata = Utils.AudioMetadata()
    private var paused: Boolean = false
    private var shouldShowPrevNext: Boolean = false

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.v(TAG, "BackgroundPlaybackService: starting")

        cachedMetadata.readAll()
        paused = mpvGetPropertyBoolean("pause") == true
        shouldShowPrevNext = (mpvGetPropertyInt("playlist-count") ?: 0) > 1

        val notification = buildBackgroundNotification(cachedMetadata, paused, shouldShowPrevNext)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)

        return START_NOT_STICKY // Android can't restart this service on its own
    }

    override fun onDestroy() {
        removeMpvObserver(this)

        thumbnailHandler.removeCallbacksAndMessages(null)

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)

        Log.v(TAG, "BackgroundPlaybackService: destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent): IBinder? { return null }

    override fun eventProperty(property: String) {
        if (!cachedMetadata.update(property))
            return
        notifyBackgroundPlayback(cachedMetadata, paused, shouldShowPrevNext)
    }

    override fun eventProperty(property: String, value: Boolean) {
        if (property != "pause")
            return
        paused = value
        notifyBackgroundPlayback(cachedMetadata, paused, shouldShowPrevNext)
    }

    override fun eventProperty(property: String, value: Long) = Unit

    override fun eventProperty(property: String, value: Double) = Unit

    override fun eventProperty(property: String, value: String) {
        if (!cachedMetadata.update(property, value))
            return
        notifyBackgroundPlayback(cachedMetadata, paused, shouldShowPrevNext)
    }

    override fun event(eventId: Int) {
        if (eventId == MpvEvent.MPV_EVENT_SHUTDOWN) {
            stopSelf()
        } else if (eventId == MpvEvent.MPV_EVENT_VIDEO_RECONFIG) {
            // ensure it doesn't run too often
            thumbnailHandler.removeCallbacks(thumbnailRunnable)
            thumbnailHandler.postDelayed(thumbnailRunnable, THUMBNAIL_REFRESH_DELAY_MS)
        }
    }


    companion object {
        /* thumbnail to display alongside the permanent notification */
        var thumbnail: Bitmap? = null
        /* Set by MPVActivity; for connecting the notification to the media session */
        var mediaToken: MediaSession.Token? = null
        /* Set by MPVActivity; to notify on thumbnail changes */
        var thumbnailChanged: (() -> Unit)? = null

        fun grabThumbnail() {
            val fmt = mpvGetPropertyString("video-format")
            thumbnail = if (fmt.isNullOrEmpty())
                null
            else
                mpvGrabThumbnail(THUMB_SIZE)
        }

        private const val TAG = "mpv"
    }
}

private const val NOTIFICATION_ID = 12345
private const val NOTIFICATION_CHANNEL_ID = "background_playback"
private const val THUMBNAIL_SAMPLE_SIZE = 16
// Debounce thumbnail regrab+refresh on video reconfig so it doesn't run too often.
private const val THUMBNAIL_REFRESH_DELAY_MS = 150L
