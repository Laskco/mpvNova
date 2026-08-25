package app.mpvnova.player

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import androidx.preference.PreferenceManager

internal object UiFont {
    const val PREF_KEY = "ui_font_family"
    const val DEFAULT_VALUE = "system"

    internal data class Choice(
        val value: String,
        val titleRes: Int,
        val detailRes: Int,
        @param:FontRes val regularFontRes: Int?,
        @param:FontRes val boldFontRes: Int?,
    )

    val choices = listOf(
        Choice(DEFAULT_VALUE, R.string.ui_font_system_title, R.string.ui_font_system_detail, null, null),
        Choice(
            "inter",
            R.string.ui_font_inter_title,
            R.string.ui_font_inter_detail,
            R.font.inter_medium,
            R.font.inter_semibold,
        ),
        Choice(
            "manrope",
            R.string.ui_font_manrope_title,
            R.string.ui_font_manrope_detail,
            R.font.manrope_medium,
            R.font.manrope_semibold,
        ),
        Choice(
            "rubik",
            R.string.ui_font_rubik_title,
            R.string.ui_font_rubik_detail,
            R.font.rubik_medium,
            R.font.rubik_semibold,
        ),
        Choice(
            "atkinson_hyperlegible",
            R.string.ui_font_atkinson_hyperlegible_title,
            R.string.ui_font_atkinson_hyperlegible_detail,
            R.font.atkinson_hyperlegible_regular,
            R.font.atkinson_hyperlegible_bold,
        ),
        Choice(
            "tinos",
            R.string.ui_font_tinos_title,
            R.string.ui_font_tinos_detail,
            R.font.tinos_regular,
            R.font.tinos_bold,
        ),
        Choice(
            "lexend",
            R.string.ui_font_lexend_title,
            R.string.ui_font_lexend_detail,
            R.font.lexend_variable,
            R.font.lexend_variable,
        ),
        Choice(
            "source_sans_3",
            R.string.ui_font_source_sans_3_title,
            R.string.ui_font_source_sans_3_detail,
            R.font.source_sans_3_variable,
            R.font.source_sans_3_variable,
        ),
        Choice(
            "lato",
            R.string.ui_font_lato_title,
            R.string.ui_font_lato_detail,
            R.font.lato_medium,
            R.font.lato_semibold,
        ),
        Choice(
            "noto_sans",
            R.string.ui_font_noto_sans_title,
            R.string.ui_font_noto_sans_detail,
            R.font.noto_sans_variable,
            R.font.noto_sans_variable,
        ),
        Choice(
            "roboto_condensed",
            R.string.ui_font_roboto_condensed_title,
            R.string.ui_font_roboto_condensed_detail,
            R.font.roboto_condensed_variable,
            R.font.roboto_condensed_variable,
        ),
        Choice(
            "archivo",
            R.string.ui_font_archivo_title,
            R.string.ui_font_archivo_detail,
            R.font.archivo_variable,
            R.font.archivo_variable,
        ),
        Choice(
            "barlow",
            R.string.ui_font_barlow_title,
            R.string.ui_font_barlow_detail,
            R.font.barlow_medium,
            R.font.barlow_semibold,
        ),
        Choice(
            "ibm_plex_sans",
            R.string.ui_font_ibm_plex_sans_title,
            R.string.ui_font_ibm_plex_sans_detail,
            R.font.ibm_plex_sans_variable,
            R.font.ibm_plex_sans_variable,
        ),
        Choice(
            "figtree",
            R.string.ui_font_figtree_title,
            R.string.ui_font_figtree_detail,
            R.font.figtree_variable,
            R.font.figtree_variable,
        ),
        Choice(
            "karla",
            R.string.ui_font_karla_title,
            R.string.ui_font_karla_detail,
            R.font.karla_variable,
            R.font.karla_variable,
        ),
        Choice(
            "mulish",
            R.string.ui_font_mulish_title,
            R.string.ui_font_mulish_detail,
            R.font.mulish_variable,
            R.font.mulish_variable,
        ),
        Choice(
            "nunito_sans",
            R.string.ui_font_nunito_sans_title,
            R.string.ui_font_nunito_sans_detail,
            R.font.nunito_sans_variable,
            R.font.nunito_sans_variable,
        ),
        Choice(
            "open_sans",
            R.string.ui_font_open_sans_title,
            R.string.ui_font_open_sans_detail,
            R.font.open_sans_variable,
            R.font.open_sans_variable,
        ),
        Choice(
            "work_sans",
            R.string.ui_font_work_sans_title,
            R.string.ui_font_work_sans_detail,
            R.font.work_sans_variable,
            R.font.work_sans_variable,
        ),
    )

