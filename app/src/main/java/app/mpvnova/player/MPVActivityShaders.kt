package app.mpvnova.player

import android.net.Uri
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
    showSettingsChoiceDialog(
        getString(R.string.shader_import_choose_source),
        listOf(
            SettingsChoiceItem(
                title = getString(R.string.shader_import_files),
                detail = getString(R.string.shader_import_files_detail),
            ) {
                pendingShaderImportCallback = callback
                shaderFilesResultLauncher.launch(
                    openDocumentChooser(this, arrayOf("*/*"), allowMultiple = true)
                )
            },
            SettingsChoiceItem(
                title = getString(R.string.shader_import_folder),
                detail = getString(R.string.shader_import_folder_detail),
            ) {
                pendingShaderImportCallback = callback
                shaderFolderResultLauncher.launch(documentTreeChooser(this))
            },
        ),
    )
}
