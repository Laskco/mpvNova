package app.mpvnova.player

import app.mpvnova.player.databinding.PlayerBinding
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.util.Log
import android.media.AudioFocusRequest
import android.media.AudioDeviceCallback
import android.media.AudioManager
import android.media.session.MediaSession
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup.MarginLayoutParams
import android.widget.SeekBar
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.updateLayoutParams
import java.text.SimpleDateFormat
import java.util.Locale

typealias ActivityResultCallback = (Int, Intent?) -> Unit
typealias StateRestoreCallback = () -> Unit

@Suppress("TooManyFunctions") // Android lifecycle and input callbacks belong to the Activity.
open class MPVActivity : AppCompatActivity() {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(UiScale.wrap(newBase))
    }

    internal val eventUiHandler = Handler(Looper.getMainLooper())
    internal val fadeHandler = Handler(Looper.getMainLooper())
    internal val stopServiceHandler = Handler(Looper.getMainLooper())
    internal val clockHandler = Handler(Looper.getMainLooper())
    internal val periodicSaveHandler = Handler(Looper.getMainLooper())
    internal val periodicSaveRunnable = object : Runnable {
        override fun run() {
            // Both writes no-op when there's nothing to save.
            savePosition()
            saveResumePosition()
            periodicSaveHandler.postDelayed(this, PERIODIC_SAVE_INTERVAL_MS)
        }
    }
    // 0 = no restore happened. Drives the "Resumed from X:XX" toast.
    internal var pendingResumeToastMs = 0L
    // Start position from intent/resume table — rechecked at FILE_LOADED
    // for near-end positions that slipped through parseIntentExtras.
    internal var pendingStartPositionMs = 0L
    // Don't read Activity.intent for resume saves — onNewIntent can swap
    // files while the Activity stays alive.
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
    internal var firstPlaybackRestartMs = 0L
    internal var fireTvVideoEdgeCropApplied = false
    internal var lastClockInfoTick = Long.MIN_VALUE
    internal var lastDisplayedSpeed = Float.NaN
    @DrawableRes
    internal var lastPlayButtonIconRes = 0

    // Intro/outro skip segments passed by the launching app (e.g. Nuvio), plus handled and
    // rewind-rearmed state so replaying a segment offers the button instead of auto-skipping again.
    internal var skipSegments: List<SkipSegment> = emptyList()
    internal val autoSkippedSegmentKeys = HashSet<String>()
    internal val rewoundSkipSegmentKeys = HashSet<String>()
    internal val dismissedSkipSegmentKeys = HashSet<String>()
    internal val autoHiddenSkipSegmentKeys = HashSet<String>()
    internal var lastSkipSegmentPlaybackPositionSec = Double.NaN
    internal val skipButtonAutoHideRunnable = Runnable { autoHideSkipButton() }

    // Paused-inactivity DVD-style screensaver.
    internal var screensaverTimeoutMs = SCREENSAVER_DEFAULT_TIMEOUT_MS
    internal var screensaverMode = ScreensaverMode.DIM
    internal var screensaverActive = false
    internal var screensaverWaking = false
    internal var dvdX = 0f
    internal var dvdY = 0f
    internal var dvdVx = 0f
    internal var dvdVy = 0f
    internal var screensaverLastFrameNanos = 0L
    internal var screensaverTintIndex = 0
    internal var screensaverTintEnabled = true
    internal val screensaverStartRunnable = Runnable { startScreensaver() }
    internal val screensaverFrameCallback =
        android.view.Choreographer.FrameCallback { frameTimeNanos -> stepScreensaver(frameTimeNanos) }

    // Coalesce ~60/sec time-pos bursts into one UI hop.
    @Volatile internal var timePosUiPending = false
    internal val timePosUiRunnable = Runnable {
        timePosUiPending = false
        if (!activityIsForeground) return@Runnable
        maybeAutoSkipSegments(psc.position / MPV_MILLIS_PER_SECOND_DOUBLE)
        if (binding.controls.visibility != View.VISIBLE) return@Runnable
        if (!userIsOperatingSeekbar && pendingSeekbarSeekMs == null && pendingDpadSeekPreviewMs == null)
            updatePlaybackTimeline(psc.position)
    }

    // TV/leanback mode — system-bar calls are no-ops but hitch the decoder.
    internal var isTvUiMode = false

    // Coalesce metadata bursts at file-load into one UI refresh.
    @Volatile internal var metadataUiPending = false
    internal val metadataUiRunnable = Runnable {
        metadataUiPending = false
        if (!activityIsForeground) return@Runnable
        updateMetadataDisplay()
    }

    // Coalesce MediaSession writes (each one ships a Parcel via IPC).
    @Volatile internal var mediaSessionUpdatePending = false
    internal val mediaSessionUpdateRunnable = Runnable {
        mediaSessionUpdatePending = false
        updateMediaSessionNow()
    }

    // Shield Hi10p decoder swap: pause, wait for playback-restart, exact-seek to realign.
    internal var pendingShieldFallbackResync = false
    internal var shieldFallbackResumeAfter = false
    internal val shieldFallbackResyncRunnable = Runnable {
        if (!activityIsForeground && !didResumeBackgroundPlayback) return@Runnable
        val pos = mpvGetPropertyDouble("time-pos/full") ?: return@Runnable
        Log.v(MPV_ACTIVITY_TAG, "shield fallback: realigning A/V at $pos")
        mpvCommand(arrayOf("seek", pos.toString(), "absolute+exact"))
        if (shieldFallbackResumeAfter) {
            shieldFallbackResumeAfter = false
            mpvSetPropertyBoolean("pause", false)
        }
    }

    internal var audioManager: AudioManager? = null
    internal var audioFocusRequest: AudioFocusRequest? = null
    internal var bluetoothAudioDelayRouteCallback: AudioDeviceCallback? = null
    internal var savedAudioDelayMs = 0L
    internal var savedSubDelayMs = 0L
    internal var savedSecondarySubDelayMs = 0L
    internal var bluetoothAudioDelayMs = 0L
    internal var appliedBluetoothAudioDelayMs: Long? = null
    internal val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener {
        onAudioFocusChange(it, "callback")
    }
    internal var audioFocusRestore: () -> Unit = {}

    internal val psc = Utils.PlaybackStateCache()
    internal var mediaSession: MediaSession? = null

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
            lastAppliedSeekMs = Long.MIN_VALUE
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

        // Accelerate-out so overlay doesn't linger at half-opacity.
        private val accelerate = androidx.interpolator.view.animation.FastOutLinearInInterpolator()

        override fun run() {
            // Only animate the compact bar. Creating hardware layers for every
            // overlay, including the full-width scrim, competes with video output.
            binding.topControls.visibility = View.GONE
            binding.playerTitleOverlay.visibility = View.GONE
            binding.controlsScrim.visibility = View.GONE
            if (!shouldShowClockWhileControlsHidden()) binding.timeInfoPanel.visibility = View.GONE
            binding.statsTextView.visibility = View.GONE
            binding.controls.animate()
                .alpha(0f)
                .setDuration(CONTROLS_FADE_DURATION)
                .setInterpolator(accelerate)
                .setListener(listener)
        }
    }

    internal val playerToastHideRunnable = Runnable {
        val hidingState = playerToastState
        binding.playerToast.animate()
            .alpha(0f)
            .setDuration(PLAYER_TOAST_FADE_OUT_MS)
            .withLayer()
            .withEndAction {
                binding.playerToast.visibility = View.GONE
                if (playerToastState === hidingState && overlayToastView == null) {
                    playerToastState = null
                }
            }
    }

    internal val seekOverlayHideRunnable = Runnable {
        binding.seekOverlay.animate()
            .alpha(0f)
            .setDuration(CONTROLS_FADE_DURATION)
            .withLayer()
            .withEndAction { binding.seekOverlay.visibility = View.GONE }
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
    internal var activeStatsPage = 0

    internal var backgroundPlayMode = ""
    internal var noUIPauseMode = ""

    internal var shouldSavePosition = false
    internal var skipSegmentsMode = SkipSegmentsMode.AUTO
    internal var skipButtonDisplayMode = SkipButtonDisplayMode.SEGMENT
    // The segment the Skip button is currently prompting for (null = button hidden).
    internal var currentSkipButtonSegment: SkipSegment? = null
    // D-pad seek: single-press step, "fast (keyframe) seek", and whether to defer seek keys to mpv.
    internal var seekStepMs = SEEK_DEFAULT_DPAD_STEP_MS
    internal var fastSeekEnabled = false
    internal var fastSeekRestoreValue: String? = null
    internal var seekKeysUseInputConf = false
    internal var preferExternalForwardedSubtitles = false

    internal var controlsAtBottom = true
    internal var playerUiCustomization = PlayerUiCustomization.DEFAULT
    internal var subtitleControlsOffsetPercent = 0
    internal var showMediaTitle = false
    internal var showClockOverlay = true
    internal var showClockDate = false
    internal var showClockOnPause = false
    internal var force24HourClock = false
    internal var controlsDisplayTimeoutMs = DEFAULT_CONTROLS_DISPLAY_TIMEOUT
    internal var keepControlsVisibleWhilePaused = false
    internal var backHidesControlsFirst = false
    internal var exitWithDoubleBack = true
    internal var lastBackPressMs = 0L
    internal var dpadUpJumpsToTopControls = false
    internal var hideControlsWhileSeeking = false
    internal var minimalSeekbarWhileSeeking = false
    // Drawer state: remembered tab, reopen-after-subdialog flag, cached binding.
    internal var lastDrawerTab: DrawerTab = DrawerTab.VIDEO
    internal var drawerReopenPending = false
    internal var drawerReopenScheduled = false
    internal var drawerBinding: app.mpvnova.player.databinding.DialogPlayerDrawerBinding? = null
    internal var playerPickerBinding: app.mpvnova.player.databinding.DialogPlayerPickerBinding? = null
    internal var speedPickerDialog: SpeedPickerDialog? = null
    internal var subDelayDialog: SubDelayPanelDialog? = null
    internal var audioDelayDialog: AudioDelayDialog? = null
    internal var audioPickerDialog: MediaPickerDialog? = null
    internal var subtitlePickerDialog: MediaPickerDialog? = null
    internal var decoderPickerDialog: MediaPickerDialog? = null
    internal var preferredDecoderPickerDialog: MediaPickerDialog? = null
    internal var subtitleStyleDialog: SubtitleStyleDialog? = null
    internal var trackPanelChildTransition = false
    internal var subtitleStyleReturnAction: (() -> Unit)? = null
    internal var subtitleStyleNavigationPending = false
    internal var playlistDialog: PlaylistDialog? = null
    internal val videoAdjustmentDialogs = mutableMapOf<String, VideoAdjustmentDialog>()
    internal var playerBrightnessDialog: VideoAdjustmentDialog? = null
    internal var drawerHandlersBound = false
    internal var currentDrawerDialog: androidx.appcompat.app.AlertDialog? = null
    // The frontmost player dialog (set when shown). Toasts host inside its window so
    // they render above the panel instead of behind it.
    internal var topPlayerDialog: android.app.Dialog? = null
    internal val playerDialogStack = mutableListOf<android.app.Dialog>()
    internal var playerChromeSnapshot: PlayerChromeSnapshot? = null
    internal var overlayToastView: android.view.View? = null
    internal var overlayToastHideRunnable: Runnable? = null
    internal var playerToastState: PlayerToastState? = null
    internal var playerToastToken = 0
    internal var remoteNextChapterKeyCode: Int? = null
    internal var playerScreenBrightnessActive = false
    internal var rememberPlayerScreenBrightness = false
    internal var playerScreenBrightnessPercent = DEFAULT_PLAYER_SCREEN_BRIGHTNESS_PERCENT
    internal var rememberVideoBrightness = false
    internal var videoBrightnessValue = VIDEO_ADJUSTMENT_DEFAULT_INT
    internal var rememberVideoContrast = false
    internal var videoContrastValue = VIDEO_ADJUSTMENT_DEFAULT_INT
    internal var rememberVideoGamma = false
    internal var videoGammaValue = VIDEO_ADJUSTMENT_DEFAULT_INT
    internal var rememberVideoSaturation = false
    internal var videoSaturationValue = VIDEO_ADJUSTMENT_DEFAULT_INT
    internal var rememberVideoHue = false
    internal var videoHueValue = VIDEO_ADJUSTMENT_DEFAULT_INT
    internal var videoFilterPresetId = VideoFilterPreset.NONE.prefValue
    internal var useTimeRemaining = false
    internal var pendingPlayerTitleSource: String? = null
    internal var pendingItemTitle: String? = null
    internal var pendingFileName: String? = null
    internal var currentPlayerTitleSource: String? = null
    internal var currentItemTitle: String? = null
    internal var currentFileName: String? = null
    internal var currentVideoTitle: String? = null
    internal var playerTitleStyle = PlayerTitleStyle.DEFAULT
    internal var appliedPlayerTitleStyle: PlayerTitleStyle? = null
    internal var playerTextStylePreviewActive = false
    internal var fittedPlayerTitleText: String? = null
    internal var fittedPlayerTitleWidth = 0
    internal var fittedPlayerTitleFontScale = 0f
    internal var fittedPlayerTitlePreferredSizeSp = 0f
    internal var cachedActiveFilterColor: Int? = null

    internal var ignoreAudioFocus = false
    internal var playlistExitWarning = true
    internal var newIntentReplace = false

    internal var persistAudioFilters = false
    internal var persistSubFilters = false
    // subScaleSteps index; default=1.0 at index 10
    internal var subScaleLevel = DEFAULT_SUB_SCALE_INDEX
    // subPosSteps index; default=100% at index 125 (the array spans -25%..125%)
    internal var subPosLevel = DEFAULT_SUB_POSITION_INDEX
    // secondaryPosSteps index; default=0% at index 25
    internal var secondaryPosLevel = DEFAULT_SECONDARY_SUB_POSITION_INDEX
    // The custom sub look is always saved, but only applied while the toggle is on.
    internal var customSubStyleEnabled = false
    internal var subStyleTextColorIndex = subtitleColorOptionIndex(SUBTITLE_TEXT_COLOR_DEFAULT_ID)
    internal var subStyleTextOpacityIndex = nearestOpacityIndex(DEFAULT_SUBTITLE_TEXT_OPACITY_PERCENT)
    internal var subStyleBorderColorIndex = subtitleColorOptionIndex(SUBTITLE_BORDER_COLOR_DEFAULT_ID)
    internal var subStyleBorderSizeIndex = DEFAULT_SUBTITLE_BORDER_INDEX
    internal var subStyleBlurIndex = DEFAULT_SUBTITLE_BLUR_INDEX
    internal var subStyleShadowSizeIndex = DEFAULT_SUBTITLE_SHADOW_SIZE_INDEX
    internal var subStyleShadowColorIndex = subtitleColorOptionIndex(SUBTITLE_SHADOW_COLOR_DEFAULT_ID)
    internal var subStyleSpacingIndex = DEFAULT_SUBTITLE_SPACING_INDEX
    internal var subStyleJustify = DEFAULT_SUBTITLE_JUSTIFY
    internal var subStyleBgColorIndex = subtitleColorOptionIndex(SUBTITLE_BG_COLOR_DEFAULT_ID)
    internal var subStyleBgOpacityIndex = nearestOpacityIndex(DEFAULT_SUBTITLE_BG_OPACITY_PERCENT)
    internal var subStyleEdge = DEFAULT_SUBTITLE_EDGE_STYLE
    internal var subStyleFontFamily = SUBTITLE_FONT_DEFAULT_FAMILY
    internal var subStyleBold = false
    internal var subStyleItalic = false
    internal var subStyleGrayImageSubs = false
    // Forces our style onto ASS subs too; off so their signs and typesetting survive.
    internal var subStyleOverrideAss = false
    // Strip: removes the script's own styling so our style hits every line, even
    // releases that use named styles instead of "Default" (loses signs/typesetting).
    internal var subStyleForceAllAss = false
    // Selective: updates ASS style definitions while retaining inline tags and positioning.
    // Mutually exclusive with the two override modes above.
    internal var subStyleSelectiveAss = false
    // Last style-override signature sent to libass. Style overrides are parsed when a subtitle
    // track loads, so changed values require one sub-reload before they become visible.
    internal var subStyleAppliedAssOverrides: String? = null
    // Quick-cycle index into the saved presets, and the preset currently being edited.
    internal var subStylePresetIndex = 0
    internal var editingSubtitleStylePreset: SubtitleStylePreset? = null
    // The sub-* values from before we touched them, so disabling restores the real baseline.
    internal var subStyleSavedDefaults: Map<String, String?>? = null
    // Depth-counted so stacked dialogs don't leave keep-open stuck on (which froze the file at EOF).
    internal var keepOpenDialogDepth = 0
    internal var keepOpenSavedValue: String? = null
    internal var sessionDecoderMode: String? = null
    internal var autoDecoderFallback = true
    internal var shieldDecoderModeEnabled = true
    internal var shieldDecoderFallback = MPVView.SHIELD_DECODER_FALLBACK_COPY
    internal var shieldMpeg2SoftwareFallbackEnabled = true
    internal var preferredDecoderMode = ""
    // Autopause: pause while controls overlay is visible. Shield variant
    // defaults on (Hi10p SW can't share CPU with the UI).
    // controlsOverlayAutoPaused = we paused (vs user) → safe to auto-resume.
    internal var autoPauseControlsOverlayEnabled = false
    internal var autoPauseShieldHi10pEnabled = true
    internal var controlsOverlayAutoPaused = false
    internal var audioNormUnderrunHintShown = false
    internal var gpuNextRenderFallbackStage = 0
    internal var gpuNextCopyRetryConfirmed = false
    internal var gpuNextCopyRetryDisplayedFrame = false
    internal var shieldHi10pPreloadApplied = false
    // Sustained-error window for gpu-next — a single transient libplacebo
    // log line must not trip the renderer fallback (that rebuilds the VO
    // mid-playback and desyncs A/V/subs).
    internal var gpuNextErrorWindowStartMs = 0L
    internal var gpuNextErrorWindowCount = 0


    internal var playbackHasStarted = false
    // External-player result state. MPV_EVENT_END_FILE can also mean an early
    // stream failure, so completion is only true when position is near duration.
    internal var playbackCompletionReached = false
    internal var resultPositionMs = -1L
    internal var resultDurationMs = 0L
    // Armed before a replacing loadfile from a new external intent: the outgoing file's
    // END_FILE (reason STOP) must not be mistaken for a real playback end and bounce the
    // caller out. Consumed on that END_FILE; cleared on the new file's START_FILE.
    internal var suppressEndFileFinishForReplace = false
    internal var onloadCommands = mutableListOf<Array<String>>()
    internal var streamOpenLoading = false
    internal var streamCacheLoading = false
    internal var cachedChapters: List<MPVView.Chapter> = emptyList()
    internal var pendingChapterSeekTime: Double? = null
    internal val clearPendingChapterSeek = Runnable { pendingChapterSeekTime = null }

    // Activity lifetime

    // Translucent player variants (e.g. TranslucentMPVActivity for Stremio) override this so
    // the launching app stays paused, not stopped. applyPlayer re-applies the color theme, so
    // the translucent attrs are merged on top of it, before the window is created.
    protected open val useTranslucentPlayerWindow: Boolean get() = false

    override fun onCreate(icicle: Bundle?) {
        AppearanceTheme.applyPlayer(this)
        if (useTranslucentPlayerWindow)
            setTheme(R.style.PlayerTranslucentOverlay)
        super.onCreate(icicle)
        suppressPlayerActivityTransition()
        lifecycle.addObserver(lifecycleObserver)

        // Launched directly from a file browser → re-run MainActivity's one-time setup.
        Utils.copyAssets(this)
        createBackgroundPlaybackNotificationChannel(this)

        setupRootView()
        initListeners()
        readSettings()
        applyPlayerUiCustomization()
        applyPlayerScreenBrightnessPreference()
        onConfigurationChanged(resources.configuration)
        setupImmersiveWindow()

        // Drop stale resume entries before adding ours.
        pruneResumeTable()
        // Both saves no-op when there's nothing to persist — safe to arm immediately.
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
        startPlayerForFile(filepath)
    }


    override fun onDestroy() {
        Log.v(MPV_ACTIVITY_TAG, "Exiting.")
        activityIsForeground = false
        player.releaseAllMpvKeys()
        cancelAllScheduledWork()
        setNoisyReceiverRegistered(false)
        releaseMediaAndAudioFocus()
        stopServiceRunnable.run()
        player.removeObserver(mpvEventObserver)
        removeMpvLogObserver(mpvLogObserver)
        player.destroy()
        super.onDestroy()
    }

    override fun finish() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            runOnUiThread { finish() }
            return
        }
        super.finish()
        suppressPlayerActivityTransition()
    }

    override fun onNewIntent(intent: Intent) {
        Log.v(MPV_ACTIVITY_TAG, "onNewIntent($intent)")
        super.onNewIntent(intent)
        setIntent(intent)
        pendingResumeToastMs = 0L

        val filepath = parsePathFromIntent(intent)
        if (filepath == null) {
            return
        }
        resetPlaybackResultState()
        val nextResumeSource = resumeSourceFromIntent(intent, filepath)
        val willReplaceCurrentFile = activityIsForeground || !didResumeBackgroundPlayback || this.newIntentReplace
        if (willReplaceCurrentFile) {
            applyNewIntentReplacement(intent, filepath, nextResumeSource)
        } else {
            onloadCommands.clear()
        }
        addAutomaticSubtitleOptions(filepath)

        if (!activityIsForeground && didResumeBackgroundPlayback) {
            applySavedAudioFilterDefaults()
            applySavedSubFilterDefaults()
            applySavedDelayDefaults()
            prepareStreamLoading(filepath)
            if (this.newIntentReplace) {
                prepareDecoderForFileLoad(filepath)
                suppressEndFileFinishForReplace = true
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
            applySavedDelayDefaults()
            prepareStreamLoading(filepath)
            prepareDecoderForFileLoad(filepath)
            suppressEndFileFinishForReplace = true
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
        player.releaseAllMpvKeys()
        commitPendingSeekbarSeek()
        cancelScreensaver()
        val shouldBackground = shouldBackground()
        if (shouldBackground)
            BackgroundPlaybackService.grabThumbnail()
        else
            BackgroundPlaybackService.thumbnail = null
        // Flush synchronously — handler queue gets purged below.
        updateMediaSessionNow()

        activityIsForeground = false
        eventUiHandler.removeCallbacksAndMessages(null)
        timePosUiPending = false
        metadataUiPending = false
        mediaSessionUpdatePending = false
        if (isFinishing) {
            savePosition()
            saveResumePosition()
            // Shut mpv down so further property changes are ignored.
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
        // Never left foreground → skip reinit.
        if (activityIsForeground) {
            super.onResume()
            return
        }

        hideControls()
        readSettings()
        applyPlayerScreenBrightnessPreference()

        activityIsForeground = true
        scheduleScreensaver()
        stopServiceHandler.removeCallbacks(stopServiceRunnable)
        stopServiceHandler.postDelayed(stopServiceRunnable, BACKGROUND_SERVICE_STOP_DELAY_MS)

        refreshUi()

        super.onResume()
    }

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
    internal var clockDateFormatter: SimpleDateFormat? = null
    internal var clockDateFormatterLocale: Locale? = null

    override fun dispatchKeyEvent(ev: KeyEvent): Boolean {
        // A key-down may have reached mpv before a dialog, overlay, or controls transition
        // changed who handles input. Always deliver its matching release first.
        val handled = if (player.releaseMpvKey(ev)) {
            true
        } else if (consumeScreensaverKey(ev)) {
            // The screensaver eats the first key and only wakes the UI.
            true
        } else {
            noteScreensaverActivity()
            when {
                // Skip button (when shown) gets first crack: OK skips, other keys dismiss it.
                handleSkipButtonKey(ev) -> true
                // Built-in handlers first; forward the rest to libmpv.
                else -> interceptDpad(ev) ||
                    interceptRemoteNextChapterButton(ev) ||
                    (ev.action == KeyEvent.ACTION_DOWN && interceptKeyDown(ev)) ||
                    player.onKey(ev) ||
                    super.dispatchKeyEvent(ev)
            }
        }
        return handled
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        if (!hasFocus) {
            player.releaseAllMpvKeys()
            commitPendingSeekbarSeek()
        }
        super.onWindowFocusChanged(hasFocus)
    }

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
        applyPlayerPanelLayout()
        scheduleSubtitleControlsPositionUpdate()
        updateSkipButtonPlacement()
    }

    // Audio filter levels. DRC is mutually exclusive with audio-norm so the
    // UI matches the active chain. Preset arrays live in AudioFilterPresets.kt.
    internal var voiceBoostLevel = 0
    internal var volumeBoostDb = 0
    internal var nightModeLevel = 0
    internal var audioNormLevel = 0
    internal var downmixLevel = 0
    internal var centerBoostLevel = 0
    internal var lastAppliedAudioFilterChain: String? = null
    internal var lastAppliedDecoderDrcScale: String? = null
    internal var audioFiltersAwaitingPostLoadReconcile = false

    // Subtitle filter state. subPosSteps spans -25..125% in 5% steps so the
    // user can click past edges without focus bouncing (mpv soft-clamps).
    internal val subScaleSteps = SUB_SCALE_STEPS
    internal val subPosSteps = SUB_POSITION_STEPS
    internal val secondaryPosSteps = subPosSteps

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
    internal val mediaSessionCallback = object : MediaSession.Callback() {
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
    }
}
