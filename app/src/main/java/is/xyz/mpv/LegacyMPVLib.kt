package `is`.xyz.mpv

import android.content.Context
import android.graphics.Bitmap
import android.view.Surface

@Suppress("unused")
object MPVLib {
    init {
        val libs = arrayOf("mpv", "player")
        for (lib in libs) {
            System.loadLibrary(lib)
        }
    }

    external fun create(appctx: Context)
    external fun init()
    external fun destroy()
    external fun attachSurface(surface: Surface)
    external fun detachSurface()

    external fun command(cmd: Array<out String>)

    external fun setOptionString(name: String, value: String): Int

    external fun grabThumbnail(dimension: Int): Bitmap?

    external fun getPropertyInt(property: String): Int?
    external fun setPropertyInt(property: String, value: Int)
    external fun getPropertyDouble(property: String): Double?
    external fun setPropertyDouble(property: String, value: Double)
    external fun getPropertyBoolean(property: String): Boolean?
    external fun setPropertyBoolean(property: String, value: Boolean)
    external fun getPropertyString(property: String): String?
    external fun setPropertyString(property: String, value: String)

    external fun observeProperty(property: String, format: Int)

    @JvmStatic
    fun eventProperty(property: String) {
        app.mpvnova.player.MPVLib.eventProperty(property)
    }

    @JvmStatic
    fun eventProperty(property: String, value: Long) {
        app.mpvnova.player.MPVLib.eventProperty(property, value)
    }

    @JvmStatic
    fun eventProperty(property: String, value: Boolean) {
        app.mpvnova.player.MPVLib.eventProperty(property, value)
    }

    @JvmStatic
    fun eventProperty(property: String, value: String) {
        app.mpvnova.player.MPVLib.eventProperty(property, value)
    }

    @JvmStatic
    fun eventProperty(property: String, value: Double) {
        app.mpvnova.player.MPVLib.eventProperty(property, value)
    }

    @JvmStatic
    fun event(eventId: Int) {
        app.mpvnova.player.MPVLib.event(eventId)
    }

    @JvmStatic
    fun logMessage(prefix: String, level: Int, text: String) {
        app.mpvnova.player.MPVLib.logMessage(prefix, level, text)
    }
}
