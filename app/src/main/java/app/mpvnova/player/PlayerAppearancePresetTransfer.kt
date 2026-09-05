package app.mpvnova.player

import android.app.Dialog
import android.content.ActivityNotFoundException
import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.withResumed
import androidx.preference.PreferenceManager.getDefaultSharedPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Construct eagerly in MPVActivity; editor callbacks run on main after the preset is saved. */
internal class PlayerAppearancePresetTransfer(private val activity: MPVActivity) : DefaultLifecycleObserver {
    private var pendingExport: ByteArray? = null
    private var pendingImport: ImportRequest? = null
    private var work: Job? = null
    private val ui = PresetTransferUi(activity)

    private val exportLauncher = activity.registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val bytes = pendingExport
        pendingExport = null
        if (uri != null && bytes != null) writeDocument(uri, bytes)
    }

    private val importLauncher = activity.registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        val request = pendingImport
        pendingImport = null
        if (uri != null && request != null) readDocument(uri, request)
    }

    init {
        activity.lifecycle.addObserver(this)
    }

    fun exportPlayerBar(name: String, style: PlayerUiCustomization) = export(
        AppearancePresetType.PLAYER_BAR, name,
    ) { style.normalized().toJson() }

    fun exportTitle(name: String, style: PlayerTitleStyle) = export(
        AppearancePresetType.TITLE, name,
    ) { style.normalized().toJson() }

    fun importPlayerBar(onImported: (PlayerUiCustomPreset) -> Unit) {
        val prefs = getDefaultSharedPreferences(activity.applicationContext)
        startImport(ImportRequest(
            type = AppearancePresetType.PLAYER_BAR,
            chrome = PlayerDialogChrome.CONTROLS_PREVIEW,
            existingNames = { PlayerUiCustomPresetStore.read(prefs).map { it.name } },
            save = { document ->
                val preset = PlayerUiCustomPreset(document.name, document.style.toPlayerUiStyle())
                PlayerUiCustomPresetStore.write(prefs, PlayerUiCustomPresetStore.read(prefs) + preset)
                onImported(preset)
            },
            editor = activity.topPlayerDialog,
        ))
    }

    fun importTitle(onImported: (PlayerTitleCustomPreset) -> Unit) {
        val prefs = getDefaultSharedPreferences(activity.applicationContext)
        startImport(ImportRequest(
            type = AppearancePresetType.TITLE,
            chrome = PlayerDialogChrome.TITLE_AND_CLOCK_PREVIEW,
            existingNames = { PlayerTitleCustomPresetStore.read(prefs).map { it.name } },
            save = { document ->
                val preset = PlayerTitleCustomPreset(document.name, document.style.toPlayerTitleStyle())
                PlayerTitleCustomPresetStore.write(prefs, PlayerTitleCustomPresetStore.read(prefs) + preset)
                onImported(preset)
            },
            editor = activity.topPlayerDialog,
        ))
    }

    override fun onDestroy(owner: LifecycleOwner) {
        pendingExport = null
        pendingImport = null
        work?.cancel()
        work = null
        ui.destroy()
        owner.lifecycle.removeObserver(this)
    }

    private fun canStart(): Boolean {
        if (activity.isFinishing || activity.isDestroyed) return false
        val pickerPending = pendingExport != null || pendingImport != null
        val busy = pickerPending || work?.isActive == true || ui.isBusy
        if (busy) ui.notice(R.string.preset_transfer_busy)
        return !busy
    }

    private fun export(type: AppearancePresetType, name: String, style: () -> org.json.JSONObject) {
        if (!canStart()) return
        work = activity.lifecycleScope.launch {
            try {
                val bytes = withContext(Dispatchers.IO) {
                    PlayerAppearancePresetCodec.encode(type, name, style())
                }
                activity.lifecycle.withResumed {
                    if (!activity.isFinishing) {
                        pendingExport = bytes
                        launchSaf { exportLauncher.launch("mpvnova-${type.wireName}.json") }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ui.noticeWhenResumed(R.string.preset_transfer_export_failed)
            }
        }
    }

    private fun startImport(request: ImportRequest) {
        if (!canStart()) return
        if (request.existingNames().size >= MAX_PRESETS) {
            ui.notice(R.string.preset_transfer_limit)
            return
        }
        pendingImport = request
        // Some providers label JSON as text or binary; the codec, not MIME, is the trust boundary.
        launchSaf { importLauncher.launch(arrayOf("*/*")) }
    }

    private fun launchSaf(launch: () -> Unit) {
        val launched = try {
            launch()
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        } catch (_: IllegalStateException) {
            false
        }
        if (!launched) {
            pendingExport = null
            pendingImport = null
            ui.notice(R.string.preset_transfer_unavailable)
        }
    }

    private fun writeDocument(uri: Uri, bytes: ByteArray) {
        work = activity.lifecycleScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    require(uri.scheme == "content")
                    val output = activity.contentResolver.openOutputStream(uri, "wt")
                        ?: error("Document unavailable")
                    output.use { it.write(bytes) }
                }
                ui.noticeWhenResumed(R.string.preset_transfer_exported)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ui.noticeWhenResumed(R.string.preset_transfer_export_failed)
            }
        }
    }

    private fun readDocument(uri: Uri, request: ImportRequest) {
        work = activity.lifecycleScope.launch {
            val document = try {
                withContext(Dispatchers.IO) {
                    require(uri.scheme == "content")
                    val input = activity.contentResolver.openInputStream(uri)
                        ?: error("Document unavailable")
                    input.use { PlayerAppearancePresetCodec.decode(it, request.type) }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                ui.noticeWhenResumed(R.string.preset_transfer_import_failed)
                return@launch
            }
            activity.lifecycle.withResumed {
                ui.confirmImport(request, document)
            }
        }
    }
}

