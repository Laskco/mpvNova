package app.mpvnova.player.preferences

import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.graphics.Rect
import android.util.TypedValue
import android.view.View
import android.view.MenuItem
import androidx.appcompat.content.res.AppCompatResources
import androidx.activity.enableEdgeToEdge
import androidx.annotation.XmlRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentManager
import androidx.preference.Preference
import androidx.preference.PreferenceGroupAdapter
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.ListPreference
import androidx.preference.PreferenceManager
import androidx.preference.PreferenceScreen
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreferenceCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.color.DynamicColors
import app.mpvnova.player.R
import app.mpvnova.player.databinding.ActivitySettingsBinding

class PreferenceActivity : AppCompatActivity(),
    PreferenceFragmentCompat.OnPreferenceStartFragmentCallback,
    SharedPreferences.OnSharedPreferenceChangeListener, FragmentManager.OnBackStackChangedListener {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var preferences: SharedPreferences
    private var currentSubtitle: CharSequence? = null

    override fun onCreate(savedInstanceState: Bundle?) {
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

    override fun onDestroy() {
        super.onDestroy()
        preferences.unregisterOnSharedPreferenceChangeListener(this)
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
        if (key != "material_you_theming") return
        if (sharedPreferences.getBoolean(key, false))
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

    abstract class StyledPreferenceFragment(
        @param:XmlRes private val preferencesRes: Int
    ) : PreferenceFragmentCompat() {
        override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
            setPreferencesFromResource(preferencesRes, rootKey)
            onPreferencesLoaded()
        }

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

    /**
     * The root preference fragment that displays preferences that link to the other preference
     * fragments below.
     */
    class SettingsFragment : StyledPreferenceFragment(R.xml.preferences_root)

    class GeneralPreference : StyledPreferenceFragment(R.xml.pref_general) {
        override fun onPreferencesLoaded() {
            // hide Material You on Android 11 or lower
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
                entries.add(getString(R.string.decoder_mode_hw_plus))
                values.add(app.mpvnova.player.MPVView.DECODER_MODE_HW_PLUS)
            }
            entries.add(getString(R.string.decoder_mode_hw))
            values.add(app.mpvnova.player.MPVView.DECODER_MODE_HW)
            entries.add(getString(R.string.decoder_mode_sw))
            values.add(app.mpvnova.player.MPVView.DECODER_MODE_SW)
            entries.add(getString(R.string.decoder_mode_gnext))
            values.add(app.mpvnova.player.MPVView.DECODER_MODE_GNEXT)
            entries.add(getString(R.string.decoder_mode_shield_h10p))
            values.add(app.mpvnova.player.MPVView.DECODER_MODE_SHIELD_H10P)
            return Pair(entries.toTypedArray(), values.toTypedArray())
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
                findPreference<Preference>("auto_rotation")?.isEnabled = false
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
}
