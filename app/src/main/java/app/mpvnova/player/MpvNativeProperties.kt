package app.mpvnova.player

fun mpvGetPropertyInt(property: String): Int? {
    ensureNativeLibrariesLoaded()
    return MPVLib.getPropertyInt(property)
}

fun mpvSetPropertyInt(property: String, value: Int) {
    ensureNativeLibrariesLoaded()
    MPVLib.setPropertyInt(property, value)
}

fun mpvGetPropertyDouble(property: String): Double? {
    ensureNativeLibrariesLoaded()
    return MPVLib.getPropertyDouble(property)
}

fun mpvSetPropertyDouble(property: String, value: Double) {
    ensureNativeLibrariesLoaded()
    MPVLib.setPropertyDouble(property, value)
}

fun mpvGetPropertyBoolean(property: String): Boolean? {
    ensureNativeLibrariesLoaded()
    return MPVLib.getPropertyBoolean(property)
}

fun mpvSetPropertyBoolean(property: String, value: Boolean) {
    ensureNativeLibrariesLoaded()
    MPVLib.setPropertyBoolean(property, value)
}

fun mpvGetPropertyString(property: String): String? {
    ensureNativeLibrariesLoaded()
    return MPVLib.getPropertyString(property)
}

fun mpvSetPropertyString(property: String, value: String) {
    ensureNativeLibrariesLoaded()
    MPVLib.setPropertyString(property, value)
}

fun mpvObserveProperty(property: String, format: Int) {
    ensureNativeLibrariesLoaded()
    MPVLib.observeProperty(property, format)
}
