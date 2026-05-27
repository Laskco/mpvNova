package app.mpvnova.player

import android.content.Intent
import android.net.Uri

/** Forward an Activity result from a file picker into mpv. */
internal fun MPVActivity.addExternalThing(cmd: String, result: Int, data: Intent?) {
    if (result != RESULT_OK) return
    val path = data?.getStringExtra("path") ?: return
    val resolvedPath = if (path.startsWith("content://"))
        translateContentUri(Uri.parse(path))
    else
        path
    mpvCommand(arrayOf(cmd, resolvedPath, "cached"))
}
