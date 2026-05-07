package app.mpvnova.player.preferences

import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.graphics.Rect
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.activity.enableEdgeToEdge
import androidx.annotation.XmlRes
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.ListPreference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.Preference.SummaryProvider
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.DynamicColors
import app.mpvnova.player.AppearanceTheme
import app.mpvnova.player.R
import app.mpvnova.player.databinding.ActivitySettingsBinding

class PreferenceActivity : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
    SharedPreferences.OnSharedPreferenceChangeListener, FragmentManager.OnBackStackChangedListener {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var preferences: SharedPreferences
    private val updateManager by lazy { AppUpdateManager(this) }
    private var currentSubtitle: CharSequence? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        AppearanceTheme.applyPreferences(this)
        super.onCreate(savedInstanceState)

        preferences = PreferenceManager.getDefaultSharedPreferences(this)
        preferences.registerOnSharedPreferenceChangeListener(this)
        supportFragmentManager.addOnBackStackChangedListener(this)
        if (preferences.getBoolean("material_you_theming", false))
            DynamicColors.applyToActivityIfAvailable(this)
        enableEdgeToEdge()
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedDispatcher.onBackPressed()
        }
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.updatePadding(
                left = systemBars.left,
                top = 0,
                right = systemBars.right,
                bottom = systemBars.bottom
            )
            binding.toolbar.updatePadding(top = systemBars.top)
            insets
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main, SettingsFragment())
                .commit()
        }
        currentSubtitle = savedInstanceState?.getCharSequence("subtitle")
            ?: getString(R.string.settings_root_subtitle)
        updateChrome()
    }

    override fun onBackStackChanged() {
        if (supportFragmentManager.backStackEntryCount == 0)
            currentSubtitle = getString(R.string.settings_root_subtitle)
        updateChrome()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putCharSequence("subtitle", currentSubtitle)
        supportFragmentManager.removeOnBackStackChangedListener(this)
    }

    override fun onResume() {
        super.onResume()
        updateManager.resumePendingInstallIfAllowed()
    }

    override fun onDestroy() {
        super.onDestroy()
        preferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key != "material_you_theming" &&
            key != AppearanceTheme.PREF_KEY &&
            key != AppearanceTheme.PREF_AMOLED_MODE &&
            key != AppearanceTheme.PREF_PURE_BLACK_SURFACES
        ) return
        if (key == "material_you_theming" && sharedPreferences.getBoolean(key, false))
            DynamicColors.applyToActivityIfAvailable(this)
        recreate()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                return true
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onPreferenceStartFragment(
        caller: PreferenceFragmentCompat, pref: Preference
    ): Boolean {
        val fragment = supportFragmentManager.fragmentFactory.instantiate(
            classLoader, pref.fragment ?: return false
        ).apply { arguments = pref.extras }

        supportFragmentManager.beginTransaction().replace(R.id.main, fragment).addToBackStack(null)
            .commit()

        currentSubtitle = pref.summary ?: pref.title
        updateChrome()
        return true
    }

    private fun updateChrome() {
        binding.heroSubtitle.text = currentSubtitle ?: getString(R.string.settings_root_subtitle)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun checkForAppUpdates() {
        updateManager.checkForUpdates()
    }

    private fun showReleaseHistory() {
        updateManager.showReleaseHistory()
    }

    abstract class StyledPreferenceFragment(
        @param:XmlRes private val preferencesRes: Int
    ) : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(preferencesRes, rootKey)
            onPreferencesLoaded()
        }

        @SuppressLint("RestrictedApi")
        override fun onCreateAdapter(preferenceScreen: PreferenceScreen): RecyclerView.Adapter<*> {
            return object : PreferenceGroupAdapter(preferenceScreen) {
                override fun onBindViewHolder(holder: PreferenceViewHolder, position: Int) {
                    super.onBindViewHolder(holder, position)
                    val preference = getItem(position)
                    holder.itemView.stateListAnimator = null
                    holder.itemView.background = if (preference?.isSelectable == true) {
                        AppCompatResources.getDrawable(holder.itemView.context, R.drawable.bg_list_row)
                    } else {
                        null
                    }
                }
            }
        }

        protected open fun onPreferencesLoaded() = Unit

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            setDivider(null)
            setDividerHeight(0)

            val horizontalPadding = dp(4)
            listView.apply {
                itemAnimator = null
                clipToPadding = true
                overScrollMode = View.OVER_SCROLL_NEVER
                setPadding(horizontalPadding, dp(2), horizontalPadding, dp(6))
                if (itemDecorationCount == 0)
                    addItemDecoration(VerticalSpaceDecoration(dp(2)))
            }
            ViewCompat.setOnApplyWindowInsetsListener(listView) { recycler, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                recycler.updatePadding(
                    left = horizontalPadding + systemBars.left,
                    top = dp(2),
                    right = horizontalPadding + systemBars.right,
                    bottom = dp(6) + systemBars.bottom
                )
                insets
            }
        }

        private fun dp(value: Int): Int = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    class VerticalSpaceDecoration(private val verticalSpace: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(
            outRect: Rect,
            view: View,
            parent: RecyclerView,
            state: RecyclerView.State
        ) {
            if (parent.getChildAdapterPosition(view) > 0)
                outRect.top = verticalSpace
        }
    }

    class SettingsFragment : StyledPreferenceFragment(R.xml.preferences_root) {
        override fun onPreferencesLoaded() {
            findPreference<Preference>("check_for_updates")?.setOnPreferenceClickListener {
                (activity as? PreferenceActivity)?.checkForAppUpdates()
                true
            }
            findPreference<Preference>("release_history")?.setOnPreferenceClickListener {
                (activity as? PreferenceActivity)?.showReleaseHistory()
                true
            }
        }
    }

    class AppearancePreference : Fragment() {
        private data class ThemeChoice(
            val value: String,
            val labelRes: Int,
            val color: Int
        )

        private val themeChoices = listOf(
            ThemeChoice("nova", R.string.appearance_theme_white, Color.WHITE),
            ThemeChoice("crimson", R.string.appearance_theme_crimson, Color.rgb(244, 67, 54)),
            ThemeChoice("ocean", R.string.appearance_theme_ocean, Color.rgb(33, 150, 243)),
            ThemeChoice("violet", R.string.appearance_theme_violet, Color.rgb(156, 39, 176)),
            ThemeChoice("emerald", R.string.appearance_theme_emerald, Color.rgb(76, 175, 80)),
            ThemeChoice("amber", R.string.appearance_theme_amber, Color.rgb(255, 152, 0)),
            ThemeChoice("rose", R.string.appearance_theme_rose, Color.rgb(216, 27, 96)),
        )

        private lateinit var preferences: SharedPreferences

        override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?
        ): View {
            return inflater.inflate(R.layout.fragment_appearance, container, false)
        }

        override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
            super.onViewCreated(view, savedInstanceState)
            AppearanceTheme.migrateLegacyOled(requireContext())
            preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
            populateColorThemes(view.findViewById(R.id.colorThemeRow))
            bindSwitchRow(
                view.findViewById(R.id.amoledRow),
                view.findViewById(R.id.amoledSwitch),
                AppearanceTheme.PREF_AMOLED_MODE
            )
            bindSwitchRow(
                view.findViewById(R.id.pureBlackRow),
                view.findViewById(R.id.pureBlackSwitch),
                AppearanceTheme.PREF_PURE_BLACK_SURFACES
            )
        }

        private fun populateColorThemes(row: LinearLayout) {
            row.removeAllViews()
            val selectedTheme = AppearanceTheme.currentValue(requireContext())
            themeChoices.forEachIndexed { index, choice ->
                row.addView(createThemeTile(choice, selectedTheme == choice.value).apply {
                    if (index == 0) requestFocus()
                })
            }
        }

        private fun createThemeTile(choice: ThemeChoice, selected: Boolean): View {
            val context = requireContext()
            val tile = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = android.view.Gravity.CENTER
                isClickable = true
                isFocusable = true
                isSelected = selected
                background = AppCompatResources.getDrawable(context, R.drawable.bg_appearance_tile)
                contentDescription = getString(choice.labelRes)
                setPadding(dp(8), dp(8), dp(8), dp(8))
                setOnClickListener {
                    if (preferences.getString(AppearanceTheme.PREF_KEY, AppearanceTheme.DEFAULT_VALUE) == choice.value)
                        return@setOnClickListener
                    preferences.edit()
                        .putString(AppearanceTheme.PREF_KEY, choice.value)
                        .apply()
                }
            }
            tile.layoutParams = LinearLayout.LayoutParams(dp(88), dp(86)).apply {
                marginEnd = dp(18)
            }

            val swatch = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(choice.color)
                    setStroke(dp(1), if (choice.value == "nova") Color.rgb(210, 210, 210) else choice.color)
                }
            }
            tile.addView(swatch, LinearLayout.LayoutParams(dp(40), dp(40)))

            val label = TextView(context).apply {
                text = getString(choice.labelRes)
                setTextColor(Color.rgb(188, 188, 188))
                textSize = 12f
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                gravity = android.view.Gravity.CENTER
                includeFontPadding = false
            }
            tile.addView(label, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(8)
            })
            return tile
        }

        private fun bindSwitchRow(row: View, switch: SwitchCompat, key: String) {
            switch.isChecked = preferences.getBoolean(key, false)
            fun setPreferenceChecked(checked: Boolean) {
                switch.isChecked = checked
                if (preferences.getBoolean(key, false) == checked)
                    return
                preferences.edit()
                    .putBoolean(key, checked)
                    .apply()
            }
            row.setOnClickListener {
                setPreferenceChecked(!preferences.getBoolean(key, false))
            }
            switch.setOnClickListener {
                setPreferenceChecked(switch.isChecked)
            }
        }

        private fun dp(value: Int): Int = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    class GeneralPreference : StyledPreferenceFragment(R.xml.pref_general) {
        override fun onPreferencesLoaded() {
            preferenceManager.findPreference<Preference>("material_you_theming")?.isVisible =
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)

            val autoFallbackPref = findPreference<SwitchPreferenceCompat>("decoder_auto_fallback")
            val preferredDecoderPref = findPreference<ListPreference>("preferred_decoder_mode")
            if (preferredDecoderPref != null) {
                val (entries, values) = buildDecoderPreferenceOptions()
                preferredDecoderPref.entries = entries
                preferredDecoderPref.entryValues = values
                if (preferredDecoderPref.value.isNullOrBlank())
                    preferredDecoderPref.value = defaultPreferredDecoderMode()
                preferredDecoderPref.summaryProvider = SummaryProvider<ListPreference> { pref ->
                    val entry = pref.entry?.toString()
                        ?: getString(R.string.pref_preferred_decoder_mode_summary)
                    getString(
                        R.string.pref_preferred_decoder_mode_summary_format,
                        entry,
                        decoderModeDescription(pref.value)
                    )
                }
            }
            fun syncDecoderPreferenceVisibility() {
                preferredDecoderPref?.isVisible = autoFallbackPref?.isChecked == false
            }
            syncDecoderPreferenceVisibility()
            autoFallbackPref?.setOnPreferenceChangeListener { _, newValue ->
                preferredDecoderPref?.isVisible = (newValue as? Boolean) == false
                true
            }
        }

        private fun buildDecoderPreferenceOptions(): Pair<Array<CharSequence>, Array<CharSequence>> {
            val entries = mutableListOf<CharSequence>()
            val values = mutableListOf<CharSequence>()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                entries.add(getString(R.string.decoder_mode_hw_plus_settings))
                values.add(app.mpvnova.player.MPVView.DECODER_MODE_HW_PLUS)
            }
            entries.add(getString(R.string.decoder_mode_hw_settings))
            values.add(app.mpvnova.player.MPVView.DECODER_MODE_HW)
            entries.add(getString(R.string.decoder_mode_sw_settings))
            values.add(app.mpvnova.player.MPVView.DECODER_MODE_SW)
            entries.add(getString(R.string.decoder_mode_gnext_settings))
            values.add(app.mpvnova.player.MPVView.DECODER_MODE_GNEXT)
            entries.add(getString(R.string.decoder_mode_shield_h10p_settings))
            values.add(app.mpvnova.player.MPVView.DECODER_MODE_SHIELD_H10P)
            return Pair(entries.toTypedArray(), values.toTypedArray())
        }

        private fun decoderModeDescription(mode: String?): String {
            return when (mode) {
                app.mpvnova.player.MPVView.DECODER_MODE_HW_PLUS ->
                    getString(R.string.decoder_mode_hw_plus_description)
                app.mpvnova.player.MPVView.DECODER_MODE_HW ->
                    getString(R.string.decoder_mode_hw_description)
                app.mpvnova.player.MPVView.DECODER_MODE_SW ->
                    getString(R.string.decoder_mode_sw_description)
                app.mpvnova.player.MPVView.DECODER_MODE_GNEXT ->
                    getString(R.string.decoder_mode_gnext_description)
                app.mpvnova.player.MPVView.DECODER_MODE_SHIELD_H10P ->
                    getString(R.string.decoder_mode_shield_h10p_description)
                else -> getString(R.string.pref_preferred_decoder_mode_summary)
            }
        }

        private fun defaultPreferredDecoderMode(): String {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                app.mpvnova.player.MPVView.DECODER_MODE_HW_PLUS
            else
                app.mpvnova.player.MPVView.DECODER_MODE_HW
        }
    }

    class VideoPreference : StyledPreferenceFragment(R.xml.pref_video)

    class UIPreference : StyledPreferenceFragment(R.xml.pref_ui) {
        override fun onPreferencesLoaded() {
            val packageManager = requireContext().packageManager
            if (!packageManager.hasSystemFeature(PackageManager.FEATURE_SCREEN_PORTRAIT))
                // Disabled top rows can trap Android TV D-pad focus before it reaches the toolbar.
                findPreference<Preference>("auto_rotation")?.isVisible = false
            findPreference<Preference>("reset_player_ui_settings")?.setOnPreferenceClickListener {
                activity?.let(SupportActions::resetPlayerUiSettings)
                true
            }
        }
    }

    class GesturePreference : StyledPreferenceFragment(R.xml.pref_gestures) {
        override fun onPreferencesLoaded() {
            val packageManager = requireContext().packageManager
            if (!packageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)) {
                for (i in 0 until preferenceScreen.preferenceCount)
                    preferenceScreen.getPreference(i).isEnabled = false
            }
        }
    }

    class DeveloperPreference : StyledPreferenceFragment(R.xml.pref_developer)

    class AdvancePreference : StyledPreferenceFragment(R.xml.pref_advanced)

    class SupportPreference : StyledPreferenceFragment(R.xml.pref_support) {
        override fun onPreferencesLoaded() {
            findPreference<Preference>("copy_debug_info")?.setOnPreferenceClickListener {
                activity?.let(SupportActions::copyDebugInfo)
                true
            }
            findPreference<Preference>("export_config_bundle")?.setOnPreferenceClickListener {
                activity?.let(SupportActions::exportConfigBundle)
                true
            }
        }
    }
}
