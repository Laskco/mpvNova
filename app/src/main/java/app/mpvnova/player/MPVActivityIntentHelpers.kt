package app.mpvnova.player

import android.net.Uri
import android.os.Bundle
import android.util.Log
import java.io.File
import java.util.Locale

internal fun MPVActivity.addOnloadOption(key: String, value: String) {
    onloadCommands.add(arrayOf("set", "file-local-options/${key}", value))
}

internal fun MPVActivity.addAutomaticSubtitleOptions(filepath: String?) {
    onloadCommands.removeAll(automaticSubtitleCommands.toSet())
    automaticSubtitleCommands.clear()
    val file = filepath?.toCanonicalLocalFile() ?: return
    if (!file.isFile)
        return

    val commandStart = onloadCommands.size
    val matchingSubtitles = matchingLocalSubtitleFiles(file)
    if (matchingSubtitles.isNotEmpty()) {
        addOnloadOption("sub-auto", "no")
        val hasSelectedIntentSubtitle = onloadCommands.any { command ->
            command.size >= SUB_ADD_SELECTED_COMMAND_SIZE &&
                command[0] == "sub-add" &&
                command[2] == "select"
        }
        matchingSubtitles.forEachIndexed { index, subfile ->
            val flag = if (index == 0 && !hasSelectedIntentSubtitle) "select" else "auto"
            Log.v(MPV_ACTIVITY_TAG, "Adding matching subtitle from local folder: $subfile")
            onloadCommands.add(arrayOf("sub-add", subfile.absolutePath, flag))
        }
    } else {
        addOnloadOption("sub-auto", "fuzzy")
        addOnloadOption("sub-file-paths", AUTOMATIC_SUBTITLE_PATHS)
    }
    automaticSubtitleCommands.addAll(onloadCommands.subList(commandStart, onloadCommands.size))
}

internal fun MPVActivity.addIntentSubtitles(launchExtras: Bundle) {
    if (!launchExtras.containsKey("subs"))
        return
    val subList = getParcelableArray<Uri>(launchExtras, "subs")
    val subsToEnable = getParcelableArray<Uri>(launchExtras, "subs.enable").toHashSet()
    for (suburi in subList) {
        val subfile = resolveUri(suburi) ?: continue
        val flag = intentSubtitleAddFlag(
            isRequestedSelected = suburi in subsToEnable,
            preferExternalForwardedSubtitles = preferExternalForwardedSubtitles,
        )
        Log.v(MPV_ACTIVITY_TAG, "Adding subtitles from intent extras: $subfile")
        onloadCommands.add(arrayOf("sub-add", subfile, flag))
    }
}

internal fun MPVActivity.applyIntentStartPosition(launchExtras: Bundle) {
    val startRequest = launchExtras.externalStartPositionRequest()
    val savedPositionMs = if (startRequest.isExplicit) null else loadResumePosition()
    val effectivePositionMs = effectiveIntentStartPosition(
        request = startRequest,
        savedPositionMs = savedPositionMs,
        durationMs = launchExtras.externalDurationMs(),
    )
    pendingStartPositionMs = effectivePositionMs
    if (startRequest.isExplicit) {
        // External launcher explicitly owns the start position (including 0);
        // suppress mpv's watch-later resume before loadfile so it cannot override or collide.
        suppressWatchLaterResumeForNextLoad = true
    }
    if (effectivePositionMs <= 0L) {
        if (startRequest.isExplicit) {
            addOnloadOption("start", "0")
            Log.v(MPV_ACTIVITY_TAG, "resume: launcher requested playback from beginning")
        }
        return
    }

    addOnloadOption("start", "${effectivePositionMs / MPV_MILLIS_PER_SECOND_FLOAT}")
    if (startRequest.positionMs > 0L || effectivePositionMs >= RESUME_TOAST_MIN_POSITION_MS) {
        pendingResumeToastMs = effectivePositionMs
        Log.v(
            MPV_ACTIVITY_TAG,
            "resume: queued toast for ${effectivePositionMs}ms " +
                "(source=${if (startRequest.positionMs > 0L) "intent" else "table"})"
        )
    }
}

