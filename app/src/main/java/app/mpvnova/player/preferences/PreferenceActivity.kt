package app.mpvnova.player.preferences

import android.annotation.SuppressLint
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.graphics.Rect
import android.net.Uri
import android.text.InputType
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.KeyEvent
import android.view.View
import android.view.MenuItem
import android.view.ViewTreeObserver
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.XmlRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.ListPreference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.Preference.SummaryProvider
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.color.DynamicColors
import app.mpvnova.player.AppearanceTheme
import app.mpvnova.player.FilePickerActivity
import app.mpvnova.player.OutlinedTextView
import app.mpvnova.player.PREF_SCREENSAVER_LOGO_URI
import app.mpvnova.player.PREF_SCREENSAVER_TINT
import app.mpvnova.player.PREF_DPAD_UP_JUMPS_TO_TOP_CONTROLS
import app.mpvnova.player.PREF_TOP_ACTIONS_IN_PLAYERBAR
import app.mpvnova.player.PlayerUiCustomizationStore
import app.mpvnova.player.PREF_VIDEO_FILTER_PRESET
import app.mpvnova.player.R
import app.mpvnova.player.SCREENSAVER_CHOICES_SEC
import app.mpvnova.player.SCREENSAVER_CUSTOM_ID
import app.mpvnova.player.screensaverTimeoutLabel
import app.mpvnova.player.SEEK_STEP_DEFAULT_SEC
import app.mpvnova.player.SEEK_STEP_MAX_SEC
import app.mpvnova.player.SEEK_STEP_MIN_SEC
import app.mpvnova.player.ShaderManagerDialog
import app.mpvnova.player.TvScrollbars
import app.mpvnova.player.UiFont
import app.mpvnova.player.UserShaderManager
import app.mpvnova.player.VideoFilterPreset
import app.mpvnova.player.decoderModeDescriptionRes
import app.mpvnova.player.defaultPreferredDecoderMode
import app.mpvnova.player.normalizedPreferredDecoderMode
import app.mpvnova.player.preferredDecoderModeOptions
import app.mpvnova.player.applyUiTextShadow
import app.mpvnova.player.AppearanceColorChoice
import app.mpvnova.player.openDocumentChooser
import app.mpvnova.player.shaderImportUrisFromResult
import app.mpvnova.player.appearanceColorChoices
import app.mpvnova.player.writeVideoFilterPreset
import app.mpvnova.player.databinding.ActivitySettingsBinding
import java.io.File

private fun View.applyPreferenceTextTreatment() {
    when (this) {
        is TextView -> {
            applyUiTextShadow()
            UiFont.applyToTextView(this)
        }
        is ViewGroup -> for (index in 0 until childCount) {
            getChildAt(index).applyPreferenceTextTreatment()
        }
    }
}

