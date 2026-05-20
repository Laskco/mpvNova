package app.mpvnova.player

import `is`.xyz.filepicker.AbstractFilePickerFragment
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.WindowInsets
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.util.Predicate
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager
import androidx.recyclerview.widget.RecyclerView
import `is`.xyz.filepicker.DocumentPickerFragment
import `is`.xyz.filepicker.FilePickerFragment
import app.mpvnova.player.databinding.ActivityFilepickerBinding
import app.mpvnova.player.databinding.FragmentFilepickerChoiceBinding
import java.io.File
import java.io.FileFilter

private val TOOLBAR_STORAGE_SELECTOR_KEYS = intArrayOf(
    KeyEvent.KEYCODE_DPAD_CENTER,
    KeyEvent.KEYCODE_ENTER,
    KeyEvent.KEYCODE_NUMPAD_ENTER,
)

class FilePickerActivity : AppCompatActivity(), AbstractFilePickerFragment.OnFilePickedListener {
    internal lateinit var binding: ActivityFilepickerBinding
    internal var fragment: MPVFilePickerFragment? = null
    internal var fragment2: MPVDocumentPickerFragment? = null

    internal var lastSeenInsets: WindowInsets? = null

    internal var documentOpener = registerForActivityResult(ActivityResultContracts.OpenDocument()) {
        it?.let { uri ->
            finishWithResult(RESULT_OK, uri.toString())
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        AppearanceTheme.applySpecialFilePicker(this)
        super.onCreate(null)
        Log.v(TAG, "FilePickerActivity: created")

        binding = ActivityFilepickerBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.hide()
        binding.toolbar.isClickable = true
        binding.toolbar.isFocusable = true
        binding.toolbar.setNavigationOnClickListener {
            onBackPressedImpl()
        }
        binding.toolbar.setOnClickListener {
            onBackPressedImpl()
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

        onBackPressedDispatcher.addCallback(this) {
            onBackPressedImpl()
        }

        // The basic issue we have here is this: https://stackoverflow.com/questions/31190612/
        // Some part of the view hierarchy swallows the insets during fragment transitions
        // and it's impossible to invoke this calculation a second time (requestApplyInsets doesn't help).
        // For that reason I wrote this creative workaround, it works surprisingly well.
        binding.fragmentContainerView.setOnApplyWindowInsetsListener { _, insets ->
            lastSeenInsets = WindowInsets(insets)
            insets
        }

        if (!FilePickerStartup.openInitialDestination(this))
            FilePickerStartup.showChoiceFragment(this)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (fragment == null)
            return
        if (permissions.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            initFilePicker()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        inflateOptionsMenu(menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedImpl()
                true
            }
            R.id.action_external_storage -> {
                FilePickerMenuActions.openExternalStorage(this)
                true
            }
            R.id.action_file_filter -> {
                FilePickerMenuActions.toggleFileFilter(this)
                true
            }
            else -> false
        }
    }

    override fun dispatchKeyEvent(ev: KeyEvent): Boolean {
        val selectFocusedToolbar = ev.action == KeyEvent.ACTION_DOWN &&
            window.currentFocus === binding.toolbar &&
            fragment != null &&
            ev.keyCode in TOOLBAR_STORAGE_SELECTOR_KEYS
        // If up is pressed at the header element display the usual options menu as a popup menu
        // to make it usable on Android TV.
        val openMenu = if (ev.action == KeyEvent.ACTION_DOWN && ev.keyCode == KeyEvent.KEYCODE_DPAD_UP) {
            val recycler: RecyclerView = findViewById(android.R.id.list)
            val holder = try {
                window.currentFocus?.let { recycler.getChildViewHolder(it) }
            } catch (ignored: IllegalArgumentException) {
                null
            }
            holder is AbstractFilePickerFragment<*>.HeaderViewHolder
        } else {
            false
        }
        return when {
            selectFocusedToolbar -> {
                FilePickerMenuActions.openExternalStorage(this)
                true
            }
            openMenu -> {
                if (!focusToolbarNavigation()) {
                    PopupMenu(this, findViewById(R.id.context_anchor)).apply {
                        setOnMenuItemClickListener {
                            this@FilePickerActivity.onOptionsItemSelected(it)
                        }
                        this@FilePickerActivity.inflateOptionsMenu(menu)
                        show()
                    }
                }
                true
            }
            else -> super.dispatchKeyEvent(ev)
        }
    }

    override fun onFilePicked(file: File) = finishWithResult(RESULT_OK, file.absolutePath)

    override fun onDirPicked(dir: File) = finishWithResult(RESULT_OK, dir.absolutePath)

    override fun onDocumentPicked(uri: Uri, isDir: Boolean) {
        if (!isDir)
            fragment2?.pathToString(uri)?.let { finishWithResult(RESULT_OK, it) }
    }

    override fun onCancelled() = finishWithResult(RESULT_CANCELED)

    fun setPickerLocation(location: String) {
        binding.filePickerLocation.text = location.ifEmpty { getString(R.string.file_picker_title) }
    }

    companion object {
        internal const val TAG = "mpv"

        internal val MEDIA_FILE_FILTER = FileFilter { file ->
            if (file.isDirectory) {
                val contents: Array<String> = file.list() ?: arrayOf()
                // filter hidden files due to stuff like ".thumbnails"
                contents.filterNot { it.startsWith('.') }.any()
            } else {
                Utils.MEDIA_EXTENSIONS.contains(file.extension.lowercase())
            }
        }

        internal val MEDIA_DOC_FILTER = Predicate<DocumentPickerFragment.Document> { doc ->
            if (doc.isDirectory) {
                true
            } else {
                val ext = doc.displayName.substringAfterLast('.', "")
                Utils.MEDIA_EXTENSIONS.contains(ext.lowercase())
            }
        }

        const val URL_DIALOG = 0
        const val FILE_PICKER = 1
        const val DOC_PICKER = 2
    }
}

private object FilePickerStartup {
    fun openInitialDestination(activity: FilePickerActivity): Boolean {
        return when (activity.intent.getIntExtra("skip", -1)) {
            FilePickerActivity.URL_DIALOG -> {
                activity.showUrlDialog()
                true
            }
            FilePickerActivity.FILE_PICKER -> {
                activity.initFilePicker()
                true
            }
            FilePickerActivity.DOC_PICKER -> {
                openInitialDocumentPicker(activity)
                true
            }
            else -> false
        }
    }

    fun showChoiceFragment(activity: FilePickerActivity) {
        activity.binding.toolbar.visibility = View.INVISIBLE
        val args = Bundle().apply {
            putString("title", activity.intent.getStringExtra("title"))
            putBoolean("allow_document", activity.intent.getBooleanExtra("allow_document", false))
        }
        activity.supportFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .add(R.id.fragment_container_view, ChoiceFragment::class.java, args, null)
            .commit()
    }

    private fun openInitialDocumentPicker(activity: FilePickerActivity) {
        val root = activity.intent.getStringExtra("root")
        if (root == null) {
            activity.finishWithResult(Activity.RESULT_CANCELED)
        } else {
            activity.initDocPicker(Uri.parse(root))
        }
    }
}

internal object FilePickerMenuActions {
    fun openExternalStorage(activity: FilePickerActivity) {
        val currentFragment = activity.fragment ?: return
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            currentFragment.goToDir(Environment.getExternalStorageDirectory())
        } else {
            showExternalStoragePicker(activity, currentFragment)
        }
    }

    fun toggleFileFilter(activity: FilePickerActivity) {
        val showMediaFiles = updateFilterPredicates(activity)
        with(Toast.makeText(activity, "", Toast.LENGTH_SHORT)) {
            setText(if (showMediaFiles) R.string.notice_show_media_files else R.string.notice_show_all_files)
            show()
        }
        activity.saveFilterState(showMediaFiles)
    }

    private fun showExternalStoragePicker(
        activity: FilePickerActivity,
        currentFragment: MPVFilePickerFragment,
    ) {
        val volumes = Utils.getStorageVolumes(activity)
        showExternalStoragePicker(activity, currentFragment, volumes)
    }

    fun showInitialStoragePicker(
        activity: FilePickerActivity,
        currentFragment: MPVFilePickerFragment,
        volumes: List<Utils.StoragePath>,
    ) {
        val anchor = currentFragment.view ?: activity.binding.fragmentContainerView
        anchor.post {
            showExternalStoragePicker(activity, currentFragment, volumes)
        }
    }

    private fun showExternalStoragePicker(
        activity: FilePickerActivity,
        currentFragment: MPVFilePickerFragment,
        volumes: List<Utils.StoragePath>,
    ) {
        AlertDialog.Builder(activity)
            .setTitle(R.string.action_external_storage)
            .setItems(volumes.map { it.description }.toTypedArray()) { dialog, item ->
                val volume = volumes[item]
                Log.v(
                    FilePickerActivity.TAG,
                    "FilePickerActivity: selected storage \"${volume.description}\" at \"${volume.path}\""
                )
                with(currentFragment) {
                    root = volume.path
                    setRootLabel(volume.description)
                    goToDir(volume.path)
                }
                dialog.dismiss()
            }
            .show()
    }

    private fun updateFilterPredicates(activity: FilePickerActivity): Boolean {
        var hasActiveFilter = false
        activity.fragment?.apply {
            hasActiveFilter = filterPredicate != null
            filterPredicate = if (!hasActiveFilter) FilePickerActivity.MEDIA_FILE_FILTER else null
        }
        activity.fragment2?.apply {
            hasActiveFilter = filterPredicate != null
            filterPredicate = if (!hasActiveFilter) FilePickerActivity.MEDIA_DOC_FILTER else null
        }
        return !hasActiveFilter
    }
}

private fun FilePickerActivity.doUiTweaks() {
    WindowCompat.setDecorFitsSystemWindows(window, false)

    // Part 2 of the workaround: apply the insets to the recycler so it can
    // take them into account.
    val recycler: RecyclerView = findViewById(android.R.id.list)
    lastSeenInsets?.let { recycler.onApplyWindowInsets(lastSeenInsets) }
}

private fun FilePickerActivity.getFilterState(): Boolean {
    with(PreferenceManager.getDefaultSharedPreferences(this)) {
        // naming is a legacy leftover
        return getBoolean("MainActivity_filter_state", false)
    }
}

private fun FilePickerActivity.saveFilterState(enabled: Boolean) {
    with(PreferenceManager.getDefaultSharedPreferences(this).edit()) {
        putBoolean("MainActivity_filter_state", enabled)
        apply()
    }
}

private fun FilePickerActivity.inflateOptionsMenu(menu: Menu) {
    menuInflater.inflate(R.menu.menu_filepicker, menu)
    if (fragment == null)
        menu.findItem(R.id.action_external_storage).isVisible = false
}

private fun FilePickerActivity.focusToolbarNavigation(): Boolean {
    val focusTarget = intArrayOf(R.id.action_external_storage, R.id.action_file_filter)
        .asSequence()
        .mapNotNull { findViewById<View?>(it) }
        .firstOrNull { it.visibility == View.VISIBLE && it.isFocusable }
    return focusTarget?.requestFocus() == true
}

private fun FilePickerActivity.initFilePicker() {
    val activeFragment = fragment ?: MPVFilePickerFragment().also { newFragment ->
        fragment = newFragment
        with(supportFragmentManager.beginTransaction()) {
            setReorderingAllowed(true)
            add(R.id.fragment_container_view, newFragment, null)
            runOnCommit { doUiTweaks() }
            commit()
        }
    }
    supportActionBar?.show()

    if (!FilePickerFragment.hasPermission(this, File("/"))) {
        Log.v(FilePickerActivity.TAG, "FilePickerActivity: waiting for file picker permission")
        return
    }

    Log.v(FilePickerActivity.TAG, "FilePickerActivity: showing file picker")
    val sharedPrefs = PreferenceManager.getDefaultSharedPreferences(this)

    if (getFilterState())
        activeFragment.filterPredicate = FilePickerActivity.MEDIA_FILE_FILTER

    var defaultPathStr = intent.getStringExtra("default_path")
    val hasExplicitDefaultPath = !defaultPathStr.isNullOrEmpty()
    if (defaultPathStr.isNullOrEmpty()) {
        defaultPathStr = sharedPrefs.getString(
            "default_file_manager_path",
            Environment.getExternalStorageDirectory().path
        )
    }
    val defaultPath = File(defaultPathStr ?: Environment.getExternalStorageDirectory().path)

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
        openFilePickerAtStorageVolume(
            activeFragment,
            defaultPath,
            hasExplicitDefaultPath
        )
    } else {
        // Old device: go to preferred path but don't restrict root
        activeFragment.goToDir(defaultPath)
    }
}

