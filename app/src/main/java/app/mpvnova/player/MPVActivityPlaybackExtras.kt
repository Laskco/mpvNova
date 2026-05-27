package app.mpvnova.player

/** Drawer-driven playback extras. */

internal fun MPVActivity.takeScreenshot() {
    // "subtitles" = decoded frame + OSD + sub overlay (what the user sees).
    // Output dir set at mpv init — see MPVView.applyScreenshotOptions.
    mpvCommand(arrayOf("screenshot", "subtitles"))
    showToast(getString(R.string.btn_screenshot), getString(R.string.toast_screenshot_saved))
}
