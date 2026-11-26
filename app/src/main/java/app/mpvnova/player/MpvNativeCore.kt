package app.mpvnova.player

import android.content.Context
import android.graphics.Bitmap
import android.view.Surface

@Volatile
private var nativeLibrariesLoaded = false

internal fun ensureNativeLibrariesLoaded() {
    MPVLib.ensureLoaded()
    nativeLibrariesLoaded = true
}

internal fun mpvPurgeNativeHeapIfLoaded() {
    if (!nativeLibrariesLoaded) return
    runCatching { MPVLib.purgeNativeHeap() }
}

fun mpvCreate(appctx: Context) {
    ensureNativeLibrariesLoaded()
    MPVLib.create(appctx.applicationContext)
}

fun mpvInit() {
    ensureNativeLibrariesLoaded()
    MPVLib.init()
}

fun mpvDestroy() {
    ensureNativeLibrariesLoaded()
    MPVLib.destroy()
}

fun mpvAttachSurface(surface: Surface) {
    ensureNativeLibrariesLoaded()
    MPVLib.attachSurface(surface)
}

fun mpvDetachSurface() {
    ensureNativeLibrariesLoaded()
    MPVLib.detachSurface()
}

fun mpvCommand(cmd: Array<out String>) {
    ensureNativeLibrariesLoaded()
    MPVLib.command(cmd)
}

internal fun mpvCommandAsync(requestId: Long, cmd: Array<out String>): Int {
    ensureNativeLibrariesLoaded()
    return MPVLib.commandAsync(requestId, cmd)
}

internal fun mpvLoadReplacementFile(filepath: String): Long {
    ensureNativeLibrariesLoaded()
    return MPVLib.loadFile(filepath)
}

fun mpvSetOptionString(name: String, value: String): Int {
    ensureNativeLibrariesLoaded()
    return MPVLib.setOptionString(name, value)
}

fun mpvGrabThumbnail(dimension: Int): Bitmap? {
    ensureNativeLibrariesLoaded()
    return MPVLib.grabThumbnail(dimension)
}
