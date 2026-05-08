package app.mpvnova.player

import android.widget.TextView
import kotlin.math.roundToInt

internal fun MPVActivity.handleGestureChange(p: PropertyChange, diff: Float) {
    val gestureTextView = binding.gestureTextView
    when (p) {
        PropertyChange.Init -> beginGestureChange(gestureTextView)
        PropertyChange.Seek -> handleGestureSeek(diff, gestureTextView)
        PropertyChange.Volume -> handleGestureVolume(diff, gestureTextView)
        PropertyChange.Bright -> handleGestureBrightness(diff, gestureTextView)
        PropertyChange.Finalize -> finalizeGestureChange(gestureTextView)
        PropertyChange.SeekFixed -> handleFixedSeekGesture(diff, gestureTextView)
        PropertyChange.PlayPause -> player.cyclePause()
        PropertyChange.Custom -> handleCustomGesture(diff)
    }
}

private fun MPVActivity.beginGestureChange(gestureTextView: TextView) {
    mightWantToToggleControls = false
    initialSeek = psc.position / MPV_MILLIS_PER_SECOND_FLOAT
    initialBright = Utils.getScreenBrightness(this) ?: AUDIO_FOCUS_DUCKING
    with(audioManager!!) {
        initialVolume = getStreamVolume(STREAM_TYPE)
        maxVolume = if (isVolumeFixed) 0 else getStreamMaxVolume(STREAM_TYPE)
    }
    if (!isPlayingAudio)
        maxVolume = 0
    pausedForSeek = 0
    fadeHandler.removeCallbacks(fadeRunnable3)
    gestureTextView.visibility = android.view.View.VISIBLE
    gestureTextView.text = ""
}

private fun MPVActivity.handleGestureSeek(diff: Float, gestureTextView: TextView) {
    val duration = psc.duration / MPV_MILLIS_PER_SECOND_FLOAT
    if (duration == 0f || initialSeek < 0)
        return
    pauseForSmoothSeekIfNeeded()
    val newPosExact = (initialSeek + diff).coerceIn(0f, duration)
    val newPos = newPosExact.roundToInt()
    val newDiff = (newPosExact - initialSeek).roundToInt()
    if (smoothSeekGesture) {
        player.timePos = newPosExact.toDouble()
    } else {
        mpvCommand(arrayOf("seek", "$newPosExact", "absolute+keyframes"))
    }
    val posText = Utils.prettyTime(newPos)
    val diffText = Utils.prettyTime(newDiff, true)
    gestureTextView.text = getString(R.string.ui_seek_distance, posText, diffText)
}

private fun MPVActivity.pauseForSmoothSeekIfNeeded() {
    if (smoothSeekGesture && pausedForSeek == 0) {
        pausedForSeek = if (psc.pause) 2 else 1
        if (pausedForSeek == 1)
            player.paused = true
    }
}

private fun MPVActivity.handleGestureVolume(diff: Float, gestureTextView: TextView) {
    if (maxVolume == 0)
        return
    val newVolume = (initialVolume + (diff * maxVolume).toInt()).coerceIn(0, maxVolume)
    val newVolumePercent = PERCENT_SCALE_INT * newVolume / maxVolume
    audioManager!!.setStreamVolume(STREAM_TYPE, newVolume, 0)
    gestureTextView.text = getString(R.string.ui_volume, newVolumePercent)
}

private fun MPVActivity.handleGestureBrightness(diff: Float, gestureTextView: TextView) {
    val layoutParams = window.attributes
    val newBright = (initialBright + diff).coerceIn(0f, 1f)
    layoutParams.screenBrightness = newBright
    window.attributes = layoutParams
    gestureTextView.text = getString(
        R.string.ui_brightness,
        (newBright * PERCENT_SCALE_INT).roundToInt()
    )
}

private fun MPVActivity.finalizeGestureChange(gestureTextView: TextView) {
    if (pausedForSeek == 1)
        player.paused = false
    gestureTextView.visibility = android.view.View.GONE
}

private fun MPVActivity.handleFixedSeekGesture(diff: Float, gestureTextView: TextView) {
    val seekTime = diff * FIXED_SEEK_GESTURE_SECONDS
    val newPos = psc.positionSec + seekTime.toInt()
    mpvCommand(arrayOf("seek", seekTime.toString(), "relative"))
    val diffText = Utils.prettyTime(seekTime.toInt(), true)
    gestureTextView.text = getString(R.string.ui_seek_distance, Utils.prettyTime(newPos), diffText)
    fadeGestureText()
}

private fun handleCustomGesture(diff: Float) {
    val keycode = CUSTOM_KEYCODE_BASE + diff.toInt()
    mpvCommand(arrayOf("keypress", "0x%x".format(keycode)))
}
