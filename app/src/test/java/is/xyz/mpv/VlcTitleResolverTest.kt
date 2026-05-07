package app.mpvnova.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class VlcTitleResolverTest {
    @Test
    fun externalItemTitleWinsAndIsDecoded() {
        val itemTitle = VlcTitleResolver.itemTitleFromExtra("Koori%20no%20Jyouheki")

        assertEquals(
            "Koori no Jyouheki",
            VlcTitleResolver.resolve(
                itemTitle = itemTitle,
                mediaTitle = "https://example.invalid/video.mkv",
                fileName = "video.mkv",
                isStream = true
            )
        )
    }

    @Test
    fun externalItemTitleIsNotReleaseParsed() {
        val title = "Gals.Cant.be.Kind.to.Otaku.S01E05.So.You.like.our.swimsuits.1080p.CR.WEB"

        assertEquals(title, VlcTitleResolver.itemTitleFromExtra(title))
    }

    @Test
    fun streamUrlMediaTitleIsIgnored() {
        assertEquals(
            "video",
            VlcTitleResolver.resolve(
                itemTitle = null,
                mediaTitle = "https://signed.example/video.mkv",
                fileName = "video.mkv",
                isStream = true
            )
        )
    }

    @Test
    fun mediaTitleMatchingFileNameIsIgnored() {
        assertEquals(
            "The.Ramparts.of.Ice.S01E01",
            VlcTitleResolver.resolve(
                itemTitle = null,
                mediaTitle = "The.Ramparts.of.Ice.S01E01.mkv",
                fileName = "The.Ramparts.of.Ice.S01E01.mkv",
                isStream = true
            )
        )
    }

    @Test
    fun validMediaTitleIsUsed() {
        assertEquals(
            "Episode 1",
            VlcTitleResolver.resolve(
                itemTitle = null,
                mediaTitle = "Episode 1",
                fileName = "The.Ramparts.of.Ice.S01E01.mkv",
                isStream = true
            )
        )
    }

    @Test
    fun fileNameFallbackOnlyStripsFinalExtension() {
        assertEquals(
            "The.Ramparts.of.Ice.S01E01.1080p.NF.WEB-DL",
            VlcTitleResolver.titleFromFileName("The.Ramparts.of.Ice.S01E01.1080p.NF.WEB-DL.mkv")
        )
    }

    @Test
    fun httpPathUsesLastPathSegmentWithoutQuery() {
        assertEquals(
            "The Ramparts of Ice S01E01.mkv",
            VlcTitleResolver.fileNameFromPathLike(
                "https://signed.example/download/The%20Ramparts%20of%20Ice%20S01E01.mkv?token=secret"
            )
        )
    }

    @Test
    fun contentPathUsesLastPathSegment() {
        assertEquals(
            "Nice File.mkv",
            VlcTitleResolver.fileNameFromPathLike("content://provider/tree/Nice%20File.mkv")
        )
    }

    @Test
    fun blankInputsStayNull() {
        assertNull(VlcTitleResolver.itemTitleFromExtra("   "))
        assertNull(VlcTitleResolver.fileNameFromPathLike(""))
        assertNull(VlcTitleResolver.titleFromFileName(null))
        assertNull(VlcTitleResolver.metaTitle(null, "video.mkv", true))
    }
}
