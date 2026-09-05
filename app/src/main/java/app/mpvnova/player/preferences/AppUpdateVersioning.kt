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
            .replace(MARKDOWN_HEADING_PREFIX_PATTERN, "")
            .trimEnd()
    }.trim()
}

internal fun String?.versionParts(): List<Int> {
    if (isNullOrBlank())
        return emptyList()
    return VERSION_PART_PATTERN.findAll(trim().removePrefix("v").removePrefix("V"))
        .mapNotNull { it.value.toIntOrNull() }
        .toList()
}

internal fun ReleaseInfo.displayTitle(): String? {
    return name.takeIf { it.isNotBlank() && it != tagName }?.cleanMarkdown()
}

internal fun chooseBestApkAsset(assets: List<JSONObject>): JSONObject? {
    val selectedName = chooseBestApkAssetName(
        assets.map { asset -> asset.optString("name") },
        Build.SUPPORTED_ABIS?.toList().orEmpty(),
        Build.VERSION.SDK_INT
    )
    return assets.firstOrNull { asset -> asset.optString("name") == selectedName }
}

internal fun chooseBestApkAssetName(
    assetNames: List<String>,
    supportedAbis: List<String>,
    sdkInt: Int = Build.VERSION.SDK_INT
): String? {
    return when {
        assetNames.isEmpty() -> null
        assetNames.size == 1 -> assetNames.first()
        else -> {
            val api29Universal = assetNames.firstOrNull { name ->
                val lowercaseName = name.lowercase()
                sdkInt <= Build.VERSION_CODES.Q &&
                    lowercaseName.contains("api29") &&
                    lowercaseName.contains("universal")
            }
            val exactMatch = supportedAbis.firstNotNullOfOrNull { abi ->
                assetNames.firstOrNull { name -> assetAbi(name) == abi.lowercase() }
            }
            val universal = assetNames.firstOrNull { assetName ->
                val name = assetName.lowercase()
                name.contains("universal") ||
                    name.contains("all") ||
                    name.contains("universal-release")
            }
            val abiNeutral = assetNames.firstOrNull { name ->
                KNOWN_ABIS.none { abi -> name.contains(abi, ignoreCase = true) }
            }
            api29Universal ?: exactMatch ?: universal ?: abiNeutral ?: assetNames.first()
        }
    }
}

private fun assetAbi(assetName: String): String? {
    val lowercaseName = assetName.lowercase()
    return KNOWN_ABIS
        .sortedByDescending(String::length)
        .firstOrNull { abi -> lowercaseName.contains(abi.lowercase()) }
        ?.lowercase()
}

private val MARKDOWN_HEADING_PREFIX_PATTERN = Regex("^#{1,6}\\s*")
private val VERSION_PART_PATTERN = Regex("\\d+")
