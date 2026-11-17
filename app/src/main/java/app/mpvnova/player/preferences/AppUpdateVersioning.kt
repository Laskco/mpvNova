package app.mpvnova.player.preferences

import android.os.Build
import org.json.JSONObject
import java.util.Locale

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

internal fun chooseBestApkAsset(assets: List<JSONObject>): JSONObject? =
    choosePreferredApk(
        assets,
        Build.SUPPORTED_ABIS?.toList().orEmpty(),
        Build.VERSION.SDK_INT,
    ) { it.optString("name") }

internal fun chooseBestApkAssetName(
    assetNames: List<String>,
    supportedAbis: List<String>,
    sdkInt: Int = Build.VERSION.SDK_INT
): String? = choosePreferredApk(assetNames, supportedAbis, sdkInt) { it }

private inline fun <T> choosePreferredApk(
    assets: List<T>, supportedAbis: List<String>, sdkInt: Int, nameOf: (T) -> String,
): T? {
    val compatibleAbis = supportedAbis.map { it.lowercase(Locale.ROOT) }.filter { it in KNOWN_ABIS }
    if (sdkInt < Build.VERSION_CODES.M || compatibleAbis.isEmpty()) return null
    val preferredNames = (compatibleAbis + "universal").map { "app-default-$it-release.apk" }
    var bestRank = preferredNames.size
    var best: T? = null
    for (asset in assets) {
        val name = nameOf(asset)
        val rank = (0 until bestRank).firstOrNull { name.equals(preferredNames[it], ignoreCase = true) }
        if (rank != null) {
            best = asset
            bestRank = rank
            if (rank == 0) break
        }
    }
    return best
}

private val MARKDOWN_HEADING_PREFIX_PATTERN = Regex("^#{1,6}\\s*")
private val VERSION_PART_PATTERN = Regex("\\d+")
