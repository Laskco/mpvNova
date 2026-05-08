package app.mpvnova.player

import java.util.concurrent.CopyOnWriteArrayList

private val mpvLogObservers = CopyOnWriteArrayList<MpvLogObserver>()

fun addMpvLogObserver(observer: MpvLogObserver) {
    ensureNativeLibrariesLoaded()
    mpvLogObservers.add(observer)
}

fun removeMpvLogObserver(observer: MpvLogObserver) {
    mpvLogObservers.remove(observer)
}

fun dispatchMpvLogMessage(prefix: String, level: Int, text: String) {
    for (observer in mpvLogObservers)
        observer.logMessage(prefix, level, text)
}