private class PresetTransferUi(private val activity: MPVActivity) {
    private var dialog: Dialog? = null
    private var transition: Job? = null
    val isBusy: Boolean get() = dialog?.isShowing == true || transition?.isActive == true

    fun destroy() {
        transition?.cancel()
        transition = null
        dialog?.dismiss()
        dialog = null
    }

    fun confirmImport(request: ImportRequest, document: AppearancePresetDocument) {
        if (!request.isCurrent(activity)) return
        val names = request.existingNames()
        if (names.size >= MAX_PRESETS) {
            notice(R.string.preset_transfer_limit)
            return
        }
        val duplicate = names.any { it.equals(document.name, ignoreCase = true) }
        val builder = AlertDialog.Builder(activity)
            .setTitle(R.string.preset_transfer_import_title)
            .setMessage(activity.getString(
                if (duplicate) R.string.preset_transfer_duplicate else R.string.preset_transfer_import_confirm,
                document.name,
            ))
            .setNegativeButton(R.string.dialog_cancel, null)
        if (duplicate) {
            builder.setPositiveButton(R.string.preset_transfer_rename_action) { _, _ ->
                transition = activity.lifecycleScope.launch {
                    // Let the confirmation detach before freezing the editor for keyboard input.
                    delay(DIALOG_TRANSITION_MS)
                    activity.lifecycle.withResumed {
                        if (request.isCurrent(activity)) renameImport(request, document)
                    }
                }
            }
        } else {
            builder.setPositiveButton(R.string.preset_transfer_import_action) { _, _ ->
                if (request.isCurrent(activity)) saveConfirmed(request, document)
            }
        }
        dialog = builder.create().also { activity.showPlayerDialog(it, request.chrome) }
    }

    private fun renameImport(request: ImportRequest, document: AppearancePresetDocument) {
        activity.showCustomPresetNameDialog(document.name, request.chrome) { name ->
            val cleanName = PlayerAppearancePresetCodec.sanitizeName(name)
            transition = activity.lifecycleScope.launch {
                // The protected helper restores keyboard/window state after dismissal.
                delay(KEYBOARD_RESTORE_MS)
                activity.lifecycle.withResumed {
                    if (cleanName.isNotBlank() && request.isCurrent(activity)) {
                        confirmImport(request, document.copy(name = cleanName))
                    }
                }
            }
        }
        dialog = activity.topPlayerDialog
    }

    private fun saveConfirmed(request: ImportRequest, document: AppearancePresetDocument) {
        // Recheck on main immediately before the store's asynchronous SharedPreferences.apply().
        val names = request.existingNames()
        when {
            names.size >= MAX_PRESETS -> notice(R.string.preset_transfer_limit)
            names.any { it.equals(document.name, ignoreCase = true) } -> {
                transition = activity.lifecycleScope.launch {
                    delay(DIALOG_TRANSITION_MS)
                    activity.lifecycle.withResumed { confirmImport(request, document) }
                }
            }
            else -> {
                request.save(document)
                notice(R.string.preset_transfer_imported)
            }
        }
    }

    suspend fun noticeWhenResumed(message: Int) {
        activity.lifecycle.withResumed { notice(message) }
    }

    fun notice(message: Int) {
        if (!activity.isFinishing && !activity.isDestroyed) activity.showToast(activity.getString(message))
    }

}

private data class ImportRequest(
    val type: AppearancePresetType,
    val chrome: PlayerDialogChrome,
    val existingNames: () -> List<String>,
    val save: (AppearancePresetDocument) -> Unit,
    val editor: Dialog?,
) {
    fun isCurrent(activity: MPVActivity): Boolean {
        val resumed = activity.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        val editorVisible = editor == null || editor.isShowing
        return resumed && editorVisible && !activity.isFinishing
    }
}

private const val MAX_PRESETS = 24
private const val DIALOG_TRANSITION_MS = 100L
private const val KEYBOARD_RESTORE_MS = 400L
