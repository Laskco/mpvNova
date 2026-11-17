package app.mpvnova.player

import app.mpvnova.player.preferences.writeSupportMpvLogEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class MpvLogSanitizerTest {
    @Test
    fun redactsUrlUserInfoPathQueryAndFragmentButKeepsSchemeHostAndPort() {
        assertEquals(
            "Opening rtsp://<redacted>@camera.example:8554/<redacted> and https://media.example/<redacted>",
            sanitizeMpvLogText(
                "Opening rtsp://operator:password@camera.example:8554/private?key=secret#hidden " +
                    "and https://media.example?token=private",
            ),
        )
        assertEquals(
            "Opening smb://<redacted>@[2001:db8::1]:445/<redacted>",
            sanitizeMpvLogText("Opening smb://user:secret@[2001:db8::1]:445/private"),
        )
    }

    @Test
    fun redactsUnparseableAuthorities() {
        assertEquals(
            "Opening https://<redacted-host>/<redacted>",
            sanitizeMpvLogText("Opening https://bad%40secret.example:notaport/private"),
        )
    }

    @Test
    fun redactsCredentialHeadersAndWholeMpvHeaderOptionLines() {
        val input = "aUtHoRiZaTiOn:\tBearer auth-secret\r\n" +
            "Proxy-Authorization: Basic proxy-secret\r\n" +
            "Set-Cookie: session=cookie-secret\r\n" +
            "X-Api-Key: api-secret\r\n" +
            "X-Amz-Security-Token: cloud-secret\r\n" +
            "[cplayer] Set property: HTTP-HEADER-FIELDS=\"X-Custom: custom-secret,X-Other: other-secret\"\r\n" +
            "Decoder ready"
        assertEquals(
            "aUtHoRiZaTiOn: <redacted>\r\nProxy-Authorization: <redacted>\r\n" +
                "Set-Cookie: <redacted>\r\nX-Api-Key: <redacted>\r\nX-Amz-Security-Token: <redacted>\r\n" +
                "http-header-fields=<redacted>\r\nDecoder ready",
            sanitizeMpvLogText(input),
        )
    }

    @Test
    fun leavesOrdinaryDiagnosticsUnchanged() {
        val log = "[ffmpeg] Video: hevc 1920x1080\n[display] Refresh rate: 59.94\n"
        assertEquals(log, sanitizeMpvLogText(log))
        assertEquals("", sanitizeMpvLogText(""))
    }

    @Test
    fun supportZipRedactsPreviouslySavedAndLiveMpvLogs() {
        val previous = "Opening https://saved-user:saved-password@media.example/old-path?token=saved-token\n" +
            "Authorization: Bearer saved-header\n" +
            "Previous decoder: hevc\n"
        val current = listOf(
            "http-header-fields=X-Custom: live-header",
            "Opening ftp://media.example/live-path",
            "Current decoder: h264",
        )
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { it.writeSupportMpvLogEntry(previous, current) }

        ZipInputStream(bytes.toByteArray().inputStream()).use { zip ->
            assertEquals("mpv-log.txt", zip.nextEntry?.name)
            val exportedLog = zip.readBytes().toString(Charsets.UTF_8)
            zip.closeEntry()
            assertNull(zip.nextEntry)
            assertTrue(exportedLog.contains("Previous mpvNova process"))
            assertTrue(exportedLog.contains("Current mpvNova process (last 3 mpv log lines)"))
            assertTrue(exportedLog.contains("Previous decoder: hevc"))
            assertTrue(exportedLog.contains("Current decoder: h264"))
            assertTrue(exportedLog.contains("https://<redacted>@media.example/<redacted>"))
            assertTrue(exportedLog.contains("Authorization: <redacted>"))
            assertTrue(exportedLog.contains("http-header-fields=<redacted>"))
            assertTrue(exportedLog.contains("ftp://media.example/<redacted>"))
            listOf("saved-user", "saved-password", "old-path", "saved-token", "saved-header", "live-header", "live-path")
                .forEach { secret -> assertFalse("ZIP exposes $secret", exportedLog.contains(secret)) }
        }
    }
}