private val THEME_RECREATE_KEYS = setOf(
    "material_you_theming",
    AppearanceTheme.PREF_KEY,
    AppearanceTheme.PREF_AMOLED_MODE,
    AppearanceTheme.PREF_PURE_BLACK_SURFACES,
    UiFont.PREF_KEY,
    app.mpvnova.player.UiScale.PREF_KEY
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

private const val NOVA_BORDER_CHANNEL = 210
private const val THEME_LABEL_CHANNEL = 188
private const val STATE_HERO_TITLE = "hero_title"
private const val STATE_HERO_SUBTITLE = "hero_subtitle"
private val SCREENSAVER_LIST_PREF_KEYS = setOf("screensaver_mode", "screensaver_timeout")

@Suppress("TooManyFunctions") // mostly Activity lifecycle overrides
class PreferenceActivity : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
    SharedPreferences.OnSharedPreferenceChangeListener, FragmentManager.OnBackStackChangedListener {
    override fun attachBaseContext(newBase: android.content.Context) {
        super.attachBaseContext(app.mpvnova.player.UiScale.wrap(newBase))
    }

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
    }

    // Registered start/stop (not create/save) so the hero title and focus
    // restore keep working after a Home-and-return without recreation.
    override fun onStart() {
        super.onStart()
        supportFragmentManager.addOnBackStackChangedListener(this)
    }

    override fun onStop() {
        supportFragmentManager.removeOnBackStackChangedListener(this)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        updateManager.resumePendingInstallIfAllowed()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        handleSupportExportPermissionResult(requestCode, grantResults)
    }

    override fun onDestroy() {
        super.onDestroy()
        preferences.unregisterOnSharedPreferenceChangeListener(this)
        clearPendingSupportExportFlow()
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
        binding.toolbar.post {
            val navigationButton = (0 until binding.toolbar.childCount)
                .map(binding.toolbar::getChildAt)
                .filterIsInstance<ImageButton>()
                .firstOrNull { it.drawable != null }
                ?: return@post
            navigationButton.id = R.id.settings_back_focus_target
            navigationButton.isFocusable = true
        }
    }

    abstract class StyledPreferenceFragment(
        @param:XmlRes private val preferencesRes: Int
    ) : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(preferencesRes, rootKey)
            styleCategoryHeaders()
            onPreferencesLoaded()
        }

        private fun styleCategoryHeaders() {
            val screen = preferenceScreen ?: return
            for (index in 0 until screen.preferenceCount) {
                (screen.getPreference(index) as? PreferenceCategory)?.layoutResource =
                    R.layout.preference_category_header
            }
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
                    holder.itemView.nextFocusUpId = if (position == firstSelectablePosition()) {
                        R.id.settings_back_focus_target
                    } else {
                        View.NO_ID
                    }
                    holder.itemView.setOnKeyListener(
                        if (position == firstSelectablePosition()) {
                            View.OnKeyListener { _, keyCode, event ->
                                if (keyCode != KeyEvent.KEYCODE_DPAD_UP || event.action != KeyEvent.ACTION_DOWN) {
                                    return@OnKeyListener false
                                }
                                val layoutManager = listView.layoutManager as? LinearLayoutManager
                                if (layoutManager?.findFirstCompletelyVisibleItemPosition() != 0) {
                                    layoutManager?.scrollToPositionWithOffset(0, 0)
                                    return@OnKeyListener true
                                }
                                requireActivity()
                                    .findViewById<View>(R.id.settings_back_focus_target)
                                    ?.requestFocus() == true
                            }
                        } else {
                            null
                        }
                    )
                    holder.itemView.applyPreferenceTextTreatment()
                }

                private fun firstSelectablePosition(): Int =
                    (0 until itemCount).firstOrNull { getItem(it)?.isSelectable == true }
                        ?: RecyclerView.NO_POSITION
            }
        }

        protected open fun onPreferencesLoaded() = Unit

        override fun onDisplayPreferenceDialog(preference: Preference) {
            when (preference) {
                is ListPreference -> showListPreferenceDialog(preference)
                is EditTextPreference -> showEditTextPreferenceDialog(preference)
                else -> super.onDisplayPreferenceDialog(preference)
            }
        }

        private fun showListPreferenceDialog(preference: ListPreference) {
            val entries = preference.entries ?: return
            val values = preference.entryValues ?: return
            val items = entries.indices.map { index ->
                val value = values[index].toString()
                val incompatibleShieldDirect =
                    preference.key == "preferred_decoder_mode" &&
                        value == app.mpvnova.player.MPVView.DECODER_MODE_GNEXT_DIRECT &&
                        app.mpvnova.player.isNvidiaShieldDevice()
                SettingsChoiceItem(
                    title = entries[index],
                    detail = if (incompatibleShieldDirect) {
                        getString(R.string.decoder_mode_gnext_direct_shield_warning)
                    } else {
                        null
                    },
                    checked = preference.value == value,
                    enabled = !incompatibleShieldDirect,
                ) {
                    if (preference.callChangeListener(value)) {
                        preference.value = value
                    }
                }
            }
            showSettingsChoiceDialog(preference.title ?: "", items)
        }

        private fun showEditTextPreferenceDialog(preference: EditTextPreference) {
            showSettingsInputDialog(
                title = preference.dialogTitle ?: preference.title ?: "",
                message = preference.dialogMessage,
                initialValue = preference.text.orEmpty(),
            ) { value ->
                if (preference.callChangeListener(value)) {
                    preference.text = value
                }
            }
        }

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
            bindFontRow(view.findViewById(R.id.fontRow), view.findViewById(R.id.fontValue))
            view.findViewById<View>(R.id.materialYouRow).apply {
                visibility = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) View.VISIBLE else View.GONE
                if (visibility == View.VISIBLE) {
                    bindSwitchRow(this, findViewById(R.id.materialYouSwitch), "material_you_theming")
                }
            }
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
            appearanceColorChoices.forEach { choice ->
                val isSelected = selectedTheme == choice.value
                row.addView(createThemeTile(choice, isSelected).apply {
                    if (isSelected) requestFocus()
                })
            }
        }

        private fun createThemeTile(choice: AppearanceColorChoice, selected: Boolean): View {
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

            val label = OutlinedTextView(context).apply {
                text = getString(choice.labelRes)
                setTextColor(Color.rgb(THEME_LABEL_CHANNEL, THEME_LABEL_CHANNEL, THEME_LABEL_CHANNEL))
                textSize = THEME_LABEL_TEXT_SIZE_SP
                typeface = if (selected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                gravity = android.view.Gravity.CENTER
                includeFontPadding = false
                UiFont.applyToTextView(this)
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

        private fun bindFontRow(row: View, valueView: TextView) {
            valueView.text = UiFont.currentLabel(requireContext())
            row.setOnClickListener {
                val selected = UiFont.currentValue(requireContext())
                val items = UiFont.choices.map { choice ->
                    SettingsChoiceItem(
                        title = getString(choice.titleRes),
                        detail = getString(choice.detailRes),
                        checked = choice.value == selected,
                    ) {
                        preferences.edit().putString(UiFont.PREF_KEY, choice.value).apply()
                    }
                }
                showSettingsChoiceDialog(getString(R.string.appearance_ui_font_title), items)
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
            bindSkipButtonDisplayVisibility()
            bindSeekStepPreference()
        }

        private fun bindSkipButtonDisplayVisibility() {
            val skipModePref = findPreference<ListPreference>("skip_segments_mode")
            val displayPref = findPreference<ListPreference>("skip_button_display")
            fun syncVisibility(value: String?) {
                displayPref?.isVisible = value == "button"
            }
            syncVisibility(skipModePref?.value)
            skipModePref?.setOnPreferenceChangeListener { _, newValue ->
                syncVisibility(newValue as? String)
                true
            }
            displayPref?.summaryProvider = SummaryProvider<ListPreference> { pref ->
                pref.entry ?: getString(R.string.pref_skip_button_display_summary)
            }
        }

        private fun bindSeekStepPreference() {
            val seekStepPref = findPreference<ListPreference>("seek_step_seconds") ?: return
            seekStepPref.summaryProvider = SummaryProvider<ListPreference> { pref ->
                seekStepSummary(pref.value)
            }
            seekStepPref.setOnPreferenceChangeListener { _, newValue ->
                if (newValue == "custom") {
                    showCustomSeekStepDialog(seekStepPref)
                    false
                } else {
                    true
                }
            }
        }

        private fun seekStepSummary(value: String?): String {
            val seconds = value?.toIntOrNull()?.coerceIn(SEEK_STEP_MIN_SEC, SEEK_STEP_MAX_SEC)
                ?: SEEK_STEP_DEFAULT_SEC
            return getString(R.string.seek_step_seconds_value, seconds)
        }

        private fun showCustomSeekStepDialog(pref: ListPreference) {
            showSettingsInputDialog(
                title = getString(R.string.pref_seek_step_custom_title),
                message = getString(R.string.pref_seek_step_custom_message),
                initialValue = (pref.value?.toIntOrNull() ?: SEEK_STEP_DEFAULT_SEC).toString(),
                inputType = InputType.TYPE_CLASS_NUMBER,
            ) { value ->
                val seconds = value.toIntOrNull()
                    ?.coerceIn(SEEK_STEP_MIN_SEC, SEEK_STEP_MAX_SEC)
                    ?: SEEK_STEP_DEFAULT_SEC
                pref.value = seconds.toString()
            }
        }
    }

    @Suppress("TooManyFunctions") // grouped screensaver preference-binding helpers
    class ScreensaverPreference : StyledPreferenceFragment(R.xml.pref_screensaver) {
        private val screensaverLogoPicker =
            registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                onScreensaverLogoPicked(uri)
            }

        override fun onPreferencesLoaded() {
            bindScreensaverLogoPreference()
            bindScreensaverSummaries()
        }

        // Route the mode/idle-time pickers through the app-styled choice dialog.
        override fun onDisplayPreferenceDialog(preference: Preference) {
            val listPref = preference as? ListPreference
            if (listPref != null && preference.key in SCREENSAVER_LIST_PREF_KEYS) {
                showScreensaverListChoice(listPref)
            } else {
                super.onDisplayPreferenceDialog(preference)
            }
        }

        private fun showScreensaverListChoice(pref: ListPreference) {
            val entries = pref.entries
            val values = pref.entryValues
            val presetValues = values.map { it.toString() }.filter { it != SCREENSAVER_CUSTOM_ID }
            val items = entries.indices.map { i ->
                val value = values[i].toString()
                if (value == SCREENSAVER_CUSTOM_ID) {
                    SettingsChoiceItem(title = entries[i], checked = pref.value !in presetValues) {
                        showScreensaverTimeInputDialog(currentScreensaverSeconds(pref)) { sec ->
                            pref.value = sec.toString()
                        }
                    }
                } else {
                    SettingsChoiceItem(title = entries[i], checked = pref.value == value) { pref.value = value }
                }
            }
            showSettingsChoiceDialog(pref.title ?: "", items)
        }

        private fun currentScreensaverSeconds(pref: ListPreference): Int =
            pref.value?.toIntOrNull() ?: SCREENSAVER_CHOICES_SEC.first()

        private fun bindScreensaverSummaries() {
            setEntrySummary("screensaver_mode", R.string.pref_screensaver_summary)
            findPreference<ListPreference>("screensaver_timeout")?.summaryProvider =
                SummaryProvider<ListPreference> { pref -> screensaverTimeoutSummary(pref.value) }
        }

        private fun screensaverTimeoutSummary(value: String?): String {
            val sec = value?.toIntOrNull() ?: return getString(R.string.pref_screensaver_idle_summary)
            return screensaverTimeoutLabel(requireContext(), sec)
        }

        private fun setEntrySummary(key: String, fallbackRes: Int) {
            findPreference<ListPreference>(key)?.summaryProvider =
                SummaryProvider<ListPreference> { pref -> pref.entry ?: getString(fallbackRes) }
        }

        private fun bindScreensaverLogoPreference() {
            val pref = findPreference<Preference>("screensaver_logo") ?: return
            updateScreensaverLogoSummary()
            pref.setOnPreferenceClickListener {
                showScreensaverLogoDialog(
                    hasCustom = hasCustomScreensaverLogo(),
                    onChoose = { screensaverLogoPicker.launch(arrayOf("image/*")) },
                    onReset = { setScreensaverLogo(uri = null, tintDefault = true) },
                )
                true
            }
        }

        private fun hasCustomScreensaverLogo(): Boolean =
            !preferenceManager.sharedPreferences?.getString(PREF_SCREENSAVER_LOGO_URI, null).isNullOrBlank()

        private fun updateScreensaverLogoSummary() {
            findPreference<Preference>("screensaver_logo")?.setSummary(
                if (hasCustomScreensaverLogo()) {
                    R.string.pref_screensaver_logo_custom
                } else {
                    R.string.pref_screensaver_logo_default
                }
            )
        }

        private fun onScreensaverLogoPicked(uri: Uri?) {
            if (uri == null) return
            runCatching {
                requireContext().contentResolver.takePersistableUriPermission(
                    uri, Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            }
            // A custom logo shows in its own colours by default; the built-in mark colour-shifts.
            setScreensaverLogo(uri = uri.toString(), tintDefault = false)
        }

        private fun setScreensaverLogo(uri: String?, tintDefault: Boolean) {
            preferenceManager.sharedPreferences?.edit()?.apply {
                if (uri == null) remove(PREF_SCREENSAVER_LOGO_URI) else putString(PREF_SCREENSAVER_LOGO_URI, uri)
                apply()
            }
            findPreference<SwitchPreferenceCompat>(PREF_SCREENSAVER_TINT)?.isChecked = tintDefault
            updateScreensaverLogoSummary()
        }
    }

    class VideoPreference : StyledPreferenceFragment(R.xml.pref_video) {
        private var shaderImportCallback: ((List<Uri>) -> Unit)? = null
        private val shaderFilesPicker =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                shaderImportCallback?.invoke(
                    shaderImportUrisFromResult(result.resultCode, result.data)
                )
                shaderImportCallback = null
            }
        private val shaderDocumentPicker =
            registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
                shaderImportCallback?.invoke(uri?.let(::listOf).orEmpty())
                shaderImportCallback = null
            }
        private val shaderFolderPicker =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val folder = shaderImportUrisFromResult(result.resultCode, result.data)
                    .singleOrNull()
                    ?.path
                    ?.let(::File)
                val uris = folder?.let { selected ->
                    UserShaderManager.rememberFolder(requireContext(), Uri.fromFile(selected))
                    UserShaderManager.shaderUrisInDirectory(selected)
                }.orEmpty()
                shaderImportCallback?.invoke(uris)
                shaderImportCallback = null
            }

        override fun onPreferencesLoaded() {
            bindVideoFilterPresetPreference()
            bindShaderManagerPreference()
        }

        private fun bindVideoFilterPresetPreference() {
            val preference = findPreference<Preference>(PREF_VIDEO_FILTER_PRESET) ?: return
            val prefs = PreferenceManager.getDefaultSharedPreferences(requireContext())
            fun selectedPreset(): VideoFilterPreset? = VideoFilterPreset.fromPref(
                prefs.getString(PREF_VIDEO_FILTER_PRESET, VideoFilterPreset.NONE.prefValue)
            )
            fun updateSummary() {
                val preset = selectedPreset()
                preference.summary = if (preset != null) {
                    getString(preset.titleRes)
                } else {
                    getString(R.string.video_filter_preset_custom)
                }
            }
            updateSummary()
            preference.setOnPreferenceClickListener {
                val selected = selectedPreset()
                showSettingsChoiceDialog(
                    getString(R.string.video_filter_presets_title),
                    VideoFilterPreset.entries.map { preset ->
                        SettingsChoiceItem(
                            title = getString(preset.titleRes),
                            detail = getString(preset.detailRes),
                            checked = preset == selected,
                        ) {
                            writeVideoFilterPreset(prefs, preset)
                            updateSummary()
                        }
                    },
                )
                true
            }
        }

        private fun bindShaderManagerPreference() {
            val shaderPreference = findPreference<Preference>("managed_shaders") ?: return
            fun updateSummary() {
                val shaders = UserShaderManager.shaders(requireContext())
                shaderPreference.summary = if (UserShaderManager.isEnabled(requireContext())) {
                    getString(R.string.shader_status, shaders.count { it.enabled }, shaders.size)
                } else {
                    getString(R.string.shader_status_manager_off, shaders.size)
                }
            }
            updateSummary()
            shaderPreference.setOnPreferenceClickListener {
                ShaderManagerDialog(
                    activity = requireActivity() as AppCompatActivity,
                    requestImport = { callback ->
                        showSettingsChoiceDialog(
                            getString(R.string.shader_import_choose_source),
                            listOf(
                                SettingsChoiceItem(title = getString(R.string.action_pick_file_old)) {
                                    shaderImportCallback = callback
                                    shaderFilesPicker.launch(
                                        Intent(requireContext(), FilePickerActivity::class.java)
                                            .putExtra("title", getString(R.string.shader_import_files))
                                            .putExtra("skip", FilePickerActivity.SHADER_FILE_PICKER)
                                    )
                                },
                                SettingsChoiceItem(title = getString(R.string.action_open_url)) {
                                    shaderImportCallback = callback
                                    shaderFilesPicker.launch(
                                        Intent(requireContext(), FilePickerActivity::class.java)
                                            .putExtra("title", getString(R.string.shader_import_files))
                                            .putExtra("skip", FilePickerActivity.URL_DIALOG)
                                    )
                                },
                                SettingsChoiceItem(title = getString(R.string.action_open_doc)) {
                                    shaderImportCallback = callback
                                    shaderDocumentPicker.launch(arrayOf("*/*"))
                                },
                                SettingsChoiceItem(title = getString(R.string.shader_import_folder)) {
                                    shaderImportCallback = callback
                                    shaderFolderPicker.launch(
                                        Intent(requireContext(), FilePickerActivity::class.java)
                                            .putExtra("title", getString(R.string.shader_import_folder))
                                            .putExtra("skip", FilePickerActivity.FOLDER_PICKER)
                                    )
                                },
                            ),
                        )
                    },
                    onChanged = { if (isAdded) updateSummary() },
                ).show()
                true
            }
        }
    }

    class UIPreference : StyledPreferenceFragment(R.xml.pref_ui) {
        override fun onPreferencesLoaded() {
            findPreference<Preference>("reset_player_ui_settings")?.setOnPreferenceClickListener {
                showSettingsConfirmationDialog(
                    title = getString(R.string.pref_reset_player_ui_confirm_title),
                    message = getString(R.string.pref_reset_player_ui_confirm_message),
                    confirmText = getString(R.string.pref_reset_player_ui_confirm_action),
                ) {
                    activity?.let(SupportActions::resetPlayerUiSettings)
                }
                true
            }
            bindTopControlsNavigation()
            bindSeekDisplayExclusivity()
        }

        private fun bindTopControlsNavigation() {
            val placement = findPreference<SwitchPreferenceCompat>(PREF_TOP_ACTIONS_IN_PLAYERBAR)
            val jump = findPreference<SwitchPreferenceCompat>(PREF_DPAD_UP_JUMPS_TO_TOP_CONTROLS)
            val preferences = PreferenceManager.getDefaultSharedPreferences(requireContext())

            fun sync(moveToPlayerBar: Boolean) {
                if (moveToPlayerBar) {
                    jump?.isChecked = false
                    preferences.edit()
                        .putBoolean(PREF_DPAD_UP_JUMPS_TO_TOP_CONTROLS, false)
                        .apply()
                }
                jump?.isEnabled = !moveToPlayerBar
            }

            sync(placement?.isChecked == true)
            placement?.setOnPreferenceChangeListener { _, newValue ->
                sync(newValue == true)
                true
            }
        }

        // The two seek-display options are mutually exclusive: turning one on greys out the other.
        private fun bindSeekDisplayExclusivity() {
            val hide = findPreference<SwitchPreferenceCompat>("hide_controls_while_seeking")
            val minimal = findPreference<SwitchPreferenceCompat>("minimal_seekbar_while_seeking")
            val minimalForced = !PlayerUiCustomizationStore.read(
                PreferenceManager.getDefaultSharedPreferences(requireContext()),
            ).seekbarVisible
            if (minimalForced) {
                hide?.isChecked = false
                minimal?.isChecked = true
            }
            hide?.isEnabled = minimal?.isChecked != true
            minimal?.isEnabled = !minimalForced && hide?.isChecked != true
            hide?.setOnPreferenceChangeListener { _, newValue ->
                minimal?.isEnabled = !minimalForced && newValue != true
                true
            }
            minimal?.setOnPreferenceChangeListener { _, newValue ->
                if (minimalForced && newValue != true) return@setOnPreferenceChangeListener false
                hide?.isEnabled = newValue != true
                true
            }
        }
    }

    class DeveloperPreference : StyledPreferenceFragment(R.xml.pref_developer)

    class AdvancePreference : StyledPreferenceFragment(R.xml.pref_advanced) {
        override fun onPreferencesLoaded() {
            val autoFallbackPref = findPreference<SwitchPreferenceCompat>("decoder_auto_fallback")
            val shieldDecoderPref = findPreference<SwitchPreferenceCompat>("shield_decoder_mode")
            val otherDeviceHi10pPref = findPreference<SwitchPreferenceCompat>(
                app.mpvnova.player.PREF_HI10P_FALLBACK_OTHER_DEVICES
            )
            val otherDeviceMpeg2Pref = findPreference<SwitchPreferenceCompat>(
                app.mpvnova.player.PREF_MPEG2_SOFTWARE_FALLBACK_OTHER_DEVICES
            )
            if (app.mpvnova.player.isNvidiaShieldDevice()) {
                otherDeviceHi10pPref?.isEnabled = false
                otherDeviceHi10pPref?.setSummary(
                    R.string.pref_hi10p_fallback_other_devices_shield_summary
                )
                otherDeviceMpeg2Pref?.isEnabled = false
                otherDeviceMpeg2Pref?.setSummary(
                    R.string.pref_mpeg2_software_fallback_other_devices_shield_summary
                )
            }
            val preferredDecoderPref = findPreference<ListPreference>("preferred_decoder_mode")

            fun refreshDecoderPreferenceOptions(
                shieldDecoderEnabled: Boolean = shieldDecoderPref?.isChecked != false
            ) {
                if (preferredDecoderPref == null)
                    return

                val (entries, values) = buildDecoderPreferenceOptions(shieldDecoderEnabled)
                preferredDecoderPref.entries = entries
                preferredDecoderPref.entryValues = values
                preferredDecoderPref.value = normalizedPreferredDecoderMode(
                    preferredDecoderPref.value,
                    shieldDecoderEnabled,
                )
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
            val options = preferredDecoderModeOptions(includeShieldMode)
            val entries = options.map { getString(it.titleRes) as CharSequence }.toTypedArray()
            val values = options.map { it.value as CharSequence }.toTypedArray()
            return Pair(entries, values)
        }

        private fun decoderModeDescription(mode: String?): String {
            return getString(decoderModeDescriptionRes(mode))
        }
    }

    class SupportPreference : StyledPreferenceFragment(R.xml.pref_support) {
        private val backupImporter =
            registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
                val uri = result.data?.data.takeIf { result.resultCode == AppCompatActivity.RESULT_OK }
                if (uri != null) activity?.let { FullBackupActions.confirmImport(it, uri) }
            }

        override fun onPreferencesLoaded() {
            findPreference<Preference>("copy_debug_info")?.setOnPreferenceClickListener {
                activity?.let(SupportActions::copyDebugInfo)
                true
            }
            findPreference<Preference>("export_config_bundle")?.setOnPreferenceClickListener {
                activity?.let(SupportActions::exportConfigBundle)
                true
            }
            findPreference<Preference>("export_full_backup")?.setOnPreferenceClickListener {
                activity?.let(FullBackupActions::exportWithDestinationFlow)
                true
            }
            findPreference<Preference>("import_full_backup")?.setOnPreferenceClickListener {
                activity?.let { host ->
                    backupImporter.launch(
                        openDocumentChooser(
                            host,
                            arrayOf(FullBackupActions.MIME_TYPE, "application/octet-stream"),
                        )
                    )
                }
                true
            }
        }
    }
}
