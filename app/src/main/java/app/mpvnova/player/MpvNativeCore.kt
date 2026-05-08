package app.mpvnova.player

import android.content.Context
import android.graphics.Bitmap
import android.view.Surface
import `is`.xyz.mpv.MPVLib as NativeMPVLib

internal fun ensureNativeLibrariesLoaded() {
    NativeMPVLib.ensureLoaded()
}

fun mpvCreate(appctx: Context) {
    ensureNativeLibrariesLoaded()
    NativeMPVLib.create(appctx)
}

fun mpvInit() {
    ensureNativeLibrariesLoaded()
    NativeMPVLib.init()
}

fun mpvDestroy() {
    ensureNativeLibrariesLoaded()
    NativeMPVLib.destroy()
}

fun mpvAttachSurface(surface: Surface) {
    ensureNativeLibrariesLoaded()
    NativeMPVLib.attachSurface(surface)
}

fun mpvDetachSurface() {
    ensureNativeLibrariesLoaded()
    NativeMPVLib.detachSurface()
}

fun mpvCommand(cmd: Array<out String>) {
    ensureNativeLibrariesLoaded()
    NativeMPVLib.command(cmd)
}

fun mpvSetOptionString(name: String, value: String): Int {
    ensureNativeLibrariesLoaded()
    return NativeMPVLib.setOptionString(name, value)
}

fun mpvGrabThumbnail(dimension: Int): Bitmap? {
    ensureNativeLibrariesLoaded()
    return NativeMPVLib.grabThumbnail(dimension)
}
