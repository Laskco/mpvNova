package app.mpvnova.player

import `is`.xyz.mpv.MPVLib as NativeMPVLib

fun mpvGetPropertyInt(property: String): Int? {
    ensureNativeLibrariesLoaded()
    return NativeMPVLib.getPropertyInt(property)
}

fun mpvSetPropertyInt(property: String, value: Int) {
    ensureNativeLibrariesLoaded()
    NativeMPVLib.setPropertyInt(property, value)
}

fun mpvGetPropertyDouble(property: String): Double? {
    ensureNativeLibrariesLoaded()
    return NativeMPVLib.getPropertyDouble(property)
}

fun mpvSetPropertyDouble(property: String, value: Double) {
    ensureNativeLibrariesLoaded()
    NativeMPVLib.setPropertyDouble(property, value)
}

fun mpvGetPropertyBoolean(property: String): Boolean? {
    ensureNativeLibrariesLoaded()
    return NativeMPVLib.getPropertyBoolean(property)
}

fun mpvSetPropertyBoolean(property: String, value: Boolean) {
    ensureNativeLibrariesLoaded()
    NativeMPVLib.setPropertyBoolean(property, value)
}

fun mpvGetPropertyString(property: String): String? {
    ensureNativeLibrariesLoaded()
    return NativeMPVLib.getPropertyString(property)
}

fun mpvSetPropertyString(property: String, value: String) {
    ensureNativeLibrariesLoaded()
    NativeMPVLib.setPropertyString(property, value)
}

fun mpvObserveProperty(property: String, format: Int) {
    ensureNativeLibrariesLoaded()
    NativeMPVLib.observeProperty(property, format)
}
