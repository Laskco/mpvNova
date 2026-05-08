package app.mpvnova.player

import java.util.concurrent.CopyOnWriteArrayList

private val eventObservers = CopyOnWriteArrayList<MpvEventObserver>()

fun addMpvObserver(observer: MpvEventObserver) {
    ensureNativeLibrariesLoaded()
    eventObservers.add(observer)
}

fun removeMpvObserver(observer: MpvEventObserver) {
    eventObservers.remove(observer)
}

fun dispatchMpvEventProperty(property: String) {
    for (observer in eventObservers)
        observer.eventProperty(property)
}

fun dispatchMpvEventProperty(property: String, value: Long) {
    for (observer in eventObservers)
        observer.eventProperty(property, value)
}

fun dispatchMpvEventProperty(property: String, value: Boolean) {
    for (observer in eventObservers)
        observer.eventProperty(property, value)
}

fun dispatchMpvEventProperty(property: String, value: Double) {
    for (observer in eventObservers)
        observer.eventProperty(property, value)
}

fun dispatchMpvEventProperty(property: String, value: String) {
    for (observer in eventObservers)
        observer.eventProperty(property, value)
}

fun dispatchMpvEvent(eventId: Int) {
    for (observer in eventObservers)
        observer.event(eventId)
}
