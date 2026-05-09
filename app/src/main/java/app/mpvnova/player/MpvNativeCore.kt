package app.mpvnova.player

import android.content.Context
import android.graphics.Bitmap
import android.view.Surface

internal fun ensureNativeLibrariesLoaded() {
    MPVLib.ensureLoaded()
}

fun mpvCreate(appctx: Context) {
    ensureNativeLibrariesLoaded()
    MPVLib.create(appctx)
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

fun mpvSetOptionString(name: String, value: String): Int {
    ensureNativeLibrariesLoaded()
    return MPVLib.setOptionString(name, value)
}

fun mpvGrabThumbnail(dimension: Int): Bitmap? {
    ensureNativeLibrariesLoaded()
    return MPVLib.grabThumbnail(dimension)
}
