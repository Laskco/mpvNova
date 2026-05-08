package app.mpvnova.player.preferences

internal data class GlassDialogOptions(
    val title: String,
    val version: String? = null,
    val releaseTitle: String? = null,
    val notesHeading: String? = null,
    val notes: String = "",
    val primaryText: String? = null,
    val ignoreText: String? = null,
    val onPrimary: (() -> Unit)? = null,
    val onIgnore: (() -> Unit)? = null,
    val showClose: Boolean = true
)

internal data class ReleaseInfo(
    val tagName: String,
    val name: String,
    val notes: String,
    val assetName: String,
    val downloadUrl: String
)

internal const val LATEST_RELEASE_URL = "https://api.github.com/repos/Laskco/mpvNova/releases/latest"
internal const val UPDATE_CACHE_DIR = "updates"
internal const val APK_MIME_TYPE = "application/vnd.android.package-archive"
internal const val IGNORED_UPDATE_TAG_KEY = "ignored_update_tag"
internal const val PENDING_UPDATE_TAG_KEY = "pending_update_tag"
internal const val PENDING_UPDATE_APK_PATH_KEY = "pending_update_apk_path"
internal const val RELEASE_HISTORY_KEY = "release_history"
internal const val RELEASE_HISTORY_LIMIT = 5
internal const val HTTP_CONNECT_TIMEOUT_MS = 15_000
internal const val HTTP_READ_TIMEOUT_MS = 30_000
private const val HTTP_SUCCESS_MIN = 200
private const val HTTP_SUCCESS_MAX = 299
internal val HTTP_SUCCESS_RANGE = HTTP_SUCCESS_MIN..HTTP_SUCCESS_MAX
internal val KNOWN_ABIS = listOf("arm64-v8a", "armeabi-v7a", "x86_64", "x86")
