package app.mpvnova.player

import android.util.Log
import java.io.File

internal fun FilePickerActivity.openFilePickerAtStorageVolume(
    activeFragment: MPVFilePickerFragment,
    defaultPath: File,
    hasExplicitDefaultPath: Boolean,
) {
    val volumes = Utils.getStorageVolumes(this)
    val preferredVolume = volumes.find { defaultPath.startsWith(it.path) }
    val targetVolume = preferredVolume ?: volumes.firstOrNull()
    if (preferredVolume == null) {
        Log.w(FilePickerActivity.TAG, "default path set to \"$defaultPath\" but no such storage volume")
    }
    if (targetVolume == null) {
        Log.e(FilePickerActivity.TAG, "can't find any volumes at all!")
        return
    }

    with(activeFragment) {
        root = targetVolume.path
        setRootLabel(targetVolume.description)
        goToDir(if (preferredVolume == null) targetVolume.path else defaultPath)
    }
    if (volumes.size > 1 && !hasExplicitDefaultPath)
        FilePickerMenuActions.showInitialStoragePicker(this, activeFragment, volumes)
}