private fun FilePickerActivity.initDocPicker(root: Uri) {
    Log.v(FilePickerActivity.TAG, "FilePickerActivity: showing document picker at \"$root\"")
    assert(fragment2 == null)
    val activeFragment = MPVDocumentPickerFragment(root)
    fragment2 = activeFragment
    supportActionBar?.show()

    val defaultPathStr = intent.getStringExtra("default_path")
    if (!defaultPathStr.isNullOrEmpty()) {
        activeFragment.apply {
            goToDir(pathFromString(defaultPathStr))
        }
    }

    if (getFilterState())
        activeFragment.filterPredicate = FilePickerActivity.MEDIA_DOC_FILTER

    with(supportFragmentManager.beginTransaction()) {
        setReorderingAllowed(true)
        add(R.id.fragment_container_view, activeFragment, null)
        runOnCommit { doUiTweaks() }
        commit()
    }
}

private fun FilePickerActivity.showUrlDialog() {
    Log.v(FilePickerActivity.TAG, "FilePickerActivity: showing url dialog")
    val helper = Utils.OpenUrlDialog(this)
    with(helper) {
        builder.setPositiveButton(R.string.dialog_ok) { _, _ ->
            finishWithResult(Activity.RESULT_OK, helper.text)
        }
        builder.setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.cancel() }
        builder.setOnCancelListener { finishWithResult(Activity.RESULT_CANCELED) }
        create().show()
    }
}

