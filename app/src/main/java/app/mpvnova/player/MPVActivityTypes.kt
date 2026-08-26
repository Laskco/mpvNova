package app.mpvnova.player

import android.view.Gravity

internal data class ResumeIdentity(val hash: String, val fileToken: String?)
internal data class TrackData(val trackId: Int, val trackType: String)
internal data class PlayerDialogLayout(
    val widthFraction: Float = 0.84f,
    val maxWidthDp: Float = 1180f,
    val gravity: Int = Gravity.CENTER,
    val verticalOffsetDp: Float = 0f,
    val heightFraction: Float? = null,
    val maxHeightDp: Float? = null,
)
internal enum class PlayerDialogChrome {
    HIDE_ALL,
    TITLE_AND_CLOCK_PREVIEW,
}

internal enum class TrackPanelReturnFocus {
    TRACK,
    DELAY,
    SUBTITLE_STYLE,
}

internal data class PlayerChromeSnapshot(
    val controlsVisibility: Int,
    val controlsScrimVisibility: Int,
    val topControlsVisibility: Int,
    val titleVisibility: Int,
    val timeInfoVisibility: Int,
    val statsVisibility: Int,
    val skipVisibility: Int,
)
internal data class TrackMeta(val mpvId: Int, val title: String, val lang: String)

internal val MPVActivity.activityContext: MPVActivity get() = this

internal abstract class ControlsFadeRunnable : Runnable {
    abstract var hasStarted: Boolean
}
