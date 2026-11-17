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
        val fields = if (filename.length <= MAX_FILENAME_LENGTH && available) parseNative(filename) else null
        return fields?.let { List(it.size / 2) { index -> it[2 * index] to it[2 * index + 1] } }.orEmpty()
    }

    @JvmStatic
    private external fun parseNative(filename: String): Array<String>?
}
