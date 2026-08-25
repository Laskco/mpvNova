package app.mpvnova.player

import android.graphics.Rect
import android.text.method.TransformationMethod
import android.view.View
import java.util.Locale

internal fun playerTitleCaseTransformation(
    textCase: PlayerTitleTextCase,
): TransformationMethod? = if (textCase == PlayerTitleTextCase.ORIGINAL) {
    null
} else {
    PlayerTitleCaseTransformation(textCase)
}

private class PlayerTitleCaseTransformation(
    private val textCase: PlayerTitleTextCase,
) : TransformationMethod {
    override fun getTransformation(source: CharSequence?, view: View?): CharSequence? {
        val text = source?.toString() ?: return source
        val locale = Locale.getDefault()
        return when (textCase) {
            PlayerTitleTextCase.ORIGINAL -> source
            PlayerTitleTextCase.UPPERCASE -> text.uppercase(locale)
            PlayerTitleTextCase.LOWERCASE -> text.lowercase(locale)
            PlayerTitleTextCase.TITLE_CASE -> text.toPlayerTitleCase(locale)
        }
    }

    override fun onFocusChanged(
        view: View?,
        sourceText: CharSequence?,
        focused: Boolean,
        direction: Int,
        previouslyFocusedRect: Rect?,
    ) = Unit
}

private fun String.toPlayerTitleCase(locale: Locale): String {
    var capitalizeNext = true
    return buildString(length) {
        this@toPlayerTitleCase.forEach { character ->
            if (character.isLetterOrDigit()) {
                append(
                    if (capitalizeNext) character.toString().uppercase(locale)
                    else character.toString().lowercase(locale),
                )
                capitalizeNext = false
            } else {
                append(character)
                capitalizeNext = character.isWhitespace() || character in WORD_SEPARATORS
            }
        }
    }
}

private val WORD_SEPARATORS = setOf('-', '/', '\u2013', '\u2014')
