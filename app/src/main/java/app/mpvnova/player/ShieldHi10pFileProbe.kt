package app.mpvnova.player

import android.media.MediaExtractor
import android.media.MediaFormat
import android.util.Log
import androidx.preference.PreferenceManager
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.util.Locale

internal const val PREF_HI10P_FALLBACK_OTHER_DEVICES = "hi10p_fallback_other_devices"

internal fun MPVActivity.prepareDecoderForFileLoad(filepath: String) {
    if (!canPreloadShieldHi10pFallback()) {
        restoreDecoderAfterShieldHi10pPreload()
        return
    }

    val source = shieldHi10pPreloadSource(filepath)
    if (source == null) {
        restoreDecoderAfterShieldHi10pPreload()
        return
    }

    Log.v(MPV_ACTIVITY_TAG, "shield fallback: preloading software decode for $source H.264 Hi10P")
    player.applyShieldHi10pFallback(shieldDecoderFallback)
    shieldHi10pPreloadApplied = true
    updateDecoderButton()
}

// Returns a short source label when the file is (or is very likely) H.264 Hi10P, else null.
// Local files are sniffed authoritatively from their H.264 SPS headers. Streams can't be
// header-read without a second (often single-use) network open, so they fall back to the
// release-name hint that Stremio/launchers almost always carry ("Hi10"/"Hi10P").
private fun MPVActivity.shieldHi10pPreloadSource(filepath: String): String? {
    val localFile = filepath.toCanonicalLocalFile()?.takeIf { it.isFile && it.canRead() }
    if (localFile != null)
        return if (localFile.isH264TenBitVideoFile()) "local" else null
    val nameHints = listOfNotNull(filepath, pendingFileName, pendingItemTitle)
    return if (nameHints.any { it.indicatesH264TenBitByName() }) "stream-name" else null
}

// "Hi10"/"Hi10P" is the anime-release shorthand for H.264 High-10 specifically. Generic
// "10-bit" markers are ambiguous (HEVC Main10 too, which Shield decodes in hardware), so
// only treat those as H.264 Hi10P when the name also calls out H.264/AVC and not HEVC.
internal fun String.indicatesH264TenBitByName(): Boolean {
    val name = lowercase(Locale.US)
    val tenBit = name.contains("10bit") || name.contains("10-bit") || name.contains("10 bit")
    val isH264 = name.contains("x264") || name.contains("h264") ||
        name.contains("h.264") || name.contains("avc")
    val isHevc = name.contains("x265") || name.contains("h265") ||
        name.contains("h.265") || name.contains("hevc")
    // "Hi10"/"Hi10P" is the anime shorthand for H.264 High-10 specifically; a generic
    // "10-bit" marker only counts when the name also names H.264/AVC and not HEVC.
    return name.contains("hi10") || (tenBit && isH264 && !isHevc)
}

private fun MPVActivity.canPreloadShieldHi10pFallback(): Boolean =
    autoDecoderFallback &&
        sessionDecoderMode == null &&
        isHi10pFallbackDeviceEnabled()

internal fun MPVActivity.isHi10pFallbackDeviceEnabled(): Boolean =
    if (isNvidiaShieldDevice()) shieldDecoderModeEnabled else hi10pFallbackOnOtherDevicesEnabled

private fun MPVActivity.restoreDecoderAfterShieldHi10pPreload() {
    if (!shieldHi10pPreloadApplied)
        return
    shieldHi10pPreloadApplied = false

    val mode = sessionDecoderMode ?: preferredDecoderMode.takeIf { !autoDecoderFallback && it.isNotBlank() }
    val blockedShieldMode = mode == MPVView.DECODER_MODE_SHIELD_H10P && !shieldDecoderModeEnabled
    when {
        mode == null || blockedShieldMode -> player.applyDefaultDecoderForFileLoad()
        mode == MPVView.DECODER_MODE_MPV_CONF -> player.applyMpvConfDecoderOptions()
        else -> player.applyDecoderMode(mode)
    }
    updateDecoderButton()
}

private fun MPVView.applyDefaultDecoderForFileLoad() {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    val startupDecoderMode = startupPreferredDecoderMode(sharedPreferences)
    if (startupDecoderMode == MPVView.DECODER_MODE_MPV_CONF) {
        applyMpvConfDecoderOptions()
        return
    }

    val vo = startupVo(sharedPreferences, startupDecoderMode)
    vo?.let { applyStandardDecoderTuning(sharedPreferences, it) }
    val hwdec = shieldGpuNextStartupHwdec(vo, startupHwdec(sharedPreferences, startupDecoderMode))
    if (hwdec != null)
        setRuntimeOption("hwdec", hwdec)
}

private fun File.isH264TenBitVideoFile(): Boolean {
    val extractor = MediaExtractor()
    return try {
        extractor.setDataSource(absolutePath)
        (0 until extractor.trackCount).any { index ->
            val format = extractor.getTrackFormat(index)
            format.getString(MediaFormat.KEY_MIME) == H264_MIME &&
                format.csd0Bytes()?.let(::h264CsdIndicatesTenBit) == true
        }
    } catch (e: IOException) {
        Log.v(MPV_ACTIVITY_TAG, "shield fallback: could not inspect local video headers", e)
        false
    } catch (e: IllegalArgumentException) {
        Log.v(MPV_ACTIVITY_TAG, "shield fallback: could not inspect local video headers", e)
        false
    } catch (e: IllegalStateException) {
        Log.v(MPV_ACTIVITY_TAG, "shield fallback: could not inspect local video headers", e)
        false
    } catch (e: SecurityException) {
        Log.v(MPV_ACTIVITY_TAG, "shield fallback: could not inspect local video headers", e)
        false
    } finally {
        extractor.release()
    }
}

private fun MediaFormat.csd0Bytes(): ByteArray? =
    if (containsKey(CSD_0_KEY)) {
        runCatching { getByteBuffer(CSD_0_KEY) }
            .getOrNull()
            ?.toByteArrayFromStart()
    } else {
        null
    }

private fun ByteBuffer.toByteArrayFromStart(): ByteArray {
    val duplicate = duplicate()
    duplicate.position(0)
    val bytes = ByteArray(duplicate.remaining())
    duplicate.get(bytes)
    return bytes
}

private const val H264_MIME = "video/avc"
private const val CSD_0_KEY = "csd-0"
