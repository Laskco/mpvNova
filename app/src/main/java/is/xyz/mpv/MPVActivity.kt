package app.mpvnova.player

import app.mpvnova.player.databinding.PlayerBinding
import app.mpvnova.player.MPVLib.MpvEvent
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
import android.util.Log
import android.media.AudioManager
import android.net.Uri
import android.os.*
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.DisplayMetrics
import android.util.Rational
import androidx.core.content.ContextCompat
import android.view.*
import android.view.ViewGroup.MarginLayoutParams
import android.widget.Button
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
import java.lang.IllegalArgumentException
import kotlin.math.roundToInt

typealias ActivityResultCallback = (Int, Intent?) -> Unit
typealias StateRestoreCallback = () -> Unit

class MPVActivity : AppCompatActivity(), MPVLib.EventObserver, MPVLib.LogObserver, TouchGesturesObserver {
    // for calls to eventUi() and eventPropertyUi()
    private val eventUiHandler = Handler(Looper.getMainLooper())
    // for use with fadeRunnable1..3
    private val fadeHandler = Handler(Looper.getMainLooper())
    // for use with stopServiceRunnable
    private val stopServiceHandler = Handler(Looper.getMainLooper())
    // for use with clock updates while controls are visible
    private val clockHandler = Handler(Looper.getMainLooper())

    /**
     * DO NOT USE THIS
     */
    private var activityIsStopped = false

    private var activityIsForeground = true
    private var didResumeBackgroundPlayback = false
    private var userIsOperatingSeekbar = false
    private var lastDisplayedPlaybackSecond = Int.MIN_VALUE

    private var audioManager: AudioManager? = null
    private var audioFocusRequest: AudioFocusRequestCompat? = null
    private var audioFocusRestore: () -> Unit = {}

    private val psc = Utils.PlaybackStateCache()
    private var mediaSession: MediaSessionCompat? = null

    private lateinit var binding: PlayerBinding
    private lateinit var gestures: TouchGestures

    // convenience alias
    private val player get() = binding.player

