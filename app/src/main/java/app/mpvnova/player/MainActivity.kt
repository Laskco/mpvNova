package app.mpvnova.player

import android.content.SharedPreferences
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.preference.PreferenceManager
import app.mpvnova.player.preferences.AppUpdateManager

private val HOME_RECREATE_PREF_KEYS = setOf(
    "material_you_theming",
    AppearanceTheme.PREF_KEY,
    AppearanceTheme.PREF_AMOLED_MODE,
    AppearanceTheme.PREF_PURE_BLACK_SURFACES,
    UiScale.PREF_KEY
)

class MainActivity : AppCompatActivity(R.layout.activity_main) {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(UiScale.wrap(newBase))
    }

    private val updateManager by lazy { AppUpdateManager(this) }
    private var checkedForUpdatesThisSession = false
    private var appliedThemeValue = AppearanceTheme.DEFAULT_VALUE
    private var appliedUiScale = UiScale.DEFAULT_SCALE_PERCENT
    private lateinit var preferences: SharedPreferences
    private var appearanceRefreshPending = false
    private val appearancePreferenceListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key in HOME_RECREATE_PREF_KEYS)
                appearanceRefreshPending = true
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        appliedThemeValue = AppearanceTheme.currentValue(this)
        appliedUiScale = UiScale.currentScalePercent(this)
        AppearanceTheme.applyFilePicker(this)
        super.onCreate(savedInstanceState)
        preferences.registerOnSharedPreferenceChangeListener(appearancePreferenceListener)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        supportActionBar?.setTitle(R.string.mpv_activity)

        if (savedInstanceState == null) {
            with (supportFragmentManager.beginTransaction()) {
                setReorderingAllowed(true)
                add(R.id.fragment_container_view, MainScreenFragment())
                commit()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (appearanceRefreshPending ||
            AppearanceTheme.currentValue(this) != appliedThemeValue ||
            UiScale.currentScalePercent(this) != appliedUiScale) {
            appearanceRefreshPending = false
            recreate()
            return
        }
        updateManager.resumePendingInstallIfAllowed()
    }

    override fun onDestroy() {
        preferences.unregisterOnSharedPreferenceChangeListener(appearancePreferenceListener)
        super.onDestroy()
    }

    fun checkForHomeUpdatesOnce() {
        if (checkedForUpdatesThisSession)
            return
        checkedForUpdatesThisSession = true
        updateManager.checkForUpdates(
            showIfCurrent = false,
            respectIgnored = true,
            showProgress = false
        )
    }
}
