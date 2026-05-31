package app.mpvnova.player.preferences

import android.annotation.SuppressLint
import android.content.SharedPreferences
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
import android.view.ViewTreeObserver
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
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
import app.mpvnova.player.TvScrollbars
import app.mpvnova.player.databinding.ActivitySettingsBinding

private val THEME_RECREATE_KEYS = setOf(
    "material_you_theming",
    AppearanceTheme.PREF_KEY,
    AppearanceTheme.PREF_AMOLED_MODE,
    AppearanceTheme.PREF_PURE_BLACK_SURFACES
)

// Visual margin inside the listView. The right offset that separates the
// scrollbar from the bg_surface_host's rounded inner edge comes from the
// fragment host FrameLayout's layout_marginEnd in activity_settings.xml,
// not from padding here — paddingRight on a RecyclerView with
// SCROLLBARS_INSIDE_INSET style positions the scrollbar within the same
// view, but doesn't shrink the view itself. The marginEnd approach does
// shrink the view, which physically moves the scrollbar inward.
private const val LIST_HORIZONTAL_PADDING_DP = 6
private const val LIST_TOP_PADDING_DP = 2
private const val LIST_BOTTOM_PADDING_DP = 6
private const val LIST_VERTICAL_SPACE_DP = 2

private const val THEME_TILE_PADDING_DP = 8
private const val THEME_TILE_WIDTH_DP = 88
private const val THEME_TILE_HEIGHT_DP = 86
private const val THEME_TILE_MARGIN_END_DP = 18
private const val THEME_SWATCH_STROKE_DP = 1
private const val THEME_SWATCH_SIZE_DP = 40
private const val THEME_LABEL_TEXT_SIZE_SP = 12f
private const val THEME_LABEL_TOP_MARGIN_DP = 8
private const val THEME_SCROLLBAR_MIN_THUMB_DP = 48

private const val CRIMSON_RED = 244
private const val CRIMSON_GREEN = 67
private const val CRIMSON_BLUE = 54
private const val OCEAN_RED = 33
private const val OCEAN_GREEN = 150
private const val OCEAN_BLUE = 243
private const val CYAN_RED = 0
private const val CYAN_GREEN = 172
private const val CYAN_BLUE = 193
private const val VIOLET_RED = 156
private const val VIOLET_GREEN = 39
private const val VIOLET_BLUE = 176
private const val EMERALD_RED = 76
private const val EMERALD_GREEN = 175
private const val EMERALD_BLUE = 80
private const val LIME_RED = 158
private const val LIME_GREEN = 157
private const val LIME_BLUE = 36
private const val AMBER_RED = 255
private const val AMBER_GREEN = 152
private const val AMBER_BLUE = 0
private const val GOLD_RED = 253
private const val GOLD_GREEN = 216
private const val GOLD_BLUE = 53
private const val COPPER_RED = 184
private const val COPPER_GREEN = 106
private const val COPPER_BLUE = 44
private const val INDIGO_RED = 57
private const val INDIGO_GREEN = 73
private const val INDIGO_BLUE = 171
private const val SLATE_RED = 120
private const val SLATE_GREEN = 144
private const val SLATE_BLUE = 156
private const val CHROME_RED = 184
private const val CHROME_GREEN = 193
private const val CHROME_BLUE = 204
private const val OYSTER_RED = 200
private const val OYSTER_GREEN = 182
private const val OYSTER_BLUE = 166
private const val IVORY_RED = 216
private const val IVORY_GREEN = 198
private const val IVORY_BLUE = 144
private const val ROSE_RED = 216
private const val ROSE_GREEN = 27
private const val ROSE_BLUE = 96
private const val NOVA_BORDER_CHANNEL = 210
private const val THEME_LABEL_CHANNEL = 188
private const val STATE_HERO_TITLE = "hero_title"
private const val STATE_HERO_SUBTITLE = "hero_subtitle"

