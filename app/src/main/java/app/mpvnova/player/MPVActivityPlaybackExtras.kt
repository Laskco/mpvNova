package app.mpvnova.player

import android.os.Environment

/** Drawer-driven playback extras. */

internal fun MPVActivity.takeScreenshot() {
    // "subtitles" = decoded frame + OSD + sub overlay (what the user sees).
    // Output dir set at mpv init — see MPVView.applyScreenshotOptions.
    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES).mkdirs()
    mpvCommand(arrayOf("screenshot", "subtitles"))
    showToast(getString(R.string.btn_screenshot), getString(R.string.toast_screenshot_saved))
}
