package app.mpvnova.player.preferences

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import java.io.IOException
import java.io.OutputStream

internal object FullBackupDownloads {
    val isDirectWriteSupported: Boolean
        get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

    fun write(
        context: Context,
        filename: String,
        mimeType: String,
        writer: (OutputStream) -> Unit,
    ) {
        if (!isDirectWriteSupported) throw IOException("Direct Downloads export requires Android 10 or newer")
        writeToMediaStore(context, filename, mimeType, writer)
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeToMediaStore(
        context: Context,
        filename: String,
        mimeType: String,
        writer: (OutputStream) -> Unit,
    ) {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, filename)
            put(MediaStore.Downloads.MIME_TYPE, mimeType)
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IOException("Could not create backup in Downloads")
        runCatching {
            resolver.openOutputStream(uri, "w")?.use(writer)
                ?: throw IOException("Could not write backup in Downloads")
            values.clear()
            values.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }.onFailure { resolver.delete(uri, null, null) }.getOrThrow()
    }
}
