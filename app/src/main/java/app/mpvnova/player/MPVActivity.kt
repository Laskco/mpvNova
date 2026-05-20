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
import android.view.KeyEvent
import android.view.LayoutInflater
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

typealias ActivityResultCallback = (Int, Intent?) -> Unit
typealias StateRestoreCallback = () -> Unit

class MPVActivity : AppCompatActivity() {
    internal val eventUiHandler = Handler(Looper.getMainLooper())
    internal val fadeHandler = Handler(Looper.getMainLooper())
    internal val stopServiceHandler = Handler(Looper.getMainLooper())
    internal val clockHandler = Handler(Looper.getMainLooper())
    internal val periodicSaveHandler = Handler(Looper.getMainLooper())
    internal val periodicSaveRunnable = object : Runnable {
        override fun run() {
            // Both writes are no-ops when there's nothing to save (no file
            // loaded yet, paused at 0, EOF reached, etc.).
            savePosition()
            saveResumePosition()
            periodicSaveHandler.postDelayed(this, PERIODIC_SAVE_INTERVAL_MS)
        }
    }
    // ms to seek to on file-load if we restored from our resume table; 0 = no
    // restore happened. Drives the "Resumed from X:XX" toast.
    internal var pendingResumeToastMs = 0L
    // The start position we asked mpv to seek to (from intent or resume table).
    // Checked at FILE_LOADED against the actual duration so we can catch
    // near-end positions that slipped through parseIntentExtras.
    internal var pendingStartPositionMs = 0L
    // Source URL/path for the currently loaded file. Do not read Activity.intent
    // directly for resume saves because onNewIntent() can load another episode
    // while the Activity instance stays alive.
    internal var currentResumeSource: String? = null

    internal var activityIsStopped = false

    internal var activityIsForeground = true
    internal var didResumeBackgroundPlayback = false
    internal var userIsOperatingSeekbar = false
    internal var pendingSeekbarSeekMs: Long? = null
    internal var pendingDpadSeekPreviewMs: Long? = null
    internal var lastDisplayedPlaybackSecond = Int.MIN_VALUE
    internal var lastSeekbarProgress = Int.MIN_VALUE
    internal var lastSeekbarUiUpdateMs = 0L
    internal var lastDpadSeekApplyMs = 0L
    internal var lastAppliedSeekMs = Long.MIN_VALUE
    internal var lastClockInfoTick = Long.MIN_VALUE
    @DrawableRes
    internal var lastPlayButtonIconRes = 0

    internal var audioManager: AudioManager? = null
    internal var audioFocusRequest: AudioFocusRequestCompat? = null
    internal var audioFocusRestore: () -> Unit = {}

    internal val psc = Utils.PlaybackStateCache()
    internal var mediaSession: MediaSessionCompat? = null

    internal lateinit var binding: PlayerBinding
    internal val lifecycleObserver = MpvActivityLifecycleObserver(this)
    internal val mpvEventObserver = MpvActivityEventObserver(this)
    internal val mpvLogObserver = MpvActivityLogObserver(this)

    internal val player get() = binding.player

