package app.mpvnova.player

import androidx.preference.PreferenceManager

internal fun MPVActivity.pickUiFont() {
    val prefs = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    val choices = UiFont.choices.map { choice ->
        PlayerPanelChoice(
            value = choice.value,
            title = getString(choice.titleRes),
            detail = getString(choice.detailRes),
        )
    }
    openPlayerChoicePanel(
        eyebrowRes = R.string.drawer_section_interface,
        titleRes = R.string.appearance_ui_font_title,
        summaryRes = R.string.appearance_ui_font_summary,
        choices = choices,
        selectedValue = UiFont.currentValue(this),
    ) { value ->
        prefs.edit().putString(UiFont.PREF_KEY, value).apply()
        UiFont.applyToViewTree(window.decorView)
    }
}