private fun FilePickerActivity.onBackPressedImpl() {
    fragment?.apply {
        if (!isBackTop) {
            goUp()
            return
        }
    }
    fragment2?.apply {
        if (!isBackTop) {
            goUp()
            return
        }
    }
    finishWithResult(Activity.RESULT_CANCELED)
}

private fun FilePickerActivity.finishWithResult(code: Int, path: String? = null) {
    val result = Intent()
    fragment?.apply {
        result.putExtra("last_path", pathToString(currentDir))
    }
    fragment2?.apply {
        result.putExtra("last_path", pathToString(currentDir))
    }
    if (path != null) {
        result.putExtra("path", path)
        Log.v(FilePickerActivity.TAG, "FilePickerActivity: picked \"$path\"")
    } else {
        Log.v(FilePickerActivity.TAG, "FilePickerActivity: nothing picked")
    }
    setResult(code, result)
    finish()
}

class ChoiceFragment : Fragment(R.layout.fragment_filepicker_choice) {
    private lateinit var binding: FragmentFilepickerChoiceBinding

    private fun removeMyself() {
        with(requireActivity().supportFragmentManager.beginTransaction()) {
            setReorderingAllowed(true)
            remove(this@ChoiceFragment)
            commit()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding = FragmentFilepickerChoiceBinding.bind(view)

        binding.message.text = requireArguments().getString("title")
        binding.fileBtn.setOnClickListener {
            removeMyself()
            (activity as FilePickerActivity).initFilePicker()
        }
        binding.urlBtn.setOnClickListener {
            // leave visible, dialog will exit anyway
            (activity as FilePickerActivity).showUrlDialog()
        }
        binding.docBtn.setOnClickListener {
            (activity as FilePickerActivity).documentOpener.launch(arrayOf("*/*"))
        }
        if (!requireArguments().getBoolean("allow_document", false))
            binding.docBtn.visibility = View.GONE
    }
}
