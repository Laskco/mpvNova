package app.mpvnova.player

import android.net.Uri
import java.io.File
import app.mpvnova.player.preferences.SettingsChoiceItem
import app.mpvnova.player.preferences.showSettingsChoiceDialog

internal fun MPVActivity.openShaderManagerPanel() {
    ShaderManagerDialog(
        activity = this,
        requestImport = { callback ->
            showShaderImportChoices(callback)
        },
        onChanged = { player.reconcileManagedShaders() },
        onDismiss = { reopenDrawerIfPending() },
    ).show()
}

private fun MPVActivity.showShaderImportChoices(callback: (List<Uri>) -> Unit) {
    val choices = listOf(
        SettingsChoiceItem(title = getString(R.string.action_pick_file_old)) {
            openFilePickerFor(
                getString(R.string.shader_import_files),
                FilePickerActivity.SHADER_FILE_PICKER,
                useCurrentMediaPath = false,
            ) { resultCode, data ->
                callback(shaderImportUrisFromResult(resultCode, data))
            }
        },
        SettingsChoiceItem(title = getString(R.string.action_open_url)) {
            openFilePickerFor(
                getString(R.string.shader_import_files),
                FilePickerActivity.URL_DIALOG,
                useCurrentMediaPath = false,
            ) { resultCode, data ->
                callback(shaderImportUrisFromResult(resultCode, data))
            }
        },
        SettingsChoiceItem(title = getString(R.string.action_open_doc)) {
            pendingActivityResultCallback = { resultCode, data ->
                callback(shaderImportUrisFromResult(resultCode, data))
            }
            documentResultLauncher.launch(arrayOf("*/*"))
        },
        SettingsChoiceItem(title = getString(R.string.shader_import_folder)) {
            openFilePickerFor(
                getString(R.string.shader_import_folder),
                FilePickerActivity.FOLDER_PICKER,
                useCurrentMediaPath = false,
            ) { resultCode, data ->
                val folder = shaderImportUrisFromResult(resultCode, data)
                    .singleOrNull()
                    ?.path
                    ?.let(::File)
                val uris = folder?.let { selected ->
                    UserShaderManager.rememberFolder(this, Uri.fromFile(selected))
                    UserShaderManager.shaderUrisInDirectory(selected)
                }.orEmpty()
                callback(uris)
            }
        },
    )
    showSettingsChoiceDialog(
        getString(R.string.shader_import_choose_source),
        choices,
    )
}
