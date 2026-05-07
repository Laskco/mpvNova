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
    fun externalItemTitleKeepsCleanTitle() {
        assertEquals(
            "Gals Cant be Kind to Otaku S01E03",
            VlcTitleResolver.itemTitleFromExtra("Gals Cant be Kind to Otaku S01E03")
        )
    }

    @Test
    fun releaseStyleExternalItemTitleIsCleaned() {
        val title = "Gals.Cant.be.Kind.to.Otaku.S01E05.So.You.like.our.swimsuits.1080p.CR.WEB"

        assertEquals("Gals Cant be Kind to Otaku S01E05", VlcTitleResolver.itemTitleFromExtra(title))
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
            "The Ramparts of Ice S01E01",
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
            "The Ramparts of Ice S01E01",
            VlcTitleResolver.titleFromFileName("The.Ramparts.of.Ice.S01E01.1080p.NF.WEB-DL.mkv")
        )
    }

    @Test
    fun fileNameFallbackStopsAtEpisodeCodeBeforeReleaseTags() {
        assertEquals(
            "Gals Cant be Kind to Otaku S01E03",
            VlcTitleResolver.titleFromFileName(
                "Gals.Cant.be.Kind.to.Otaku.S01E03.Do.You.Want.to.Come.Over.1080p.CR.WEB-DL.mkv"
            )
        )
    }

    @Test
    fun torrentMetadataQueryUsesSafeNameOnly() {
        assertEquals(
            "Koori no Jyouheki",
            VlcTitleResolver.queryTitleFromPathLike(
                "1?torrent_name=The.Ramparts.of.Ice.S01E01.SINIRLAR.VE.DUVARLAR.REPACK.1080p.NF.WEB-DL.mkv&name=Koori%20no%20Jyouheki&media_id=tt39123061"
            )
        )
    }

    @Test
    fun mediaTitleContainingTorrentMetadataIsIgnored() {
        assertEquals(
            "Gals Cant be Kind to Otaku S01E03",
            VlcTitleResolver.resolve(
                itemTitle = null,
                mediaTitle = "1?torrent_name=Gals.Cant.be.Kind.to.Otaku.S01E03.1080p.mkv&media_id=tt123",
                fileName = "Gals.Cant.be.Kind.to.Otaku.S01E03.1080p.mkv",
                isStream = true
            )
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