    internal val seekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (!fromUser)
                return
            val positionMs = millisFromSeekbarProgress(progress)
            scheduleSeekbarSeek(positionMs)
            updatePlaybackTimeline(positionMs, forceTextUpdate = true)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            userIsOperatingSeekbar = true
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            userIsOperatingSeekbar = false
            commitPendingSeekbarSeek()
            showControls() // re-trigger display timeout
        }
    }

    internal val commitSeekbarSeekRunnable = Runnable {
        commitPendingSeekbarSeek()
    }

    internal var becomingNoisyReceiverRegistered = false
    internal val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS, "noisy")
            }
        }
    }

    internal val fadeRunnable: ControlsFadeRunnable = object : ControlsFadeRunnable() {
        override var hasStarted = false
        private val listener = object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) { hasStarted = true }

            override fun onAnimationCancel(animation: Animator) { hasStarted = false }

            override fun onAnimationEnd(animation: Animator) {
                if (hasStarted)
                    hideControls()
                hasStarted = false
            }
        }

        override fun run() {
            binding.topControls.animate().alpha(0f).setDuration(CONTROLS_FADE_DURATION)
            binding.playerTitleOverlay.animate().alpha(0f).setDuration(CONTROLS_FADE_DURATION)
            binding.controls.animate().alpha(0f).setDuration(CONTROLS_FADE_DURATION).setListener(listener)
        }
    }

    internal val playerToastHideRunnable = Runnable {
        binding.playerToast.animate()
            .alpha(0f)
            .setDuration(PLAYER_TOAST_FADE_OUT_MS)
            .withEndAction { binding.playerToast.visibility = View.GONE }
    }

    internal val stopServiceRunnable = Runnable {
        val intent = Intent(this, BackgroundPlaybackService::class.java)
        applicationContext.stopService(intent)
    }

    internal val clockRunnable = object : Runnable {
        override fun run() {
            updateClockInfo()
            val now = System.currentTimeMillis()
            val delay = CLOCK_TICK_INTERVAL_MS - (now % CLOCK_TICK_INTERVAL_MS)
            clockHandler.postDelayed(this, delay.coerceAtLeast(MIN_CLOCK_TICK_DELAY_MS))
        }
    }

    internal var statsFPS = false
    internal var statsLuaMode = 0

    internal var backgroundPlayMode = ""
    internal var noUIPauseMode = ""

    internal var shouldSavePosition = false

    internal var controlsAtBottom = true
    internal var showMediaTitle = false
    internal var controlsDisplayTimeoutMs = DEFAULT_CONTROLS_DISPLAY_TIMEOUT
    internal var keepControlsVisibleWhilePaused = false
    internal var remoteNextChapterKeyCode: Int? = null
    internal var playerScreenBrightnessActive = false
    internal var rememberPlayerScreenBrightness = false
    internal var playerScreenBrightnessPercent = DEFAULT_PLAYER_SCREEN_BRIGHTNESS_PERCENT
    internal var rememberVideoContrast = false
    internal var videoContrastValue = VIDEO_ADJUSTMENT_DEFAULT_INT
    internal var rememberVideoGamma = false
    internal var videoGammaValue = VIDEO_ADJUSTMENT_DEFAULT_INT
    internal var rememberVideoSaturation = false
    internal var videoSaturationValue = VIDEO_ADJUSTMENT_DEFAULT_INT
    internal var useTimeRemaining = false
    internal var pendingItemTitle: String? = null
    internal var pendingFileName: String? = null
    internal var currentItemTitle: String? = null
    internal var currentVideoTitle: String? = null
    internal var cachedActiveFilterColor: Int? = null

    internal var ignoreAudioFocus = false
    internal var playlistExitWarning = true
    internal var newIntentReplace = false

    internal var persistAudioFilters = false
    internal var persistSubFilters = false
    // subScaleSteps index; default=1.0 at index 3
    internal var subScaleLevel = DEFAULT_SUB_SCALE_INDEX
    // subPosSteps index; default=100% at index 25 (the array spans -25%..125%)
    internal var subPosLevel = DEFAULT_SUB_POSITION_INDEX
    // secondaryPosSteps index; default=0% at index 5
    internal var secondaryPosLevel = DEFAULT_SECONDARY_SUB_POSITION_INDEX
    internal var sessionDecoderMode: String? = null
    internal var autoDecoderFallback = true
    internal var shieldDecoderModeEnabled = true
    internal var shieldDecoderFallback = MPVView.SHIELD_DECODER_FALLBACK_COPY
    internal var preferredDecoderMode = ""
    internal var audioNormUnderrunHintShown = false
    internal var gpuNextRenderFallbackStage = 0
    internal var gpuNextCopyRetryConfirmed = false
    internal var gpuNextCopyRetryDisplayedFrame = false


    internal var playbackHasStarted = false
    // Set true once mpv reports MPV_EVENT_END_FILE for the current file. Some
    // launchers, including Stremio's mpv parser, treat an OK result without
    // position/duration extras as completed playback.
    internal var eofWasReached = false
    internal var onloadCommands = mutableListOf<Array<String>>()
    internal var streamOpenLoading = false
    internal var streamCacheLoading = false
    internal var cachedChapters: List<MPVView.Chapter> = emptyList()
    internal var pendingChapterSeekTime: Double? = null
    internal val clearPendingChapterSeek = Runnable { pendingChapterSeekTime = null }

    // Activity lifetime

    override fun onCreate(icicle: Bundle?) {
        AppearanceTheme.applyPlayer(this)
        super.onCreate(icicle)
        lifecycle.addObserver(lifecycleObserver)

        // Do these here and not in MainActivity because mpv can be launched from a file browser
        Utils.copyAssets(this)
        createBackgroundPlaybackNotificationChannel(this)

        binding = PlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        hideControls()

        initListeners()

        readSettings()
        applyPlayerScreenBrightnessPreference()
        onConfigurationChanged(resources.configuration)
        run {
            // edge-to-edge & immersive mode
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE))
            binding.topPiPBtn.visibility = View.GONE

        // Best-effort cleanup: drop stale resume entries before we add a new
        // one for this session. Runs in O(table size) which is bounded.
        pruneResumeTable()
        // Periodic position save during playback. Both savePosition() and
        // saveResumePosition() are no-ops when there's nothing meaningful to
        // persist, so it's safe to start the timer right away.
        periodicSaveHandler.postDelayed(periodicSaveRunnable, PERIODIC_SAVE_INTERVAL_MS)

        val filepath = parsePathFromIntent(intent)
        currentResumeSource = resumeSourceFromIntent(intent, filepath)
        prepareMediaTitleFromIntent(intent, filepath)
        if (intent.action == Intent.ACTION_VIEW) {
            parseIntentExtras(intent.extras)
        }
        addAutomaticSubtitleOptions(filepath)

        if (filepath == null) {
            Log.e(MPV_ACTIVITY_TAG, "No file given, exiting")
            showToast(getString(R.string.error_no_file))
            finishWithResult(RESULT_CANCELED)
            return
        }

        player.addObserver(mpvEventObserver)
        addMpvLogObserver(mpvLogObserver)
        player.initialize(filesDir.path, cacheDir.path)
        applySavedAudioFilterDefaults()
        applySavedSubFilterDefaults()
        prepareStreamLoading(filepath)
        player.playFile(filepath)

        mediaSession = initMediaSession()
        updateMediaSession()
        BackgroundPlaybackService.mediaToken = mediaSession?.sessionToken

        val manager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager = manager
        val audioSessionId = manager.generateAudioSessionId()
        if (audioSessionId != AudioManager.ERROR)
            player.setAudioSessionId(audioSessionId)
        else
            Log.w(MPV_ACTIVITY_TAG, "AudioManager.generateAudioSessionId() returned error")

        volumeControlStream = STREAM_TYPE
    }


    override fun onDestroy() {
        Log.v(MPV_ACTIVITY_TAG, "Exiting.")

        activityIsForeground = false
        // Stop periodic resume saves; one final save runs from onPause()
        // already so no need to flush again here.
        periodicSaveHandler.removeCallbacks(periodicSaveRunnable)
        eventUiHandler.removeCallbacks(commitSeekbarSeekRunnable)

        if (becomingNoisyReceiverRegistered) {
            unregisterReceiver(becomingNoisyReceiver)
            becomingNoisyReceiverRegistered = false
        }

        BackgroundPlaybackService.mediaToken = null
        mediaSession?.let {
            it.isActive = false
            it.release()
        }
        mediaSession = null

        audioFocusRequest?.let { request ->
            audioManager?.let { manager ->
                AudioManagerCompat.abandonAudioFocusRequest(manager, request)
            }
        }
        audioFocusRequest = null

        stopServiceRunnable.run()

        player.removeObserver(mpvEventObserver)
        removeMpvLogObserver(mpvLogObserver)
        player.destroy()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        Log.v(MPV_ACTIVITY_TAG, "onNewIntent($intent)")
        super.onNewIntent(intent)
        if (intent != null)
            setIntent(intent)
        pendingResumeToastMs = 0L

        val filepath = intent?.let { parsePathFromIntent(it) }
        if (filepath == null) {
            return
        }
        resetPlaybackResultState()
        val nextResumeSource = resumeSourceFromIntent(intent, filepath)
        val willReplaceCurrentFile = activityIsForeground || !didResumeBackgroundPlayback || this.newIntentReplace
        if (willReplaceCurrentFile) {
            currentResumeSource = nextResumeSource
            prepareMediaTitleFromIntent(intent, filepath)
            parseIntentExtras(intent.extras)
        } else {
            onloadCommands.clear()
        }
        addAutomaticSubtitleOptions(filepath)

        if (!activityIsForeground && didResumeBackgroundPlayback) {
            applySavedAudioFilterDefaults()
            applySavedSubFilterDefaults()
            prepareStreamLoading(filepath)
            if (this.newIntentReplace) {
                mpvCommand(arrayOf("loadfile", filepath, "replace"))
                showToast(getString(R.string.notice_file_play))
            } else {
                mpvCommand(arrayOf("loadfile", filepath, "append"))
                showToast(getString(R.string.notice_file_appended))
            }
            moveTaskToBack(true)
        } else {
            applySavedAudioFilterDefaults()
            applySavedSubFilterDefaults()
            prepareStreamLoading(filepath)
            mpvCommand(arrayOf("loadfile", filepath))
        }
    }









    override fun onPause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (isInPictureInPictureMode) {
                Log.v(MPV_ACTIVITY_TAG, "Playback continuing in picture-in-picture")
                super.onPause()
                return
            }
        }

        onPauseImpl()
    }






    internal fun onPauseImpl() {
        val fmt = mpvGetPropertyString("video-format")
        val shouldBackground = shouldBackground()
        if (shouldBackground && !fmt.isNullOrEmpty())
            BackgroundPlaybackService.thumbnail = mpvGrabThumbnail(THUMB_SIZE)
        else
            BackgroundPlaybackService.thumbnail = null
        // media session uses the same thumbnail
        updateMediaSession()

        activityIsForeground = false
        eventUiHandler.removeCallbacksAndMessages(null)
        if (isFinishing) {
            savePosition()
            saveResumePosition()
            // tell mpv to shut down so that any other property changes or such are ignored,
            // preventing useless busywork
            mpvCommand(arrayOf("stop"))
        } else if (!shouldBackground) {
            player.paused = true
        }
        writeSettings()
        super.onPause()

        didResumeBackgroundPlayback = shouldBackground
        if (shouldBackground) {
            Log.v(MPV_ACTIVITY_TAG, "Resuming playback in background")
            stopServiceHandler.removeCallbacks(stopServiceRunnable)
            val serviceIntent = Intent(this, BackgroundPlaybackService::class.java)
            if (!tryStartForegroundService(serviceIntent)) {
                didResumeBackgroundPlayback = false
                player.paused = true
            }
        }
    }

    override fun onResume() {
        // If we never actually left the foreground, don't reinitialize playback state.
        if (activityIsForeground) {
            super.onResume()
            return
        }

        hideControls()
        readSettings()
        applyPlayerScreenBrightnessPreference()

        activityIsForeground = true
        stopServiceHandler.removeCallbacks(stopServiceRunnable)
        stopServiceHandler.postDelayed(stopServiceRunnable, BACKGROUND_SERVICE_STOP_DELAY_MS)

        refreshUi()

        super.onResume()
    }


    // ===== Custom resume table for HTTPS streams =====
    // mpv's built-in watch-later keys positions by URL hash, which breaks for
    // Stremio / Nuvio debrid streams (the auth token in the URL rotates between
    // sessions, so the "same episode" hashes differently every launch and no
    // resume entry ever matches). Fix: pull a stable hex hash plus a normalized
    // filename token out of the path and key resume entries by both. The filename
    // token matters for season packs where every episode shares the same torrent
    // hash.






    /**
     * Persist the current position to our resume table. Called from the
     * 30-second periodic timer and from onPause(). No-op if the URL doesn't
     * contain a stable hex id, or if we're at the very start / very end of
     * the file (treated as "not really started" / "effectively done").
     */

    /**
     * Look up a previously-saved resume position for the current URL.
     * Returns ms to seek to, or null if there's no entry / it's stale /
     * the stored position is within 30s of the duration.
     */

    /**
     * Actively remove saved positions when a video finishes naturally.
     * Called from MPV_EVENT_END_FILE *before* psc.eof() wipes state, so
     * both the custom resume table entry and mpv's watch-later file are
     * cleared. Without this, the periodic 30-second save may have written
     * a near-end position that never gets cleaned up (psc.eof() makes the
     * normal save path return early before reaching the removal code).
     */

    /**
     * Drop resume-table entries older than 30 days, and if we still have
     * more than 500 of them, drop the oldest until we're under the cap.
     * Cheap to run on startup; just a single SharedPreferences scan.
     */



    // This handles both "real" audio focus changes by the callbacks, which aren't
    // really used anymore after Android 12 (except for AUDIOFOCUS_LOSS),
    // as well as actions equivalent to a focus change that we make up ourselves.

    // UI

    /** dpad navigation */
    internal var btnSelected = -1
    internal val dpadControlsScratch = ArrayList<View>(DPAD_CONTROLS_SCRATCH_CAPACITY)
    internal var pendingDpadLongClickView: View? = null
    internal var pendingDpadLongClickRunnable: Runnable? = null
    internal var dpadLongClickPerformed = false

    internal var mightWantToToggleControls = false

    /** true if we're actually outputting any audio (includes the mute state, but not pausing) */
    internal var isPlayingAudio = false

    internal var useAudioUI = false

    internal var clockFormatter: SimpleDateFormat? = null
    internal var clockFormatterIs24: Boolean? = null




    /** Make controls visible, also controls the timeout until they fade. */

    /** Hide controls instantly */

    /** Start fading out the controls */

    /**
     * Toggle visibility of controls (if allowed)
     * @return future visibility state
     */


    override fun dispatchKeyEvent(ev: KeyEvent): Boolean {
        // try built-in event handler first, forward all other events to libmpv
        val handled = interceptDpad(ev) ||
            interceptRemoteNextChapterButton(ev) ||
            (ev.action == KeyEvent.ACTION_DOWN && interceptKeyDown(ev)) ||
            player.onKey(ev)
        return handled || super.dispatchKeyEvent(ev)
    }

    /**
     * Returns views eligible for dpad button navigation
     */







    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)

        binding.controls.updateLayoutParams<MarginLayoutParams> {
            bottomMargin = if (!controlsAtBottom) {
                Utils.convertDp(this@MPVActivity, FLOATING_CONTROLS_BOTTOM_MARGIN_DP)
            } else {
                0
            }
            leftMargin = if (!controlsAtBottom) {
                Utils.convertDp(
                    this@MPVActivity,
                    FLOATING_CONTROLS_SIDE_MARGIN_LANDSCAPE_DP
                )
            } else {
                0
            }
            rightMargin = leftMargin
        }
    }




    // Intent/Uri parsing








    // UI (Part 2)




    /**
     * Build the current subtitle picker row list. Order, from top:
     *   1. "None" (always pinned so deselecting is always one click away)
     *   2. Primary track (with "BOTTOM" badge, only when secondary is also on)
     *   3. Secondary track (with "TOP" badge)
     *   4. Every other track in its natural order
     * Both active tracks get a checkmark so the user can see at a glance which
     * two languages are currently rendering.
     */











    // ========================================================================
    // Audio filter toggles (Voice Boost / DRC / Audio Normalization)
    // DRC mirrors the recovered native dynaudnorm stage as closely as we can in
    // mpv. It is treated as a primary dynamics stage and kept mutually
    // exclusive with Audio Normalization so the UI matches the active filter
    // chain instead of silently suppressing one stage behind the scenes.
    // ========================================================================
    internal var voiceBoostLevel = 0
    internal var volumeBoostDb = 0
    internal var nightModeLevel = 0
    internal var audioNormLevel = 0
    internal var downmixLevel = 0

    internal val voiceBoostFilterLabel = "@voiceboost"
    internal val volumeBoostFilterLabel = "@volumeboost"
    internal val nightModeFilterLabel = "@nightmode"
    internal val audioNormFilterLabel = "@dynaudnorm"
    internal val downmixFilterLabel = "@dialoguedownmix"
    internal val drcAudioStageFilterLabel = "@drcaudio"
    internal val drcFilterBody = "dynaudnorm=f=100:p=1/sqrt(2):m=100:s=12:g=11"

    internal val voiceBoostPresetLabelIds = intArrayOf(
        R.string.filter_value_off,
        R.string.voice_boost_preset_soft,
        R.string.voice_boost_preset_light,
        R.string.voice_boost_preset_clear,
        R.string.voice_boost_preset_speech,
        R.string.voice_boost_preset_loud
    )
    internal val downmixPresetLabelIds = intArrayOf(
        R.string.filter_value_off,
        R.string.dialogue_downmix_preset_soft,
        R.string.dialogue_downmix_preset_strong,
        R.string.dialogue_downmix_preset_tv,
        R.string.dialogue_downmix_preset_focus,
        R.string.dialogue_downmix_preset_anchor
    )
    internal val nightModePresetLabelIds = intArrayOf(
        R.string.filter_value_off,
        R.string.night_mode_preset_drc
    )
    internal val audioNormPresetLabelIds = intArrayOf(
        R.string.filter_value_off,
        R.string.audio_norm_preset_light,
        R.string.audio_norm_preset_smooth,
        R.string.audio_norm_preset_speech,
        R.string.audio_norm_preset_balanced,
        R.string.audio_norm_preset_strong,
        R.string.audio_norm_preset_loudnorm_22
    )

    internal val nightModePresets = listOf(
        "",
        "$nightModeFilterLabel:lavfi=[$drcFilterBody]"
    )
    internal val audioNormPresets = listOf(
        "",
        "$audioNormFilterLabel:lavfi=[" +
            "dynaudnorm=framelen=500:gausssize=9:peak=0.94:maxgain=3.0:coupling=1," +
            "equalizer=f=240:t=q:w=1.0:g=-0.5," +
            "equalizer=f=2600:t=q:w=0.9:g=0.5," +
            "acompressor=threshold=-20dB:ratio=1.35:attack=22:release=280:knee=2.5:" +
            "link=average:detection=rms:makeup=1.02," +
            "alimiter=limit=0.98:attack=2:release=24]",
        "$audioNormFilterLabel:lavfi=[" +
            "dynaudnorm=framelen=460:gausssize=9:peak=0.94:maxgain=4.5:coupling=1," +
            "equalizer=f=235:t=q:w=1.0:g=-0.7," +
            "equalizer=f=2700:t=q:w=0.9:g=0.7," +
            "acompressor=threshold=-21dB:ratio=1.55:attack=20:release=300:knee=2.8:" +
            "link=average:detection=rms:makeup=1.05," +
            "alimiter=limit=0.97:attack=2:release=22]",
        "$audioNormFilterLabel:lavfi=[" +
            "dynaudnorm=framelen=420:gausssize=7:peak=0.93:maxgain=6.0:coupling=1," +
            "equalizer=f=230:t=q:w=1.0:g=-0.9," +
            "equalizer=f=2800:t=q:w=0.9:g=0.9," +
            "acompressor=threshold=-22dB:ratio=1.75:attack=18:release=320:knee=3.0:" +
            "link=average:detection=rms:makeup=1.08," +
            "alimiter=limit=0.96:attack=2:release=20]",
        "$audioNormFilterLabel:lavfi=[" +
            "dynaudnorm=framelen=380:gausssize=7:peak=0.93:maxgain=7.5:coupling=1," +
            "equalizer=f=225:t=q:w=1.0:g=-1.1," +
            "equalizer=f=2900:t=q:w=0.9:g=1.1," +
            "acompressor=threshold=-23dB:ratio=1.95:attack=16:release=340:knee=3.2:" +
            "link=average:detection=rms:makeup=1.10," +
            "alimiter=limit=0.95:attack=2:release=18]",
        "$audioNormFilterLabel:lavfi=[" +
            "dynaudnorm=framelen=340:gausssize=5:peak=0.92:maxgain=9.0:coupling=1," +
            "equalizer=f=220:t=q:w=1.0:g=-1.3," +
            "equalizer=f=3000:t=q:w=0.9:g=1.3," +
            "acompressor=threshold=-24dB:ratio=2.15:attack=14:release=360:knee=3.5:" +
            "link=average:detection=rms:makeup=1.12," +
            "alimiter=limit=0.94:attack=2:release=18]",
        "$audioNormFilterLabel:lavfi=[loudnorm=I=-22:TP=-1.5:LRA=2]"
    )
    internal val voiceBoostPresets = listOf(
        "",
        "$voiceBoostFilterLabel:lavfi=[" +
            "highpass=f=72:p=2," +
            "equalizer=f=180:t=q:w=0.8:g=-0.5," +
            "equalizer=f=360:t=q:w=1.0:g=-0.6," +
            "equalizer=f=1250:t=q:w=1.1:g=0.8," +
            "equalizer=f=2300:t=q:w=0.9:g=1.3," +
            "equalizer=f=3400:t=q:w=0.9:g=1.0," +
            "equalizer=f=6500:t=q:w=1.0:g=-0.2]",
        "$voiceBoostFilterLabel:lavfi=[" +
            "highpass=f=76:p=2," +
            "equalizer=f=180:t=q:w=0.8:g=-0.8," +
            "equalizer=f=360:t=q:w=1.0:g=-0.9," +
            "equalizer=f=1300:t=q:w=1.1:g=1.2," +
            "equalizer=f=2400:t=q:w=0.9:g=1.8," +
            "equalizer=f=3500:t=q:w=0.9:g=1.4," +
            "equalizer=f=6600:t=q:w=1.0:g=-0.3]",
        "$voiceBoostFilterLabel:lavfi=[" +
            "highpass=f=80:p=2," +
            "equalizer=f=175:t=q:w=0.8:g=-1.1," +
            "equalizer=f=360:t=q:w=1.0:g=-1.2," +
            "equalizer=f=1400:t=q:w=1.1:g=1.6," +
            "equalizer=f=2550:t=q:w=0.9:g=2.4," +
            "equalizer=f=3650:t=q:w=0.9:g=1.8," +
            "equalizer=f=6800:t=q:w=1.0:g=-0.4]",
        "$voiceBoostFilterLabel:lavfi=[" +
            "highpass=f=84:p=2," +
            "equalizer=f=170:t=q:w=0.8:g=-1.4," +
            "equalizer=f=360:t=q:w=1.0:g=-1.5," +
            "equalizer=f=1500:t=q:w=1.1:g=2.0," +
            "equalizer=f=2700:t=q:w=0.9:g=3.0," +
            "equalizer=f=3800:t=q:w=0.9:g=2.2," +
            "equalizer=f=7000:t=q:w=1.0:g=-0.5]",
        "$voiceBoostFilterLabel:lavfi=[" +
            "highpass=f=88:p=2," +
            "equalizer=f=165:t=q:w=0.8:g=-1.8," +
            "equalizer=f=360:t=q:w=1.0:g=-1.9," +
            "equalizer=f=1600:t=q:w=1.1:g=2.4," +
            "equalizer=f=2900:t=q:w=0.9:g=3.6," +
            "equalizer=f=4000:t=q:w=0.9:g=2.6," +
            "equalizer=f=7200:t=q:w=1.0:g=-0.6]"
    )
    internal val drcVoiceBoostPresets = listOf(
        "",
        "$voiceBoostFilterLabel:lavfi=[" +
            "highpass=f=68:p=2," +
            "equalizer=f=220:t=q:w=0.9:g=-0.4," +
            "equalizer=f=520:t=q:w=1.0:g=-0.5," +
            "equalizer=f=1050:t=q:w=1.1:g=0.8," +
            "equalizer=f=1550:t=q:w=1.0:g=1.6," +
            "equalizer=f=2400:t=q:w=0.95:g=1.8," +
            "equalizer=f=3600:t=q:w=1.0:g=0.6," +
            "equalizer=f=6200:t=q:w=1.0:g=-0.8]",
        "$voiceBoostFilterLabel:lavfi=[" +
            "highpass=f=70:p=2," +
            "equalizer=f=220:t=q:w=0.9:g=-0.7," +
            "equalizer=f=520:t=q:w=1.0:g=-0.8," +
            "equalizer=f=1100:t=q:w=1.1:g=1.2," +
            "equalizer=f=1650:t=q:w=1.0:g=2.2," +
            "equalizer=f=2500:t=q:w=0.95:g=2.4," +
            "equalizer=f=3600:t=q:w=1.0:g=0.8," +
            "equalizer=f=6400:t=q:w=1.0:g=-1.0]",
        "$voiceBoostFilterLabel:lavfi=[" +
            "highpass=f=72:p=2," +
            "equalizer=f=210:t=q:w=0.9:g=-1.0," +
            "equalizer=f=500:t=q:w=1.0:g=-1.1," +
            "equalizer=f=1150:t=q:w=1.1:g=1.6," +
            "equalizer=f=1750:t=q:w=1.0:g=2.8," +
            "equalizer=f=2600:t=q:w=0.95:g=3.0," +
            "equalizer=f=3650:t=q:w=1.0:g=1.0," +
            "equalizer=f=6600:t=q:w=1.0:g=-1.2]",
        "$voiceBoostFilterLabel:lavfi=[" +
            "highpass=f=74:p=2," +
            "equalizer=f=200:t=q:w=0.9:g=-1.3," +
            "equalizer=f=480:t=q:w=1.0:g=-1.4," +
            "equalizer=f=1200:t=q:w=1.1:g=2.0," +
            "equalizer=f=1850:t=q:w=1.0:g=3.4," +
            "equalizer=f=2750:t=q:w=0.95:g=3.6," +
            "equalizer=f=3700:t=q:w=1.0:g=1.1," +
            "equalizer=f=6800:t=q:w=1.0:g=-1.4]",
        "$voiceBoostFilterLabel:lavfi=[" +
            "highpass=f=76:p=2," +
            "equalizer=f=190:t=q:w=0.9:g=-1.6," +
            "equalizer=f=460:t=q:w=1.0:g=-1.7," +
            "equalizer=f=1250:t=q:w=1.1:g=2.4," +
            "equalizer=f=1950:t=q:w=1.0:g=4.0," +
            "equalizer=f=2900:t=q:w=0.95:g=4.2," +
            "equalizer=f=3800:t=q:w=1.0:g=1.2," +
            "equalizer=f=7000:t=q:w=1.0:g=-1.6]"
    )
    internal val volumeBoostStepsDb = VOLUME_BOOST_STEPS_DB



































    // ===== Subtitle filter presets & state =====

    // Default (1.0x) is at index 3.
    internal val subScaleSteps = SUB_SCALE_STEPS

    // -25..125 range in 5% steps. The on-screen range is 0..100% but we let
    // the user keep clicking past those edges (mpv soft-clamps `sub-pos` to the
    // visible range) so they can dial in extreme values without the buttons
    // bouncing focus on them. Index 5 = 0% (top edge), index 25 = 100% (bottom
    // edge). Same array drives both primary and secondary positions.
    internal val subPosSteps = SUB_POSITION_STEPS
    internal val secondaryPosSteps = subPosSteps


    // ===== Track memory =====
    // When the user manually picks an audio or subtitle track, we stash its
    // language and title in SharedPreferences. On the next file load we look
    // at the new file's track list and try to find a track that "looks like"
    // the same thing — same language, lots of overlapping title tokens. This
    // is what makes binge-watching a series feel right: pick "Signs & Songs"
    // on episode 1 and the same kind of track gets picked on episode 2 even
    // when the track IDs and exact title strings differ between releases.


    /** Read every track of the given type ("sub" or "audio") with title & lang. */

    internal val trackTitleStopwords = setOf(
        "the", "and", "of", "a", "an", "or", "to", "for"
    )


    /** Compare two track titles by Jaccard-like recall over normalized tokens. */

    /** Match languages by their first 2 letters so "en"/"eng"/"english" align. */

    /**
     * Persist the user's manual pick so we can try to re-apply it on the next
     * file. We never persist "None" (mpvId == -1) — that's a one-off action,
     * not a preference.
     */

    /**
     * On file-load, look at the current track list and try to find the best
     * match for the user's last manual pick.
     *
     *  1. **Exact title match** (case-insensitive, after lang filter) wins
     *     instantly — that's the same release group/labeling, no need to score.
     *  2. Otherwise, fall back to token-overlap scoring. We require a
     *     language match (or the saved language to be empty) and at least
     *     50% of the saved title's tokens to overlap with the candidate.
     */

























    internal var pendingActivityResultCallback: ActivityResultCallback? = null
    internal val filePickerResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            pendingActivityResultCallback?.invoke(it.resultCode, it.data)
            pendingActivityResultCallback = null
        }
    internal val documentResultLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            val result = uri?.let { Intent().putExtra("path", it.toString()) }
            pendingActivityResultCallback?.invoke(
                if (uri != null) RESULT_OK else RESULT_CANCELED,
                result
            )
            pendingActivityResultCallback = null
        }
























    /**
     * Reads the chapter list from mpv and pushes tick positions to the seekbar.
     * Also shows/hides [nextChapterBtn] depending on whether chapters exist.
     */

    /**
     * Shows a single-choice dialog listing all chapters; selecting one jumps to it.
     * Long-pressing [nextChapterBtn] triggers this.
     */



    /**
     * Update Picture-in-picture parameters. Will only run if in PiP mode unless
     * `force` is set.
     */


    // Media Session handling

    internal val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPause() {
            player.paused = true
        }
        override fun onPlay() {
            player.paused = false
        }
        override fun onSeekTo(pos: Long) {
            player.timePos = (pos / MPV_MILLIS_PER_SECOND_DOUBLE)
        }
        override fun onSkipToNext() = playlistNext()
        override fun onSkipToPrevious() = playlistPrev()
        override fun onSetRepeatMode(repeatMode: Int) {
            mpvSetPropertyString("loop-playlist",
                if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ALL) "inf" else "no")
            mpvSetPropertyString("loop-file",
                if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ONE) "inf" else "no")
        }
        override fun onSetShuffleMode(shuffleMode: Int) {
            player.changeShuffle(false, shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL)
        }
    }



    // mpv events




















}
