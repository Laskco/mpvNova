package app.mpvnova.player

import androidx.annotation.Keep

@Keep
internal object AnitomyNg {
    private const val MAX_FILENAME_LENGTH = 1024
    private val available: Boolean by lazy {
        try {
            System.loadLibrary("mpvnova_anitomy")
            true
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    fun parse(filename: String): List<Pair<String, String>> {
        if (filename.length > MAX_FILENAME_LENGTH) return emptyList()
        val fields = if (available) parseNative(filename) else null
        return fields?.asList()?.chunked(2)?.mapNotNull { pair ->
            if (pair.size == 2) pair[0] to pair[1] else null
        }.orEmpty()
    }

    @JvmStatic
    private external fun parseNative(filename: String): Array<String>?
}
