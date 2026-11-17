package app.mpvnova.player

import java.io.IOException
import java.nio.file.Files
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNoException
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalAutoNextTest {
    @get:Rule val directory = TemporaryFolder()

    @Test
    fun selectedRegularFileCanExpandItsParentButDirectoryCannot() {
        val media = directory.newFile("episode.mkv")
        assertTrue(media.canQueueFromSelectedDirectory())
        assertFalse(directory.newFolder("folder.mkv").canQueueFromSelectedDirectory())
        assertFalse(directory.root.resolve("missing.mkv").canQueueFromSelectedDirectory())
    }

    @Test
    fun linkCannotExpandADifferentParent() {
        val target = directory.newFolder("elsewhere").resolve("episode.mkv").apply { writeText("test") }
        val link = directory.root.resolve("episode.mkv")
        try {
            Files.createSymbolicLink(link.toPath(), target.toPath())
        } catch (error: IOException) {
            assumeNoException(error)
        } catch (error: UnsupportedOperationException) {
            assumeNoException(error)
        }
        assertFalse(link.canQueueFromSelectedDirectory())
    }

    @Test
    fun documentMustResolveToExactPathInsideItsTree() {
        val primary = "/storage/emulated/0"
        assertTrue(isLocalFileInStorageTree("primary:Shows/A.mkv", "primary:Shows", "$primary/Shows/A.mkv", primary))
        assertTrue(isLocalFileInStorageTree("ABCD-1234:Shows/A.mkv", "ABCD-1234:", "/storage/ABCD-1234/Shows/A.mkv", primary))
        assertFalse(isLocalFileInStorageTree("primary:Shows2/A.mkv", "primary:Shows", "$primary/Shows2/A.mkv", primary))
        assertFalse(isLocalFileInStorageTree("primary:Shows/A.mkv", "primary:Shows", "$primary/Other/A.mkv", primary))
        assertFalse(isLocalFileInStorageTree("primary:Shows/../A.mkv", "primary:Shows", "$primary/Shows/../A.mkv", primary))
        assertFalse(isLocalFileInStorageTree("ABCD-1234:Shows/A.mkv", "primary:Shows", "/storage/ABCD-1234/Shows/A.mkv", primary))
    }
}