    private val seekBarChangeListener = object : SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
            if (!fromUser)
                return
            val positionMs = millisFromSeekbarProgress(progress)
            player.timePos = positionMs / 1000.0
            updatePlaybackTimeline(positionMs, forceTextUpdate = true)
        }

        override fun onStartTrackingTouch(seekBar: SeekBar) {
            userIsOperatingSeekbar = true
        }

        override fun onStopTrackingTouch(seekBar: SeekBar) {
            userIsOperatingSeekbar = false
            showControls() // re-trigger display timeout
        }
    }

    private var becomingNoisyReceiverRegistered = false
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS, "noisy")
            }
        }
    }

    // Fade out controls
    private val fadeRunnable = object : Runnable {
        var hasStarted = false
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
            binding.controlsScrim.animate().alpha(0f).setDuration(CONTROLS_FADE_DURATION)
            binding.controls.animate().alpha(0f).setDuration(CONTROLS_FADE_DURATION).setListener(listener)
        }
    }

    // Fade out unlock button
    private val fadeRunnable2 = object : Runnable {
        private val listener = object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator) {
                binding.unlockBtn.visibility = View.GONE
            }
        }

        override fun run() {
            binding.unlockBtn.animate().alpha(0f).setDuration(CONTROLS_FADE_DURATION).setListener(listener)
        }
    }

    // Fade out gesture text
    private val fadeRunnable3 = object : Runnable {
        // okay this doesn't actually fade...
        override fun run() {
            binding.gestureTextView.visibility = View.GONE
        }
    }

    private val playerToastHideRunnable = Runnable {
        binding.playerToast.animate()
            .alpha(0f)
            .setDuration(180L)
            .withEndAction { binding.playerToast.visibility = View.GONE }
    }

    private val stopServiceRunnable = Runnable {
        val intent = Intent(this, BackgroundPlaybackService::class.java)
        applicationContext.stopService(intent)
    }

    private val clockRunnable = object : Runnable {
        override fun run() {
            updateClockInfo()
            val now = System.currentTimeMillis()
            val delay = 30_000L - (now % 30_000L)
            clockHandler.postDelayed(this, delay.coerceAtLeast(1_000L))
        }
    }

    /* Settings */
    private var statsFPS = false
    private var statsLuaMode = 0 // ==0 disabled, >0 page number

    private var backgroundPlayMode = ""
    private var noUIPauseMode = ""

    private var shouldSavePosition = false

    private var autoRotationMode = ""

    private var controlsAtBottom = true
    private var showMediaTitle = false
    private var useTimeRemaining = false

    private var ignoreAudioFocus = false
    private var playlistExitWarning = true
    private var newIntentReplace = false

    private var smoothSeekGesture = false

    private var persistAudioFilters = false
    private var sessionDecoderMode: String? = null
    private var autoDecoderFallback = true
    private var preferredDecoderMode = ""
    private var audioNormUnderrunHintShown = false
    private var gpuNextRenderFallbackStage = 0
    private var gpuNextCopyRetryConfirmed = false
    private var gpuNextCopyRetryDisplayedFrame = false
    /* * */

    @SuppressLint("ClickableViewAccessibility")
    private fun initListeners() {
        with (binding) {
            prevBtn.setOnClickListener { playlistPrev() }
            nextBtn.setOnClickListener { playlistNext() }
            // Short click opens the TV-styled picker dialog; long click falls
            // back to the legacy "cycle to next" behavior.
            cycleAudioBtn.setOnClickListener { pickAudio() }
            cycleSubsBtn.setOnClickListener { pickSub() }
            playBtn.setOnClickListener { player.cyclePause() }
            cycleDecoderBtn.setOnClickListener { pickDecoder() }
            statsToggleBtn.setOnClickListener { toggleStatsOverlay() }
            cycleSpeedBtn.setOnClickListener { cycleSpeed() }
            voiceBoostBtn.setOnClickListener { adjustVoiceBoost(1, wrap = true) }
            volumeBoostBtn.setOnClickListener { adjustVolumeBoost(1, wrap = true) }
            nightModeBtn.setOnClickListener { adjustNightMode(1, wrap = true) }
            audioNormBtn.setOnClickListener { adjustAudioNorm(1, wrap = true) }
            nextChapterBtn.setOnClickListener { MPVLib.command(arrayOf("add", "chapter", "1")) }
            topLockBtn.setOnClickListener { lockUI() }
            topPiPBtn.setOnClickListener { goIntoPiP() }
            topMenuBtn.setOnClickListener { openTopMenu() }
            unlockBtn.setOnClickListener { unlockUI() }
            playbackDurationTxt.setOnClickListener {
                useTimeRemaining = !useTimeRemaining
                updatePlaybackText(psc.positionSec, force = true)
                updatePlaybackDuration(psc.duration)
            }

            cycleAudioBtn.setOnLongClickListener { cycleAudio(); true }
            cycleSpeedBtn.setOnLongClickListener { pickSpeed(); true }
            cycleSubsBtn.setOnLongClickListener { cycleSub(); true }
            prevBtn.setOnLongClickListener { openPlaylistMenu(pauseForDialog()); true }
            nextBtn.setOnLongClickListener { openPlaylistMenu(pauseForDialog()); true }
            cycleDecoderBtn.setOnLongClickListener { cycleDecoderMode(); true }
            statsToggleBtn.setOnLongClickListener {
                MPVLib.command(arrayOf("script-binding", "stats/display-page-1"))
                true
            }
            voiceBoostBtn.setOnLongClickListener { adjustVoiceBoost(-1, wrap = true); true }
            volumeBoostBtn.setOnLongClickListener { adjustVolumeBoost(-1, wrap = true); true }
            nightModeBtn.setOnLongClickListener { adjustNightMode(-1, wrap = true); true }
            audioNormBtn.setOnLongClickListener { adjustAudioNorm(-1, wrap = true); true }
            nextChapterBtn.setOnLongClickListener { showChapterPickerDialog(); true }

            playbackSeekbar.setOnSeekBarChangeListener(seekBarChangeListener)
            playbackSeekbar.keyProgressIncrement = seekbarProgressFromMillis(SEEK_BAR_DPAD_STEP_MS)
        }

        player.setOnTouchListener { _, e ->
            if (lockedUI) false else gestures.onTouchEvent(e)
        }

        ViewCompat.setOnApplyWindowInsetsListener(binding.outside) { _, windowInsets ->
            // guidance: https://medium.com/androiddevelopers/gesture-navigation-handling-visual-overlaps-4aed565c134c
            val insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val insets2 = windowInsets.getInsets(WindowInsetsCompat.Type.displayCutout())
            binding.outside.updateLayoutParams<MarginLayoutParams> {
                // avoid system bars and cutout
                leftMargin = Math.max(insets.left, insets2.left)
                topMargin = Math.max(insets.top, insets2.top)
                bottomMargin = Math.max(insets.bottom, insets2.bottom)
                rightMargin = Math.max(insets.right, insets2.right)
            }
            WindowInsetsCompat.CONSUMED
        }

        onBackPressedDispatcher.addCallback(this) {
            onBackPressedImpl()
        }

        addOnPictureInPictureModeChangedListener { info ->
            onPiPModeChangedImpl(info.isInPictureInPictureMode)
        }
    }

    private var playbackHasStarted = false
    private var onloadCommands = mutableListOf<Array<String>>()

    // Activity lifetime

    override fun onCreate(icicle: Bundle?) {
        super.onCreate(icicle)

        // Do these here and not in MainActivity because mpv can be launched from a file browser
        Utils.copyAssets(this)
        BackgroundPlaybackService.createNotificationChannel(this)

        binding = PlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Init controls to be hidden and view fullscreen
        hideControls()

        // Initialize listeners for the player view
        initListeners()

        gestures = TouchGestures(this)

        // set up initial UI state
        readSettings()
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
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN))
            binding.topLockBtn.visibility = View.GONE

        if (showMediaTitle)
            binding.controlsTitleGroup.visibility = View.VISIBLE

        updateOrientation(true)

        // Parse the intent
        val filepath = parsePathFromIntent(intent)
        if (intent.action == Intent.ACTION_VIEW) {
            parseIntentExtras(intent.extras)
        }

        if (filepath == null) {
            Log.e(TAG, "No file given, exiting")
            showToast(getString(R.string.error_no_file))
            finishWithResult(RESULT_CANCELED)
            return
        }

        player.addObserver(this)
        MPVLib.addLogObserver(this)
        player.initialize(filesDir.path, cacheDir.path)
        applySavedAudioFilterDefaults()
        player.playFile(filepath)

        mediaSession = initMediaSession()
        updateMediaSession()
        BackgroundPlaybackService.mediaToken = mediaSession?.sessionToken

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val audioSessionId = audioManager!!.generateAudioSessionId()
        if (audioSessionId != AudioManager.ERROR)
            player.setAudioSessionId(audioSessionId)
        else
            Log.w(TAG, "AudioManager.generateAudioSessionId() returned error")

        volumeControlStream = STREAM_TYPE
    }

    private fun finishWithResult(code: Int, includeTimePos: Boolean = false) {
        // FIXME: should track end-file events to accurately report OK vs CANCELED
        if (isFinishing) // only count first call
            return
        val result = Intent(RESULT_INTENT)
        result.data = if (intent.data?.scheme == "file") null else intent.data
        if (includeTimePos) {
            result.putExtra("position", psc.position.toInt())
            result.putExtra("duration", psc.duration.toInt())
        }
        setResult(code, result)
        finish()
    }

    override fun onDestroy() {
        Log.v(TAG, "Exiting.")

        // Suppress any further callbacks
        activityIsForeground = false

        BackgroundPlaybackService.mediaToken = null
        mediaSession?.let {
            it.isActive = false
            it.release()
        }
        mediaSession = null

        audioFocusRequest?.let {
            AudioManagerCompat.abandonAudioFocusRequest(audioManager!!, it)
        }
        audioFocusRequest = null

        // take the background service with us
        stopServiceRunnable.run()

        player.removeObserver(this)
        MPVLib.removeLogObserver(this)
        player.destroy()
        super.onDestroy()
    }

    override fun onNewIntent(intent: Intent?) {
        Log.v(TAG, "onNewIntent($intent)")
        super.onNewIntent(intent)

        // Happens when mpv is still running (not necessarily playing) and the user selects a new
        // file to be played from another app
        val filepath = intent?.let { parsePathFromIntent(it) }
        if (filepath == null) {
            return
        }

        if (!activityIsForeground && didResumeBackgroundPlayback) {
            applySavedAudioFilterDefaults()
            if (this.newIntentReplace) {
                MPVLib.command(arrayOf("loadfile", filepath, "replace"))
                showToast(getString(R.string.notice_file_play))
            } else {
                MPVLib.command(arrayOf("loadfile", filepath, "append"))
                showToast(getString(R.string.notice_file_appended))
            }
            moveTaskToBack(true)
        } else {
            applySavedAudioFilterDefaults()
            MPVLib.command(arrayOf("loadfile", filepath))
        }
    }

    private fun updateAudioPresence() {
        val haveAudio = MPVLib.getPropertyBoolean("current-tracks/audio/selected")
        if (haveAudio == null) {
            // If we *don't know* if there's an active audio track then don't update to avoid
            // spurious UI changes. The property will become available again later.
            return
        }
        isPlayingAudio = (haveAudio && MPVLib.getPropertyBoolean("mute") != true)
    }

    private fun isPlayingAudioOnly(): Boolean {
        if (!isPlayingAudio)
            return false
        val image = MPVLib.getPropertyString("current-tracks/video/image")
        return image.isNullOrEmpty() || image == "yes"
    }

    private fun shouldBackground(): Boolean {
        if (isFinishing) // about to exit?
            return false
        return when (backgroundPlayMode) {
            "always" -> true
            "audio-only" -> isPlayingAudioOnly()
            else -> false // "never"
        }
    }

    override fun onPause() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (isInMultiWindowMode || isInPictureInPictureMode) {
                Log.v(TAG, "Going into multi-window mode")
                super.onPause()
                return
            }
        }

        onPauseImpl()
    }

    private fun tryStartForegroundService(intent: Intent): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                ContextCompat.startForegroundService(this, intent)
            } catch (e: ForegroundServiceStartNotAllowedException) {
                Log.w(TAG, e)
                return false
            }
        } else {
            ContextCompat.startForegroundService(this, intent)
        }
        return true
    }

    private fun onPauseImpl() {
        val fmt = MPVLib.getPropertyString("video-format")
        val shouldBackground = shouldBackground()
        if (shouldBackground && !fmt.isNullOrEmpty())
            BackgroundPlaybackService.thumbnail = MPVLib.grabThumbnail(THUMB_SIZE)
        else
            BackgroundPlaybackService.thumbnail = null
        // media session uses the same thumbnail
        updateMediaSession()

        activityIsForeground = false
        eventUiHandler.removeCallbacksAndMessages(null)
        if (isFinishing) {
            savePosition()
            // tell mpv to shut down so that any other property changes or such are ignored,
            // preventing useless busywork
            MPVLib.command(arrayOf("stop"))
        } else if (!shouldBackground) {
            player.paused = true
        }
        writeSettings()
        super.onPause()

        didResumeBackgroundPlayback = shouldBackground
        if (shouldBackground) {
            Log.v(TAG, "Resuming playback in background")
            stopServiceHandler.removeCallbacks(stopServiceRunnable)
            val serviceIntent = Intent(this, BackgroundPlaybackService::class.java)
            if (!tryStartForegroundService(serviceIntent)) {
                didResumeBackgroundPlayback = false
                player.paused = true
            }
        }
    }

    private fun readSettings() {
        // FIXME: settings should be in their own class completely
        val prefs = getDefaultSharedPreferences(applicationContext)
        val getString: (String, Int) -> String = { key, defaultRes ->
            prefs.getString(key, resources.getString(defaultRes))!!
        }

        gestures.syncSettings(prefs, resources)

        val statsMode = prefs.getString("stats_mode", "") ?: ""
        this.statsFPS = statsMode == "native_fps"
        this.statsLuaMode = if (statsMode.startsWith("lua"))
            statsMode.removePrefix("lua").toInt()
        else
            0
        this.backgroundPlayMode = getString("background_play", R.string.pref_background_play_default)
        this.noUIPauseMode = getString("no_ui_pause", R.string.pref_no_ui_pause_default)
        this.shouldSavePosition = prefs.getBoolean("save_position", false)
        if (this.autoRotationMode != "manual") // don't reset
            this.autoRotationMode = getString("auto_rotation", R.string.pref_auto_rotation_default)
        this.controlsAtBottom = prefs.getBoolean("bottom_controls", true)
        this.showMediaTitle = prefs.getBoolean("display_media_title", false)
        this.useTimeRemaining = prefs.getBoolean("use_time_remaining", false)
        this.ignoreAudioFocus = prefs.getBoolean("ignore_audio_focus", false)
        this.playlistExitWarning = prefs.getBoolean("playlist_exit_warning", true)
        this.newIntentReplace = prefs.getBoolean("new_intent_replace", false)
        this.smoothSeekGesture = prefs.getBoolean("seek_gesture_smooth", false)
        this.persistAudioFilters = prefs.getBoolean("persist_audio_filters", false)
        this.voiceBoostLevel = if (persistAudioFilters) {
            when {
                prefs.contains("voice_boost_level") -> prefs.getInt("voice_boost_level", 0)
                prefs.getBoolean("voice_boost_on", false) -> 2
                else -> 0
            }
        } else 0
        this.volumeBoostDb = if (persistAudioFilters) prefs.getInt("volume_boost_db", 0) else 0
        this.nightModeLevel = if (persistAudioFilters) {
            when {
                prefs.contains("night_mode_level") -> prefs.getInt("night_mode_level", 0)
                prefs.getBoolean("night_mode_on", false) -> 2
                else -> 0
            }
        } else 0
        this.audioNormLevel = if (persistAudioFilters) {
            when {
                prefs.contains("audio_norm_level") -> prefs.getInt("audio_norm_level", 0)
                prefs.getBoolean("audio_norm_on", false) -> 2
                else -> 0
            }
        } else 0
        this.downmixLevel = if (persistAudioFilters) {
            when {
                prefs.contains("downmix_level") -> prefs.getInt("downmix_level", 0)
                prefs.getBoolean("downmix_on", false) -> 1
                else -> 0
            }
        } else 0
        this.autoDecoderFallback = prefs.getBoolean("decoder_auto_fallback", true)
        this.preferredDecoderMode = prefs.getString("preferred_decoder_mode", "") ?: ""
        clampAudioFilterState()
    }

    private fun writeSettings() {
        val prefs = getDefaultSharedPreferences(applicationContext)

        with (prefs.edit()) {
            putBoolean("use_time_remaining", useTimeRemaining)
            putBoolean("persist_audio_filters", persistAudioFilters)
            putBoolean("voice_boost_on", if (persistAudioFilters) isVoiceBoostOn() else false)
            putInt("voice_boost_level", if (persistAudioFilters) voiceBoostLevel else 0)
            putInt("volume_boost_db", if (persistAudioFilters) volumeBoostDb else 0)
            putBoolean("night_mode_on", if (persistAudioFilters) isNightModeOn() else false)
            putInt("night_mode_level", if (persistAudioFilters) nightModeLevel else 0)
            putBoolean("audio_norm_on", if (persistAudioFilters) isAudioNormOn() else false)
            putInt("audio_norm_level", if (persistAudioFilters) audioNormLevel else 0)
            putBoolean("downmix_on", if (persistAudioFilters) isDownmixOn() else false)
            putInt("downmix_level", if (persistAudioFilters) downmixLevel else 0)
            commit()
        }
    }

    private fun clampAudioFilterState() {
        voiceBoostLevel = voiceBoostLevel.coerceIn(0, voiceBoostPresets.lastIndex)
        nightModeLevel = nightModeLevel.coerceIn(0, nightModePresets.lastIndex)
        audioNormLevel = audioNormLevel.coerceIn(0, audioNormPresets.lastIndex)
        downmixLevel = downmixLevel.coerceIn(0, downmixPresetLabelIds.lastIndex)
        val volumeIndex = volumeBoostStepsDb.indexOf(volumeBoostDb)
        volumeBoostDb = if (volumeIndex >= 0) {
            volumeBoostDb
        } else {
            volumeBoostStepsDb.first()
        }
    }

    override fun onStart() {
        super.onStart()
        activityIsStopped = false
    }

    override fun onStop() {
        super.onStop()
        activityIsStopped = true
    }

    override fun onResume() {
        // If we weren't actually in the background (e.g. multi window mode), don't reinitialize stuff
        if (activityIsForeground) {
            super.onResume()
            return
        }

        if (lockedUI) { // precaution
            Log.w(TAG, "resumed with locked UI, unlocking")
            unlockUI()
        }

        // Init controls to be hidden and view fullscreen
        hideControls()
        readSettings()

        activityIsForeground = true
        // stop background service with a delay
        stopServiceHandler.removeCallbacks(stopServiceRunnable)
        stopServiceHandler.postDelayed(stopServiceRunnable, 1000L)

        refreshUi()

        super.onResume()
    }

    private fun savePosition() {
        if (!shouldSavePosition)
            return
        if (MPVLib.getPropertyBoolean("eof-reached") ?: true) {
            Log.d(TAG, "player indicates EOF, not saving watch-later config")
            return
        }
        MPVLib.command(arrayOf("write-watch-later-config"))
    }

    /**
     * Requests or abandons audio focus and noisy receiver depending on the playback state.
     * @warning Call from event thread, not UI thread
     */
    private fun handleAudioFocus() {
        if ((psc.pause && !psc.cachePause) || !isPlayingAudio) {
            if (becomingNoisyReceiverRegistered)
                unregisterReceiver(becomingNoisyReceiver)
            becomingNoisyReceiverRegistered = false
            // TODO: could abandon audio focus after a timeout
        } else {
            if (!becomingNoisyReceiverRegistered)
                registerReceiver(
                    becomingNoisyReceiver,
                    IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
                )
            becomingNoisyReceiverRegistered = true
            // (re-)request audio focus
            // Note that this will actually request focus every time the user unpauses, refer to discussion in #1066
            if (requestAudioFocus()) {
                onAudioFocusChange(AudioManager.AUDIOFOCUS_GAIN, "request")
            } else {
                onAudioFocusChange(AudioManager.AUDIOFOCUS_LOSS, "request")
            }
        }
    }

    private fun requestAudioFocus(): Boolean {
        val manager = audioManager ?: return false
        val req = audioFocusRequest ?:
            with(AudioFocusRequestCompat.Builder(AudioManagerCompat.AUDIOFOCUS_GAIN)) {
            setAudioAttributes(with(AudioAttributesCompat.Builder()) {
                // N.B.: libmpv may use different values in ao_audiotrack, but here we always pretend to be music.
                setUsage(AudioAttributesCompat.USAGE_MEDIA)
                setContentType(AudioAttributesCompat.CONTENT_TYPE_MUSIC)
                build()
            })
            setOnAudioFocusChangeListener {
                onAudioFocusChange(it, "callback")
            }
            build()
        }
        val res = AudioManagerCompat.requestAudioFocus(manager, req)
        if (res == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioFocusRequest = req
            return true
        }
        return false
    }

    // This handles both "real" audio focus changes by the callbacks, which aren't
    // really used anymore after Android 12 (except for AUDIOFOCUS_LOSS),
    // as well as actions equivalent to a focus change that we make up ourselves.
    private fun onAudioFocusChange(type: Int, source: String) {
        Log.v(TAG, "Audio focus changed: $type ($source)")
        if (ignoreAudioFocus || isFinishing)
            return
        when (type) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // loss can occur in addition to ducking, so remember the old callback
                val oldRestore = audioFocusRestore
                val wasPlayerPaused = player.paused ?: false
                player.paused = true
                audioFocusRestore = {
                    oldRestore()
                    if (!wasPlayerPaused) player.paused = false
                }
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                MPVLib.command(arrayOf("multiply", "volume", AUDIO_FOCUS_DUCKING.toString()))
                audioFocusRestore = {
                    val inv = 1f / AUDIO_FOCUS_DUCKING
                    MPVLib.command(arrayOf("multiply", "volume", inv.toString()))
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                audioFocusRestore()
                audioFocusRestore = {}
            }
        }
    }

    // UI

    /** dpad navigation */
    private var btnSelected = -1

    private var mightWantToToggleControls = false

    /** true if we're actually outputting any audio (includes the mute state, but not pausing) */
    private var isPlayingAudio = false

    private var useAudioUI = false

    private var lockedUI = false

    private fun keepPlaybackForDialog(): StateRestoreCallback {
        val oldValue = MPVLib.getPropertyString("keep-open")
        MPVLib.setPropertyBoolean("keep-open", true)
        return {
            oldValue?.also { MPVLib.setPropertyString("keep-open", it) }
        }
    }

    private fun pauseForDialog(): StateRestoreCallback {
        val useKeepOpen = when (noUIPauseMode) {
            "always" -> true
            "audio-only" -> isPlayingAudioOnly()
            else -> false // "never"
        }
        if (useKeepOpen) {
            // don't pause but set keep-open so mpv doesn't exit while the user is doing stuff
            return keepPlaybackForDialog()
        }

        // Pause playback during UI dialogs
        val wasPlayerPaused = player.paused ?: true
        player.paused = true
        return {
            if (!wasPlayerPaused)
                player.paused = false
        }
    }

    private fun updateStats() {
        if (!statsFPS)
            return
        binding.statsTextView.text = getString(R.string.ui_fps, player.estimatedVfFps)
    }

    private fun updateClockInfo() {
        val is24Hour = android.text.format.DateFormat.is24HourFormat(this)
        val pattern = if (is24Hour) "HH:mm" else "hh:mm a"
        val formatter = SimpleDateFormat(pattern, Locale.getDefault())
        val now = System.currentTimeMillis()
        binding.clockTextView.text = formatter.format(Date(now))

        val remainingSeconds = (psc.durationSec - psc.positionSec).coerceAtLeast(0)
        if (psc.durationSec > 0 && remainingSeconds > 0) {
            val endTimeMillis = now + (remainingSeconds * 1000L)
            binding.endsAtTextView.visibility = View.VISIBLE
            binding.endsAtTextView.text = getString(
                R.string.player_ends_at,
                formatter.format(Date(endTimeMillis))
            )
        } else {
            binding.endsAtTextView.visibility = View.GONE
        }
    }

    private fun controlsShouldBeVisible(): Boolean {
        if (lockedUI)
            return false
        return userIsOperatingSeekbar
    }

    /** Make controls visible, also controls the timeout until they fade. */
    private fun showControls() {
        if (lockedUI) {
            Log.w(TAG, "cannot show UI in locked mode")
            return
        }

        // remove all callbacks that were to be run for fading
        fadeHandler.removeCallbacks(fadeRunnable)
        binding.controls.animate().cancel()
        binding.topControls.animate().cancel()
        binding.controlsScrim.animate().cancel()

        // reset controls alpha to be visible
        binding.controls.alpha = 1f
        binding.topControls.alpha = 1f
        binding.controlsScrim.alpha = 1f

        if (binding.controls.visibility != View.VISIBLE) {
            binding.controls.visibility = View.VISIBLE
            binding.topControls.visibility = View.VISIBLE
            binding.controlsScrim.visibility = View.VISIBLE
            binding.timeInfoPanel.visibility = View.VISIBLE

            if (this.statsFPS) {
                updateStats()
                binding.statsTextView.visibility = View.VISIBLE
            }

            val insetsController = WindowCompat.getInsetsController(window, window.decorView)
            insetsController.show(WindowInsetsCompat.Type.navigationBars())
        }

        updateClockInfo()
        clockHandler.removeCallbacks(clockRunnable)
        clockHandler.post(clockRunnable)

        if (btnSelected != -1) {
            binding.controls.post {
                if (btnSelected != -1 && binding.controls.visibility == View.VISIBLE) {
                    updateSelectedDpadButton()
                }
            }
        }

        // add a new callback to hide the controls once again
        if (!controlsShouldBeVisible())
            fadeHandler.postDelayed(fadeRunnable, CONTROLS_DISPLAY_TIMEOUT)
    }

    /** Hide controls instantly */
    fun hideControls() {
        if (controlsShouldBeVisible())
            return
        if (btnSelected != -1) {
            btnSelected = -1
            updateSelectedDpadButton()
        }
        binding.playbackSeekbar.clearFocus()
        // use GONE here instead of INVISIBLE (which makes more sense) because of Android bug with surface views
        // see http://stackoverflow.com/a/12655713/2606891
        binding.controls.visibility = View.GONE
        binding.topControls.visibility = View.GONE
        binding.controlsScrim.visibility = View.GONE
        binding.timeInfoPanel.visibility = View.GONE
        binding.statsTextView.visibility = View.GONE
        clockHandler.removeCallbacks(clockRunnable)

        val insetsController = WindowCompat.getInsetsController(window, window.decorView)
        insetsController.hide(WindowInsetsCompat.Type.systemBars())
    }

    /** Start fading out the controls */
    private fun hideControlsFade() {
        fadeHandler.removeCallbacks(fadeRunnable)
        fadeHandler.post(fadeRunnable)
    }

    /**
     * Toggle visibility of controls (if allowed)
     * @return future visibility state
     */
    private fun toggleControls(): Boolean {
        if (lockedUI)
            return false
        if (controlsShouldBeVisible())
            return true
        return if (binding.controls.visibility == View.VISIBLE && !fadeRunnable.hasStarted) {
            hideControlsFade()
            false
        } else {
            showControls()
            true
        }
    }

    private fun showUnlockControls() {
        fadeHandler.removeCallbacks(fadeRunnable2)
        binding.unlockBtn.animate().setListener(null).cancel()

        binding.unlockBtn.alpha = 1f
        binding.unlockBtn.visibility = View.VISIBLE

        fadeHandler.postDelayed(fadeRunnable2, CONTROLS_DISPLAY_TIMEOUT)
    }

    override fun dispatchKeyEvent(ev: KeyEvent): Boolean {
        if (lockedUI) {
            showUnlockControls()
            return super.dispatchKeyEvent(ev)
        }

        // try built-in event handler first, forward all other events to libmpv
        val handled = interceptDpad(ev) ||
                (ev.action == KeyEvent.ACTION_DOWN && interceptKeyDown(ev)) ||
                player.onKey(ev)
        if (handled) {
            return true
        }
        return super.dispatchKeyEvent(ev)
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent?): Boolean {
        if (lockedUI)
            return super.dispatchGenericMotionEvent(ev)

        if (ev != null && ev.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) {
            if (ev.actionMasked == MotionEvent.ACTION_SCROLL) {
                val horizontal = ev.getAxisValue(MotionEvent.AXIS_HSCROLL)
                val vertical = ev.getAxisValue(MotionEvent.AXIS_VSCROLL)
                val dominant = if (kotlin.math.abs(horizontal) > kotlin.math.abs(vertical)) {
                    horizontal
                } else {
                    vertical
                }
                if (dominant != 0f) {
                    showControls()
                    btnSelected = 0
                    updateSelectedDpadButton()
                    binding.playbackSeekbar.requestFocus()
                    val notchCount = kotlin.math.max(1, kotlin.math.abs(dominant).roundToInt())
                    val direction = if (dominant < 0f) 1 else -1
                    seekPlaybackFromDpad(direction * notchCount * 10_000L)
                    return true
                }
            }
            if (player.onPointerEvent(ev))
                return true
            // keep controls visible when mouse moves
            if (ev.actionMasked == MotionEvent.ACTION_HOVER_MOVE)
                showControls()
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (lockedUI) {
            if (ev.action == MotionEvent.ACTION_UP || ev.action == MotionEvent.ACTION_DOWN)
                showUnlockControls()
            return super.dispatchTouchEvent(ev)
        }

        if (super.dispatchTouchEvent(ev)) {
            // reset delay if the event has been handled
            // ideally we'd want to know if the event was delivered to controls, but we can't
            if (binding.controls.visibility == View.VISIBLE && !fadeRunnable.hasStarted)
                showControls()
            if (ev.action == MotionEvent.ACTION_UP)
                return true
        }
        if (ev.action == MotionEvent.ACTION_DOWN)
            mightWantToToggleControls = true
        if (ev.action == MotionEvent.ACTION_UP && mightWantToToggleControls) {
            toggleControls()
        }
        return true
    }

    /**
     * Returns views eligible for dpad button navigation
     */
    private fun dpadButtons(): List<View> {
        if (binding.controls.visibility != View.VISIBLE || binding.topControls.visibility != View.VISIBLE) {
            return emptyList()
        }
        val views = mutableListOf<View>()
        if (binding.playbackSeekbar.isEnabled) {
            views += binding.playbackSeekbar
        }
        val groups = arrayOf(binding.controlsButtonGroup, binding.topControls)
        for (g in groups) {
            for (i in 0 until g.childCount) {
                val view = g.getChildAt(i)
                if (view.isEnabled && view.isVisible && view.isFocusable) {
                    views += view
                }
            }
        }
        return views
    }

    private fun firstControlButtonIndex(controls: List<View>): Int {
        val firstNonSeekbar = controls.indexOfFirst { it !== binding.playbackSeekbar }
        return if (firstNonSeekbar >= 0) firstNonSeekbar else 0
    }

    private fun firstControlButtonView(): View? {
        val groups = arrayOf(binding.controlsButtonGroup, binding.topControls)
        for (group in groups) {
            for (i in 0 until group.childCount) {
                val child = group.getChildAt(i)
                if (child.isEnabled && child.isVisible && child.isFocusable) {
                    return child
                }
            }
        }
        return null
    }

    private fun interceptDpad(ev: KeyEvent): Boolean {
        var controls = dpadButtons()

        if (btnSelected == -1 && controls.isEmpty()) {
            when (ev.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (ev.action == KeyEvent.ACTION_DOWN) {
                        showControls()
                        controls = dpadButtons()
                        if (controls.isEmpty())
                            return false
                        btnSelected = if (ev.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                            firstControlButtonIndex(controls)
                        } else {
                            0
                        }
                        updateSelectedDpadButton()
                        if (ev.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                            firstControlButtonView()?.requestFocus()
                        }
                        binding.controls.post {
                            if (btnSelected != -1 && binding.controls.visibility == View.VISIBLE) {
                                updateSelectedDpadButton()
                                if (ev.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                                    firstControlButtonView()?.requestFocus()
                                }
                            }
                        }
                    }
                    return true
                }
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (ev.action == KeyEvent.ACTION_DOWN) {
                        showControls()
                        btnSelected = 0
                        updateSelectedDpadButton()
                        binding.playbackSeekbar.requestFocus()
                        seekPlaybackFromDpad(seekDeltaFromDpadEvent(ev))
                    }
                    return true
                }
            }
        }

        if (controls.isEmpty())
            return false

        if (btnSelected == -1) { // UP and DOWN are always grabbed and overridden
            when (ev.keyCode) {
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (ev.action == KeyEvent.ACTION_DOWN) { // activate dpad navigation
                        btnSelected = if (ev.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                            firstControlButtonIndex(controls)
                        } else {
                            0
                        }
                        updateSelectedDpadButton()
                        if (ev.keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                            firstControlButtonView()?.requestFocus()
                        }
                        showControls()
                    }
                    return true
                }
            }
            return false
        }

        val selectedView = controls.getOrNull(btnSelected)
        val seekbarSelected = selectedView === binding.playbackSeekbar

        // this runs when dpad navigation is active:
        when (ev.keyCode) {
            KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN -> {
                if (ev.action == KeyEvent.ACTION_DOWN) { // deactivate dpad navigation
                    when {
                        ev.keyCode == KeyEvent.KEYCODE_DPAD_UP && !seekbarSelected -> {
                            btnSelected = 0
                            updateSelectedDpadButton()
                            showControls()
                        }
                        ev.keyCode == KeyEvent.KEYCODE_DPAD_DOWN && seekbarSelected && controls.size > 1 -> {
                            btnSelected = 1
                            updateSelectedDpadButton()
                            showControls()
                        }
                        else -> {
                            btnSelected = -1
                            updateSelectedDpadButton()
                            hideControlsFade()
                        }
                    }
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                if (ev.action == KeyEvent.ACTION_DOWN) {
                    if (seekbarSelected) {
                        seekPlaybackFromDpad(seekDeltaFromDpadEvent(ev))
                    } else {
                        btnSelected = (btnSelected + 1) % controls.count()
                        updateSelectedDpadButton()
                    }
                    showControls()
                }
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                if (ev.action == KeyEvent.ACTION_DOWN) {
                    if (seekbarSelected) {
                        seekPlaybackFromDpad(seekDeltaFromDpadEvent(ev))
                    } else {
                        val count = controls.count()
                        btnSelected = (count + btnSelected - 1) % count
                        updateSelectedDpadButton()
                    }
                    showControls()
                }
                return true
            }
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                if (seekbarSelected)
                    return false
                if (ev.action == KeyEvent.ACTION_UP) {
                    val view = controls.getOrNull(btnSelected)
                    // 500ms appears to be the standard
                    if (ev.eventTime - ev.downTime > 500L)
                        view?.performLongClick()
                    else
                        view?.performClick()
                    showControls()
                }
                return true
            }
        }
        return false
    }

    private fun updateSelectedDpadButton() {
        val controls = dpadButtons()
        controls.forEachIndexed { i, child ->
            val selected = i == btnSelected
            child.isSelected = selected
            if (child is ChapterSeekBar) {
                child.setDpadSelected(selected)
            }
            if (!selected && child.isFocused) {
                child.clearFocus()
            }
        }
        controls.getOrNull(btnSelected)?.let { selectedChild ->
            if (selectedChild !== binding.playbackSeekbar && binding.playbackSeekbar.isFocused) {
                binding.playbackSeekbar.clearFocus()
            }
            if (selectedChild.isFocusable) {
                selectedChild.requestFocus()
            }
        }
    }

    private fun interceptKeyDown(event: KeyEvent): Boolean {
        // intercept some keys to provide functionality native to
        // mpvNova even if libmpv already implements these
        var unhandled = 0

        when (event.unicodeChar.toChar()) {
            // (overrides a default binding)
            'j' -> cycleSub()
            '#' -> cycleAudio()

            else -> unhandled++
        }
        // Note: dpad center is bound according to how Android TV apps should generally behave,
        // see <https://developer.android.com/docs/quality-guidelines/tv-app-quality>.
        // Due to implementation inconsistencies enter and numpad enter need to perform the same
        // function (issue #963).
        when (event.keyCode) {
            // (no default binding)
            KeyEvent.KEYCODE_CAPTIONS -> cycleSub()
            KeyEvent.KEYCODE_MEDIA_AUDIO_TRACK -> cycleAudio()
            KeyEvent.KEYCODE_INFO -> toggleControls()
            KeyEvent.KEYCODE_MENU -> openTopMenu()
            KeyEvent.KEYCODE_GUIDE -> openTopMenu()
            KeyEvent.KEYCODE_NUMPAD_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> player.cyclePause()

            // (overrides a default binding)
            KeyEvent.KEYCODE_ENTER -> player.cyclePause()

            else -> unhandled++
        }

        return unhandled < 2
    }

    private fun onBackPressedImpl() {
        if (lockedUI)
            return showUnlockControls()

        val notYetPlayed = psc.playlistCount - psc.playlistPos - 1
        if (notYetPlayed <= 0 || !playlistExitWarning) {
            finishWithResult(RESULT_OK, true)
            return
        }

        val restore = pauseForDialog()
        with (AlertDialog.Builder(this)) {
            setMessage(getString(R.string.exit_warning_playlist, notYetPlayed))
            setPositiveButton(R.string.dialog_yes) { dialog, _ ->
                dialog.dismiss()
                finishWithResult(RESULT_OK, true)
            }
            setNegativeButton(R.string.dialog_no) { dialog, _ ->
                dialog.dismiss()
                restore()
            }
            create().show()
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val wm = windowManager.currentWindowMetrics
            gestures.setMetrics(wm.bounds.width().toFloat(), wm.bounds.height().toFloat())
        } else @Suppress("DEPRECATION") {
            val dm = DisplayMetrics()
            windowManager.defaultDisplay.getRealMetrics(dm)
            gestures.setMetrics(dm.widthPixels.toFloat(), dm.heightPixels.toFloat())
        }

        // Adjust control margins
        binding.controls.updateLayoutParams<MarginLayoutParams> {
            bottomMargin = if (!controlsAtBottom) {
                Utils.convertDp(this@MPVActivity, 60f)
            } else {
                0
            }
            leftMargin = if (!controlsAtBottom) {
                Utils.convertDp(this@MPVActivity, if (isLandscape) 60f else 24f)
            } else {
                0
            }
            rightMargin = leftMargin
        }
    }

    private fun onPiPModeChangedImpl(state: Boolean) {
        Log.v(TAG, "onPiPModeChanged($state)")
        if (state) {
            lockedUI = true
            hideControls()
            return
        }

        unlockUI()
        // For whatever stupid reason Android provides no good detection for when PiP is exited
        // so we have to do this shit <https://stackoverflow.com/questions/43174507/#answer-56127742>
        // If we don't exit the activity here it will stick around and not be retrievable from the
        // recents screen, or react to onNewIntent().
        if (activityIsStopped) {
            // Note: On Android 12 or older there's another bug with this: the result will not
            // be delivered to the calling activity and is instead instantly returned the next
            // time, which makes it looks like the file picker is broken.
            finishWithResult(RESULT_OK, true)
        }
    }

    private fun playlistPrev() = MPVLib.command(arrayOf("playlist-prev"))
    private fun playlistNext() = MPVLib.command(arrayOf("playlist-next"))

    private fun showToast(msg: String, cancel: Boolean = false, durationMs: Long = 1800L) {
        showToastInternal(null, msg, cancel, durationMs)
    }

    private fun showToast(
        title: String,
        detail: String,
        cancel: Boolean = true,
        durationMs: Long = 1800L
    ) {
        showToastInternal(title, detail, cancel, durationMs)
    }

    private fun showToastInternal(
        title: String?,
        detail: String,
        cancel: Boolean,
        durationMs: Long
    ) {
        fadeHandler.removeCallbacks(playerToastHideRunnable)
        binding.playerToast.animate().cancel()
        if (cancel) {
            binding.playerToast.alpha = 1f
        }

        binding.playerToastTitle.isVisible = !title.isNullOrBlank()
        binding.playerToastTitle.text = title
        binding.playerToastMessage.text = detail
        binding.playerToast.visibility = View.VISIBLE

        if (binding.playerToast.alpha < 1f) {
            binding.playerToast.alpha = 0f
            binding.playerToast.animate().alpha(1f).setDuration(140L)
        } else {
            binding.playerToast.alpha = 1f
        }

        fadeHandler.postDelayed(playerToastHideRunnable, durationMs)
    }

    // Intent/Uri parsing

    private fun parsePathFromIntent(intent: Intent): String? {
        fun safeResolveUri(u: Uri?): String? {
            return if (u != null && u.isHierarchical && !u.isRelative)
                resolveUri(u)
            else null
        }

        return when (intent.action) {
            Intent.ACTION_VIEW -> {
                // Normal file open or URL view
                intent.data?.let { resolveUri(it) }
            }

            Intent.ACTION_SEND -> {
                // Handle single shared file or text link
                var parsed = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (parsed == null) {
                    parsed = intent.getStringExtra(Intent.EXTRA_TEXT)?.let {
                        Uri.parse(it.trim())
                    }
                }

                safeResolveUri(parsed)
            }

            Intent.ACTION_SEND_MULTIPLE -> {
                // Multiple shared files
                val uris = IntentCompat.getParcelableArrayListExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                if (!uris.isNullOrEmpty()) {
                    val paths = uris.mapNotNull { uri ->
                        safeResolveUri(uri)
                    }
                    if (paths.size == 1) {
                        return paths[0]
                    } else if (!paths.isEmpty()) {
                        // Use a memory playlist
                        val memoryUri = "memory://#EXTM3U\n${paths.joinToString("\n")}\n"
                        Log.v(TAG, "Created memory playlist URI (${paths.size})")
                        return memoryUri
                    }
                }
                return null
            }

            else -> {
                // Custom intent from MainScreenFragment
                intent.getStringExtra("filepath")
            }
        }
    }

    private fun resolveUri(data: Uri): String? {
        val filepath = when (data.scheme) {
            "file" -> data.path
            "content" -> translateContentUri(data)
            // mpv supports data URIs but needs data:// to pass it through correctly
            "data" -> "data://${data.schemeSpecificPart}"
            "http", "https", "rtmp", "rtmps", "rtp", "rtsp", "mms", "mmst", "mmsh",
            "tcp", "udp", "lavf", "ftp"
            -> data.toString()
            else -> null
        }

        if (filepath == null)
            Log.e(TAG, "unknown scheme: ${data.scheme}")
        return filepath
    }

    private fun translateContentUri(uri: Uri): String {
        val resolver = applicationContext.contentResolver
        Log.v(TAG, "Resolving content URI: $uri")
        try {
            resolver.openFileDescriptor(uri, "r")?.use { pfd ->
                // See if we can skip the indirection and read the real file directly
                val path = Utils.findRealPath(pfd.fd)
                if (path != null) {
                    Log.v(TAG, "Found real file path: $path")
                    return path
                }
            }
        } catch(e: Exception) {
            Log.e(TAG, "Failed to open content fd: $e")
        }

        // Otherwise, just let mpv open the content URI directly via ffmpeg
        return uri.toString()
    }

    private fun parseIntentExtras(extras: Bundle?) {
        onloadCommands.clear()
        if (extras == null)
            return

        fun pushOption(key: String, value: String) {
            onloadCommands.add(arrayOf("set", "file-local-options/${key}", value))
        }

        // Note: these only apply to the first file, it's not clear what the semantics for a
        // playlist should be.

        if (extras.getByte("decode_mode") == 2.toByte())
            pushOption("hwdec", "no")
        if (extras.containsKey("subs")) {
            val subList = Utils.getParcelableArray<Uri>(extras, "subs")
            val subsToEnable = Utils.getParcelableArray<Uri>(extras, "subs.enable")

            for (suburi in subList) {
                val subfile = resolveUri(suburi) ?: continue
                val flag = if (subsToEnable.any { it == suburi }) "select" else "auto"

                Log.v(TAG, "Adding subtitles from intent extras: $subfile")
                onloadCommands.add(arrayOf("sub-add", subfile, flag))
            }
        }
        extras.getInt("position", 0).let {
            if (it > 0)
                pushOption("start", "${it / 1000f}")
        }
        extras.getString("title", "").let {
            if (!it.isNullOrEmpty())
                pushOption("force-media-title", it)
        }
        // TODO: `headers` would be good, maybe `tls_verify`
    }

    // UI (Part 2)

    data class TrackData(val trackId: Int, val trackType: String)
    private fun trackSwitchNotification(f: () -> TrackData) {
        val (track_id, track_type) = f()
        val trackPrefix = when (track_type) {
            "audio" -> getString(R.string.track_audio)
            "sub"   -> getString(R.string.track_subs)
            "video" -> "Video"
            else    -> "???"
        }

        val detail = if (track_id == -1) {
            getString(R.string.track_off)
        } else {
            player.tracks[track_type]?.firstOrNull{ it.mpvId == track_id }?.name ?: "???"
        }
        showToast(trackPrefix, detail, true)
    }

    private fun cycleAudio() = trackSwitchNotification {
        player.cycleAudio(); TrackData(player.aid, "audio")
    }
    private fun cycleSub() = trackSwitchNotification {
        player.cycleSub(); TrackData(player.sid, "sub")
    }

    private fun selectTrack(type: String, get: () -> Int, set: (Int) -> Unit) {
        val tracks = player.tracks.getValue(type)
        val selectedMpvId = get()
        val selectedIndex = tracks.indexOfFirst { it.mpvId == selectedMpvId }
        val restore = keepPlaybackForDialog()

        with (AlertDialog.Builder(this)) {
            setSingleChoiceItems(tracks.map { it.name }.toTypedArray(), selectedIndex) { dialog, item ->
                val trackId = tracks[item].mpvId

                set(trackId)
                dialog.dismiss()
                trackSwitchNotification { TrackData(trackId, type) }
            }
            setOnDismissListener { restore() }
            create().show()
        }
    }

    private fun showWidePlayerDialog(
        dialog: AlertDialog,
        widthFraction: Float = 0.84f,
        maxWidthDp: Float = 1180f,
        gravity: Int = Gravity.CENTER,
        verticalOffsetDp: Float = 0f,
    ) {
        dialog.show()
        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            decorView.setPadding(0, 0, 0, 0)
            setGravity(gravity)

            val screenWidth = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                windowManager.currentWindowMetrics.bounds.width()
            } else @Suppress("DEPRECATION") {
                val dm = DisplayMetrics()
                windowManager.defaultDisplay.getRealMetrics(dm)
                dm.widthPixels
            }
            val maxWidthPx = Utils.convertDp(this@MPVActivity, maxWidthDp)
            val desiredWidth = (screenWidth * widthFraction).roundToInt()
            setLayout(minOf(desiredWidth, maxWidthPx), WindowManager.LayoutParams.WRAP_CONTENT)
            if (verticalOffsetDp != 0f) {
                attributes = attributes.apply {
                    y = Utils.convertDp(this@MPVActivity, verticalOffsetDp)
                }
            }
        }
    }

    private fun pickAudio() {
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
            dialog.dismiss()
            trackSwitchNotification { TrackData(trackId, "audio") }
        }
        impl.onVoiceBoostAdjust = { delta -> adjustVoiceBoost(delta) }
        impl.onVolumeBoostAdjust = { delta -> adjustVolumeBoost(delta) }
        impl.onNightModeAdjust = { delta -> adjustNightMode(delta) }
        impl.onAudioNormAdjust = { delta -> adjustAudioNorm(delta) }
        impl.onDownmixAdjust = { delta -> adjustDownmix(delta) }
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
                title = getString(R.string.dialog_title_audio),
                items = items,
                showFilters = true,
                initialVoiceBoostState = currentVoiceBoostState(),
                initialVolumeBoostState = currentVolumeBoostState(),
                initialNightModeState = currentNightModeState(),
                initialAudioNormState = currentAudioNormState(),
                initialDownmixState = currentDownmixState(),
                persistFiltersOn = persistAudioFilters,
            ), 0, 0, 0, 0)
            setOnDismissListener { restore() }
            create()
        }
        showWidePlayerDialog(
            dialog,
            widthFraction = 0.84f,
            maxWidthDp = 1160f,
        )
    }

    private fun pickSub() {
        val restore = keepPlaybackForDialog()
        val tracks = player.tracks.getValue("sub")
        val selectedId = player.sid
        val items = tracks.map {
            MediaPickerDialog.Item(it.name, it.mpvId, it.mpvId == selectedId)
        }
        val impl = MediaPickerDialog()
        lateinit var dialog: AlertDialog
        impl.onItemClick = { idx ->
            val trackId = tracks[idx].mpvId
            player.sid = trackId
            dialog.dismiss()
            trackSwitchNotification { TrackData(trackId, SubTrackDialog.TRACK_TYPE) }
        }
        impl.onDelayClick = {
            dialog.dismiss()
            openSubDelayDialog()
        }

        val delayValue = String.format("%.2f s", player.subDelay ?: 0.0)

        @Suppress("DEPRECATION")
        dialog = with(AlertDialog.Builder(this)) {
            val inflater = LayoutInflater.from(context)
            setView(impl.buildView(
                inflater,
                title = getString(R.string.dialog_title_subs),
                items = items,
                showDelay = true,
                delayText = delayValue,
            ), 0, 0, 0, 0)
            setOnDismissListener { restore() }
            create()
        }
        showWidePlayerDialog(dialog, widthFraction = 0.84f, maxWidthDp = 1100f)
    }

    private fun openSubDelayDialog() {
        val restore = keepPlaybackForDialog()
        val picker = SubDelayDialog(-600.0, 600.0)
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
        dialog.show()
    }

    private fun openPlaylistMenu(restore: StateRestoreCallback) {
        val impl = PlaylistDialog(player)
        lateinit var dialog: AlertDialog

        impl.listeners = object : PlaylistDialog.Listeners {
            private fun openFilePicker(skip: Int) {
                openFilePickerFor(RCODE_LOAD_FILE, "", skip) { result, data ->
                    if (result == RESULT_OK) {
                        val path = data!!.getStringExtra("path")!!
                        MPVLib.command(arrayOf("loadfile", path, "append"))
                        impl.refresh()
                    }
                }
            }
            override fun pickFile() = openFilePicker(FilePickerActivity.FILE_PICKER)

            override fun openUrl() {
                val helper = Utils.OpenUrlDialog(this@MPVActivity)
                with (helper) {
                    builder.setPositiveButton(R.string.dialog_ok) { _, _ ->
                        MPVLib.command(arrayOf("loadfile", helper.text, "append"))
                        impl.refresh()
                    }
                    builder.setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.cancel() }
                    create().show()
                }
            }

            override fun onItemPicked(item: MPVView.PlaylistItem) {
                MPVLib.setPropertyInt("playlist-pos", item.index)
                dialog.dismiss()
            }
        }

        dialog = with (AlertDialog.Builder(this)) {
            val inflater = LayoutInflater.from(context)
            setView(impl.buildView(inflater))
            setOnDismissListener { restore() }
            create()
        }
        dialog.show()
    }

    private fun pickDecoder() {
        val restore = keepPlaybackForDialog()

        val rawItems = mutableListOf(
            Pair(getString(R.string.decoder_mode_hw), MPVView.DECODER_MODE_HW),
            Pair(getString(R.string.decoder_mode_sw), MPVView.DECODER_MODE_SW),
            Pair(getString(R.string.decoder_mode_gnext), MPVView.DECODER_MODE_GNEXT),
            Pair(getString(R.string.decoder_mode_shield_h10p), MPVView.DECODER_MODE_SHIELD_H10P)
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            rawItems.add(0, Pair(getString(R.string.decoder_mode_hw_plus), MPVView.DECODER_MODE_HW_PLUS))
        val currentMode = player.currentDecoderMode
        val items = rawItems.map {
            MediaPickerDialog.Item(it.first, it.second, it.second == currentMode)
        }

        val impl = MediaPickerDialog()
        lateinit var dialog: AlertDialog
        impl.onItemClick = { idx ->
            sessionDecoderMode = rawItems[idx].second
            player.applyDecoderMode(rawItems[idx].second)
            updateDecoderButton()
            dialog.dismiss()
        }

        @Suppress("DEPRECATION")
        dialog = with(AlertDialog.Builder(this)) {
            val inflater = LayoutInflater.from(context)
            setView(impl.buildView(
                inflater,
                title = getString(R.string.dialog_title_decoder),
                items = items,
            ), 0, 0, 0, 0)
            setOnDismissListener { restore() }
            create()
        }
        showWidePlayerDialog(dialog, widthFraction = 0.7f, maxWidthDp = 860f)
    }

    private fun cycleDecoderMode() {
        val modes = mutableListOf(
            MPVView.DECODER_MODE_HW,
            MPVView.DECODER_MODE_SW,
            MPVView.DECODER_MODE_GNEXT,
            MPVView.DECODER_MODE_SHIELD_H10P
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            modes.add(0, MPVView.DECODER_MODE_HW_PLUS)
        val currentMode = player.currentDecoderMode
        val currentIndex = modes.indexOf(currentMode).takeIf { it >= 0 } ?: 0
        val nextMode = modes[(currentIndex + 1) % modes.size]
        sessionDecoderMode = nextMode
        player.applyDecoderMode(nextMode)
        updateDecoderButton()
    }

    private fun cycleSpeed() {
        player.cycleSpeed()
    }

    // ========================================================================
    // Audio filter toggles (Voice Boost / Night Mode / Audio Normalization)
    //
    // We keep a small named filter set and rebuild it deterministically so the
    // UI state survives file switches cleanly.
    // ========================================================================
    private var voiceBoostLevel = 0
    private var volumeBoostDb = 0
    private var nightModeLevel = 0
    private var audioNormLevel = 0
    private var downmixLevel = 0

    private val voiceBoostFilterLabel = "@voiceboost"
    private val volumeBoostFilterLabel = "@volumeboost"
    private val nightModeFilterLabel = "@nightmode"
    private val audioNormFilterLabel = "@dynaudnorm"
    private val downmixFilterLabel = "@dialoguedownmix"

    private val voiceBoostPresetLabelIds = intArrayOf(
        R.string.filter_value_off,
        R.string.voice_boost_preset_soft,
        R.string.voice_boost_preset_light,
        R.string.voice_boost_preset_clear,
        R.string.voice_boost_preset_speech,
        R.string.voice_boost_preset_loud
    )
    private val downmixPresetLabelIds = intArrayOf(
        R.string.filter_value_off,
        R.string.dialogue_downmix_preset_soft,
        R.string.dialogue_downmix_preset_strong,
        R.string.dialogue_downmix_preset_tv,
        R.string.dialogue_downmix_preset_focus,
        R.string.dialogue_downmix_preset_anchor
    )
    private val nightModePresetLabelIds = intArrayOf(
        R.string.filter_value_off,
        R.string.night_mode_preset_soft,
        R.string.night_mode_preset_late,
        R.string.night_mode_preset_balanced,
        R.string.night_mode_preset_strong,
        R.string.night_mode_preset_cinema
    )
    private val audioNormPresetLabelIds = intArrayOf(
        R.string.filter_value_off,
        R.string.audio_norm_preset_light,
        R.string.audio_norm_preset_smooth,
        R.string.audio_norm_preset_speech,
        R.string.audio_norm_preset_balanced,
        R.string.audio_norm_preset_strong
    )

    private val nightModePresets = listOf(
        "",
        "$nightModeFilterLabel:lavfi=[" +
            "acompressor=threshold=-24dB:ratio=5:attack=5:release=180:knee=4," +
            "treble=g=-1.0:f=8000:w=1.2:t=o," +
            "bass=g=-1.5:f=110:w=1.6:t=o," +
            "volume=1.5dB," +
            "alimiter=limit=0.92:attack=2:release=24]",
        "$nightModeFilterLabel:lavfi=[" +
            "acompressor=threshold=-28dB:ratio=8:attack=5:release=190:knee=5," +
            "acompressor=threshold=-13dB:ratio=2.0:attack=18:release=260:knee=3," +
            "treble=g=-2.0:f=8000:w=1.4:t=o," +
            "bass=g=-2.3:f=105:w=1.8:t=o," +
            "volume=3dB," +
            "alimiter=limit=0.86:attack=2:release=22]",
        "$nightModeFilterLabel:lavfi=[" +
            "acompressor=threshold=-31dB:ratio=12:attack=5:release=200:knee=5," +
            "acompressor=threshold=-12dB:ratio=2.4:attack=18:release=320:knee=4," +
            "treble=g=-2.8:f=7800:w=1.4:t=o," +
            "bass=g=-2.8:f=100:w=1.9:t=o," +
            "volume=4dB," +
            "alimiter=limit=0.80:attack=1:release=20]",
        "$nightModeFilterLabel:lavfi=[" +
            "acompressor=threshold=-33dB:ratio=16:attack=4:release=210:knee=6," +
            "acompressor=threshold=-12dB:ratio=2.8:attack=20:release=360:knee=4," +
            "treble=g=-3.4:f=7600:w=1.5:t=o," +
            "bass=g=-3.2:f=100:w=2.0:t=o," +
            "volume=5dB," +
            "alimiter=limit=0.72:attack=1:release=18]",
        "$nightModeFilterLabel:lavfi=[" +
            "acompressor=threshold=-33dB:ratio=20:attack=5:release=200:knee=6," +
            "acompressor=threshold=-12dB:ratio=3:attack=20:release=400:knee=4," +
            "treble=g=-4:f=8000:w=1.5:t=o," +
            "bass=g=-3:f=100:w=2:t=o," +
            "volume=6dB," +
            "alimiter=limit=0.65:attack=1:release=15]"
    )
    private val audioNormPresets = listOf(
        "",
        "$audioNormFilterLabel:lavfi=[" +
            "loudnorm=I=-18:TP=-1.8:LRA=12:linear=true:print_format=none]",
        "$audioNormFilterLabel:lavfi=[" +
            "loudnorm=I=-17:TP=-1.7:LRA=10:linear=true:print_format=none]",
        "$audioNormFilterLabel:lavfi=[" +
            "loudnorm=I=-16:TP=-1.5:LRA=8:linear=false:print_format=none]",
        "$audioNormFilterLabel:lavfi=[" +
            "loudnorm=I=-15:TP=-1.4:LRA=6:linear=false:print_format=none," +
            "alimiter=limit=0.94:attack=2:release=24]",
        "$audioNormFilterLabel:lavfi=[" +
            "loudnorm=I=-14:TP=-1.2:LRA=4:linear=false:print_format=none," +
            "alimiter=limit=0.92:attack=2:release=22]"
    )
    private val voiceBoostPresets = listOf(
        "",
        "$voiceBoostFilterLabel:lavfi=[" +
            "highpass=f=80:p=2," +
            "equalizer=f=300:t=q:w=1.0:g=-2.0," +
            "equalizer=f=2800:t=q:w=0.8:g=3.0," +
            "equalizer=f=10000:t=q:w=1.0:g=1.0," +
            "acompressor=threshold=-28dB:ratio=2.0:attack=8:release=90:makeup=1.1]",
        "$voiceBoostFilterLabel:lavfi=[" +
            "highpass=f=85:p=2," +
            "equalizer=f=300:t=q:w=1.0:g=-3.0," +
            "equalizer=f=2800:t=q:w=0.8:g=4.5," +
            "equalizer=f=10000:t=q:w=1.0:g=1.4," +
            "acompressor=threshold=-30dB:ratio=2.6:attack=8:release=85:makeup=1.2," +
            "alimiter=limit=0.96:attack=2:release=24]",
        "$voiceBoostFilterLabel:lavfi=[" +
            "highpass=f=90:p=2," +
            "equalizer=f=300:t=q:w=1.0:g=-3.6," +
            "equalizer=f=2800:t=q:w=0.8:g=5.8," +
            "equalizer=f=10000:t=q:w=1.0:g=1.8," +
            "acompressor=threshold=-31dB:ratio=3.0:attack=7:release=82:makeup=1.4," +
            "alimiter=limit=0.95:attack=2:release=22]",
        "$voiceBoostFilterLabel:lavfi=[" +
            "highpass=f=95:p=2," +
            "equalizer=f=300:t=q:w=1.0:g=-4.2," +
            "equalizer=f=2800:t=q:w=0.8:g=7.0," +
            "equalizer=f=10000:t=q:w=1.0:g=2.0," +
            "acompressor=threshold=-32dB:ratio=3.3:attack=7:release=80:makeup=1.5," +
            "alimiter=limit=0.94:attack=2:release=20]",
        "$voiceBoostFilterLabel:lavfi=[" +
            "highpass=f=100:p=2," +
            "equalizer=f=300:t=q:w=1.0:g=-5.0," +
            "equalizer=f=2800:t=q:w=0.8:g=8.0," +
            "equalizer=f=10000:t=q:w=1.0:g=2.2," +
            "acompressor=threshold=-33dB:ratio=3.8:attack=6:release=78:makeup=1.6," +
            "alimiter=limit=0.93:attack=1:release=18]"
    )
    private val volumeBoostStepsDb = intArrayOf(0, 2, 4, 6, 8, 10, 12, 15, 18, 21)

    private fun refreshFilterTint(btn: android.widget.ImageButton, active: Boolean) {
        val colorRes = if (active) R.color.tv_filter_active_icon else R.color.tv_text
        btn.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, colorRes))
    }

    private fun isVoiceBoostOn() = voiceBoostLevel > 0

    private fun isVolumeBoostOn() = volumeBoostDb > 0

    private fun isNightModeOn() = nightModeLevel > 0

    private fun isAudioNormOn() = audioNormLevel > 0

    private fun isDownmixOn() = downmixLevel > 0

    private fun channelCountFromLayout(layout: String): Int {
        val normalized = layout.trim().lowercase(Locale.US)
        return when {
            normalized == "mono" -> 1
            normalized == "stereo" || normalized == "2.0" -> 2
            normalized.contains("7.1") -> 8
            normalized.contains("6.1") -> 7
            normalized.contains("5.1") -> 6
            normalized.contains("5.0") -> 5
            normalized.contains("4.0") -> 4
            normalized.contains("3.1") -> 4
            normalized.contains("3.0") -> 3
            else -> 2
        }
    }

    private fun selectedAudioTrackChannelCount(): Int? {
        val trackCount = MPVLib.getPropertyInt("track-list/count") ?: return null
        for (i in 0 until trackCount) {
            if (MPVLib.getPropertyString("track-list/$i/type") != "audio")
                continue
            if (MPVLib.getPropertyBoolean("track-list/$i/selected") != true)
                continue
            MPVLib.getPropertyInt("track-list/$i/demux-channel-count")?.let { count ->
                if (count > 0) return count
            }
            MPVLib.getPropertyString("track-list/$i/demux-channels")?.let { layout ->
                return channelCountFromLayout(layout)
            }
            MPVLib.getPropertyString("track-list/$i/audio-channels")?.let { layout ->
                return channelCountFromLayout(layout)
            }
        }
        return null
    }

    private fun currentAudioChannelCount(): Int {
        selectedAudioTrackChannelCount()?.let { count ->
            if (count > 0) return count
        }
        MPVLib.getPropertyInt("audio-params/channel-count")?.let { count ->
            if (count > 0) return count
        }
        val layout = MPVLib.getPropertyString("audio-params/channels") ?: return 2
        return channelCountFromLayout(layout)
    }

    private fun surroundDialogueDownmixFilter(): String? {
        if (currentAudioChannelCount() < 6)
            return null
        val body = when (downmixLevel) {
            1 -> "FL=0.72*FC+0.28*FL+0.10*BL+0.10*SL+0.04*BR+0.04*SR+0.02*LFE|" +
                "FR=0.72*FC+0.28*FR+0.10*BR+0.10*SR+0.04*BL+0.04*SL+0.02*LFE"
            2 -> "FL=0.84*FC+0.24*FL+0.08*BL+0.08*SL+0.03*BR+0.03*SR+0.02*LFE|" +
                "FR=0.84*FC+0.24*FR+0.08*BR+0.08*SR+0.03*BL+0.03*SL+0.02*LFE"
            3 -> "FL=0.96*FC+0.20*FL+0.07*BL+0.07*SL+0.03*BR+0.03*SR+0.015*LFE|" +
                "FR=0.96*FC+0.20*FR+0.07*BR+0.07*SR+0.03*BL+0.03*SL+0.015*LFE"
            4 -> "FL=1.04*FC+0.16*FL+0.05*BL+0.05*SL+0.02*BR+0.02*SR+0.01*LFE|" +
                "FR=1.04*FC+0.16*FR+0.05*BR+0.05*SR+0.02*BL+0.02*SL+0.01*LFE"
            5 -> "FL=1.10*FC+0.12*FL+0.04*BL+0.04*SL+0.015*BR+0.015*SR+0.01*LFE|" +
                "FR=1.10*FC+0.12*FR+0.04*BR+0.04*SR+0.015*BL+0.015*SL+0.01*LFE"
            else -> return null
        }
        return "$downmixFilterLabel:lavfi=[pan=stereo|$body]"
    }

    private fun volumeBoostFilter(): String {
        val settings = when {
            volumeBoostDb <= 4 -> Triple("-20dB", "3.0", "0.92")
            volumeBoostDb <= 8 -> Triple("-21dB", "4.0", "0.90")
            volumeBoostDb <= 12 -> Triple("-22dB", "5.0", "0.88")
            volumeBoostDb <= 15 -> Triple("-23dB", "6.0", "0.86")
            else -> Triple("-24dB", "7.0", "0.84")
        }
        return "$volumeBoostFilterLabel:lavfi=[" +
            "acompressor=threshold=${settings.first}:ratio=${settings.second}:attack=3:release=60:knee=2.5," +
            "volume=${volumeBoostDb}dB," +
            "alimiter=limit=${settings.third}:attack=2:release=20]"
    }

    private fun nightModeFilter(): String = nightModePresets[nightModeLevel]

    private fun getVoiceBoostLabel(): String = getString(voiceBoostPresetLabelIds[voiceBoostLevel])

    private fun getDownmixBaseLabel(): String = getString(downmixPresetLabelIds[downmixLevel])

    private fun getDownmixLabel(): String {
        if (!isDownmixOn()) {
            return getString(R.string.filter_value_off)
        }
        val channels = currentAudioChannelCount()
        return if (channels >= 6) {
            getString(R.string.format_downmix_active, getDownmixBaseLabel(), channels)
        } else if (channels <= 2) {
            getString(R.string.format_downmix_stereo, getDownmixBaseLabel())
        } else {
            getString(R.string.format_downmix_multichannel, getDownmixBaseLabel(), channels)
        }
    }

    private fun getVolumeBoostLabel(): String {
        return if (volumeBoostDb > 0) {
            getString(R.string.format_db, volumeBoostDb)
        } else {
            getString(R.string.filter_value_off)
        }
    }

    private fun getNightModeLabel(): String = getString(nightModePresetLabelIds[nightModeLevel])

    private fun getAudioNormLabel(): String = getString(audioNormPresetLabelIds[audioNormLevel])

    private fun currentVoiceBoostState(): MediaPickerDialog.ValueState {
        val maxLevel = voiceBoostPresets.lastIndex
        return MediaPickerDialog.ValueState(
            label = getVoiceBoostLabel(),
            active = isVoiceBoostOn(),
            canDecrease = voiceBoostLevel > 0,
            canIncrease = voiceBoostLevel < maxLevel
        )
    }

    private fun currentVolumeBoostState(): MediaPickerDialog.ValueState {
        val currentIndex = volumeBoostStepsDb.indexOf(volumeBoostDb).takeIf { it >= 0 } ?: 0
        val maxIndex = volumeBoostStepsDb.lastIndex
        return MediaPickerDialog.ValueState(
            label = getVolumeBoostLabel(),
            active = isVolumeBoostOn(),
            canDecrease = currentIndex > 0,
            canIncrease = currentIndex < maxIndex
        )
    }

    private fun currentDownmixState(): MediaPickerDialog.ValueState {
        val maxLevel = downmixPresetLabelIds.lastIndex
        val active = isDownmixOn() && currentAudioChannelCount() >= 6
        return MediaPickerDialog.ValueState(
            label = getDownmixLabel(),
            active = active,
            canDecrease = downmixLevel > 0,
            canIncrease = downmixLevel < maxLevel
        )
    }

    private fun currentNightModeState(): MediaPickerDialog.ValueState {
        val maxLevel = nightModePresets.lastIndex
        return MediaPickerDialog.ValueState(
            label = getNightModeLabel(),
            active = isNightModeOn(),
            canDecrease = nightModeLevel > 0,
            canIncrease = nightModeLevel < maxLevel
        )
    }

    private fun currentAudioNormState(): MediaPickerDialog.ValueState {
        val maxLevel = audioNormPresets.lastIndex
        return MediaPickerDialog.ValueState(
            label = getAudioNormLabel(),
            active = isAudioNormOn(),
            canDecrease = audioNormLevel > 0,
            canIncrease = audioNormLevel < maxLevel
        )
    }

    private fun refreshAllFilterTints() {
        refreshFilterTint(binding.voiceBoostBtn, isVoiceBoostOn())
        refreshFilterTint(binding.volumeBoostBtn, isVolumeBoostOn())
        refreshFilterTint(binding.nightModeBtn, isNightModeOn())
        refreshFilterTint(binding.audioNormBtn, isAudioNormOn())
    }

    private fun buildAudioFilterChain(): String {
        val filters = mutableListOf<String>()
        if (isDownmixOn())
            surroundDialogueDownmixFilter()?.let { filters += it }
        if (isVoiceBoostOn())
            filters += voiceBoostPresets[voiceBoostLevel]
        if (isAudioNormOn())
            filters += audioNormPresets[audioNormLevel]
        if (isNightModeOn())
            filters += nightModeFilter()
        if (isVolumeBoostOn())
            filters += volumeBoostFilter()
        return filters.joinToString(",")
    }

    private fun applySavedAudioFilterDefaults() {
        val filterChain = if (persistAudioFilters) buildAudioFilterChain() else ""
        MPVLib.setOptionString("af", filterChain)
    }

    private fun applyAudioFilterState() {
        MPVLib.setPropertyString("af", buildAudioFilterChain())
    }

    private fun rebuildAudioFilters() {
        applyAudioFilterState()
    }

    private fun adjustVoiceBoost(delta: Int, wrap: Boolean = false): MediaPickerDialog.ValueState {
        val maxLevel = voiceBoostPresets.lastIndex
        val nextLevel = when {
            wrap -> {
                when {
                    voiceBoostLevel + delta > maxLevel -> 0
                    voiceBoostLevel + delta < 0 -> maxLevel
                    else -> voiceBoostLevel + delta
                }
            }
            else -> (voiceBoostLevel + delta).coerceIn(0, maxLevel)
        }
        voiceBoostLevel = nextLevel
        rebuildAudioFilters()
        refreshAllFilterTints()
        writeSettings()
        showToast(
            getString(R.string.btn_voice_boost),
            if (isVoiceBoostOn()) getVoiceBoostLabel() else getString(R.string.status_off)
        )
        return currentVoiceBoostState()
    }

    private fun adjustVolumeBoost(delta: Int, wrap: Boolean = false): MediaPickerDialog.ValueState {
        val currentIndex = volumeBoostStepsDb.indexOf(volumeBoostDb).takeIf { it >= 0 } ?: 0
        val maxIndex = volumeBoostStepsDb.lastIndex
        val nextIndex = when {
            wrap -> {
                when {
                    currentIndex + delta > maxIndex -> 0
                    currentIndex + delta < 0 -> maxIndex
                    else -> currentIndex + delta
                }
            }
            else -> (currentIndex + delta).coerceIn(0, maxIndex)
        }
        volumeBoostDb = volumeBoostStepsDb[nextIndex]
        rebuildAudioFilters()
        refreshAllFilterTints()
        writeSettings()
        showToast(
            getString(R.string.btn_volume_boost),
            if (isVolumeBoostOn()) getVolumeBoostLabel() else getString(R.string.status_off)
        )
        return currentVolumeBoostState()
    }

    private fun adjustDownmix(delta: Int, wrap: Boolean = false): MediaPickerDialog.ValueState {
        val maxLevel = downmixPresetLabelIds.lastIndex
        val nextLevel = when {
            wrap -> {
                when {
                    downmixLevel + delta > maxLevel -> 0
                    downmixLevel + delta < 0 -> maxLevel
                    else -> downmixLevel + delta
                }
            }
            else -> (downmixLevel + delta).coerceIn(0, maxLevel)
        }
        downmixLevel = nextLevel
        rebuildAudioFilters()
        writeSettings()
        showToast(
            getString(R.string.btn_dialogue_downmix),
            getDownmixLabel()
        )
        return currentDownmixState()
    }

    private fun adjustNightMode(delta: Int, wrap: Boolean = false): MediaPickerDialog.ValueState {
        val maxLevel = nightModePresets.lastIndex
        val nextLevel = when {
            wrap -> {
                when {
                    nightModeLevel + delta > maxLevel -> 0
                    nightModeLevel + delta < 0 -> maxLevel
                    else -> nightModeLevel + delta
                }
            }
            else -> (nightModeLevel + delta).coerceIn(0, maxLevel)
        }
        nightModeLevel = nextLevel
        rebuildAudioFilters()
        refreshAllFilterTints()
        writeSettings()
        showToast(
            getString(R.string.btn_night_mode),
            if (isNightModeOn()) getNightModeLabel() else getString(R.string.status_off)
        )
        return currentNightModeState()
    }

    private fun adjustAudioNorm(delta: Int, wrap: Boolean = false): MediaPickerDialog.ValueState {
        val maxLevel = audioNormPresets.lastIndex
        val nextLevel = when {
            wrap -> {
                when {
                    audioNormLevel + delta > maxLevel -> 0
                    audioNormLevel + delta < 0 -> maxLevel
                    else -> audioNormLevel + delta
                }
            }
            else -> (audioNormLevel + delta).coerceIn(0, maxLevel)
        }
        audioNormLevel = nextLevel
        rebuildAudioFilters()
        refreshAllFilterTints()
        writeSettings()
        showToast(
            getString(R.string.btn_audio_norm),
            if (isAudioNormOn()) getAudioNormLabel() else getString(R.string.status_off)
        )
        return currentAudioNormState()
    }

    private fun pickSpeed() {
        // TODO: replace this with SliderPickerDialog
        val picker = SpeedPickerDialog()

        val restore = keepPlaybackForDialog()
        genericPickerDialog(picker, R.string.title_speed_dialog, "speed") {
            restore()
        }
    }

    private fun goIntoPiP() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return
        enterPictureInPictureMode(buildPiPParams())
    }

    private fun lockUI() {
        lockedUI = true
        hideControlsFade()
    }

    private fun unlockUI() {
        binding.unlockBtn.visibility = View.GONE
        lockedUI = false
        showControls()
    }

    data class MenuItem(@param:IdRes val idRes: Int, val handler: () -> Boolean)
    private fun genericMenu(
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

        if (Utils.visibleChildren(dialogView) == 0) {
            Log.w(TAG, "Not showing menu because it would be empty")
            restoreState()
            return
        }

        Utils.handleInsetsAsPadding(dialogView)

        with (builder) {
            setView(dialogView)
            setOnCancelListener { restoreState() }
            dialog = create()
        }
        dialog.show()
    }

    private fun openTopMenu() {
        val restoreState = keepPlaybackForDialog()

        fun addExternalThing(cmd: String, result: Int, data: Intent?) {
            if (result != RESULT_OK)
                return
            // file picker may return a content URI or a bare file path
            val path = data!!.getStringExtra("path")!!
            val path2 = if (path.startsWith("content://"))
                translateContentUri(Uri.parse(path))
            else
                path
            MPVLib.command(arrayOf(cmd, path2, "cached"))
        }

        /******/
        val hiddenButtons = mutableSetOf<Int>()
        val buttons: MutableList<MenuItem> = mutableListOf(
                MenuItem(R.id.audioBtn) {
                    openFilePickerFor(RCODE_EXTERNAL_AUDIO, R.string.open_external_audio) { result, data ->
                        addExternalThing("audio-add", result, data)
                        restoreState()
                    }; false
                },
                MenuItem(R.id.subBtn) {
                    openFilePickerFor(RCODE_EXTERNAL_SUB, R.string.open_external_sub) { result, data ->
                        addExternalThing("sub-add", result, data)
                        restoreState()
                    }; false
                },
                MenuItem(R.id.playlistBtn) {
                    openPlaylistMenu(restoreState); false
                },
                MenuItem(R.id.backgroundBtn) {
                    // restoring state may (un)pause so do that first
                    restoreState()
                    backgroundPlayMode = "always"
                    player.paused = false
                    moveTaskToBack(true)
                    false
                },
                MenuItem(R.id.chapterBtn) {
                    val chapters = player.loadChapters()
                    if (chapters.isEmpty())
                        return@MenuItem true
                    val chapterArray = chapters.map {
                        val timecode = Utils.prettyTime(it.time.roundToInt())
                        if (!it.title.isNullOrEmpty())
                            getString(R.string.ui_chapter, it.title, timecode)
                        else
                            getString(R.string.ui_chapter_fallback, it.index+1, timecode)
                    }.toTypedArray()
                    val selectedIndex = MPVLib.getPropertyInt("chapter") ?: 0
                    with (AlertDialog.Builder(this)) {
                        setSingleChoiceItems(chapterArray, selectedIndex) { dialog, item ->
                            MPVLib.setPropertyInt("chapter", chapters[item].index)
                            dialog.dismiss()
                        }
                        setOnDismissListener { restoreState() }
                        create().show()
                    }; false
                },
                MenuItem(R.id.chapterPrev) {
                    MPVLib.command(arrayOf("add", "chapter", "-1")); true
                },
                MenuItem(R.id.chapterNext) {
                    MPVLib.command(arrayOf("add", "chapter", "1")); true
                },
                MenuItem(R.id.advancedBtn) { openAdvancedMenu(restoreState); false },
                MenuItem(R.id.orientationBtn) {
                    autoRotationMode = "manual"
                    cycleOrientation()
                    true
                }
        )

        if (!isPlayingAudio)
            hiddenButtons.add(R.id.backgroundBtn)
        if ((MPVLib.getPropertyInt("chapter-list/count") ?: 0) == 0)
            hiddenButtons.add(R.id.rowChapter)
        /******/

        genericMenu(R.layout.dialog_top_menu, buttons, hiddenButtons, restoreState)
    }

    private fun genericPickerDialog(
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
                        MPVLib.setPropertyInt(property, it.toInt())
                    else
                        MPVLib.setPropertyDouble(property, it)
                }
            }
            setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.cancel() }
            setOnDismissListener { restoreState() }
            create()
        }

        picker.number = MPVLib.getPropertyDouble(property)
        dialog.show()
    }

    private fun openAdvancedMenu(restoreState: StateRestoreCallback) {
        /******/
        val hiddenButtons = mutableSetOf<Int>()
        val buttons: MutableList<MenuItem> = mutableListOf(
                MenuItem(R.id.subSeekPrev) {
                    MPVLib.command(arrayOf("sub-seek", "-1")); true
                },
                MenuItem(R.id.subSeekNext) {
                    MPVLib.command(arrayOf("sub-seek", "1")); true
                },
                MenuItem(R.id.statsBtn) {
                    MPVLib.command(arrayOf("script-binding", "stats/display-stats-toggle")); true
                },
                MenuItem(R.id.aspectBtn) {
                    val ratios = resources.getStringArray(R.array.aspect_ratios)
                    with (AlertDialog.Builder(this)) {
                        setItems(R.array.aspect_ratio_names) { dialog, item ->
                            if (ratios[item] == "panscan") {
                                MPVLib.setPropertyString("video-aspect-override", "-1")
                                MPVLib.setPropertyDouble("panscan", 1.0)
                            } else {
                                MPVLib.setPropertyString("video-aspect-override", ratios[item])
                                MPVLib.setPropertyDouble("panscan", 0.0)
                            }
                            dialog.dismiss()
                        }
                        setOnDismissListener { restoreState() }
                        create().show()
                    }; false
                },
        )

        val statsButtons = arrayOf(R.id.statsBtn1, R.id.statsBtn2, R.id.statsBtn3)
        for (i in 1..3) {
            buttons.add(MenuItem(statsButtons[i-1]) {
                MPVLib.command(arrayOf("script-binding", "stats/display-page-$i")); true
            })
        }

        // contrast, brightness and others get a -100 to 100 slider
        val basicIds = arrayOf(R.id.contrastBtn, R.id.brightnessBtn, R.id.gammaBtn, R.id.saturationBtn)
        val basicProps = arrayOf("contrast", "brightness", "gamma", "saturation")
        val basicTitles = arrayOf(R.string.contrast, R.string.video_brightness, R.string.gamma, R.string.saturation)
        basicIds.forEachIndexed { index, id ->
            buttons.add(MenuItem(id) {
                val slider = SliderPickerDialog(-100.0, 100.0, 1, R.string.format_fixed_number)
                genericPickerDialog(slider, basicTitles[index], basicProps[index], restoreState)
                false
            })
        }

        // audio / sub delay get a decimal picker
        buttons.add(MenuItem(R.id.audioDelayBtn) {
            val picker = DecimalPickerDialog(-600.0, 600.0)
            genericPickerDialog(picker, R.string.audio_delay, "audio-delay", restoreState)
            false
        })
        buttons.add(MenuItem(R.id.subDelayBtn) {
            val picker = SubDelayDialog(-600.0, 600.0)
            val dialog = with(AlertDialog.Builder(this)) {
                setTitle(R.string.sub_delay)
                val inflater = LayoutInflater.from(context)
                setView(picker.buildView(inflater))
                setPositiveButton(R.string.dialog_ok) { _, _ ->
                    picker.delay1?.let { player.subDelay = it }
                    picker.delay2?.let { player.secondarySubDelay = it }
                }
                setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.cancel() }
                setOnDismissListener { restoreState() }
                create()
            }

            picker.delay1 = player.subDelay ?: 0.0
            picker.delay2 = if (player.secondarySid != -1) player.secondarySubDelay else null
            dialog.show()
            false
        })

        if (player.vid == -1)
            hiddenButtons.addAll(arrayOf(R.id.rowVideo1, R.id.rowVideo2, R.id.aspectBtn))
        if (player.aid == -1 || player.vid == -1)
            hiddenButtons.add(R.id.audioDelayBtn)
        if (player.sid == -1)
            hiddenButtons.addAll(arrayOf(R.id.subDelayBtn, R.id.rowSubSeek))
        /******/

        genericMenu(R.layout.dialog_advanced_menu, buttons, hiddenButtons, restoreState)
    }

    private fun cycleOrientation() {
        requestedOrientation = if (requestedOrientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
        else
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    }

    private var pendingActivityResultCallback: ActivityResultCallback? = null
    private val filePickerResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            pendingActivityResultCallback?.invoke(it.resultCode, it.data)
            pendingActivityResultCallback = null
        }

    private fun openFilePickerFor(requestCode: Int, title: String, skip: Int?, callback: ActivityResultCallback) {
        val intent = Intent(this, FilePickerActivity::class.java)
        intent.putExtra("title", title)
        intent.putExtra("allow_document", true)
        skip?.let { intent.putExtra("skip", it) }
        // start file picker at directory of current file
        val path = MPVLib.getPropertyString("path") ?: ""
        if (path.startsWith('/'))
            intent.putExtra("default_path", File(path).parent)

        pendingActivityResultCallback = callback
        filePickerResultLauncher.launch(intent)
    }
    private fun openFilePickerFor(requestCode: Int, @StringRes titleRes: Int, callback: ActivityResultCallback) {
        openFilePickerFor(requestCode, getString(titleRes), null, callback)
    }

    private fun refreshUi() {
        // forces update of entire UI, used when resuming the activity
        updatePlaybackStatus(psc.pause)
        updatePlaybackTimeline(psc.position, forceTextUpdate = true)
        updatePlaybackDuration(psc.duration)
        updateAudioUI()
        refreshAllFilterTints()
        updateOrientation()
        updateMetadataDisplay()
        updateDecoderButton()
        updateSpeedButton()
        updatePlaylistButtons()
        player.loadTracks()
        updateChapterMarkers()
    }

    private fun updateAudioUI() {
        // Note: prev/next now live in the button group at all times (TV redesign).
        // For the audio-only UI we just reorder the button group; for video mode
        // we use the full button row including the new audio-filter buttons.
        val audioButtons = arrayOf(R.id.prevBtn, R.id.cycleAudioBtn, R.id.playBtn,
                R.id.nextChapterBtn, R.id.cycleSpeedBtn, R.id.nextBtn)
        val videoButtons = arrayOf(R.id.playBtn, R.id.nextChapterBtn, R.id.prevBtn, R.id.nextBtn,
                R.id.cycleSubsBtn, R.id.cycleAudioBtn,
                R.id.cycleSpeedBtn, R.id.cycleDecoderBtn, R.id.statsToggleBtn,
                R.id.voiceBoostBtn, R.id.volumeBoostBtn, R.id.nightModeBtn, R.id.audioNormBtn)

        val shouldUseAudioUI = isPlayingAudioOnly()
        if (shouldUseAudioUI == useAudioUI)
            return
        useAudioUI = shouldUseAudioUI
        Log.v(TAG, "Audio UI: $useAudioUI")

        val buttonGroup = binding.controlsButtonGroup

        if (useAudioUI) {
            // Change button layout of buttons group
            Utils.viewGroupReorder(buttonGroup, audioButtons)

            // Show song title and more metadata
            binding.controlsTitleGroup.visibility = View.VISIBLE
            Utils.viewGroupReorder(binding.controlsTitleGroup, arrayOf(R.id.titleTextView, R.id.minorTitleTextView))
            updateMetadataDisplay()

            showControls()
        } else {
            Utils.viewGroupReorder(buttonGroup, videoButtons)

            // Show title only depending on settings
            if (showMediaTitle) {
                binding.controlsTitleGroup.visibility = View.VISIBLE
                Utils.viewGroupReorder(binding.controlsTitleGroup, arrayOf(R.id.fullTitleTextView))
                updateMetadataDisplay()
            } else {
                binding.controlsTitleGroup.visibility = View.GONE
            }

            hideControls() // do NOT use fade runnable
        }

        // Visibility might have changed, so update
        updatePlaylistButtons()
    }

    private fun updateMetadataDisplay() {
        if (!useAudioUI) {
            if (showMediaTitle)
                binding.fullTitleTextView.text = psc.meta.formatTitle()
        } else {
            binding.titleTextView.text = psc.meta.formatTitle()
            binding.minorTitleTextView.text = psc.meta.formatArtistAlbum()
        }
    }

    private fun seekbarProgressFromMillis(positionMs: Long): Int {
        val scaled = positionMs.coerceAtLeast(0L) * SEEK_BAR_PRECISION / 1000L
        return scaled.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
    }

    private fun millisFromSeekbarProgress(progress: Int): Long {
        return progress.toLong() * 1000L / SEEK_BAR_PRECISION
    }

    private fun seekPlaybackFromDpad(deltaMs: Long) {
        val durationMs = psc.duration.coerceAtLeast(0L)
        if (durationMs <= 0L)
            return
        val currentPositionMs = psc.position.coerceAtLeast(0L)
        val newPositionMs = (currentPositionMs + deltaMs).coerceIn(0L, durationMs)
        player.timePos = newPositionMs / 1000.0
        binding.playbackSeekbar.progress = seekbarProgressFromMillis(newPositionMs)
        updatePlaybackTimeline(newPositionMs, forceTextUpdate = true)
    }

    private fun seekDeltaFromDpadEvent(ev: KeyEvent): Long {
        val direction = if (ev.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) 1L else -1L
        val magnitudeMs = when {
            ev.repeatCount >= 18 -> 12_000L
            ev.repeatCount >= 8 -> 10_000L
            ev.repeatCount >= 1 -> 8_000L
            else -> 10_000L
        }
        return direction * magnitudeMs
    }

    private fun updatePlaybackTimeline(positionMs: Long, forceTextUpdate: Boolean = false) {
        if (!userIsOperatingSeekbar) {
            binding.playbackSeekbar.progress = seekbarProgressFromMillis(positionMs)
        }
        updatePlaybackText((positionMs / 1000L).toInt().coerceAtLeast(0), force = forceTextUpdate)
    }

    private fun updatePlaybackText(position: Int, force: Boolean = false) {
        if (!force && lastDisplayedPlaybackSecond == position)
            return
        lastDisplayedPlaybackSecond = position
        binding.playbackPositionTxt.text = Utils.prettyTime(position)
        if (useTimeRemaining) {
            val diff = psc.durationSec - position
            binding.playbackDurationTxt.text = if (diff <= 0)
                "-00:00"
            else
                Utils.prettyTime(-diff, true)
        }

        // Keep the expensive secondary UI work at roughly once per second even
        // though the seekbar itself now updates with full playback precision.
        updateStats()
        if (binding.timeInfoPanel.visibility == View.VISIBLE)
            updateClockInfo()
    }

    private fun updatePlaybackDuration(durationMs: Long) {
        val duration = (durationMs / 1000f).roundToInt()
        if (!useTimeRemaining)
            binding.playbackDurationTxt.text = Utils.prettyTime(duration)
        if (!userIsOperatingSeekbar)
            binding.playbackSeekbar.max = seekbarProgressFromMillis(durationMs)
        if (duration > 0)
            updateChapterMarkers()
        if (binding.timeInfoPanel.visibility == View.VISIBLE)
            updateClockInfo()
    }

    private fun updatePlaybackStatus(paused: Boolean) {
        val r = if (paused) R.drawable.ic_play_arrow_black_24dp else R.drawable.ic_pause_black_24dp
        binding.playBtn.setImageResource(r)

        updatePiPParams()
        if (paused) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    private fun updateDecoderButton() {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            eventUiHandler.post { updateDecoderButton() }
            return
        }
        binding.cycleDecoderBtn.text = when (player.currentDecoderMode) {
            MPVView.DECODER_MODE_HW_PLUS -> "HW+"
            MPVView.DECODER_MODE_HW -> "HW"
            MPVView.DECODER_MODE_GNEXT, MPVView.DECODER_MODE_SHIELD_H10P -> "G-NEXT"
            MPVView.DECODER_MODE_SW -> "SW"
            else -> "HW"
        }
    }

    private fun toggleStatsOverlay() {
        MPVLib.command(arrayOf("script-binding", "stats/display-stats-toggle"))
    }

    private fun maybeApplyShieldHi10pFallback() {
        if (!autoDecoderFallback)
            return
        if (!isNvidiaShieldDevice())
            return
        if (!player.isHi10pH264Video())
            return
        if (player.currentDecoderMode !in arrayOf(MPVView.DECODER_MODE_HW, MPVView.DECODER_MODE_HW_PLUS))
            return

        player.applyDecoderMode(MPVView.DECODER_MODE_SHIELD_H10P)
        updateDecoderButton()
    }

    private fun applySessionDecoderModeIfNeeded() {
        val mode = sessionDecoderMode ?: preferredDecoderMode.takeIf {
            !autoDecoderFallback && it.isNotBlank()
        } ?: return
        player.applyDecoderMode(mode)
        updateDecoderButton()
    }

    private fun isNvidiaShieldDevice(): Boolean {
        return Build.MANUFACTURER.contains("NVIDIA", ignoreCase = true) ||
                Build.MODEL.contains("SHIELD", ignoreCase = true) ||
                Build.PRODUCT.contains("shield", ignoreCase = true)
    }

    private fun updateSpeedButton() {
        binding.cycleSpeedBtn.text = getString(R.string.ui_speed, psc.speed)
    }

    private fun updatePlaylistButtons() {
        val plCount = psc.playlistCount
        val plPos = psc.playlistPos

        if (!useAudioUI && plCount == 1) {
            // use View.GONE so the buttons won't take up any space
            binding.prevBtn.visibility = View.GONE
            binding.nextBtn.visibility = View.GONE
            return
        }
        binding.prevBtn.visibility = View.VISIBLE
        binding.nextBtn.visibility = View.VISIBLE

        val g = ContextCompat.getColor(this, R.color.tint_disabled)
        val w = ContextCompat.getColor(this, R.color.tint_normal)
        binding.prevBtn.imageTintList = ColorStateList.valueOf(if (plPos == 0) g else w)
        binding.nextBtn.imageTintList = ColorStateList.valueOf(if (plPos == plCount-1) g else w)
    }

    /**
     * Reads the chapter list from mpv and pushes tick positions to the seekbar.
     * Also shows/hides [nextChapterBtn] depending on whether chapters exist.
     */
    private fun updateChapterMarkers() {
        val duration = psc.durationSec
        val chapters = player.loadChapters()
        val hasChapters = chapters.isNotEmpty()

        binding.nextChapterBtn.visibility = if (hasChapters) View.VISIBLE else View.GONE

        if (!hasChapters || duration <= 0) {
            binding.playbackSeekbar.clearChapters()
            return
        }

        binding.playbackSeekbar.setChapters(chapters.map { it.time }, duration.toDouble())
    }

    /**
     * Shows a single-choice dialog listing all chapters; selecting one jumps to it.
     * Long-pressing [nextChapterBtn] triggers this.
     */
    private fun showChapterPickerDialog() {
        val chapters = player.loadChapters()
        if (chapters.isEmpty()) return
        val restore = keepPlaybackForDialog()
        val items = chapters.map { ch ->
            val timecode = Utils.prettyTime(ch.time.roundToInt())
            if (ch.title != null)
                getString(R.string.ui_chapter, ch.title, timecode)
            else
                getString(R.string.ui_chapter_fallback, ch.index + 1, timecode)
        }.toTypedArray()
        val selected = MPVLib.getPropertyInt("chapter") ?: 0
        androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle(R.string.dialog_title_chapter)
            .setSingleChoiceItems(items, selected) { dialog, item ->
                MPVLib.setPropertyInt("chapter", chapters[item].index)
                dialog.dismiss()
            }
            .setNegativeButton(R.string.dialog_cancel) { d, _ -> d.cancel() }
            .setOnDismissListener { restore() }
            .show()
    }

    private fun updateOrientation(initial: Boolean = false) {
        // screen orientation is fixed (Android TV)
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_SCREEN_PORTRAIT))
            return

        if (autoRotationMode != "auto") {
            if (!initial)
                return // don't reset at runtime
            requestedOrientation = when (autoRotationMode) {
                "landscape" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                "portrait" -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
                else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            }
        }
        if (initial || player.vid == -1)
            return

        val ratio = player.getVideoAspect()?.toFloat() ?: 0f
        if (ratio == 0f || ratio in (1f / ASPECT_RATIO_MIN) .. ASPECT_RATIO_MIN) {
            // video is square, let Android do what it wants
            requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            return
        }
        requestedOrientation = if (ratio > 1f)
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
    }

    @RequiresApi(26)
    private fun makeRemoteAction(@DrawableRes icon: Int, @StringRes title: Int, intentAction: String): RemoteAction {
        val intent = NotificationButtonReceiver.createIntent(this, intentAction)
        return RemoteAction(Icon.createWithResource(this, icon), getString(title), "", intent)
    }

    /**
     * Update Picture-in-picture parameters. Will only run if in PiP mode unless
     * `force` is set.
     */
    private fun updatePiPParams(force: Boolean = false) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O)
            return
        if (!isInPictureInPictureMode && !force)
            return

        try {
            setPictureInPictureParams(buildPiPParams())
        } catch (e: IllegalArgumentException) {
            // Android has some limits of what the aspect ratio can be
            setPictureInPictureParams(buildPiPParams(Rational(1, 1)))
        }
    }

    @RequiresApi(26)
    private fun buildPiPParams(fallbackAspectRatio: Rational? = null): PictureInPictureParams {
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
                (player.getVideoAspect() ?: 0.0).times(10000).toInt(),
                10000
            )
            setAspectRatio(aspect)
            setActions(actions)
            build()
        }
    }

    // Media Session handling

    private val mediaSessionCallback = object : MediaSessionCompat.Callback() {
        override fun onPause() {
            player.paused = true
        }
        override fun onPlay() {
            player.paused = false
        }
        override fun onSeekTo(pos: Long) {
            player.timePos = (pos / 1000.0)
        }
        override fun onSkipToNext() = playlistNext()
        override fun onSkipToPrevious() = playlistPrev()
        override fun onSetRepeatMode(repeatMode: Int) {
            MPVLib.setPropertyString("loop-playlist",
                if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ALL) "inf" else "no")
            MPVLib.setPropertyString("loop-file",
                if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ONE) "inf" else "no")
        }
        override fun onSetShuffleMode(shuffleMode: Int) {
            player.changeShuffle(false, shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL)
        }
    }

    private fun initMediaSession(): MediaSessionCompat {
        /*
            https://developer.android.com/guide/topics/media-apps/working-with-a-media-session
            https://developer.android.com/guide/topics/media-apps/audio-app/mediasession-callbacks
            https://developer.android.com/reference/android/support/v4/media/session/MediaSessionCompat
         */
        val session = MediaSessionCompat(this, TAG)
        session.setFlags(0)
        session.setCallback(mediaSessionCallback)
        return session
    }

    private fun updateMediaSession() {
        synchronized (psc) {
            mediaSession?.let { psc.write(it) }
        }
    }

    // mpv events

    private fun eventPropertyUi(property: String, dummy: Any?, metaUpdated: Boolean) {
        if (!activityIsForeground) return
        when (property) {
            "track-list" -> {
                player.loadTracks()
                maybeApplyShieldHi10pFallback()
            }
            "current-tracks/audio/selected", "current-tracks/video/image" -> {
                updateAudioUI()
                maybeApplyShieldHi10pFallback()
            }
            "hwdec-current" -> {
                updateDecoderButton()
                updateGpuNextRetryConfirmation()
            }
        }
        if (metaUpdated)
            updateMetadataDisplay()
    }

    private fun eventPropertyUi(property: String, value: Boolean) {
        if (!activityIsForeground) return
        when (property) {
            "pause" -> updatePlaybackStatus(value)
            "mute" -> { // indirectly from updateAudioPresence()
                updateAudioUI()
            }
        }
    }

    private fun eventPropertyUi(property: String, value: Long) {
        if (!activityIsForeground) return
        when (property) {
            "playlist-pos", "playlist-count" -> updatePlaylistButtons()
        }
    }

    private fun eventPropertyUi(property: String, value: Double) {
        if (!activityIsForeground) return
        when (property) {
            "time-pos/full" -> updatePlaybackTimeline(psc.position)
            "duration/full" -> updatePlaybackDuration(psc.duration)
            "video-params/aspect", "video-params/rotate" -> {
                updateOrientation()
                updatePiPParams()
            }
        }
    }

    private fun eventPropertyUi(property: String, value: String, metaUpdated: Boolean) {
        if (!activityIsForeground) return
        when (property) {
            "speed" -> updateSpeedButton()
            "current-vo" -> {
                updateDecoderButton()
                updateGpuNextRetryConfirmation()
            }
        }
        if (metaUpdated)
            updateMetadataDisplay()
    }

    private fun eventUi(eventId: Int) {
        if (!activityIsForeground) return
        // empty
    }

    override fun eventProperty(property: String) {
        val metaUpdated = psc.update(property)
        if (metaUpdated)
            updateMediaSession()
        if (property == "loop-file" || property == "loop-playlist") {
            mediaSession?.setRepeatMode(when (player.getRepeat()) {
                2 -> PlaybackStateCompat.REPEAT_MODE_ONE
                1 -> PlaybackStateCompat.REPEAT_MODE_ALL
                else -> PlaybackStateCompat.REPEAT_MODE_NONE
            })
        } else if (property == "current-tracks/audio/selected") {
            updateAudioPresence()
            if (persistAudioFilters) {
                rebuildAudioFilters()
                eventUiHandler.post { refreshAllFilterTints() }
            }
        }

        if (property == "pause" || property == "current-tracks/audio/selected")
            handleAudioFocus()

        if (!activityIsForeground) return
        eventUiHandler.post { eventPropertyUi(property, null, metaUpdated) }
    }

    override fun eventProperty(property: String, value: Boolean) {
        val metaUpdated = psc.update(property, value)
        if (metaUpdated)
            updateMediaSession()
        if (property == "shuffle") {
            mediaSession?.setShuffleMode(if (value)
                PlaybackStateCompat.SHUFFLE_MODE_ALL
            else
                PlaybackStateCompat.SHUFFLE_MODE_NONE)
        } else if (property == "mute") {
            updateAudioPresence()
        }

        if (metaUpdated || property == "mute")
            handleAudioFocus()

        if (!activityIsForeground) return
        eventUiHandler.post { eventPropertyUi(property, value) }
    }

    override fun eventProperty(property: String, value: Long) {
        if (psc.update(property, value))
            updateMediaSession()

        if (!activityIsForeground) return
        eventUiHandler.post { eventPropertyUi(property, value) }
    }

    override fun eventProperty(property: String, value: Double) {
        if (psc.update(property, value))
            updateMediaSession()

        if (!activityIsForeground) return
        eventUiHandler.post { eventPropertyUi(property, value) }
    }

    override fun eventProperty(property: String, value: String) {
        val metaUpdated = psc.update(property, value)
        if (metaUpdated)
            updateMediaSession()

        if (!activityIsForeground) return
        eventUiHandler.post { eventPropertyUi(property, value, metaUpdated) }
    }

    override fun event(eventId: Int) {
        if (eventId == MpvEvent.MPV_EVENT_END_FILE) {
            psc.eof()
            updateMediaSession()
        }

        if (eventId == MpvEvent.MPV_EVENT_SHUTDOWN)
            finishWithResult(if (playbackHasStarted) RESULT_OK else RESULT_CANCELED)

        if (eventId == MpvEvent.MPV_EVENT_START_FILE) {
            audioNormUnderrunHintShown = false
            gpuNextRenderFallbackStage = 0
            gpuNextCopyRetryConfirmed = false
            gpuNextCopyRetryDisplayedFrame = false
            applySessionDecoderModeIfNeeded()
            val cmds = onloadCommands.toTypedArray()
            onloadCommands.clear()
            for (c in cmds)
                MPVLib.command(c)
            if (this.statsLuaMode > 0 && !playbackHasStarted) {
                MPVLib.command(arrayOf("script-binding", "stats/display-page-${this.statsLuaMode}-toggle"))
            }
            playbackHasStarted = true
        }

        if (eventId == MpvEvent.MPV_EVENT_FILE_LOADED) {
            if (persistAudioFilters) {
                rebuildAudioFilters()
                eventUiHandler.post { refreshAllFilterTints() }
            } else if (isVoiceBoostOn() || isVolumeBoostOn() || isNightModeOn() || isAudioNormOn()) {
                voiceBoostLevel = 0
                volumeBoostDb = 0
                nightModeLevel = 0
                audioNormLevel = 0
                rebuildAudioFilters()
                eventUiHandler.post { refreshAllFilterTints() }
            }
        }

        if (!activityIsForeground) return
        eventUiHandler.post { eventUi(eventId) }
    }

    override fun logMessage(prefix: String, level: Int, text: String) {
        updateGpuNextRetryFrameConfirmation(prefix, text)
        maybeApplyGpuNextRenderFallback(prefix, level, text)

        if (audioNormUnderrunHintShown)
            return
        if (!activityIsForeground)
            return
        if (!text.contains("Audio device underrun detected", ignoreCase = true))
            return
        if (!isAudioNormOn() || isDownmixOn() || currentAudioChannelCount() < 6)
            return

        audioNormUnderrunHintShown = true
        eventUiHandler.post {
            showToast(
                getString(R.string.btn_audio_norm),
                getString(R.string.toast_audio_norm_surround_hint)
            )
        }
    }

    private fun maybeApplyGpuNextRenderFallback(prefix: String, level: Int, text: String) {
        if (!autoDecoderFallback)
            return
        if (level > MPVLib.MpvLogLevel.MPV_LOG_LEVEL_ERROR)
            return

        val requestedVo = player.requestedVideoOutput.trim().lowercase(Locale.US)
        if (!requestedVo.startsWith("gpu-next"))
            return

        val normalizedPrefix = prefix.trim().lowercase(Locale.US)
        val normalizedText = text.trim().lowercase(Locale.US)
        val isGpuNextFailure =
            normalizedPrefix.contains("gpu-next") &&
                (normalizedText.contains("failed rendering image") ||
                    normalizedText.contains("failed rendering frame") ||
                    normalizedText.contains("failed creating pass") ||
                    normalizedText.contains("shader link log")) ||
            normalizedText.contains("struct type mismatch between shaders") ||
            normalizedText.contains("acquirelatestimage failed")

        if (!isGpuNextFailure)
            return

        val activeHwdec = player.hwdecActive.trim().lowercase(Locale.US)
        val requestedHwdec = run {
            val option = MPVLib.getPropertyString("hwdec")
                ?: MPVLib.getPropertyString("options/hwdec")
                ?: ""
            option.trim().lowercase(Locale.US)
        }

        val shouldRetryWithCopyHwdec = gpuNextRenderFallbackStage == 0 &&
                activeHwdec != "mediacodec-copy" &&
                requestedHwdec != "mediacodec-copy"

        if (shouldRetryWithCopyHwdec) {
            gpuNextRenderFallbackStage = 1
            gpuNextCopyRetryConfirmed = false
            gpuNextCopyRetryDisplayedFrame = false
            Log.w(
                TAG,
                "gpu-next render failure detected, retrying with mediacodec-copy ($prefix: $text)"
            )
            player.fallbackGpuNextToCopyHwdec()
            eventUiHandler.post {
                updateDecoderButton()
                if (activityIsForeground) {
                    showToast(
                        getString(R.string.pref_gpu_next_title),
                        getString(R.string.toast_gpu_next_copy_fallback),
                        durationMs = 3200L
                    )
                }
            }
            return
        }

        if (gpuNextRenderFallbackStage == 1 &&
            (!gpuNextCopyRetryConfirmed || !gpuNextCopyRetryDisplayedFrame)
        ) {
            Log.w(
                TAG,
                "Ignoring gpu-next failure log while mediacodec-copy retry is still stabilizing ($prefix: $text)"
            )
            return
        }

        if ((gpuNextRenderFallbackStage == 1 || gpuNextRenderFallbackStage == 2) &&
            gpuNextCopyRetryConfirmed &&
            gpuNextCopyRetryDisplayedFrame
        ) {
            gpuNextRenderFallbackStage = 2
            Log.w(
                TAG,
                "gpu-next still reports render errors after the HW retry, but keeping gpu-next to match stock mpv behavior ($prefix: $text)"
            )
            return
        }

        gpuNextRenderFallbackStage = 2
        Log.w(TAG, "gpu-next render failure detected before HW retry, falling back to gpu ($prefix: $text)")
        player.fallbackGpuNextToGpu()
        eventUiHandler.post {
            updateDecoderButton()
            if (activityIsForeground) {
                showToast(
                    getString(R.string.pref_gpu_next_title),
                    getString(R.string.toast_gpu_next_fallback),
                    durationMs = 3200L
                )
            }
        }
    }

    private fun updateGpuNextRetryConfirmation() {
        if (gpuNextRenderFallbackStage != 1 || gpuNextCopyRetryConfirmed)
            return

        val activeVo = player.activeVideoOutput.trim().lowercase(Locale.US)
        val requestedVo = player.requestedVideoOutput.trim().lowercase(Locale.US)
        val activeHwdec = player.hwdecActive.trim().lowercase(Locale.US)

        if (requestedVo.startsWith("gpu-next") &&
            activeVo.startsWith("gpu-next") &&
            activeHwdec == "mediacodec-copy"
        ) {
            gpuNextCopyRetryConfirmed = true
            Log.w(TAG, "Confirmed gpu-next retry is running with mediacodec-copy")
        }
    }

    private fun updateGpuNextRetryFrameConfirmation(prefix: String, text: String) {
        if (gpuNextRenderFallbackStage != 1 || gpuNextCopyRetryDisplayedFrame)
            return

        val normalizedPrefix = prefix.trim().lowercase(Locale.US)
        val normalizedText = text.trim().lowercase(Locale.US)
        val frameShown =
            normalizedPrefix == "cplayer" &&
                (normalizedText.contains("first video frame after restart shown") ||
                    normalizedText.contains("playback restart complete"))

        if (frameShown) {
            gpuNextCopyRetryDisplayedFrame = true
            Log.w(TAG, "Confirmed gpu-next retry produced video output")
        }
    }

    // Gesture handler

    private var initialSeek = 0f
    private var initialBright = 0f
    private var initialVolume = 0
    private var maxVolume = 0
    /** 0 = initial, 1 = paused, 2 = was already paused */
    private var pausedForSeek = 0

    private fun fadeGestureText() {
        fadeHandler.removeCallbacks(fadeRunnable3)
        binding.gestureTextView.visibility = View.VISIBLE

        fadeHandler.postDelayed(fadeRunnable3, 500L)
    }

    override fun onPropertyChange(p: PropertyChange, diff: Float) {
        val gestureTextView = binding.gestureTextView
        when (p) {
            /* Drag gestures */
            PropertyChange.Init -> {
                mightWantToToggleControls = false

                initialSeek = (psc.position / 1000f)
                initialBright = Utils.getScreenBrightness(this) ?: 0.5f
                with (audioManager!!) {
                    initialVolume = getStreamVolume(STREAM_TYPE)
                    maxVolume = if (isVolumeFixed)
                        0
                    else
                        getStreamMaxVolume(STREAM_TYPE)
                }
                if (!isPlayingAudio)
                    maxVolume = 0 // disallow volume gesture if no audio
                pausedForSeek = 0

                fadeHandler.removeCallbacks(fadeRunnable3)
                gestureTextView.visibility = View.VISIBLE
                gestureTextView.text = ""
            }
            PropertyChange.Seek -> {
                // disable seeking when duration is unknown
                val duration = (psc.duration / 1000f)
                if (duration == 0f || initialSeek < 0)
                    return
                if (smoothSeekGesture && pausedForSeek == 0) {
                    pausedForSeek = if (psc.pause) 2 else 1
                    if (pausedForSeek == 1)
                        player.paused = true
                }

                val newPosExact = (initialSeek + diff).coerceIn(0f, duration)
                val newPos = newPosExact.roundToInt()
                val newDiff = (newPosExact - initialSeek).roundToInt()
                if (smoothSeekGesture) {
                    player.timePos = newPosExact.toDouble() // (exact seek)
                } else {
                    // seek faster than assigning to timePos but less precise
                    MPVLib.command(arrayOf("seek", "$newPosExact", "absolute+keyframes"))
                }
                // Note: don't call updatePlaybackTimeline() here because mpv will seek a timestamp
                // actually present in the file, and not the exact one we specified.

                val posText = Utils.prettyTime(newPos)
                val diffText = Utils.prettyTime(newDiff, true)
                gestureTextView.text = getString(R.string.ui_seek_distance, posText, diffText)
            }
            PropertyChange.Volume -> {
                if (maxVolume == 0)
                    return
                val newVolume = (initialVolume + (diff * maxVolume).toInt()).coerceIn(0, maxVolume)
                val newVolumePercent = 100 * newVolume / maxVolume
                audioManager!!.setStreamVolume(STREAM_TYPE, newVolume, 0)

                gestureTextView.text = getString(R.string.ui_volume, newVolumePercent)
            }
            PropertyChange.Bright -> {
                val lp = window.attributes
                val newBright = (initialBright + diff).coerceIn(0f, 1f)
                lp.screenBrightness = newBright
                window.attributes = lp

                gestureTextView.text = getString(R.string.ui_brightness, (newBright * 100).roundToInt())
            }
            PropertyChange.Finalize -> {
                if (pausedForSeek == 1)
                    player.paused = false
                gestureTextView.visibility = View.GONE
            }

            /* Tap gestures */
            PropertyChange.SeekFixed -> {
                val seekTime = diff * 10f
                val newPos = psc.positionSec + seekTime.toInt() // only for display
                MPVLib.command(arrayOf("seek", seekTime.toString(), "relative"))

                val diffText = Utils.prettyTime(seekTime.toInt(), true)
                gestureTextView.text = getString(R.string.ui_seek_distance, Utils.prettyTime(newPos), diffText)
                fadeGestureText()
            }
            PropertyChange.PlayPause -> player.cyclePause()
            PropertyChange.Custom -> {
                val keycode = 0x10002 + diff.toInt()
                MPVLib.command(arrayOf("keypress", "0x%x".format(keycode)))
            }
        }
    }

    companion object {
        private const val TAG = "mpv"
        // how long should controls be displayed on screen (ms)
        private const val CONTROLS_DISPLAY_TIMEOUT = 10_000L
        // how long controls fade to disappear (ms)
        private const val CONTROLS_FADE_DURATION = 500L
        // resolution (px) of the thumbnail displayed with playback notification
        private const val THUMB_SIZE = 384
        // smallest aspect ratio that is considered non-square
        private const val ASPECT_RATIO_MIN = 1.2f // covers 5:4 and up
        // fraction to which audio volume is ducked on loss of audio focus
        private const val AUDIO_FOCUS_DUCKING = 0.5f
        // request codes for invoking other activities
        private const val RCODE_EXTERNAL_AUDIO = 1000
        private const val RCODE_EXTERNAL_SUB = 1001
        private const val RCODE_LOAD_FILE = 1002
        // action of result intent
        private const val RESULT_INTENT = "app.mpvnova.player.MPVActivity.result"
        // stream type used with AudioManager
        private const val STREAM_TYPE = AudioManager.STREAM_MUSIC
        // precision used by seekbar (1/s)
        private const val SEEK_BAR_PRECISION = 1000L
        // step used when the seekbar is the active TV dpad target
        private const val SEEK_BAR_DPAD_STEP_MS = 1000L
    }
}
