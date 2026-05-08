package app.mpvnova.player

import android.content.Context
import android.os.Build
import android.os.Environment
import android.util.AttributeSet
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.preference.PreferenceManager
import app.mpvnova.player.MpvFormat.MPV_FORMAT_DOUBLE
import app.mpvnova.player.MpvFormat.MPV_FORMAT_FLAG
import app.mpvnova.player.MpvFormat.MPV_FORMAT_INT64
import app.mpvnova.player.MpvFormat.MPV_FORMAT_NONE
import app.mpvnova.player.MpvFormat.MPV_FORMAT_STRING
import kotlin.reflect.KProperty

private data class PreferredOption(val preferenceName: String, val mpvOption: String, val default: String = "")

internal const val MPV_VIEW_LOG_TAG = "mpv"
internal const val MPV_VIEW_HWDECS = "mediacodec,mediacodec-copy"
internal const val MPV_VIEW_SHIELD_H10P_DEMUXER_BYTES = 50 * 1024 * 1024
internal const val MPV_VIEW_MIN_VALID_ASPECT = 0.001
internal const val MPV_VIEW_HALF_ROTATION_DEGREES = 180
internal const val MPV_VIEW_RIGHT_ANGLE_DEGREES = 90
internal const val MPV_VIEW_MODERN_DEMUXER_CACHE_MIB = 64
internal const val MPV_VIEW_LEGACY_DEMUXER_CACHE_MIB = 32
internal const val MPV_VIEW_BYTES_PER_MIB = 1024 * 1024
private const val PLAYBACK_SPEED_HALF = 0.5
private const val PLAYBACK_SPEED_THREE_QUARTERS = 0.75
private const val PLAYBACK_SPEED_NORMAL = 1.0
private const val PLAYBACK_SPEED_ONE_AND_QUARTER = 1.25
private const val PLAYBACK_SPEED_ONE_AND_HALF = 1.5
private const val PLAYBACK_SPEED_ONE_AND_THREE_QUARTERS = 1.75
private const val PLAYBACK_SPEED_DOUBLE = 2.0
internal val MPV_VIEW_PLAYBACK_SPEED_STEPS = doubleArrayOf(
    PLAYBACK_SPEED_HALF,
    PLAYBACK_SPEED_THREE_QUARTERS,
    PLAYBACK_SPEED_NORMAL,
    PLAYBACK_SPEED_ONE_AND_QUARTER,
    PLAYBACK_SPEED_ONE_AND_HALF,
    PLAYBACK_SPEED_ONE_AND_THREE_QUARTERS,
    PLAYBACK_SPEED_DOUBLE
)

internal class MPVView(context: Context, attrs: AttributeSet) : BaseMPVView(context, attrs) {
    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    override fun initOptions() {
        val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)

