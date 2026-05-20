package app.mpvnova.player

import java.io.File

internal fun String.toCanonicalLocalFile(): File? {
    val localPath = takeUnless { it.contains("://") || it.contains('\u0000') }
    return localPath?.let(::File)?.canonicalFileOrNull()
}

internal fun File.resolveConfinedChild(childName: String): File? {
    val safeChildName = childName.takeUnless {
        it.contains('/') || it.contains('\\') || it.contains('\u0000')
    }
    val parent = canonicalFileOrNull()
    return if (safeChildName != null && parent != null) {
        File(parent, safeChildName)
            .canonicalFileOrNull()
            ?.takeIf { it.isUnder(parent) }
    } else {
        null
    }
}

internal fun File.listConfinedChildren(root: File): Sequence<File> {
    val canonicalRoot = root.canonicalFileOrNull()
    return listFiles()
        ?.asSequence()
        ?.mapNotNull { child ->
            child.canonicalFileOrNull()
                ?.takeIf { canonicalRoot != null && it.isUnder(canonicalRoot) }
        }
        ?: emptySequence()
}

internal fun File.canonicalFileOrNull(): File? =
    runCatching { canonicalFile }.getOrNull()

private fun File.isUnder(parent: File): Boolean {
    val childPath = path
    val parentPath = parent.path
    val parentPrefix = parentPath.trimEnd(File.separatorChar) + File.separator
    return childPath == parentPath || childPath.startsWith(parentPrefix)
}