class PreferenceActivity : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
    SharedPreferences.OnSharedPreferenceChangeListener, FragmentManager.OnBackStackChangedListener {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var preferences: SharedPreferences
    private val updateManager by lazy { AppUpdateManager(this) }
    private var currentTitle: CharSequence? = null
    private var currentSubtitle: CharSequence? = null
    private var lastNavigatedPosition: Int = -1

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
        currentTitle = savedInstanceState?.getCharSequence(STATE_HERO_TITLE)
            ?: getString(R.string.settings_hero_title)
        currentSubtitle = savedInstanceState?.getCharSequence(STATE_HERO_SUBTITLE)
            ?: getString(R.string.settings_root_subtitle)
        updateChrome()
    }

    override fun onBackStackChanged() {
        if (supportFragmentManager.backStackEntryCount == 0) {
            currentTitle = getString(R.string.settings_hero_title)
            currentSubtitle = getString(R.string.settings_root_subtitle)
            val position = lastNavigatedPosition
            if (position >= 0) {
                binding.root.post {
                    val frag = supportFragmentManager.findFragmentById(R.id.main)
                    if (frag is PreferenceFragmentCompat) {
                        frag.listView?.let { rv ->
                            rv.scrollToPosition(position)
                            rv.post {
                                rv.findViewHolderForAdapterPosition(position)
                                    ?.itemView?.requestFocus()
                            }
                        }
                    }
                }
            }
        }
        updateChrome()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putCharSequence(STATE_HERO_TITLE, currentTitle)
        outState.putCharSequence(STATE_HERO_SUBTITLE, currentSubtitle)
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
        if (key !in THEME_RECREATE_KEYS) return
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

        val screen = caller.preferenceScreen
        for (i in 0 until screen.preferenceCount) {
            if (screen.getPreference(i) === pref) {
                lastNavigatedPosition = i
                break
            }
        }

        supportFragmentManager.beginTransaction().replace(R.id.main, fragment).addToBackStack(null)
            .commit()

        currentTitle = pref.title ?: getString(R.string.settings_hero_title)
        currentSubtitle = pref.summary ?: pref.title
        updateChrome()
        return true
    }

    private fun updateChrome() {
        binding.heroTitle.text = currentTitle ?: getString(R.string.settings_hero_title)
        binding.heroSubtitle.text = currentSubtitle ?: getString(R.string.settings_root_subtitle)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
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

            val horizontalPadding = dp(LIST_HORIZONTAL_PADDING_DP)
            listView.apply {
                itemAnimator = null
                clipToPadding = true
                overScrollMode = View.OVER_SCROLL_NEVER
                setPadding(horizontalPadding, dp(LIST_TOP_PADDING_DP), horizontalPadding, dp(LIST_BOTTOM_PADDING_DP))
                if (itemDecorationCount == 0)
                    addItemDecoration(VerticalSpaceDecoration(dp(LIST_VERTICAL_SPACE_DP)))
                isVerticalScrollBarEnabled = false
                val scrollbarThumb = requireActivity().findViewById<View>(R.id.settingsScrollbarThumb)
                if (scrollbarThumb != null) {
                    TvScrollbars.bind(this, scrollbarThumb)
                }
            }
            ViewCompat.setOnApplyWindowInsetsListener(listView) { recycler, insets ->
                val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
                recycler.updatePadding(
                    left = horizontalPadding + systemBars.left,
                    top = dp(LIST_TOP_PADDING_DP),
                    right = horizontalPadding + systemBars.right,
                    bottom = dp(LIST_BOTTOM_PADDING_DP) + systemBars.bottom
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
                (activity as? PreferenceActivity)?.updateManager?.checkForUpdates()
                true
            }
            findPreference<Preference>("release_history")?.setOnPreferenceClickListener {
                (activity as? PreferenceActivity)?.updateManager?.showReleaseHistory()
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
            ThemeChoice(
                "crimson",
                R.string.appearance_theme_crimson,
                Color.rgb(CRIMSON_RED, CRIMSON_GREEN, CRIMSON_BLUE)
            ),
            ThemeChoice("ocean", R.string.appearance_theme_ocean, Color.rgb(OCEAN_RED, OCEAN_GREEN, OCEAN_BLUE)),
            ThemeChoice("cyan", R.string.appearance_theme_cyan, Color.rgb(CYAN_RED, CYAN_GREEN, CYAN_BLUE)),
            ThemeChoice("violet", R.string.appearance_theme_violet, Color.rgb(VIOLET_RED, VIOLET_GREEN, VIOLET_BLUE)),
            ThemeChoice(
                "emerald",
                R.string.appearance_theme_emerald,
                Color.rgb(EMERALD_RED, EMERALD_GREEN, EMERALD_BLUE)
            ),
            ThemeChoice("lime", R.string.appearance_theme_lime, Color.rgb(LIME_RED, LIME_GREEN, LIME_BLUE)),
            ThemeChoice("amber", R.string.appearance_theme_amber, Color.rgb(AMBER_RED, AMBER_GREEN, AMBER_BLUE)),
            ThemeChoice("gold", R.string.appearance_theme_gold, Color.rgb(GOLD_RED, GOLD_GREEN, GOLD_BLUE)),
            ThemeChoice("copper", R.string.appearance_theme_copper, Color.rgb(COPPER_RED, COPPER_GREEN, COPPER_BLUE)),
            ThemeChoice("indigo", R.string.appearance_theme_indigo, Color.rgb(INDIGO_RED, INDIGO_GREEN, INDIGO_BLUE)),
            ThemeChoice("rose", R.string.appearance_theme_rose, Color.rgb(ROSE_RED, ROSE_GREEN, ROSE_BLUE)),
            ThemeChoice("slate", R.string.appearance_theme_slate, Color.rgb(SLATE_RED, SLATE_GREEN, SLATE_BLUE)),
            ThemeChoice("chrome", R.string.appearance_theme_chrome, Color.rgb(CHROME_RED, CHROME_GREEN, CHROME_BLUE)),
            ThemeChoice("oyster", R.string.appearance_theme_oyster, Color.rgb(OYSTER_RED, OYSTER_GREEN, OYSTER_BLUE)),
            ThemeChoice("ivory", R.string.appearance_theme_ivory, Color.rgb(IVORY_RED, IVORY_GREEN, IVORY_BLUE)),
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
            bindColorThemeScrollbar(view)
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

        private fun bindColorThemeScrollbar(view: View) {
            val scroller = view.findViewById<HorizontalScrollView>(R.id.colorThemeScroller)
            val track = view.findViewById<FrameLayout>(R.id.colorThemeScrollbarTrack)
            val thumb = view.findViewById<View>(R.id.colorThemeScrollbarThumb)
            fun update() {
                val contentWidth = scroller.getChildAt(0)?.width ?: 0
                val viewportWidth = scroller.width - scroller.paddingLeft - scroller.paddingRight
                val scrollRange = contentWidth - viewportWidth
                val trackWidth = track.width
                val hasOverflow = scrollRange > 0 && viewportWidth > 0 && trackWidth > 0
                track.visibility = if (hasOverflow) View.VISIBLE else View.INVISIBLE
                if (!hasOverflow) return

                val thumbWidth = ((viewportWidth.toFloat() / contentWidth) * trackWidth)
                    .toInt()
                    .coerceIn(dp(THEME_SCROLLBAR_MIN_THUMB_DP), trackWidth)
                val maxTravel = trackWidth - thumbWidth
                val scrollFraction = (scroller.scrollX.toFloat() / scrollRange).coerceIn(0f, 1f)
                val leftMargin = (scrollFraction * maxTravel).toInt()
                val params = thumb.layoutParams as FrameLayout.LayoutParams
                if (params.width != thumbWidth || params.leftMargin != leftMargin) {
                    params.width = thumbWidth
                    params.leftMargin = leftMargin
                    thumb.layoutParams = params
                }
            }

            val scrollListener = ViewTreeObserver.OnScrollChangedListener { update() }
            val layoutListener = ViewTreeObserver.OnGlobalLayoutListener { update() }
            scroller.viewTreeObserver.addOnScrollChangedListener(scrollListener)
            scroller.viewTreeObserver.addOnGlobalLayoutListener(layoutListener)
            scroller.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(v: View) = Unit

                override fun onViewDetachedFromWindow(v: View) {
                    if (scroller.viewTreeObserver.isAlive) {
                        scroller.viewTreeObserver.removeOnScrollChangedListener(scrollListener)
                        scroller.viewTreeObserver.removeOnGlobalLayoutListener(layoutListener)
                    }
                }
            })
            scroller.post { update() }
        }

        private fun populateColorThemes(row: LinearLayout) {
            row.removeAllViews()
            val selectedTheme = AppearanceTheme.currentValue(requireContext())
            themeChoices.forEachIndexed { _, choice ->
                val isSelected = selectedTheme == choice.value
                row.addView(createThemeTile(choice, isSelected).apply {
                    if (isSelected) requestFocus()
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
                setPadding(
                    dp(THEME_TILE_PADDING_DP),
                    dp(THEME_TILE_PADDING_DP),
                    dp(THEME_TILE_PADDING_DP),
                    dp(THEME_TILE_PADDING_DP)
                )
                setOnClickListener {
                    if (preferences.getString(AppearanceTheme.PREF_KEY, AppearanceTheme.DEFAULT_VALUE) == choice.value)
                        return@setOnClickListener
                    preferences.edit()
                        .putString(AppearanceTheme.PREF_KEY, choice.value)
                    .apply()
                }
            }
            tile.layoutParams = LinearLayout.LayoutParams(dp(THEME_TILE_WIDTH_DP), dp(THEME_TILE_HEIGHT_DP)).apply {
                marginEnd = dp(THEME_TILE_MARGIN_END_DP)
            }

            val swatch = FrameLayout(context).apply {
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(choice.color)
                    setStroke(
                        dp(THEME_SWATCH_STROKE_DP),
                        if (choice.value == "nova") {
                            Color.rgb(NOVA_BORDER_CHANNEL, NOVA_BORDER_CHANNEL, NOVA_BORDER_CHANNEL)
                        } else {
                            choice.color
                        }
                    )
                }
            }
            tile.addView(swatch, LinearLayout.LayoutParams(dp(THEME_SWATCH_SIZE_DP), dp(THEME_SWATCH_SIZE_DP)))

            val label = TextView(context).apply {
                text = getString(choice.labelRes)
                setTextColor(Color.rgb(THEME_LABEL_CHANNEL, THEME_LABEL_CHANNEL, THEME_LABEL_CHANNEL))
                textSize = THEME_LABEL_TEXT_SIZE_SP
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                gravity = android.view.Gravity.CENTER
                includeFontPadding = false
            }
            tile.addView(label, LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(THEME_LABEL_TOP_MARGIN_DP)
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
        }
    }

    class VideoPreference : StyledPreferenceFragment(R.xml.pref_video)

    class UIPreference : StyledPreferenceFragment(R.xml.pref_ui) {
        override fun onPreferencesLoaded() {
            findPreference<Preference>("reset_player_ui_settings")?.setOnPreferenceClickListener {
                activity?.let(SupportActions::resetPlayerUiSettings)
                true
            }
        }
    }

    class DeveloperPreference : StyledPreferenceFragment(R.xml.pref_developer)

    class AdvancePreference : StyledPreferenceFragment(R.xml.pref_advanced) {
        override fun onPreferencesLoaded() {
            val autoFallbackPref = findPreference<SwitchPreferenceCompat>("decoder_auto_fallback")
            val shieldDecoderPref = findPreference<SwitchPreferenceCompat>("shield_decoder_mode")
            val preferredDecoderPref = findPreference<ListPreference>("preferred_decoder_mode")

            fun refreshDecoderPreferenceOptions(
                shieldDecoderEnabled: Boolean = shieldDecoderPref?.isChecked != false
            ) {
                if (preferredDecoderPref == null)
                    return

                val (entries, values) = buildDecoderPreferenceOptions(shieldDecoderEnabled)
                preferredDecoderPref.entries = entries
                preferredDecoderPref.entryValues = values
                if (preferredDecoderPref.value.isNullOrBlank() ||
                    !values.contains(preferredDecoderPref.value)
                ) {
                    preferredDecoderPref.value = defaultPreferredDecoderMode()
                }
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

            refreshDecoderPreferenceOptions()
            fun syncDecoderPreferenceVisibility() {
                preferredDecoderPref?.isVisible = autoFallbackPref?.isChecked == false
            }
            syncDecoderPreferenceVisibility()
            autoFallbackPref?.setOnPreferenceChangeListener { _, newValue ->
                preferredDecoderPref?.isVisible = (newValue as? Boolean) == false
                true
            }
            shieldDecoderPref?.setOnPreferenceChangeListener { _, newValue ->
                val enabled = (newValue as? Boolean) != false
                if (!enabled &&
                    preferredDecoderPref?.value == app.mpvnova.player.MPVView.DECODER_MODE_SHIELD_H10P
                ) {
                    preferredDecoderPref.value = defaultPreferredDecoderMode()
                }
                refreshDecoderPreferenceOptions(enabled)
                true
            }
        }

        private fun buildDecoderPreferenceOptions(
            includeShieldMode: Boolean
        ): Pair<Array<CharSequence>, Array<CharSequence>> {
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
            entries.add(getString(R.string.decoder_mode_mpv_conf_settings))
            values.add(app.mpvnova.player.MPVView.DECODER_MODE_MPV_CONF)
            if (includeShieldMode) {
                entries.add(getString(R.string.decoder_mode_shield_h10p_settings))
                values.add(app.mpvnova.player.MPVView.DECODER_MODE_SHIELD_H10P)
            }
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
                app.mpvnova.player.MPVView.DECODER_MODE_MPV_CONF ->
                    getString(R.string.decoder_mode_mpv_conf_description)
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
