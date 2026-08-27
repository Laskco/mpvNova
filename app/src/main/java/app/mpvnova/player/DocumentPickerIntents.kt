package app.mpvnova.player

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.File

internal fun openDocumentChooser(
    context: Context,
    mimeTypes: Array<String>,
    allowMultiple: Boolean = false,
): Intent {
    val target = Intent(Intent.ACTION_GET_CONTENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType(if (mimeTypes.size == 1) mimeTypes.first() else "*/*")
        .putExtra(Intent.EXTRA_MIME_TYPES, mimeTypes)
        .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, allowMultiple)
        .addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION,
        )
    return Intent.createChooser(target, context.getString(R.string.document_picker_choose_app))
}

internal fun Intent.selectedDocumentUris(): List<Uri> = buildList {
    data?.let(::add)
    clipData?.let { selected ->
        for (index in 0 until selected.itemCount) {
            add(selected.getItemAt(index).uri)
        }
    }
}.distinct()

internal fun shaderImportUrisFromResult(resultCode: Int, data: Intent?): List<Uri> {
    val path = data?.getStringExtra("path")
    return when {
        resultCode != Activity.RESULT_OK -> emptyList()
        path == null -> data?.selectedDocumentUris().orEmpty()
        path.startsWith('/') -> listOf(Uri.fromFile(File(path)))
        else -> listOf(Uri.parse(path))
    }
}
