package app.mpvnova.player

import android.app.Activity
import android.content.Context
import android.content.res.AssetManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Parcelable
import android.os.storage.StorageManager
import android.os.storage.StorageVolume
import android.provider.Settings
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.text.InputType
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.os.BundleCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.widget.addTextChangedListener
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import app.mpvnova.player.databinding.DialogUrlInputBinding
import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.roundToInt

private const val TAG = "mpv"
private const val SECONDS_PER_MINUTE = 60
private const val SECONDS_PER_HOUR = 3_600
private const val MAX_SINGLE_DIGIT = 9
private const val SYSTEM_BRIGHTNESS_MAX = 255f
private const val MILLIS_PER_SECOND = 1_000L
private const val MILLIS_PER_SECOND_FLOAT = 1_000f
private const val MILLIS_PER_SECOND_DOUBLE = 1_000.0
private val IGNORED_MOUNT_PREFIXES = listOf("/proc", "/sys", "/dev", "/apex")

private object AssetCopier {
    fun copyFile(
        assetManager: AssetManager,
        filename: String,
        outFile: File,
        force: Boolean = false,
    ): Boolean {
        return try {
            assetManager.open(filename, AssetManager.ACCESS_STREAMING).use { input ->
                copyIfNeeded(input, outFile, filename, force)
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to copy asset file: $filename", e)
            false
        }
    }

    private fun copyIfNeeded(
        input: InputStream,
        outFile: File,
        filename: String,
        force: Boolean,
    ): Boolean {
        // Note that .available() will return the full file size for asset streams, and it even works
        // for compressed assets. Though none of this is documented...
        val avail = input.available().toLong()
        if (!force && outFile.isFile && outFile.length() == avail) {
            Log.v(TAG, "Skipping copy of asset file (exists same size): $filename")
            return true
        }
        return copyStream(input, outFile, avail, filename)
    }

    private fun copyStream(input: InputStream, outFile: File, avail: Long, filename: String): Boolean {
        outFile.parentFile?.mkdirs()
        val temporaryFile = File(outFile.parentFile, ".${outFile.name}.tmp")
        try {
            temporaryFile.outputStream().use { output -> input.copyTo(output) }
            if (!temporaryFile.renameTo(outFile)) {
                if (outFile.exists() && !outFile.delete())
                    throw IOException("Could not replace ${outFile.path}")
                if (!temporaryFile.renameTo(outFile))
                    throw IOException("Could not move asset into ${outFile.path}")
            }
        } finally {
            temporaryFile.delete()
        }
        Log.w(TAG, "Copied asset file ($avail bytes): $filename")
        return true
    }
}

/** Write the 'fonts.conf' for fontconfig. */
private fun writeFontsConf(context: Context, configFile: File): Boolean {
    val parts = mutableListOf(
        "<fontconfig>",
        // Android system fonts reside here
        "<dir>/system/fonts/</dir>",
        "<dir>/product/fonts/</dir>",
        // Bundled + user-imported subtitle fonts (see copyAssets / importSubtitleFont)
        "<dir>${context.filesDir.path}/fonts</dir>",
        // Point fontconfig to the right cache path so that caching works
        "<cachedir>${context.cacheDir.path}</cachedir>",
        // Conveniently there is *no* Java API to query the system default fonts, but we can
        // manually specify the font families we know Android uses and provides by default.
        // (compare to 60-latin.conf shipped with fontconfig)
        "<alias><family>serif</family>",
        "<prefer><family>Noto Serif</family></prefer>",
        "</alias>",
        "<alias><family>sans-serif</family>",
        "<prefer>",
        "<family>Roboto</family>",
        "<family>Noto Sans</family>", // other languages
        "</prefer>",
        "</alias>",
        "<alias><family>monospace</family>",
        "<prefer><family>Droid Sans Mono</family></prefer>",
        "</alias>",
        "</fontconfig>"
    )
    try {
        configFile.writeText(parts.joinToString("\n"))
        return true
    } catch (e: IOException) {
        Log.w(TAG, "Failed to write fonts.conf", e)
        return false
    }
}

private fun twoDigits(value: Int): String {
    return if (value in 0..MAX_SINGLE_DIGIT) "0$value" else value.toString()
}

@RequiresApi(Build.VERSION_CODES.N)
private object StorageVolumeResolver {
    @Suppress("DEPRECATION")
    fun collectCandidates(context: Context): List<String> {
        val candidates = mutableListOf<String>()
        candidates.add(Environment.getExternalStorageDirectory().absolutePath)
        runCatching {
            context.externalMediaDirs.forEach {
                if (it != null)
                    candidates.add(it.absolutePath)
            }
        }.onFailure { error ->
            Log.w(TAG, "Failed to inspect external media directories", error)
        }
        runCatching {
            File("/proc/mounts").forEachLine { line -> addProcMountCandidate(candidates, line) }
        }.onFailure { error ->
            Log.w(TAG, "Failed to inspect /proc/mounts", error)
        }
        return candidates
    }