        mpvSetOptionString("profile", "fast")
        setVo(defaultVo(sharedPreferences))
        val hwdec = defaultHwdec(sharedPreferences)
        applyDisplayRefreshRate()
        applyPreferredOptions(sharedPreferences)
        applyVideoPreferenceOptions(sharedPreferences)
        applyCorePlaybackOptions(hwdec)
        applyScreenshotOptions()
    }

    private fun applyDisplayRefreshRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val disp = ContextCompat.getDisplayOrDefault(context)
            val refreshRate = disp.mode.refreshRate

            Log.v(MPV_VIEW_LOG_TAG, "Display ${disp.displayId} reports FPS of $refreshRate")
            mpvSetOptionString("display-fps-override", refreshRate.toString())
        } else {
            Log.v(MPV_VIEW_LOG_TAG, "Android version too old, disabling refresh rate functionality " +
                       "(${Build.VERSION.SDK_INT} < ${Build.VERSION_CODES.M})")
        }
    }

    private fun applyPreferredOptions(sharedPreferences: android.content.SharedPreferences) {
        val opts = arrayOf(
                PreferredOption("default_audio_language", "alang", "eng"),
                PreferredOption("default_subtitle_language", "slang", "eng"),

                PreferredOption("video_scale", "scale"),
                PreferredOption("video_scale_param1", "scale-param1"),
                PreferredOption("video_scale_param2", "scale-param2"),

                PreferredOption("video_downscale", "dscale"),
                PreferredOption("video_downscale_param1", "dscale-param1"),
                PreferredOption("video_downscale_param2", "dscale-param2"),

                PreferredOption("video_tscale", "tscale"),
                PreferredOption("video_tscale_param1", "tscale-param1"),
                PreferredOption("video_tscale_param2", "tscale-param2")
        )

        for ((preferenceName, mpvOption, default) in opts) {
            val preference = sharedPreferences.getString(preferenceName, default)
            if (!preference.isNullOrBlank())
                mpvSetOptionString(mpvOption, preference)
        }
    }

    private fun applyVideoPreferenceOptions(sharedPreferences: android.content.SharedPreferences) {
        val debandMode = sharedPreferences.getString("video_debanding", "")
        if (debandMode == "gradfun") {
            // lower the default radius (16) to improve performance
            mpvSetOptionString("vf", "gradfun=radius=12")
        } else if (debandMode == "gpu") {
            mpvSetOptionString("deband", "yes")
        }

        mpvSetOptionString("video-sync", defaultVideoSync(sharedPreferences))

        if (sharedPreferences.getBoolean("video_interpolation", false))
            mpvSetOptionString("interpolation", "yes")

        if (sharedPreferences.getBoolean("gpudebug", false))
            mpvSetOptionString("gpu-debug", "yes")

        if (sharedPreferences.getBoolean("video_fastdecode", false)) {
            mpvSetOptionString("vd-lavc-fast", "yes")
            mpvSetOptionString("vd-lavc-skiploopfilter", "nonkey")
        }
    }

    private fun applyCorePlaybackOptions(hwdec: String) {
        mpvSetOptionString("gpu-context", "android")
        mpvSetOptionString("opengl-es", "yes")
        mpvSetOptionString("hwdec", hwdec)
        mpvSetOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
        mpvSetOptionString("ao", "audiotrack,opensles")
        mpvSetOptionString("audio-set-media-role", "yes")
        mpvSetOptionString("tls-verify", "yes")
        mpvSetOptionString("tls-ca-file", "${this.context.filesDir.path}/cacert.pem")
        mpvSetOptionString("input-default-bindings", "yes")
        // Never let libmpv draw its legacy seek/progress OSD. The app provides
        // its own timeline UI and routes supported seek inputs through it.
        mpvSetOptionString("osd-on-seek", "no")
        mpvSetOptionString("osd-bar", "no")
        // Give libass more room for complex ASS/SSA subtitle effects before it
        // has to evict cached glyphs/bitmaps and re-rasterize them mid-scene.
        mpvSetOptionString("sub-bitmap-max-size", "256")
        mpvSetOptionString("sub-glyph-limit", "200000")
        // Limit demuxer cache since the defaults are too high for mobile devices
        val cacheBytes = defaultDemuxerCacheBytes()
        mpvSetOptionString("demuxer-max-bytes", cacheBytes.toString())
        mpvSetOptionString("demuxer-max-back-bytes", cacheBytes.toString())
    }

    private fun applyScreenshotOptions() {
        val screenshotDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES)
        screenshotDir.mkdirs()
        mpvSetOptionString("screenshot-directory", screenshotDir.path)
        mpvSetOptionString("vd-lavc-film-grain", "cpu")
    }

    override fun postInitOptions() {
        mpvSetOptionString("save-position-on-quit", "no")
    }

    override fun observeProperties() {
        data class Property(val name: String, val format: Int = MPV_FORMAT_NONE)
        val p = arrayOf(
            Property("time-pos/full", MPV_FORMAT_DOUBLE),
            Property("duration/full", MPV_FORMAT_DOUBLE),
            Property("pause", MPV_FORMAT_FLAG),
            Property("paused-for-cache", MPV_FORMAT_FLAG),
            Property("speed", MPV_FORMAT_STRING),
            Property("track-list"),
            Property("video-params/aspect", MPV_FORMAT_DOUBLE),
            Property("video-params/rotate", MPV_FORMAT_DOUBLE),
            Property("playlist-pos", MPV_FORMAT_INT64),
            Property("playlist-count", MPV_FORMAT_INT64),
            Property("current-tracks/video/image"),
            Property("media-title", MPV_FORMAT_STRING),
            Property("metadata"),
            Property("loop-playlist"),
            Property("loop-file"),
            Property("shuffle", MPV_FORMAT_FLAG),
            Property("hwdec-current"),
            Property("current-vo", MPV_FORMAT_STRING),
            Property("mute", MPV_FORMAT_FLAG),
            Property("current-tracks/audio/selected")
        )

        for ((name, format) in p)
            mpvObserveProperty(name, format)
    }

    data class Track(val mpvId: Int, val name: String)
    var tracks = mapOf<String, MutableList<Track>>(
            "audio" to arrayListOf(),
            "video" to arrayListOf(),
            "sub" to arrayListOf())

    data class PlaylistItem(val index: Int, val filename: String, val title: String?)

    data class Chapter(val index: Int, val title: String?, val time: Double)

    class TrackDelegate(private val name: String) {
        operator fun getValue(thisRef: Any?, property: KProperty<*>): Int {
            val v = mpvGetPropertyString(name)
            // we can get null here for "no" or other invalid value
            return v?.toIntOrNull() ?: -1
        }
        operator fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
            if (value == -1)
                mpvSetPropertyString(name, "no")
            else
                mpvSetPropertyInt(name, value)
        }
    }

    var vid: Int by TrackDelegate("vid")
    var sid: Int by TrackDelegate("sid")
    var secondarySid: Int by TrackDelegate("secondary-sid")
    var aid: Int by TrackDelegate("aid")

    companion object {
        const val DECODER_MODE_HW_PLUS = "hw_plus"
        const val DECODER_MODE_HW = "hw"
        const val DECODER_MODE_SW = "sw"
        const val DECODER_MODE_GNEXT = "g_next"
        const val DECODER_MODE_SHIELD_H10P = "shield_h10p"
    }
}
