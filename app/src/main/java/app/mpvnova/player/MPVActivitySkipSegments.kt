package app.mpvnova.player

import android.os.Bundle
import android.util.Log
import org.json.JSONArray
import org.json.JSONException

// Stop skipping a hair before the segment end so playback lands cleanly past it.
private const val SKIP_SEGMENT_END_GUARD_SEC = 1.0
private const val SKIP_SEGMENT_REWIND_MIN_DELTA_SEC = 1.0

/**
 * Reads the `skip_segments` launch extra (a JSON array of `{type,start,end}`, seconds) set by the
 * launching app and stores it for auto-skipping. Tolerant of missing/garbage data.
 */
internal fun MPVActivity.parseSkipSegments(extras: Bundle?) {
    skipSegments = emptyList()
    autoSkippedSegmentKeys.clear()
    rewoundSkipSegmentKeys.clear()
    dismissedSkipSegmentKeys.clear()
    autoHiddenSkipSegmentKeys.clear()
    lastSkipSegmentPlaybackPositionSec = Double.NaN
    hideSkipButton()

    val json = extras?.getString("skip_segments")?.takeIf { it.isNotBlank() } ?: return
    skipSegments = try {
        val arr = JSONArray(json)
        (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val start = o.optDouble("start", Double.NaN)
            val end = o.optDouble("end", Double.NaN)
            if (start.isNaN() || end.isNaN() || end <= start) return@mapNotNull null
            SkipSegment(o.optString("type", "intro"), start, end)
        }
    } catch (e: JSONException) {
        Log.w(MPV_ACTIVITY_TAG, "Failed to parse skip_segments", e)
        emptyList()
    }

    if (skipSegments.isNotEmpty())
        Log.d(MPV_ACTIVITY_TAG, "Loaded ${skipSegments.size} skip segment(s)")
}

/**
 * Called on each playback time tick. Depending on [MPVActivity.skipSegmentsMode]:
 *  - OFF: do nothing.
 *  - AUTO: seek past a new segment once; after rewinding across it, offer the Skip button.
 *  - BUTTON: show the Skip button for a new or rewind-rearmed segment.
 */
internal fun MPVActivity.maybeAutoSkipSegments(posSec: Double) {
    rearmSkippedSegmentsAfterRewind(posSec)
    if (skipSegmentsMode == SkipSegmentsMode.OFF || skipSegments.isEmpty()) {
        hideSkipButton()
        return
    }
    val seg = skipSegments.firstOrNull { segment ->
        posSec >= segment.start &&
            posSec < segment.end - SKIP_SEGMENT_END_GUARD_SEC &&
            (segment.key() !in autoSkippedSegmentKeys ||
                segment.key() in rewoundSkipSegmentKeys)
    }
    if (seg == null) {
        hideSkipButton() // left the active window (or it was handled)
        return
    }
    when (skipSegmentsMode) {
        SkipSegmentsMode.AUTO -> {
            if (seg.key() in rewoundSkipSegmentKeys) {
                showSkipButton(seg)
            } else {
                autoSkippedSegmentKeys.add(seg.key()) // each segment is auto-skipped only once
                performSegmentSkip(seg)
            }
        }
        SkipSegmentsMode.BUTTON -> showSkipButton(seg)
        SkipSegmentsMode.OFF -> Unit
    }
}

private fun MPVActivity.rearmSkippedSegmentsAfterRewind(posSec: Double) {
    val previousPosSec = lastSkipSegmentPlaybackPositionSec
    lastSkipSegmentPlaybackPositionSec = posSec
    if (!previousPosSec.isFinite() || !posSec.isFinite()) return

    skipSegments.forEach { segment ->
        val key = segment.key()
        if (
            key in autoSkippedSegmentKeys &&
            rewoundIntoOrBeforeSkippedSegment(previousPosSec, posSec, segment)
        ) {
            rewoundSkipSegmentKeys.add(key)
            dismissedSkipSegmentKeys.remove(key)
            autoHiddenSkipSegmentKeys.remove(key)
            Log.d(MPV_ACTIVITY_TAG, "Rearmed skip button for $key after rewind")
        }
    }
}

internal fun rewoundIntoOrBeforeSkippedSegment(
    previousPosSec: Double,
    currentPosSec: Double,
    segment: SkipSegment,
): Boolean {
    val positionsAreFinite = previousPosSec.isFinite() && currentPosSec.isFinite()
    if (!positionsAreFinite) {
        return false
    }

    val rewindDeltaSec = previousPosSec - currentPosSec
    val segmentWindowEndSec = segment.end - SKIP_SEGMENT_END_GUARD_SEC
    val rewoundEnough = rewindDeltaSec >= SKIP_SEGMENT_REWIND_MIN_DELTA_SEC
    val landedBeforeWindowEnd = currentPosSec < segmentWindowEndSec
    val landedInsideSegment = currentPosSec >= segment.start
    val crossedEntireSegment = previousPosSec >= segmentWindowEndSec
    val landedInRearmRange = landedInsideSegment || crossedEntireSegment
    return rewoundEnough && landedBeforeWindowEnd && landedInRearmRange
}

/** Seek past [seg] and flash the chapter-style toast. Shared by auto-skip and the Skip button. */
internal fun MPVActivity.performSegmentSkip(seg: SkipSegment) {
    // Exact seek: lands precisely at the segment end (a keyframe seek overshoots into the
    // episode). The latency here is the network re-buffer of the jump, not the decode.
    mpvCommand(arrayOf("seek", seg.end.toString(), "absolute+exact"))
    val detail = getString(R.string.toast_skip_segment_detail, Utils.prettyTime(seg.end.toInt()))
    eventUiHandler.post { showToast(skipSegmentLabel(seg.type), detail, cancel = false) }
    Log.d(MPV_ACTIVITY_TAG, "Skipped ${seg.key()} -> ${seg.end}")
}

internal fun SkipSegment.key(): String = "$type:$start"

internal fun MPVActivity.skipSegmentLabel(type: String): String = when (type.lowercase()) {
    "recap" -> getString(R.string.skip_segment_recap)
    "ed", "mixed-ed", "outro", "credits", "ending" -> getString(R.string.skip_segment_outro)
    else -> getString(R.string.skip_segment_intro)
}
