@file:Suppress("MatchingDeclarationName")

package app.mpvnova.player

import androidx.annotation.StringRes

internal data class ShieldFallbackOption(
    val value: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val descRes: Int,
    val compactLabel: String,
)

internal val SHIELD_FALLBACK_OPTIONS = listOf(
    ShieldFallbackOption(
        MPVView.SHIELD_DECODER_FALLBACK_DEFAULT,
        R.string.shield_fallback_default_title,
        R.string.shield_fallback_default_desc,
        "Stock",
    ),
    ShieldFallbackOption(
        MPVView.SHIELD_DECODER_FALLBACK_COPY,
        R.string.shield_fallback_light_title,
        R.string.shield_fallback_light_desc,
        "Light",
    ),
)

internal fun shieldFallbackOption(value: String?): ShieldFallbackOption {
    val normalized = value.toShieldDecoderFallback()
    return SHIELD_FALLBACK_OPTIONS.firstOrNull { it.value == normalized }
        ?: SHIELD_FALLBACK_OPTIONS.first()
}