    fun findRoot(storageManager: StorageManager, path: String): Pair<File, StorageVolume>? {
        var root = File(path)
        val vol = getStorageVolumeSafely(storageManager, root)
        if (vol == null || !vol.isReadableMounted())
            return null

        while (true) {
            val parent = root.parentFile
            if (parent == null || getStorageVolumeSafely(storageManager, parent) != vol)
                break
            root = parent
        }
        return root to vol
    }

    private fun getStorageVolumeSafely(storageManager: StorageManager, file: File): StorageVolume? {
        return try {
            storageManager.getStorageVolume(file)
        } catch (ignored: SecurityException) {
            null
        } catch (ignored: IllegalArgumentException) {
            null
        }
    }

    private fun StorageVolume.isReadableMounted(): Boolean {
        return state == Environment.MEDIA_MOUNTED || state == Environment.MEDIA_MOUNTED_READ_ONLY
    }

    private fun addProcMountCandidate(candidates: MutableList<String>, line: String) {
        val path = line.split(' ', limit = 3).getOrNull(1) ?: return
        // The rootfs mount ("/") shows up in /proc/mounts and, when
        // MANAGE_EXTERNAL_STORAGE is granted, StorageManager.getStorageVolume(/)
        // happily returns the primary volume — so "/" leaks into the list of
        // storage volumes. That breaks the picker two ways:
        //   1. File.startsWith("/") matches every absolute path, so "/"
        //      always wins as the "preferred volume" and the picker lands
        //      at filesystem root showing just ".." (see screenshot bug).
        //   2. Even after fixing the prefix match, "/" would still appear
        //      as a confusing entry in the multi-volume chooser popup.
        // Skip it outright.
        if (path == "/") return
        if (!IGNORED_MOUNT_PREFIXES.any { path.startsWith(it) }) {
            candidates.add(path)
        }
    }
}

fun visibleChildren(view: View): Int {
    if (view is ViewGroup && view.visibility == View.VISIBLE) {
        return (0 until view.childCount).sumOf { visibleChildren(view.getChildAt(it)) }
    }
    return if (view.visibility == View.VISIBLE) 1 else 0
}

inline fun <reified T: Parcelable> getParcelableArray(bundle: Bundle, key: String): Array<T> {
    val array = BundleCompat.getParcelableArray(bundle, key, T::class.java)
    return if (array == null)
        emptyArray()
    else // the result is not T[] nor castable because BundleCompat is stupid
        array.mapNotNull { it as? T }.toTypedArray()
}

/**
 * Sets the inset listener for the given view so that system bars are simply avoided by padding.
 * Note that this will modify the view's padding and probably leave ugly empty space at the top
 * (if using an action bar).
 */
fun handleInsetsAsPadding(view: View) {
    data class Padding(val left: Int, val top: Int, val right: Int, val bottom: Int)
    var originalPadding: Padding? = null
    ViewCompat.setOnApplyWindowInsetsListener(view) { _, insets ->
        val i = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        // yes, really
        val orig = originalPadding ?: Padding(
            view.paddingLeft,
            view.paddingTop,
            view.paddingRight,
            view.paddingBottom
        ).also { originalPadding = it }
        view.setPadding(
            orig.left + i.left,
            orig.top + i.top,
            orig.right + i.right,
            orig.bottom + i.bottom
        )
        insets
    }
}

@RequiresApi(Build.VERSION_CODES.N)
private fun storageVolumeDisplayName(volume: StorageVolume, context: Context, root: File): String {
    val label = runCatching {
        volume.getDescription(context).trim()
    }.getOrDefault("").ifEmpty { root.path }
    val mountId = root.name
    return if (volume.isPrimary || mountId.isEmpty() || label.contains(mountId, ignoreCase = true)) {
        label
    } else {
        "$label ($mountId)"
    }
}

internal object Utils {
    @Volatile
    private var bundledAssetsReady = false

