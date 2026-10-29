package app.mpvnova.player

import `is`.xyz.filepicker.DocumentPickerFragment
import app.mpvnova.player.preferences.PreferenceActivity
import app.mpvnova.player.databinding.FragmentMainScreenBinding
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.ViewCompat
import androidx.core.view.updatePadding
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.preference.PreferenceManager

@Suppress("TooManyFunctions")
class MainScreenFragment : Fragment(R.layout.fragment_main_screen) {
    private var _binding: FragmentMainScreenBinding? = null
    private val binding: FragmentMainScreenBinding
        get() = checkNotNull(_binding)

    private lateinit var documentTreeOpener: ActivityResultLauncher<Uri?>
    private lateinit var filePickerLauncher: ActivityResultLauncher<Intent>
    private lateinit var playerLauncher: ActivityResultLauncher<Intent>

    private var firstRun = false
    private var returningFromPlayer = false

    private var prev = ""
    private var prevData: String? = null
    private var lastPath = ""
    private var homeUpdateRunnable: Runnable? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        firstRun = savedInstanceState == null

        documentTreeOpener = registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) {
            it?.let { root ->
                requireContext().contentResolver.takePersistableUriPermission(
                    root, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                saveChoice("doc", root.toString())

                val i = Intent(context, FilePickerActivity::class.java)
                i.putExtra("skip", FilePickerActivity.DOC_PICKER)
                i.putExtra("root", root.toString())
                filePickerLauncher.launch(i)
            }
        }
        filePickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode != Activity.RESULT_OK) {
                return@registerForActivityResult
            }
            it.data?.getStringExtra("last_path")?.let { path ->
                lastPath = path
            }
            it.data?.getStringExtra("path")?.let { path ->
                playFile(path)
            }
        }
        playerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // we don't care about the result but remember that we've been here
            returningFromPlayer = true
            Log.v(TAG, "returned from player ($it)")
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        _binding = FragmentMainScreenBinding.bind(view)

        ViewCompat.setOnApplyWindowInsetsListener(binding.homeScroll) { scroll, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            scroll.updatePadding(
                left = systemBars.left,
                top = systemBars.top,
                right = systemBars.right
            )
            insets
        }

        binding.docBtn.setOnClickListener {
            openDocumentTree()
        }
        binding.urlBtn.setOnClickListener {
            saveChoice("url")
            val helper = Utils.OpenUrlDialog(requireContext())
            with (helper) {
                builder.setPositiveButton(R.string.dialog_ok) { _, _ ->
                    playFile(helper.text)
                }
                builder.setNegativeButton(R.string.dialog_cancel) { dialog, _ -> dialog.cancel() }
                create().show()
            }
        }
        binding.filepickerBtn.setOnClickListener {
            saveChoice("file")
            val i = Intent(context, FilePickerActivity::class.java)
            i.putExtra("skip", FilePickerActivity.FILE_PICKER)
            if (lastPath != "")
                i.putExtra("default_path", lastPath)
            filePickerLauncher.launch(i)
        }
        binding.settingsBtn.setOnClickListener {
            saveChoice("") // will reset
            startActivity(Intent(context, PreferenceActivity::class.java))
        }

        if (BuildConfig.DEBUG) {
            binding.settingsBtn.setOnLongClickListener { showDebugMenu(); true }
        }
    }

    override fun onResume() {
        super.onResume()
        val launchedFlow = if (firstRun) {
            restoreChoice()
        } else if (returningFromPlayer) {
            restoreChoice(prev, prevData)
        } else {
            false
        }
        firstRun = false
        returningFromPlayer = false
        if (!launchedFlow)
            scheduleHomeUpdateCheck()
    }

    override fun onPause() {
        homeUpdateRunnable?.let { runnable ->
            _binding?.root?.removeCallbacks(runnable)
        }
        homeUpdateRunnable = null
        super.onPause()
    }

    private fun scheduleHomeUpdateCheck() {
        val currentBinding = _binding ?: return
        homeUpdateRunnable?.let(currentBinding.root::removeCallbacks)
        val runnable = Runnable {
            if (isResumed)
                (activity as? MainActivity)?.checkForHomeUpdatesOnce()
        }
        homeUpdateRunnable = runnable
        currentBinding.root.postDelayed(runnable, HOME_UPDATE_CHECK_DELAY_MS)
    }

    private fun saveChoice(type: String, data: String? = null) {
        if (prev != type)
            lastPath = ""
        prev = type
        prevData = data

        val currentBinding = _binding ?: return
        if (!currentBinding.switch1.isChecked)
            return
        currentBinding.switch1.isChecked = false
        with (PreferenceManager.getDefaultSharedPreferences(requireContext()).edit()) {
            putString("MainScreenFragment_remember", type)
            if (data == null)
                remove("MainScreenFragment_remember_data")
            else
                putString("MainScreenFragment_remember_data", data)
            apply()
        }
    }

    override fun onDestroyView() {
        homeUpdateRunnable?.let { runnable -> _binding?.root?.removeCallbacks(runnable) }
        homeUpdateRunnable = null
        _binding = null
        super.onDestroyView()
    }

    private fun restoreChoice(): Boolean {
        return with (PreferenceManager.getDefaultSharedPreferences(requireContext())) {
            restoreChoice(
                getString("MainScreenFragment_remember", "") ?: "",
                getString("MainScreenFragment_remember_data", "")
            )
        }
    }

    private fun restoreChoice(type: String, data: String?): Boolean {
        return when (type) {
            "doc" -> {
                val uri = Uri.parse(data)
                // check that we can still access the folder
                if (DocumentPickerFragment.isTreeUsable(requireContext(), uri)) {
                    val i = Intent(context, FilePickerActivity::class.java)
                    i.putExtra("skip", FilePickerActivity.DOC_PICKER)
                    i.putExtra("root", uri.toString())
                    if (lastPath != "")
                        i.putExtra("default_path", lastPath)
                    filePickerLauncher.launch(i)
                    true
                } else {
                    false
                }
            }
            "url" -> binding.urlBtn.callOnClick()
            "file" -> binding.filepickerBtn.callOnClick()
            else -> false
        }
    }

    private fun openDocumentTree() {
        if (!hasDocumentTreePicker(requireContext())) {
            showDocumentPickerUnavailable()
            return
        }
        try {
            documentTreeOpener.launch(null)
        } catch (ignored: ActivityNotFoundException) {
            showDocumentPickerUnavailable()
        }
    }

    private fun showDocumentPickerUnavailable() {
        Toast.makeText(requireContext(), R.string.document_picker_unavailable, Toast.LENGTH_LONG).show()
    }

    private fun playFile(filepath: String) {
        val i: Intent
        if (filepath.startsWith("content://")) {
            i = Intent(Intent.ACTION_VIEW, Uri.parse(filepath))
        } else {
            i = Intent()
            i.putExtra("filepath", filepath)
        }
        i.setClass(requireContext(), MPVActivity::class.java)
        playerLauncher.launch(i)
    }

    companion object {
        private const val TAG = "mpv"
        private const val HOME_UPDATE_CHECK_DELAY_MS = 900L
    }
}

private fun Fragment.showDebugMenu() {
    assert(BuildConfig.DEBUG)
    val context = requireContext()
    with(AlertDialog.Builder(context)) {
        setItems(DEBUG_ACTIVITIES) { dialog, idx ->
            dialog.dismiss()
            val intent = Intent(Intent.ACTION_MAIN)
            intent.setClassName(context, "${context.packageName}.${DEBUG_ACTIVITIES[idx]}")
            startActivity(intent)
        }
        create().show()
    }
}

// Developer diagnostic activities.
private val DEBUG_ACTIVITIES = arrayOf(
    "CodecInfoActivity"
)
