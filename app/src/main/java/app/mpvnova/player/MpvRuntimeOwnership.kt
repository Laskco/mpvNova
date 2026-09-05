package app.mpvnova.player

/** Coordinates access to libmpv, which exposes one process-wide native handle. */
internal object MpvRuntimeOwnership {
    private var owner: Any? = null

    @Synchronized
    fun tryAcquire(candidate: Any): Boolean {
        if (owner != null)
            return false
        owner = candidate
        return true
    }

    @Synchronized
    fun release(candidate: Any) {
        if (owner === candidate)
            owner = null
    }

    @Synchronized
    fun isOwnedBy(candidate: Any): Boolean = owner === candidate

    @Synchronized
    fun hasOwner(): Boolean = owner != null
}
