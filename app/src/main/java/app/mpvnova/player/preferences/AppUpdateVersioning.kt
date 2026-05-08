package app.mpvnova.player.preferences

import android.os.Build
import org.json.JSONObject

internal fun normalizedVersion(versionName: String): String {
    return versionName.removeSuffix("-oldapi")
}

internal fun versionsMatch(first: String?, second: String?): Boolean {
    val normalizedFirst = first?.trim()?.removePrefix("v")?.removePrefix("V").orEmpty()
    val normalizedSecond = second?.trim()?.removePrefix("v")?.removePrefix("V").orEmpty()
    return normalizedFirst.isNotBlank() && normalizedFirst == normalizedSecond
}

internal fun isRemoteNewer(remote: String?, local: String?): Boolean {
    val remoteParts = remote.versionParts()
    val localParts = local.versionParts()
    return if (remoteParts.isEmpty() || localParts.isEmpty()) {
        val remoteNormalized = remote?.trim()?.removePrefix("v")?.removePrefix("V").orEmpty()
        val localNormalized = local?.trim()?.removePrefix("v")?.removePrefix("V").orEmpty()
        remoteNormalized.isNotBlank() && localNormalized.isNotBlank() &&
            remoteNormalized != localNormalized
    } else {
        val max = maxOf(remoteParts.size, localParts.size)
        val firstDifference = (0 until max).firstOrNull { index ->
            remoteParts.getOrElse(index) { 0 } != localParts.getOrElse(index) { 0 }
        }
        firstDifference?.let { index ->
            remoteParts.getOrElse(index) { 0 } > localParts.getOrElse(index) { 0 }
        } ?: false
    }
}

internal fun Throwable.cleanMessage(): String {
    return message?.takeIf { it.isNotBlank() } ?: javaClass.simpleName
}

internal fun String.cleanMarkdown(): String {
    return lines().joinToString("\n") { line ->
        line.replace("`", "")
            .replace(Regex("^#{1,6}\\s*"), "")
            .trimEnd()
    }.trim()
}

internal fun String.safeFilePart(): String {
    return replace(Regex("[^A-Za-z0-9._-]"), "_")
}

internal fun String?.versionParts(): List<Int> {
    if (isNullOrBlank())
        return emptyList()
    return Regex("\\d+").findAll(trim().removePrefix("v").removePrefix("V"))
        .mapNotNull { it.value.toIntOrNull() }
        .toList()
}

internal fun ReleaseInfo.displayTitle(): String? {
    return name.takeIf { it.isNotBlank() && it != tagName }?.cleanMarkdown()
}

internal fun chooseBestApkAsset(assets: List<JSONObject>): JSONObject? {
    return when {
        assets.isEmpty() -> null
        assets.size == 1 -> assets.first()
        else -> bestCompatibleApkAsset(assets)
    }
}

private fun bestCompatibleApkAsset(assets: List<JSONObject>): JSONObject {
    val supportedAbis = Build.SUPPORTED_ABIS?.toList().orEmpty()
    val exactMatch = supportedAbis.firstNotNullOfOrNull { abi ->
        assets.firstOrNull { asset ->
            asset.optString("name").contains(abi, ignoreCase = true)
        }
    }
    val universal = assets.firstOrNull { asset ->
        val name = asset.optString("name").lowercase()
        name.contains("universal") ||
            name.contains("all") ||
            name.contains("universal-release")
    }
    val abiNeutral = assets.firstOrNull { asset ->
        val name = asset.optString("name")
        KNOWN_ABIS.none { abi -> name.contains(abi, ignoreCase = true) }
    }
    return exactMatch ?: universal ?: abiNeutral ?: assets.first()
}
