package app.mpvnova.player

import android.util.JsonReader
import android.util.JsonToken
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.StringReader
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction

internal enum class AppearancePresetType(val wireName: String) {
    PLAYER_BAR("playerbar"),
    TITLE("clocktitle"),
}

internal data class AppearancePresetDocument(
    val type: AppearancePresetType,
    val name: String,
    val style: JSONObject,
)

/** Only the appearance model is serialized, never preferences or playback metadata. */
internal object PlayerAppearancePresetCodec {
    const val MAX_BYTES = 128 * 1024
    private const val FORMAT = "mpvnova.appearance-preset"
    private const val VERSION = 1

    fun encode(type: AppearancePresetType, name: String, style: JSONObject): ByteArray {
        val cleanName = sanitizeName(name)
        require(cleanName.isNotBlank())
        validatePresetStyle(style, template(type))
        return JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("type", type.wireName)
            put("name", cleanName)
            put("style", style)
        }.toString(2).toByteArray(Charsets.UTF_8).also { require(it.size <= MAX_BYTES) }
    }

    fun decode(input: InputStream, expectedType: AppearancePresetType): AppearancePresetDocument {
        val bytes = readBounded(input)
        val text = Charsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .decode(ByteBuffer.wrap(bytes)).toString()
        val root = JsonReader(StringReader(text)).use { reader ->
            reader.isLenient = false
            val value = PresetJsonParser(reader).readValue(0) as? JSONObject ?: error("Expected object")
            require(reader.peek() == JsonToken.END_DOCUMENT)
            value
        }
        require(root.keys().asSequence().toSet() == setOf("format", "version", "type", "name", "style"))
        require(root.get("format") == FORMAT)
        require(root.get("version") is Number)
        require(root.get("version").toString().toBigDecimal().compareTo(VERSION.toBigDecimal()) == 0)
        require(root.get("type") == expectedType.wireName)
        val name = sanitizeName(root.get("name") as? String ?: error("Expected name"))
        require(name.isNotBlank())
        val style = root.getJSONObject("style")
        validatePresetStyle(style, template(expectedType))
        return AppearancePresetDocument(expectedType, name, style)
    }

    fun sanitizeName(name: String): String = normalizedCustomPresetName(
        name.filterNot { Character.isISOControl(it) || Character.getType(it) == Character.FORMAT.toInt() },
    ).trimEnd { Character.isHighSurrogate(it) }

    private fun template(type: AppearancePresetType) = when (type) {
        AppearancePresetType.PLAYER_BAR -> PlayerUiCustomization.DEFAULT.toJson().apply {
            // JSONObject.put(key, null) omits theme-default colors from serialized styles.
            OPTIONAL_COLOR_FIELDS.forEach { put(it, "") }
        }
        AppearancePresetType.TITLE -> PlayerTitleStyle.DEFAULT.toJson()
    }

    private fun readBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        while (true) {
            val count = readChunk(input, buffer, minOf(buffer.size, MAX_BYTES + 1 - output.size()))
            if (count < 0) break
            output.write(buffer, 0, count)
            require(output.size() <= MAX_BYTES)
        }
        return output.toByteArray()
    }

    private fun readChunk(input: InputStream, buffer: ByteArray, limit: Int): Int {
        val count = input.read(buffer, 0, limit)
        if (count != 0) return count
        val byte = input.read()
        return if (byte < 0) -1 else {
            buffer[0] = byte.toByte()
            1
        }
    }
}

private class PresetJsonParser(private val reader: JsonReader) {
    fun readValue(depth: Int): Any {
        require(depth <= MAX_DEPTH)
        return when (reader.peek()) {
            JsonToken.BEGIN_OBJECT -> readObject(depth)
            JsonToken.BEGIN_ARRAY -> readArray(depth)
            JsonToken.STRING -> reader.nextString().also { require(it.length <= MAX_STRING_LENGTH) }
            JsonToken.BOOLEAN -> reader.nextBoolean()
            JsonToken.NUMBER -> reader.nextString().let { raw ->
                require(raw.length <= MAX_STRING_LENGTH && raw.toDouble().isFinite())
                raw.toBigDecimal()
            }
            JsonToken.NULL -> {
                reader.nextNull()
                JSONObject.NULL
            }
            else -> error("Unsupported JSON value")
        }
    }

    private fun readObject(depth: Int) = JSONObject().apply {
        reader.beginObject()
        while (reader.hasNext()) {
            val key = reader.nextName()
            require(key.length <= MAX_STRING_LENGTH && !has(key))
            put(key, readValue(depth + 1))
        }
        reader.endObject()
    }

    private fun readArray(depth: Int) = JSONArray().apply {
        reader.beginArray()
        while (reader.hasNext()) {
            require(length() < MAX_ARRAY_ITEMS)
            put(readValue(depth + 1))
        }
        reader.endArray()
    }
}

private fun validatePresetStyle(value: JSONObject, schema: JSONObject) {
    // Missing recognized fields use the typed parser's defaults, preserving older v1 files.
    value.keys().forEach { key ->
        require(schema.has(key))
        val actual = value.get(key)
        if (key !in OPTIONAL_COLOR_FIELDS || actual !== JSONObject.NULL) {
            validatePresetField(actual, schema.get(key))
        }
    }
}

private fun validatePresetField(actual: Any, expected: Any) {
    when (expected) {
        is JSONObject -> validatePresetStyle(actual as? JSONObject ?: error("Expected object"), expected)
        is JSONArray -> validatePresetArray(actual)
        is Boolean -> require(actual is Boolean)
        is String -> require(actual is String && actual.length <= MAX_STRING_LENGTH)
        is Int -> validatePresetInteger(actual)
        is Number -> require(actual is Number && actual.toDouble().isFinite() && actual.toFloat().isFinite())
        else -> error("Unsupported schema field")
    }
}

private fun validatePresetArray(actual: Any) {
    require(actual is JSONArray && actual.length() <= MAX_ARRAY_ITEMS)
    for (index in 0 until actual.length()) {
        require(actual.get(index) is String && actual.getString(index).length <= MAX_STRING_LENGTH)
    }
}

private fun validatePresetInteger(actual: Any) {
    require(actual is Number)
    val number = actual.toDouble()
    require(number.isFinite() && number >= Int.MIN_VALUE && number <= Int.MAX_VALUE)
    require(actual.toString().toBigDecimal().stripTrailingZeros().scale() <= 0)
}

private const val MAX_DEPTH = 8
private const val MAX_ARRAY_ITEMS = 64
private const val MAX_STRING_LENGTH = 256
private val OPTIONAL_COLOR_FIELDS = setOf(
    "seekbarPlayedColor", "seekbarBufferedColor", "seekbarUnplayedColor", "chapterMarkerColor",
)