private const val AUTOMATIC_SUBTITLE_PATHS = "subs:sub:subtitles:subtitle"
private const val SUB_ADD_SELECTED_COMMAND_SIZE = 3

internal fun matchingLocalSubtitleFiles(videoFile: File): List<File> {
    val canonicalVideo = videoFile.canonicalFileOrNull()?.takeIf { it.isFile }
    val parent = canonicalVideo?.parentFile?.canonicalFileOrNull()
    return if (canonicalVideo == null || parent == null) {
        emptyList()
    } else {
        val videoBase = canonicalVideo.nameWithoutExtension
        val searchDirs = automaticSubtitleSearchDirs(parent)
        val candidates = searchDirs.asSequence().flatMap { directory ->
            val rawChildren = directory.listFiles()
            if (rawChildren != null) {
                rawChildren.asSequence()
                    .filter { it.isMatchingSubtitleFor(videoBase, parent) }
                    .mapNotNull { it.canonicalFileOrNull()?.takeIf { canon -> canon.isUnder(parent) } }
            } else {
                // Fallback when directory listing is restricted: probe exact sidecar filenames directly.
                AUTOMATIC_SUBTITLE_EXTENSIONS.asSequence()
                    .mapNotNull { ext -> directory.resolveConfinedChildFromCanonical("$videoBase.$ext") }
            }
        }
        candidates
            .filter { candidate ->
                candidate.isMatchingSubtitleFor(videoBase, parent) &&
                    candidate.isFile && candidate.canRead()
            }
            .distinctBy { it.absolutePath }
            .map { candidate ->
                RankedSidecar(candidate, matchingSubtitleRank(videoBase, candidate.nameWithoutExtension),
                    candidate.name.lowercase(Locale.ROOT))
            }
            .sortedWith(compareBy<RankedSidecar> { it.rank }.thenBy { it.name })
            .map { it.file }
            .toList()
    }
}

private data class RankedSidecar(val file: File, val rank: Int, val name: String)

private val AUTOMATIC_SUBTITLE_DIRS = listOf("subs", "sub", "subtitles", "subtitle")

private val AUTOMATIC_SUBTITLE_EXTENSIONS = setOf(
    "ass",
    "srt",
    "ssa",
    "sub",
    "vtt",
)

private fun automaticSubtitleSearchDirs(canonicalParent: File): List<File> = buildList {
    add(canonicalParent)
    AUTOMATIC_SUBTITLE_DIRS.forEach { name ->
        val directory = canonicalParent.resolveConfinedChildFromCanonical(name) ?: return@forEach
        if (directory.isDirectory) {
            add(directory)
            directory.listFiles()
                ?.asSequence()
                ?.filter { it.isDirectory }
                ?.mapNotNull { child ->
                    child.canonicalFileOrNull()?.takeIf { it.isUnder(canonicalParent) }
                }
                ?.forEach(::add)
        }
    }
}

private fun File.isMatchingSubtitleFor(videoBase: String, videoParent: File): Boolean {
    val extension = extension.lowercase(Locale.ROOT)
    val matchesVideoSpecificDirectory = parentFile?.let { parent ->
        parent.parentFile != videoParent && subtitleBaseMatchesVideo(parent.name, videoBase)
    } == true
    return extension in AUTOMATIC_SUBTITLE_EXTENSIONS &&
        (
            subtitleBaseMatchesVideo(nameWithoutExtension, videoBase) ||
                matchesVideoSpecificDirectory
            )
}

internal fun subtitleBaseMatchesVideo(subtitleBase: String, videoBase: String): Boolean =
    subtitleBase.equals(videoBase, ignoreCase = true) ||
        (
            subtitleBase.startsWith(videoBase, ignoreCase = true) &&
                subtitleBase.getOrNull(videoBase.length) in AUTOMATIC_SUBTITLE_SEPARATORS
            )

private val AUTOMATIC_SUBTITLE_SEPARATORS = setOf('.', '-', '_', ' ', '[', '(')

private fun matchingSubtitleRank(videoBase: String, subtitleBase: String): Int {
    return if (subtitleBase.equals(videoBase, ignoreCase = true)) 0 else 1
}
