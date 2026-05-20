package app.mpvnova.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class AutomaticSubtitleTest {
    @Test
    fun subtitleBaseMatchesExactAndPrefixNames() {
        assertTrue(subtitleBaseMatchesVideo("Episode_01", "Episode_01"))
        assertTrue(subtitleBaseMatchesVideo("Episode_01.extra", "Episode_01"))
        assertTrue(subtitleBaseMatchesVideo("Episode_01-en", "Episode_01"))
        assertTrue(subtitleBaseMatchesVideo("Episode_01_eng", "Episode_01"))
        assertTrue(subtitleBaseMatchesVideo("Episode_01 English", "Episode_01"))
    }

    @Test
    fun subtitleBaseRejectsUnrelatedAndAdjacentNames() {
        assertFalse(subtitleBaseMatchesVideo("Episode_010", "Episode_01"))
        assertFalse(subtitleBaseMatchesVideo("OtherEpisode_01", "Episode_01"))
        assertFalse(subtitleBaseMatchesVideo("Unrelated", "Episode_01"))
    }

    @Test
    fun matchingLocalSubtitleFilesFindsExactPrefixAndSubdirectoryMatches() {
        val root = Files.createTempDirectory("mpvnova-subs").toFile()
        try {
            val video = File(root, "Episode_01.mp4").also { it.writeText("video") }
            File(root, "Episode_01.ass").writeText("exact")
            File(root, "Episode_01.extra.srt").writeText("prefix")
            File(root, "Episode_010.srt").writeText("adjacent")
            File(root, "Unrelated.ass").writeText("unrelated")
            File(root, "subs").mkdir()
            File(root, "subs/Episode_01.vtt").writeText("nested")

            val names = matchingLocalSubtitleFiles(video).map { it.name }

            assertEquals(
                listOf("Episode_01.ass", "Episode_01.vtt", "Episode_01.extra.srt"),
                names
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun matchingLocalSubtitleFilesUsesOnlyTheCurrentVideoPrefix() {
        val root = Files.createTempDirectory("mpvnova-subs-multiple").toFile()
        try {
            val episodeOne = File(root, "Show.S01E01.mkv").also { it.writeText("episode one") }
            val episodeTwo = File(root, "Show.S01E02.mkv").also { it.writeText("episode two") }
            File(root, "Show.S01E01.ass").writeText("episode one subtitles")
            File(root, "Show.S01E02.ass").writeText("episode two subtitles")
            File(root, "Show.S01E02.extra.srt").writeText("episode two extra subtitles")
            File(root, "Show.S01E03.ass").writeText("wrong episode")
            File(root, "Show.S01E010.ass").writeText("adjacent but wrong")

            assertEquals(
                listOf("Show.S01E01.ass"),
                matchingLocalSubtitleFiles(episodeOne).map { it.name }
            )
            assertEquals(
                listOf("Show.S01E02.ass", "Show.S01E02.extra.srt"),
                matchingLocalSubtitleFiles(episodeTwo).map { it.name }
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun matchingLocalSubtitleFilesSupportsSharedAndEpisodeSpecificSubtitleFolders() {
        val root = Files.createTempDirectory("mpvnova-subs-folders").toFile()
        try {
            val episodeOne = File(root, "Show.S01E01.mkv").also { it.writeText("episode one") }
            val episodeTwo = File(root, "Show.S01E02.mkv").also { it.writeText("episode two") }
            File(root, "subtitles").mkdir()
            File(root, "subtitles/Show.S01E01.ass").writeText("shared folder exact")
            File(root, "subtitles/Show.S01E02.ass").writeText("shared folder other episode")
            File(root, "subtitles/Show.S01E01").mkdir()
            File(root, "subtitles/Show.S01E01/English.ass").writeText("episode folder language name")
            File(root, "subtitles/Show.S01E01/Signs.srt").writeText("episode folder signs")
            File(root, "subtitles/Show.S01E02").mkdir()
            File(root, "subtitles/Show.S01E02/English.ass").writeText("episode two language name")
            File(root, "subtitles/Unrelated").mkdir()
            File(root, "subtitles/Unrelated/English.ass").writeText("unrelated folder")

            assertEquals(
                listOf("Show.S01E01.ass", "English.ass", "Signs.srt"),
                matchingLocalSubtitleFiles(episodeOne).map { it.name }
            )
            assertEquals(
                listOf("Show.S01E02.ass", "English.ass"),
                matchingLocalSubtitleFiles(episodeTwo).map { it.name }
            )
        } finally {
            root.deleteRecursively()
        }
    }
}
