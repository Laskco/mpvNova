package app.mpvnova.player;

import android.content.Context;
import android.graphics.Bitmap;
import android.view.Surface;

@SuppressWarnings("unused")
public final class MPVLib {
    static {
        System.loadLibrary("mpv");
        System.loadLibrary("player");
    }

    private MPVLib() {
    }

    public static void ensureLoaded() {
    }

    public static native void create(Context appctx);
    public static native void init();
    public static native void destroy();
    public static native void attachSurface(Surface surface);
    public static native void detachSurface();

    public static native void command(String[] cmd);

    public static native int setOptionString(String name, String value);

    public static native Bitmap grabThumbnail(int dimension);

    public static native Integer getPropertyInt(String property);
    public static native void setPropertyInt(String property, int value);
    public static native Double getPropertyDouble(String property);
    public static native void setPropertyDouble(String property, double value);
    public static native Boolean getPropertyBoolean(String property);
    public static native void setPropertyBoolean(String property, boolean value);
    public static native String getPropertyString(String property);
    public static native void setPropertyString(String property, String value);

    public static native void observeProperty(String property, int format);

    public static void eventProperty(String property) {
        MpvEventBridgeKt.dispatchMpvEventProperty(property);
    }

    public static void eventProperty(String property, long value) {
        MpvEventBridgeKt.dispatchMpvEventProperty(property, value);
    }

    public static void eventProperty(String property, boolean value) {
        MpvEventBridgeKt.dispatchMpvEventProperty(property, value);
    }

    public static void eventProperty(String property, String value) {
        MpvEventBridgeKt.dispatchMpvEventProperty(property, value);
    }

    public static void eventProperty(String property, double value) {
        MpvEventBridgeKt.dispatchMpvEventProperty(property, value);
    }

    public static void event(int eventId) {
        MpvEventBridgeKt.dispatchMpvEvent(eventId);
    }

    public static void logMessage(String prefix, int level, String text) {
        MpvLogBridgeKt.dispatchMpvLogMessage(prefix, level, text);
    }
}