    fun copyAssets(context: Context) {
        if (bundledAssetsReady) return
        synchronized(this) {
            if (bundledAssetsReady) return
            val assetManager = context.assets
            val files = arrayOf("cacert.pem")
            val configDir = context.filesDir.path
            val marker = File(configDir, ".bundled-assets-version")
            val sourceModified = File(context.applicationInfo.sourceDir).lastModified()
            val assetVersion = "${BuildConfig.VERSION_CODE}:$sourceModified"
            val recordedAssetVersion = runCatching { marker.readText() }.getOrNull()
            val refreshBundledAssets = recordedAssetVersion != assetVersion
            var copiedSuccessfully = true

            for (name in files) {
                copiedSuccessfully = AssetCopier.copyFile(
                    assetManager,
                    name,
                    File("$configDir/$name"),
                    force = refreshBundledAssets,
                ) && copiedSuccessfully
            }

            File("$configDir/subfont.ttf").delete()
            File("$configDir/scripts/auto_subs.lua").delete()
            val scriptsDir = File("$configDir/scripts").apply { mkdirs() }
            copiedSuccessfully = AssetCopier.copyFile(
                assetManager,
                "scripts/shield_mpeg2_fallback.lua",
                File(scriptsDir, "shield_mpeg2_fallback.lua"),
                force = refreshBundledAssets,
            ) && copiedSuccessfully

            copiedSuccessfully = copyBundledFonts(
                assetManager,
                configDir,
                force = refreshBundledAssets,
            ) && copiedSuccessfully
            val fontsConf = File("$configDir/fonts.conf")
            if (refreshBundledAssets || !fontsConf.isFile)
                copiedSuccessfully = writeFontsConf(context, fontsConf) && copiedSuccessfully

            if (copiedSuccessfully && refreshBundledAssets) {
                runCatching { marker.writeText(assetVersion) }
                    .onFailure { Log.w(TAG, "Failed to record bundled asset version", it) }
            }
            bundledAssetsReady = copiedSuccessfully
        }
    }

    private fun copyBundledFonts(
        assetManager: android.content.res.AssetManager,
        configDir: String,
        force: Boolean,
    ): Boolean {
        val fontsDir = File("$configDir/fonts").apply { mkdirs() }
        val names = runCatching { assetManager.list("fonts") }
            .onFailure { error -> Log.e(TAG, "Failed to list bundled fonts", error) }
            .getOrNull()
        var copiedSuccessfully = true
        if (names == null) {
            copiedSuccessfully = false
        } else {
            for (name in names) {
                val dest = File(fontsDir, name)
                copiedSuccessfully = AssetCopier.copyFile(
                    assetManager,
                    "fonts/$name",
                    dest,
                    force = force,
                ) && copiedSuccessfully
            }
        }
        return copiedSuccessfully
    }

