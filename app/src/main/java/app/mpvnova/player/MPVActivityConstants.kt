package app.mpvnova.player

import android.content.Intent
import android.media.AudioManager

internal const val MPV_ACTIVITY_TAG = "mpv"
internal const val RESULT_OK = android.app.Activity.RESULT_OK
internal const val RESULT_CANCELED = android.app.Activity.RESULT_CANCELED
internal const val DEFAULT_CONTROLS_DISPLAY_TIMEOUT = 5_000L
// Controls-overlay fade-out. Snappy 280ms paired with a Material
// accelerate curve so the overlay clears in one motion.
internal const val CONTROLS_FADE_DURATION = 280L
internal const val PLAYER_TOAST_FADE_IN_MS = 140L
internal const val PLAYER_TOAST_FADE_OUT_MS = 180L
internal const val LOADING_OVERLAY_FADE_MS = 180L
internal const val TOAST_UNTITLED_BASE_MS = 1_800L
internal const val TOAST_UNTITLED_MAX_CHARS = 120
internal const val TOAST_UNTITLED_PER_CHAR_MS = 8L
internal const val TOAST_UNTITLED_MAX_MS = 3_000L
internal const val TOAST_TITLED_BASE_MS = 3_200L
internal const val TOAST_TITLED_MAX_CHARS = 180
internal const val TOAST_TITLED_PER_CHAR_MS = 14L
internal const val TOAST_TITLED_MAX_MS = 5_600L
internal const val TOAST_TOP_WITH_TITLE_DP = 96f
internal const val TOAST_TOP_NO_TITLE_DP = 22f
internal const val CLOCK_TICK_INTERVAL_MS = 30_000L
internal const val MIN_CLOCK_TICK_DELAY_MS = 1_000L
internal const val BACKGROUND_SERVICE_STOP_DELAY_MS = 1_000L
internal const val THUMB_SIZE = 384
internal const val ASPECT_RATIO_MIN = 1.2f
internal const val AUDIO_FOCUS_DUCKING = 0.5f
internal const val RESULT_INTENT = "is.xyz.mpv.MPVActivity.result"
internal const val STREAM_TYPE = AudioManager.STREAM_MUSIC
internal const val MILLIS_PER_SECOND_LONG = 1_000L
internal const val MPV_MILLIS_PER_SECOND_FLOAT = 1_000f
internal const val MPV_MILLIS_PER_SECOND_DOUBLE = 1_000.0
internal const val SEEK_BAR_PRECISION = 1000L
internal const val PLAYER_SEEKBAR_UI_INTERVAL_MS = 125L
// Coalesce mpv's ~60Hz time-pos burst into a handful of UI updates/sec.
internal const val TIME_POS_UI_COALESCE_DELAY_MS = 200L
internal const val SEEKBAR_SEEK_DEBOUNCE_MS = 90L
internal const val DPAD_SEEK_DEBOUNCE_MS = 140L
internal const val DPAD_SEEK_APPLY_INTERVAL_MS = 250L
internal const val SEEK_BAR_DPAD_STEP_MS = 1000L
internal const val SEEK_DEFAULT_DPAD_STEP_MS = 10_000L
internal const val SEEK_SLOW_STEP_MS = 8_000L
internal const val SEEK_MEDIUM_STEP_MS = 10_000L
internal const val SEEK_FAST_STEP_MS = 12_000L
internal const val SEEK_SLOW_REPEAT_THRESHOLD = 1
internal const val SEEK_MEDIUM_REPEAT_THRESHOLD = 8
internal const val SEEK_FAST_REPEAT_THRESHOLD = 18
internal const val CHAPTER_SKIP_EPSILON_SEC = 0.25
internal const val CHAPTER_SEEK_MEMORY_MS = 500L
internal const val PERIODIC_SAVE_INTERVAL_MS = 30_000L
internal const val RESUME_NEAR_END_MS = 30_000L
internal const val RESUME_TABLE_MAX_AGE_MS = 30L * 24L * 60L * 60L * MILLIS_PER_SECOND_LONG
internal const val RESUME_TABLE_MAX_ENTRIES = 500
internal const val RESUME_ENTRY_PART_COUNT = 3
internal const val RESUME_ENTRY_TIMESTAMP_INDEX = 2
internal const val RESUME_FILE_TOKEN_MAX_LENGTH = 120
internal const val RESUME_FILE_TOKEN_MIN_LENGTH = 3
internal const val RESUME_TOAST_MIN_POSITION_MS = 60_000L
internal const val RESUME_TOAST_DURATION_MS = 3_000L
internal const val MONO_CHANNEL_COUNT = 1
internal const val STEREO_CHANNEL_COUNT = 2
internal const val FRONT_3_0_CHANNEL_COUNT = 3
internal const val QUAD_CHANNEL_COUNT = 4
internal const val SURROUND_5_0_CHANNEL_COUNT = 5
internal const val SURROUND_5_1_CHANNEL_COUNT = 6
internal const val SURROUND_6_1_CHANNEL_COUNT = 7
internal const val SURROUND_7_1_CHANNEL_COUNT = 8
internal const val MIN_SURROUND_CHANNELS = 6
internal const val TRACK_MEMORY_MIN_SCORE = 0.5
internal const val GPU_NEXT_FALLBACK_TOAST_MS = 5_200L
// Wait for decoder + VO to settle after the Shield Hi10p fallback before
// firing the resync seek.
internal const val SHIELD_FALLBACK_RESYNC_DELAY_MS = 900L
internal const val DEFAULT_AUDIO_SAMPLE_RATE = 48_000
internal const val DB_TO_LINEAR_BASE = 10.0
internal const val DB_POWER_DIVISOR = 20.0
internal const val PERCENT_SCALE_DOUBLE = 100.0
internal const val PERCENT_SCALE_FLOAT = 100f
internal const val MIN_PLAYER_SCREEN_BRIGHTNESS_PERCENT = 1
internal const val MAX_PLAYER_SCREEN_BRIGHTNESS_PERCENT = 100
internal const val DEFAULT_PLAYER_SCREEN_BRIGHTNESS_PERCENT = 100
internal const val MAX_PLAYER_BRIGHTNESS_DIM_ALPHA = 0.99f
internal const val DEFAULT_SUB_SCALE_INDEX = 3
internal const val DEFAULT_SUB_POSITION_INDEX = 25
internal const val DEFAULT_SECONDARY_SUB_POSITION_INDEX = 5
internal const val DEFAULT_SUB_SCALE = 1.0
internal const val DEFAULT_SUB_POSITION_PERCENT = 100
internal const val DEFAULT_SECONDARY_SUB_POSITION_PERCENT = 0
internal const val SUB_POSITION_MIN_PERCENT = -25
internal const val SUB_POSITION_MAX_PERCENT = 125
internal const val SUB_POSITION_STEP_PERCENT = 5
internal const val PLAYER_TITLE_HORIZONTAL_MARGIN_DP = 64f
internal const val PLAYER_TITLE_MIN_WIDTH_DP = 260f
internal const val PLAYER_TITLE_MAX_WIDTH_DP = 980f
internal const val DPAD_LONG_PRESS_MS = 500L
internal const val DPAD_CONTROLS_SCRATCH_CAPACITY = 24
internal const val FLOATING_CONTROLS_BOTTOM_MARGIN_DP = 60f
internal const val FLOATING_CONTROLS_SIDE_MARGIN_LANDSCAPE_DP = 60f
internal const val STATS_PAGE_FIRST = 1
internal const val STATS_PAGE_LAST = 3
internal const val VIDEO_ADJUSTMENT_MIN_INT = -100
internal const val VIDEO_ADJUSTMENT_MAX_INT = 100
internal const val VIDEO_ADJUSTMENT_DEFAULT_INT = 0
internal const val AUDIO_DELAY_MIN_SEC = -600.0
internal const val AUDIO_DELAY_MAX_SEC = 600.0
internal const val SUB_DELAY_MIN_SEC = -600.0
internal const val SUB_DELAY_MAX_SEC = 600.0
internal const val ADVANCED_SUB_DELAY_DIALOG_WIDTH_FRACTION = 0.56f
internal const val ADVANCED_SUB_DELAY_DIALOG_MAX_WIDTH_DP = 620f
internal const val ADVANCED_SUB_DELAY_DIALOG_HEIGHT_FRACTION = 0.72f
internal const val ADVANCED_SUB_DELAY_DIALOG_MAX_HEIGHT_DP = 520f
// Player settings drawer (right-edge VIMU-style panel). Wider than other
// in-player dialogs since it has two columns (content + tabs).
internal const val DRAWER_WIDTH_FRACTION = 0.52f
internal const val DRAWER_MAX_WIDTH_DP = 800f
// Window for "press Back twice to exit". Long enough to be comfortable,
// short enough that stray presses don't half-arm the next real exit.
internal const val DOUBLE_BACK_WINDOW_MS = 2000L
internal const val SQUARE_ASPECT_RATIO = 1
internal const val PIP_ASPECT_RATIO_SCALE = 10_000
internal const val REMOTE_ACTION_EMPTY_TEXT = ""
internal const val PREF_REMOTE_NEXT_CHAPTER_BUTTON = "remote_next_chapter_button"
internal const val REMOTE_BUTTON_DISABLED = "off"
internal const val BOOST_LIMIT_LIGHT_DB = 4
internal const val BOOST_LIMIT_MODERATE_DB = 8
internal const val BOOST_LIMIT_STRONG_DB = 12
internal const val BOOST_LIMIT_HIGH_DB = 15
internal const val DOWNMIX_LIGHT_LEVEL = 1
internal const val DOWNMIX_BALANCED_LEVEL = 2
internal const val DOWNMIX_STRONG_LEVEL = 3
internal const val DOWNMIX_HIGH_LEVEL = 4
internal const val DOWNMIX_MAX_LEVEL = 5
internal const val VOLUME_BOOST_STEP_OFF_DB = 0
internal const val VOLUME_BOOST_STEP_SOFT_DB = 2
internal const val VOLUME_BOOST_STEP_LIGHT_DB = 4
internal const val VOLUME_BOOST_STEP_MODERATE_DB = 6
internal const val VOLUME_BOOST_STEP_STRONG_DB = 8
internal const val VOLUME_BOOST_STEP_HIGH_DB = 10
internal const val VOLUME_BOOST_STEP_VERY_HIGH_DB = 12
internal const val VOLUME_BOOST_STEP_MAX_SAFE_DB = 15
internal const val VOLUME_BOOST_STEP_EXTRA_DB = 18
internal const val VOLUME_BOOST_STEP_EXTREME_DB = 21
internal const val SUB_SCALE_MIN = 0.5
internal const val SUB_SCALE_SMALL = 0.65
internal const val SUB_SCALE_LOW = 0.8
internal const val SUB_SCALE_HIGH = 1.15
internal const val SUB_SCALE_LARGE = 1.3
internal const val SUB_SCALE_XL = 1.5
internal const val SUB_SCALE_XXL = 1.75
internal const val SUB_SCALE_MAX = 2.0
internal val VLC_TITLE_EXTRA_KEYS = arrayOf(
    "title",
    Intent.EXTRA_TITLE,
    Intent.EXTRA_SUBJECT,
)
internal val VOLUME_BOOST_STEPS_DB = intArrayOf(
    VOLUME_BOOST_STEP_OFF_DB,
    VOLUME_BOOST_STEP_SOFT_DB,
    VOLUME_BOOST_STEP_LIGHT_DB,
    VOLUME_BOOST_STEP_MODERATE_DB,
    VOLUME_BOOST_STEP_STRONG_DB,
    VOLUME_BOOST_STEP_HIGH_DB,
    VOLUME_BOOST_STEP_VERY_HIGH_DB,
    VOLUME_BOOST_STEP_MAX_SAFE_DB,
    VOLUME_BOOST_STEP_EXTRA_DB,
    VOLUME_BOOST_STEP_EXTREME_DB,
)
internal val SUB_SCALE_STEPS = doubleArrayOf(
    SUB_SCALE_MIN,
    SUB_SCALE_SMALL,
    SUB_SCALE_LOW,
    DEFAULT_SUB_SCALE,
    SUB_SCALE_HIGH,
    SUB_SCALE_LARGE,
    SUB_SCALE_XL,
    SUB_SCALE_XXL,
    SUB_SCALE_MAX,
)
internal val SUB_POSITION_STEPS =
    (SUB_POSITION_MIN_PERCENT..SUB_POSITION_MAX_PERCENT step SUB_POSITION_STEP_PERCENT)
        .toList()
        .toIntArray()
internal val RESUME_HASH_REGEX = Regex("/([a-fA-F0-9]{40,64})(?=/|$)")
internal val FILE_EXTENSION_REGEX = Regex("""\.[a-z0-9]{2,5}$""")
internal val NON_ALNUM_REGEX = Regex("""[^a-z0-9]+""")
internal val TITLE_NON_ALNUM_REGEX = Regex("[^a-z0-9 ]")
internal val WHITESPACE_REGEX = Regex("\\s+")
internal val GPU_NEXT_RETRY_STAGES = setOf(1, 2)
internal val GPU_NEXT_RENDER_FAILURE_TEXT = listOf(
    "failed rendering image",
    "failed rendering frame",
    "failed creating pass",
    "shader link log"
)
internal val GPU_NEXT_GENERAL_FAILURE_TEXT = listOf(
    "struct type mismatch between shaders",
    "acquirelatestimage failed"
)