    fun currentValue(context: Context): String {
        val saved = PreferenceManager.getDefaultSharedPreferences(context)
            .getString(PREF_KEY, DEFAULT_VALUE)
        return choices.firstOrNull { it.value == saved }?.value ?: DEFAULT_VALUE
    }

    fun currentLabel(context: Context): String {
        val selected = currentValue(context)
        return context.getString(choices.first { it.value == selected }.titleRes)
    }

    fun hasChoice(value: String): Boolean = choices.any { it.value == value }

    fun typeface(
        context: Context,
        value: String,
        weight: Int,
        italic: Boolean,
        fallback: Typeface = Typeface.DEFAULT,
    ): Typeface {
        val choice = choices.firstOrNull { it.value == value }
            ?: choices.first { it.value == DEFAULT_VALUE }
        val fontRes = if (weight >= FONT_WEIGHT_SEMIBOLD) {
            choice.boldFontRes
        } else {
            choice.regularFontRes
        }
        val base = when {
            choice.value == DEFAULT_VALUE -> Typeface.create("sans-serif", Typeface.NORMAL)
            fontRes != null -> ResourcesCompat.getFont(context, fontRes) ?: fallback
            else -> fallback
        }
        return weightedTypeface(base, weight, italic)
    }

    fun applyToViewTree(root: View) {
        when (root) {
            is TextView -> applyToTextView(root)
            is ViewGroup -> for (index in 0 until root.childCount) {
                applyToViewTree(root.getChildAt(index))
            }
        }
    }

    fun applyToTextView(view: TextView) {
        val original = originalTypeface(view)
        val choice = choices.first { it.value == currentValue(view.context) }
        val bold = original.isBold
        val fontRes = if (bold) choice.boldFontRes else choice.regularFontRes
        if (fontRes == null) {
            view.typeface = original
            return
        }

        val base = ResourcesCompat.getFont(view.context, fontRes) ?: return
        val italic = original.isItalic
        view.typeface = styledTypeface(base, bold, italic)
    }

    fun applyWeight(view: TextView, bold: Boolean) {
        val original = originalTypeface(view)
        val choice = choices.first { it.value == currentValue(view.context) }
        val fontRes = if (bold) choice.boldFontRes else choice.regularFontRes
        if (fontRes == null) {
            view.typeface = Typeface.create(
                original,
                if (bold) Typeface.BOLD else Typeface.NORMAL,
            )
            return
        }

        val base = ResourcesCompat.getFont(view.context, fontRes) ?: return
        view.typeface = styledTypeface(base, bold, italic = false)
    }

    private fun originalTypeface(view: TextView): Typeface {
        val stored = view.getTag(R.id.ui_font_original_typeface) as? Typeface
        if (stored != null) return stored

        val original = view.typeface ?: Typeface.DEFAULT
        view.setTag(R.id.ui_font_original_typeface, original)
        return original
    }

    private fun styledTypeface(base: Typeface, bold: Boolean, italic: Boolean): Typeface {
        return weightedTypeface(
            base,
            if (bold) FONT_WEIGHT_SEMIBOLD else FONT_WEIGHT_MEDIUM,
            italic,
        )
    }

    private fun weightedTypeface(base: Typeface, weight: Int, italic: Boolean): Typeface {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
            return Typeface.create(base, weight, italic)
        val style = when {
            weight >= FONT_WEIGHT_SEMIBOLD && italic -> Typeface.BOLD_ITALIC
            weight >= FONT_WEIGHT_SEMIBOLD -> Typeface.BOLD
            italic -> Typeface.ITALIC
            else -> Typeface.NORMAL
        }
        return Typeface.create(base, style)
    }

    private const val FONT_WEIGHT_MEDIUM = 500
    private const val FONT_WEIGHT_SEMIBOLD = 600
}