    fun findRealPath(fd: Int): String? {
        var realPath: String? = null
        try {
            val path = File("/proc/self/fd/${fd}").canonicalPath
            if (!path.startsWith("/proc") && File(path).canRead()) {
                File(path).inputStream().use { it.read() }
                realPath = path
            }
        } catch (ignored: IOException) {
            realPath = null
        } catch (ignored: SecurityException) {
            realPath = null
        }
        return realPath
    }

    fun convertDp(context: Context, dp: Float): Int {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                context.resources.displayMetrics).toInt()
    }

    fun prettyTime(d: Int, sign: Boolean = false): String {
        val duration = abs(d)
        val hours = duration / SECONDS_PER_HOUR
        val minutes = duration % SECONDS_PER_HOUR / SECONDS_PER_MINUTE
        val seconds = duration % SECONDS_PER_MINUTE
        val formatted = if (hours == 0)
            "${twoDigits(minutes)}:${twoDigits(seconds)}"
        else
            "$hours:${twoDigits(minutes)}:${twoDigits(seconds)}"

        return if (sign)
            (if (d >= 0) "+" else "-") + formatted
        else
            formatted
    }

    fun getScreenBrightness(activity: Activity): Float? {
        val lp = activity.window.attributes
        if (lp.screenBrightness >= 0f)
            return lp.screenBrightness

        // read system pref: https://stackoverflow.com/questions/4544967/#answer-8114307
        // (doesn't work with auto-brightness mode)
        val resolver = activity.contentResolver
        return try {
            Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS) / SYSTEM_BRIGHTNESS_MAX
        } catch (e: Settings.SettingNotFoundException) {
            Log.d(TAG, "System brightness setting is not available", e)
            null
        }
    }

    data class StoragePath(val path: File, val description: String)

    @RequiresApi(Build.VERSION_CODES.N)
    fun getStorageVolumes(context: Context): List<StoragePath> {
        val list = mutableListOf<StoragePath>()
        val storageManager = context.getSystemService(Context.STORAGE_SERVICE) as StorageManager

        for (path in StorageVolumeResolver.collectCandidates(context)) {
            val (root, volume) = StorageVolumeResolver.findRoot(storageManager, path) ?: continue
            if (!list.any { it.path == root }) {
                list.add(StoragePath(root, storageVolumeDisplayName(volume, context, root)))
            }
        }
        return list
    }

    fun viewGroupMove(from: ViewGroup, id: Int, to: ViewGroup, toIndex: Int) {
        val view: View? = (0 until from.childCount)
                .map { from.getChildAt(it) }.firstOrNull { it.id == id }
        if (view == null)
            error("$from does not have child with id=$id")
        from.removeView(view)
        to.addView(view, if (toIndex >= 0) toIndex else (to.childCount + 1 + toIndex))
    }

    fun viewGroupReorder(group: ViewGroup, idOrder: IntArray) {
        val m = mutableMapOf<Int, View>()
        for (i in 0 until group.childCount) {
            val c = group.getChildAt(i)
            m[c.id] = c
        }

        val requestedIds = idOrder.toHashSet()
        val alreadyOrdered = group.childCount == m.size &&
                idOrder.size <= group.childCount &&
                idOrder.indices.all { i ->
                    val c = group.getChildAt(i)
                    c.id == idOrder[i] && c.visibility == View.VISIBLE
                } &&
                (idOrder.size until group.childCount).all { i ->
                    val c = group.getChildAt(i)
                    c.id !in requestedIds && c.visibility == View.GONE
                }
        if (alreadyOrdered)
            return

        group.removeAllViews()
        // Re-add children in specified order and unhide
        for (id in idOrder) {
            val c = m.remove(id) ?: error("$group did not have child with id=$id")
            c.visibility = View.VISIBLE
            group.addView(c)
        }
        // Keep unspecified children but hide them
        for (c in m.values) {
            c.visibility = View.GONE
            group.addView(c)
        }
    }

    fun fileBasename(str: String): String {
        val isURL = str.indexOf("://") != -1
        val last = str.replaceBeforeLast('/', "").trimStart('/')
        return if (isURL)
            Uri.decode(last.replaceAfter('?', "").trimEnd('?'))
        else
            last
    }

    @JvmStatic
    fun isLikelyMediaPath(name: String): Boolean {
        val ext = name.substringAfterLast('.', "").lowercase()
        return MEDIA_EXTENSIONS.contains(ext)
    }

    class AudioMetadata {
        @Volatile
        var mediaTitle: String? = null
            private set
        @Volatile
        var mediaArtist: String? = null
            private set
        @Volatile
        var mediaAlbum: String? = null
            private set

        fun readAll() {
            mediaTitle = mpvGetPropertyString("media-title")
            update("metadata") // read artist & album
        }

        /** callback for properties of type <code>MPV_FORMAT_NONE</code> */
        fun update(property: String): Boolean {
            if (property == "metadata") {
                // If we observe individual keys libmpv won't notify us once they become
                // unavailable, so we observe "metadata" and read both keys on trigger.
                mediaArtist = mpvGetPropertyString("metadata/by-key/Artist")
                mediaAlbum = mpvGetPropertyString("metadata/by-key/Album")
                return true
            }
            return false
        }

        /** callback for properties of type <code>MPV_FORMAT_STRING</code> */
        fun update(property: String, value: String): Boolean {
            when (property) {
                "media-title" -> mediaTitle = value
                else -> return false
            }
            return true
        }

        fun formatTitle(): String? = if (!mediaTitle.isNullOrEmpty()) mediaTitle else null

        fun formatArtistAlbum(): String? {
            val artistEmpty = mediaArtist.isNullOrEmpty()
            val albumEmpty = mediaAlbum.isNullOrEmpty()
            return when {
                !artistEmpty && !albumEmpty -> "$mediaArtist / $mediaAlbum"
                !artistEmpty -> mediaArtist
                !albumEmpty -> mediaAlbum
                else -> null
            }
        }
    }


    /**
     * Helper class that keeps much more state than <code>AudioMetadata</code>, in order to facilitate
     * updating a media session.
     * @see MediaSession
     */
    class PlaybackStateCache {
        val meta = AudioMetadata()
        @Volatile
        var cachePause = false
            private set
        @Volatile
        var pause = false
            private set
        /** playback position in ms */
        @Volatile
        var position = -1L
            private set
        /** duration in ms */
        @Volatile
        var duration = 0L
            private set
        @Volatile
        var playlistPos = 0
            private set
        @Volatile
        var playlistCount = 0
            private set
        @Volatile
        var speed = 1f
            private set

        /** playback position in seconds */
        val positionSec get() = (position / MILLIS_PER_SECOND).toInt()
        /** duration in seconds */
        val durationSec get() = (duration / MILLIS_PER_SECOND_FLOAT).roundToInt()

        /** callback for properties of type <code>MPV_FORMAT_NONE</code> */
        @Synchronized
        fun update(property: String): Boolean {
            return meta.update(property)
        }

        /** callback for properties of type <code>MPV_FORMAT_STRING</code> */
        @Synchronized
        fun update(property: String, value: String): Boolean {
            return if (meta.update(property, value)) {
                true
            } else {
                when (property) {
                    "speed" -> {
                        value.toFloatOrNull()?.let {
                            speed = it
                            true
                        } ?: false
                    }
                    else -> false
                }
            }
        }

        /** callback for properties of type <code>MPV_FORMAT_FLAG</code> */
        @Synchronized
        fun update(property: String, value: Boolean): Boolean {
            return when (property) {
                "pause" -> {
                    pause = value
                    true
                }
                "paused-for-cache" -> {
                    cachePause = value
                    true
                }
                else -> false
            }
        }

        /** callback for properties of type <code>MPV_FORMAT_INT64</code> */
        @Synchronized
        fun update(property: String, value: Long): Boolean {
            return when (property) {
                "time-pos" -> {
                    position = value * MILLIS_PER_SECOND
                    true
                }
                "playlist-pos" -> {
                    playlistPos = value.toInt()
                    true
                }
                "playlist-count" -> {
                    playlistCount = value.toInt()
                    true
                }
                else -> false
            }
        }

        /** callback for properties of type <code>MPV_FORMAT_DOUBLE</code> */
        @Synchronized
        fun update(property: String, value: Double): Boolean {
            return when (property) {
                "time-pos/full" -> {
                    position = value.times(MILLIS_PER_SECOND_DOUBLE).coerceAtLeast(0.0).toLong()
                    false
                }
                "duration/full" -> {
                    duration = ceil(value * MILLIS_PER_SECOND_DOUBLE).coerceAtLeast(0.0).toLong()
                    true
                }
                else -> false
            }
        }

        /** reset playback data when a file ends */
        @Synchronized
        fun eof() {
            position = -1L
            duration = 0L
        }

        private val mediaMetadataBuilder = MediaMetadata.Builder()
        private val playbackStateBuilder = PlaybackState.Builder()
        private var publishedSession: MediaSession? = null
        private var publishedMetadata: MetadataSnapshot? = null

        private data class MetadataSnapshot(
            val album: String?,
            val artist: String?,
            val title: String?,
            val duration: Long,
            val artwork: Bitmap?,
        )

        private fun buildMediaMetadata(metadata: MetadataSnapshot): MediaMetadata {
            return with (mediaMetadataBuilder) {
                putText(MediaMetadata.METADATA_KEY_ALBUM, metadata.album)
                // Include null so disabling artwork clears the previous image.
                putBitmap(MediaMetadata.METADATA_KEY_ART, metadata.artwork)
                putText(MediaMetadata.METADATA_KEY_ARTIST, metadata.artist)
                putLong(MediaMetadata.METADATA_KEY_DURATION, metadata.duration)
                putText(MediaMetadata.METADATA_KEY_TITLE, metadata.title)
                build()
            }
        }

        private fun buildPlaybackState(): PlaybackState {
            val stateInt = when {
                position < 0 || duration <= 0 -> PlaybackState.STATE_NONE
                cachePause -> PlaybackState.STATE_BUFFERING
                pause -> PlaybackState.STATE_PAUSED
                else -> PlaybackState.STATE_PLAYING
            }
            var actions = PlaybackState.ACTION_PLAY or
                    PlaybackState.ACTION_PLAY_PAUSE or
                    PlaybackState.ACTION_PAUSE
            if (duration > 0)
                actions = actions or PlaybackState.ACTION_SEEK_TO
            if (playlistCount > 1) {
                // we could be very pedantic here but it's probably better to either show both or none
                actions = actions or PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                        PlaybackState.ACTION_SKIP_TO_NEXT
            }
            return with (playbackStateBuilder) {
                setState(stateInt, position, speed)
                setActions(actions)
                build()
            }
        }

        @Synchronized
        fun write(session: MediaSession, includeThumb: Boolean = true) {
            val metadata = MetadataSnapshot(
                meta.mediaAlbum,
                meta.mediaArtist,
                meta.mediaTitle,
                duration.takeIf { it > 0 } ?: -1,
                BackgroundPlaybackService.thumbnail.takeIf { includeThumb },
            )
            with (session) {
                // Position updates need a playback state, not another artwork Binder transfer.
                if (publishedSession !== session || publishedMetadata != metadata) {
                    setMetadata(buildMediaMetadata(metadata))
                    publishedSession = session
                    publishedMetadata = metadata
                }
                val ps = buildPlaybackState()
                isActive = ps.state != PlaybackState.STATE_NONE
                setPlaybackState(ps)
            }
        }
    }

    class OpenUrlDialog(context: Context) {
        val builder = MaterialAlertDialogBuilder(context)
        private val binding = DialogUrlInputBinding.inflate(LayoutInflater.from(builder.context))
        private val editText = binding.editText
        private lateinit var dialog: AlertDialog

        init {
            editText.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
            editText.addTextChangedListener {
                val positiveButton = if (::dialog.isInitialized)
                    dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                else
                    null
                if (it.isNullOrEmpty()) {
                    binding.inputLayout.error = null
                    positiveButton?.isEnabled = false
                } else if (validate(it.toString())) {
                    binding.inputLayout.error = null
                    positiveButton?.isEnabled = true
                } else {
                    binding.inputLayout.error = context.getString(R.string.uri_invalid_protocol)
                    positiveButton?.isEnabled = false
                }
            }

            builder.apply {
                setTitle(R.string.action_open_url)
                setView(binding.root)
            }
        }

        private fun validate(text: String): Boolean {
            val uri = Uri.parse(text)
            return uri.isHierarchical && !uri.isRelative &&
                    !(uri.host.isNullOrEmpty() && uri.path.isNullOrEmpty()) &&
                    PROTOCOLS.contains(uri.scheme)
        }

        fun create(): AlertDialog {
            dialog = builder.create()
            editText.post { // initial state
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled = false
            }
            return dialog
        }

        val text: String
            get() = editText.text.toString()
    }

    // This is used to filter files in the file picker, so it contains just about everything
    // FFmpeg/mpv could possibly read
    val MEDIA_EXTENSIONS = setOf(
            /* Playlist */
            "cue", "m3u", "m3u8", "pls", "strm", "vlc",

            /* Audio */
            "3ga", "3ga2", "a52", "aac", "ac3", "ac4", "adt", "adts", "aif", "aifc", "aiff", "alac",
            "amr", "ape", "au", "awb", "dsf", "dts", "dts-hd", "dtshd", "eac3", "f4a", "flac",
            "lc3", "lpcm", "m1a", "m2a", "m4a", "mka", "mlp", "mp+", "mp1", "mp2", "mp3",
            "mpa", "mpc", "mpga", "mpp", "oga", "ogg", "opus", "pcm", "qoa", "ra", "ram", "rax",
            "shn", "snd", "spx", "tak", "thd", "thd+ac3", "true-hd", "truehd", "tta", "wav", "weba",
            "wma", "wv", "wvp",

            /* Video / Container */
            "264", "265", "266", "3g2", "3ga", "3gp", "3gp2", "3gpp", "3gpp2", "amr", "asf",
            "asx", "av1", "avc", "avf", "avi", "bdm", "bdmv", "clpi", "cpi", "divx", "dv", "evo",
            "evob", "f4v", "flc", "fli", "flic", "flv", "gxf", "h264", "h265", "h266", "hdmov",
            "hdv", "hevc", "lrv", "m1u", "m1v", "m2t", "m2ts", "m2v", "m4u", "m4v", "mk3d", "mkv",
            "mj2", "mov", "mp2", "mp2v", "mp4", "mp4v", "mpe", "mpeg", "mpeg2", "mpeg4", "mpg",
            "mpg4", "mpl", "mpv", "mpv2", "mts", "mtv", "mxf", "mxu", "nsv", "nut", "ogg", "ogm",
            "ogv", "ogx", "qt", "qtvr", "rm", "rmj", "rmm", "rms", "rmvb", "rmx", "rv", "rvx",
            "sdp", "tod", "trp", "ts", "tsa", "tsv", "tts", "vc1", "vfw", "vob", "vro", "vvc",
            "webm", "wm", "wmv", "wmx", "x264", "x265", "xvid", "y4m", "yuv",

            /* Picture */
            "apng", "avif", "bmp", "exr", "gif", "heic", "heif", "j2c", "j2k", "jfif", "jp2", "jpc",
            "jpe", "jpeg", "jpg", "jpg2", "png", "qoi", "tga", "tif", "tiff", "webp",
    )

    // cf. AndroidManifest.xml and MPVActivity.resolveUri()
    val PROTOCOLS = setOf(
        "file", "content", "http", "https", "data", "ftp",
        "rtmp", "rtmps", "rtp", "rtsp", "mms", "mmst", "mmsh", "tcp", "udp", "lavf"
    )
}
