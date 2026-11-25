package app.mpvnova.player

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.os.Build
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.FontRes
import androidx.core.content.res.ResourcesCompat
import androidx.preference.PreferenceManager
import java.util.concurrent.ConcurrentHashMap

internal object UiFont {
    const val PREF_KEY = "ui_font_family"
    const val DEFAULT_VALUE = "system"

    private data class TypefaceKey(
        val value: String,
        val weight: Int,
        val italic: Boolean,
    )

    private val baseFontCache = ConcurrentHashMap<Int, Typeface>()
    private val styledTypefaceCache = ConcurrentHashMap<TypefaceKey, Typeface>()
    private val systemStyleCache = ConcurrentHashMap<Int, Typeface>()
    @Volatile
    private var observedPrefs: SharedPreferences? = null
    @Volatile
    private var cachedPrefValue: String? = null
    private val prefListener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
        if (key == null || key == PREF_KEY) {
            cachedPrefValue = resolveValue(prefs.getString(PREF_KEY, DEFAULT_VALUE))
        }
    }

    internal data class Choice(
        val value: String,
        val titleRes: Int,
        val detailRes: Int,
        @param:FontRes val regularFontRes: Int?,
        @param:FontRes val boldFontRes: Int?,
    ) {
        val styledTypefaces: Array<Typeface?> = arrayOfNulls(STYLED_TYPEFACE_SLOTS)
    }

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

    private val choicesByValue: Map<String, Choice> = choices.associateBy(Choice::value)
    private val defaultChoice: Choice = choicesByValue.getValue(DEFAULT_VALUE)

    fun currentValue(context: Context): String {
        val appContext = context.applicationContext ?: context
        val prefs = observedPrefs ?: PreferenceManager.getDefaultSharedPreferences(appContext).also {
            synchronized(this) {
                if (observedPrefs == null) {
                    it.registerOnSharedPreferenceChangeListener(prefListener)
                    cachedPrefValue = resolveValue(it.getString(PREF_KEY, DEFAULT_VALUE))
                    observedPrefs = it
                }
            }
        }
        return cachedPrefValue ?: resolveValue(prefs.getString(PREF_KEY, DEFAULT_VALUE)).also {
            cachedPrefValue = it
        }
    }

    fun currentLabel(context: Context): String {
        val choice = choicesByValue[currentValue(context)] ?: defaultChoice
        return context.getString(choice.titleRes)
    }

    fun hasChoice(value: String): Boolean = choicesByValue.containsKey(value)

    fun typeface(
        context: Context,
        value: String,
        weight: Int,
        italic: Boolean,
        fallback: Typeface = Typeface.DEFAULT,
    ): Typeface {
        val choice = choicesByValue[value] ?: defaultChoice
        val key = TypefaceKey(choice.value, weight, italic)
        return styledTypefaceCache[key] ?: run {
            val fontRes = if (weight >= FONT_WEIGHT_SEMIBOLD) choice.boldFontRes else choice.regularFontRes
            val base = when {
                choice.value == DEFAULT_VALUE -> Typeface.create("sans-serif", Typeface.NORMAL)
                fontRes != null -> loadBaseFont(context, fontRes)
                else -> null
            }
            if (base != null) {
                weightedTypeface(base, weight, italic).also { styledTypefaceCache[key] = it }
            } else {
                weightedTypeface(fallback, weight, italic)
            }
        }
    }

    fun applyToViewTree(root: View) {
        val choice = choicesByValue[currentValue(root.context)] ?: defaultChoice
        applyToViewTree(root, choice)
    }

    private fun applyToViewTree(root: View, choice: Choice) {
        when (root) {
            is TextView -> applyToTextView(root, choice)
            is ViewGroup -> for (index in 0 until root.childCount) {
                applyToViewTree(root.getChildAt(index), choice)
            }
        }
    }

    fun applyToTextView(view: TextView) {
        val choice = choicesByValue[currentValue(view.context)] ?: defaultChoice
        applyToTextView(view, choice)
    }

    private fun applyToTextView(view: TextView, choice: Choice) {
        val target = if (choice.regularFontRes == null && choice.boldFontRes == null) {
            view.getTag(R.id.ui_font_original_typeface) as? Typeface
        } else {
            val original = originalTypeface(view)
            resolveStyledChoiceTypeface(view.context, choice, original.isBold, original.isItalic)
        }
        if (target != null && view.typeface !== target) {
            view.typeface = target
        }
    }

    fun applyWeight(view: TextView, bold: Boolean) {
        val choice = choicesByValue[currentValue(view.context)] ?: defaultChoice
        val target = if (choice.regularFontRes == null && choice.boldFontRes == null) {
            val original = originalTypeface(view)
            val style = if (bold) Typeface.BOLD else Typeface.NORMAL
            if (original === Typeface.DEFAULT || original === Typeface.SANS_SERIF) {
                systemStyleCache.getOrPut(style) { Typeface.create(Typeface.SANS_SERIF, style) }
            } else {
                Typeface.create(original, style)
            }
        } else {
            originalTypeface(view)
            resolveStyledChoiceTypeface(view.context, choice, bold, italic = false)
        }
        if (target != null && view.typeface !== target) {
            view.typeface = target
        }
    }

    private fun resolveValue(saved: String?): String =
        if (saved != null && choicesByValue.containsKey(saved)) saved else DEFAULT_VALUE

    private fun loadBaseFont(context: Context, @FontRes fontRes: Int): Typeface? {
        return baseFontCache[fontRes] ?: ResourcesCompat.getFont(
            context.applicationContext ?: context,
            fontRes,
        )?.also { baseFontCache[fontRes] = it }
    }

    private fun resolveStyledChoiceTypeface(
        context: Context,
        choice: Choice,
        bold: Boolean,
        italic: Boolean,
    ): Typeface? {
        val slot = (if (bold) 1 else 0) or (if (italic) 2 else 0)
        choice.styledTypefaces[slot]?.let { return it }
        val weight = if (bold) FONT_WEIGHT_SEMIBOLD else FONT_WEIGHT_MEDIUM
        val fontRes = if (bold) choice.boldFontRes else choice.regularFontRes
        val base = fontRes?.let { loadBaseFont(context, it) } ?: return null
        return weightedTypeface(base, weight, italic).also { choice.styledTypefaces[slot] = it }
    }

    private fun originalTypeface(view: TextView): Typeface {
        val stored = view.getTag(R.id.ui_font_original_typeface) as? Typeface
        if (stored != null) return stored

        val original = view.typeface ?: Typeface.DEFAULT
        view.setTag(R.id.ui_font_original_typeface, original)
        return original
    }

    private const val STYLED_TYPEFACE_SLOTS = 4
    private const val FONT_WEIGHT_MEDIUM = 500
    private const val FONT_WEIGHT_SEMIBOLD = 600
}

private fun weightedTypeface(base: Typeface, weight: Int, italic: Boolean): Typeface {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P)
        return Typeface.create(base, weight, italic)
    val style = when {
        weight >= UI_FONT_WEIGHT_SEMIBOLD && italic -> Typeface.BOLD_ITALIC
        weight >= UI_FONT_WEIGHT_SEMIBOLD -> Typeface.BOLD
        italic -> Typeface.ITALIC
        else -> Typeface.NORMAL
    }
    return Typeface.create(base, style)
}

private const val UI_FONT_WEIGHT_SEMIBOLD = 600
