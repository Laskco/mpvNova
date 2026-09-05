package app.mpvnova.player

import android.app.Activity
import android.content.Context
import androidx.preference.PreferenceManager
import app.mpvnova.player.preferences.SettingsChoiceItem
import app.mpvnova.player.preferences.showSettingsChoiceDialog

internal const val PREF_VIDEO_EDGE_CLEANUP = "video_edge_cleanup"
internal val VIDEO_EDGE_CLEANUP_VALUES = listOf("auto", "off", "8", "16", "24", "32")

internal fun Context.videoEdgeCleanupValue(): String =
    (PreferenceManager.getDefaultSharedPreferences(this).all[PREF_VIDEO_EDGE_CLEANUP] as? String)
        ?.takeIf { it in VIDEO_EDGE_CLEANUP_VALUES } ?: "off"

internal fun Context.videoEdgeCleanupLabel(): String = resources.getStringArray(R.array.video_edge_cleanup_entries)[
    VIDEO_EDGE_CLEANUP_VALUES.indexOf(videoEdgeCleanupValue())
]

internal fun Activity.showVideoEdgeCleanup(onChanged: () -> Unit) {
    val current = videoEdgeCleanupValue()
    val labels = resources.getStringArray(R.array.video_edge_cleanup_entries)
    val items = VIDEO_EDGE_CLEANUP_VALUES.mapIndexed { index, value ->
        SettingsChoiceItem(title = labels[index], checked = current == value) {
            PreferenceManager.getDefaultSharedPreferences(this).edit().putString(PREF_VIDEO_EDGE_CLEANUP, value).apply()
            (this as? MPVActivity)?.applyFireTvVideoEdgeCropIfNeeded()
            onChanged()
        }
    }
    showSettingsChoiceDialog(getString(R.string.video_edge_cleanup_title), items,
        getString(R.string.video_edge_cleanup_summary))
}

// Own only the crop we set. Never overwrite a user's or script's independent video-crop.
internal class VideoEdgeCropSession {
    private var applied: String? = null
    private var original = ""

    @Synchronized
    fun update(desired: String?, read: () -> String?, write: (String) -> Unit) {
        val current = read() ?: return
        if (applied != null && current != applied) applied = null
        if (desired == null) {
            clear(read, write)
        } else if (current != desired && (applied != null || current.isEmpty())) {
            if (applied == null) original = current
            val previous = applied
            write(desired)
            applied = when (read()) {
                desired -> desired
                previous -> previous
                else -> null
            }
        }
    }

    @Synchronized
    fun clear(read: () -> String?, write: (String) -> Unit) {
        if (applied != null && read() == applied) write(original)
        applied = null
        original = ""
    }
}
