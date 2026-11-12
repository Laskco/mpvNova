package app.mpvnova.player

import java.net.URI
import java.net.URISyntaxException

// mpv accepts many network protocols, including RTSP, FTP, SMB and protocol wrappers.
private val MPV_LOG_URL = Regex("(?i)[a-z][a-z0-9+.-]*://[^\\s\\\"<>]+")
private val MPV_LOG_HEADER = Regex(
    "(?i)(authorization|proxy-authorization|cookie|set-cookie):\\s*[^\\r\\n]+",
)
private const val URL_SCHEME_DELIMITER_LENGTH = 3

internal fun sanitizeMpvLogText(text: String): String {
    val redactedHeaders = MPV_LOG_HEADER.replace(text) { match ->
        "${match.groupValues[1]}: <redacted>"
    }
    return MPV_LOG_URL.replace(redactedHeaders) { match -> sanitizeUrl(match.value) }
}

private fun sanitizeUrl(url: String): String {
    val schemeEnd = url.indexOf("://") + URL_SCHEME_DELIMITER_LENGTH
    if (schemeEnd < URL_SCHEME_DELIMITER_LENGTH)
        return "<redacted-url>"
    val authorityEnd = sequenceOf(
        url.indexOf('/', schemeEnd),
        url.indexOf('?', schemeEnd),
        url.indexOf('#', schemeEnd),
    ).filter { it >= 0 }.minOrNull() ?: url.length
    val authority = url.substring(schemeEnd, authorityEnd)
    val hostPort = authority.substringAfterLast('@').trimEnd('\'')
    // Only expose a parsed host/port, never an unrecognized authority containing secrets.
    val safeHost = try {
        val parsed = URI("${url.substring(0, schemeEnd)}$hostPort")
        parsed.host?.let { host -> host + if (parsed.port >= 0) ":${parsed.port}" else "" }
    } catch (_: URISyntaxException) {
        null
    }
    val hasPathOrParameters = authorityEnd < url.length
    return buildString {
        append(url, 0, schemeEnd)
        if ('@' in authority) append("<redacted>@")
        append(safeHost ?: if (authority.isEmpty()) "" else "<redacted-host>")
        if (hasPathOrParameters)
            append("/<redacted>")
    }
}
