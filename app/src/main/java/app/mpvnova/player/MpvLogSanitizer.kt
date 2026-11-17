package app.mpvnova.player

import java.net.URI
import java.net.URISyntaxException

// mpv accepts many network protocols, including RTSP, FTP, SMB and protocol wrappers.
private val MPV_LOG_URL = Regex("(?i)[a-z][a-z0-9+.-]*://[^\\s\\\"<>]+")
private val MPV_LOG_HEADER = Regex(
    "(?i)\\b((?:proxy-)?authorization|(?:set-)?cookie|(?:x-)?(?:api-key|auth-token|access-token)" +
        "|x-amz-security-token)[ \\t]*:[^\\r\\n]*",
)
private const val HTTP_HEADER_FIELDS = "http-header-fields"
// The entire value is sensitive: list options can contain arbitrary header names, not only known credentials.
private val MPV_HEADER_OPTION_LINE = Regex("(?im)^[^\\r\\n]*\\b$HTTP_HEADER_FIELDS\\b[^\\r\\n]*")
private const val URL_SCHEME_DELIMITER_LENGTH = 3

internal fun sanitizeMpvLogText(text: String): String {
    val redactedOptions = if (text.contains(HTTP_HEADER_FIELDS, ignoreCase = true)) {
        MPV_HEADER_OPTION_LINE.replace(text, "$HTTP_HEADER_FIELDS=<redacted>")
    } else text
    if (':' !in redactedOptions) return redactedOptions
    val redactedHeaders = MPV_LOG_HEADER.replace(redactedOptions) { match ->
        "${match.groupValues[1]}: <redacted>"
    }
    return if ("://" in redactedHeaders) {
        MPV_LOG_URL.replace(redactedHeaders) { match -> sanitizeUrl(match.value) }
    } else redactedHeaders
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
