package app.mpvnova.player

import android.content.Context
import android.content.Intent
import android.net.Uri

internal fun documentTreeChooser(context: Context): Intent {
    val target = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).addFlags(
        Intent.FLAG_GRANT_READ_URI_PERMISSION or
            Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
    )
    return Intent.createChooser(target, context.getString(R.string.document_picker_choose_app))
}

internal fun createDocumentChooser(
    context: Context,
    mimeType: String,
    filename: String,
): Intent {
    val target = Intent(Intent.ACTION_CREATE_DOCUMENT)
        .addCategory(Intent.CATEGORY_OPENABLE)
        .setType(mimeType)
        .putExtra(Intent.EXTRA_TITLE, filename)
        .addFlags(
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
    return Intent.createChooser(target, context.getString(R.string.document_picker_choose_app))
}

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
