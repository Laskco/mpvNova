package app.mpvnova.player

import java.io.File

internal fun String.toCanonicalLocalFile(): File? {
    val localPath = takeUnless { it.contains("://") || it.contains('\u0000') }
    return localPath?.let(::File)?.canonicalFileOrNull()
}

internal fun File.resolveConfinedChildFromCanonical(childName: String): File? {
    val safeChildName = childName.takeUnless {
        it.contains('/') || it.contains('\\') || it.contains('\u0000')
    } ?: return null
    return File(this, safeChildName)
        .canonicalFileOrNull()
        ?.takeIf { it.isUnder(this) }
}

internal fun File.canonicalFileOrNull(): File? =
    runCatching { canonicalFile }.getOrNull()

internal fun File.isUnder(parent: File): Boolean {
    val childPath = path
    val parentPath = parent.path
    val parentPrefix = parentPath.trimEnd(File.separatorChar) + File.separator
    return childPath == parentPath || childPath.startsWith(parentPrefix)
}
