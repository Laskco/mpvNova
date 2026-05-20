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
    val file = filepath?.toCanonicalLocalFile() ?: return
    if (!file.isFile)
        return

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
}

internal fun MPVActivity.addIntentSubtitles(launchExtras: Bundle) {
    if (!launchExtras.containsKey("subs"))
        return
    val subList = getParcelableArray<Uri>(launchExtras, "subs")
    val subsToEnable = getParcelableArray<Uri>(launchExtras, "subs.enable")
    for (suburi in subList) {
        val subfile = resolveUri(suburi) ?: continue
        val flag = if (subsToEnable.any { it == suburi }) "select" else "auto"
        Log.v(MPV_ACTIVITY_TAG, "Adding subtitles from intent extras: $subfile")
        onloadCommands.add(arrayOf("sub-add", subfile, flag))
    }
}

internal fun MPVActivity.applyIntentStartPosition(launchExtras: Bundle) {
    val intentPositionMs = launchExtras.getInt("position", 0).toLong()
    val effectivePositionMs = effectiveIntentStartPosition(launchExtras, intentPositionMs)
    pendingStartPositionMs = effectivePositionMs
    if (effectivePositionMs <= 0L)
        return

    addOnloadOption("start", "${effectivePositionMs / MPV_MILLIS_PER_SECOND_FLOAT}")
    if (effectivePositionMs >= RESUME_TOAST_MIN_POSITION_MS) {
        pendingResumeToastMs = effectivePositionMs
        Log.v(
            MPV_ACTIVITY_TAG,
            "resume: queued toast for ${effectivePositionMs}ms " +
                "(source=${if (intentPositionMs > 0L) "intent" else "table"})"
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
        val exactCandidates = searchDirs
            .asSequence()
            .flatMap { directory ->
                AUTOMATIC_SUBTITLE_EXTENSIONS.asSequence().map { extension ->
                    directory.resolveConfinedChild("$videoBase.$extension")
                }
            }
            .filterNotNull()
        val listedCandidates = searchDirs
            .asSequence()
            .flatMap { directory -> directory.listConfinedChildren(parent) }
        (exactCandidates + listedCandidates)
            .filter { candidate ->
                candidate.isFile &&
                    candidate.canRead() &&
                    candidate.isMatchingSubtitleFor(videoBase, parent)
            }
            .distinctBy { it.absolutePath }
            .sortedWith(
                compareBy<File> { matchingSubtitleRank(videoBase, it.nameWithoutExtension) }
                    .thenBy { it.name.lowercase(Locale.ROOT) }
            )
            .toList()
    }
}

private val AUTOMATIC_SUBTITLE_DIRS = listOf("subs", "sub", "subtitles", "subtitle")

private val AUTOMATIC_SUBTITLE_EXTENSIONS = setOf(
    "ass",
    "srt",
    "ssa",
    "sub",
    "vtt",
)

private fun automaticSubtitleSearchDirs(parent: File): List<File> {
    val canonicalParent = parent.canonicalFileOrNull() ?: return emptyList()
    return buildList {
        add(canonicalParent)
        AUTOMATIC_SUBTITLE_DIRS.forEach { name ->
            val directory = canonicalParent.resolveConfinedChild(name) ?: return@forEach
            if (directory.isDirectory) {
                add(directory)
                directory.listConfinedChildren(canonicalParent)
                    .filter { it.isDirectory }
                    .forEach(::add)
            }
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

private fun MPVActivity.effectiveIntentStartPosition(launchExtras: Bundle, intentPositionMs: Long): Long {
    val intentDurationMs = launchExtras.getInt("duration", 0).toLong()
    val intentNearEnd = intentDurationMs > 0L &&
        intentPositionMs >= intentDurationMs - RESUME_NEAR_END_MS
    return when {
        intentPositionMs > 0L && !intentNearEnd -> intentPositionMs
        intentPositionMs <= 0L -> loadResumePosition() ?: 0L
        else -> 0L
    }
}
