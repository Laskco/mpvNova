package app.mpvnova.player

import androidx.preference.PreferenceManager

internal fun MPVActivity.pickPlayerAppearanceColor() {
    AppearanceTheme.migrateLegacyOled(this)
    val preferences = PreferenceManager.getDefaultSharedPreferences(applicationContext)
    val choices = appearanceColorChoices.map { choice ->
        PlayerPanelChoice(
            value = choice.value,
            title = getString(choice.labelRes),
            swatchColor = choice.color,
        )
    }
    val toggles = listOf(
        PlayerPanelToggle(
            key = AppearanceTheme.PREF_AMOLED_MODE,
            title = getString(R.string.appearance_amoled_mode_title),
            detail = getString(R.string.appearance_amoled_mode_summary),
            checked = preferences.getBoolean(AppearanceTheme.PREF_AMOLED_MODE, false),
        ),
        PlayerPanelToggle(
            key = AppearanceTheme.PREF_PURE_BLACK_SURFACES,
            title = getString(R.string.appearance_pure_black_surfaces_title),
            detail = getString(R.string.appearance_pure_black_surfaces_summary),
            checked = preferences.getBoolean(AppearanceTheme.PREF_PURE_BLACK_SURFACES, false),
        ),
    )
    openPlayerChoicePanel(
        eyebrowRes = R.string.drawer_section_interface,
        titleRes = R.string.appearance_color_theme_title,
        summaryRes = R.string.player_appearance_color_summary,
        choices = choices,
        selectedValue = AppearanceTheme.currentValue(this),
        toggles = toggles,
        refreshThemeOnChange = true,
        onToggled = { key, checked ->
            preferences.edit().putBoolean(key, checked).apply()
            applyLivePlayerAppearance()
        },
    ) { value ->
        preferences.edit()
            .putString(AppearanceTheme.PREF_KEY, value)
            .apply()
        applyLivePlayerAppearance()
    }
}

private fun MPVActivity.applyLivePlayerAppearance() {
    AppearanceTheme.applyPlayer(this)
    applyTranslucentPlayerWindowTheme()
    cachedActiveFilterColor = null
    appliedPlayerTitleStyle = null
    refreshPlayerUiTheme()
    applyPlayerTitleStyle(force = true)
    refreshAllFilterTints()
    drawerBinding = null
    drawerHandlersBound = false
    window.decorView.invalidate()
}
