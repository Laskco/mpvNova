package app.mpvnova.player

internal fun PlayerTitleStyle.movePart(part: PlayerTitlePart, delta: Int): PlayerTitleStyle {
    return if (part.isTitlePart()) {
        copy(titleOrder = titleOrder.swapBy(part.titleUnit(), delta))
    } else {
        copy(clockOrder = clockOrder.swapBy(part.clockUnit(), delta))
    }
}

internal fun PlayerTitleStyle.positionOf(part: PlayerTitlePart): Int = if (part.isTitlePart()) {
    titleOrder.indexOf(part.titleUnit())
} else {
    clockOrder.indexOf(part.clockUnit())
}

internal fun PlayerTitleStyle.resetPartPosition(part: PlayerTitlePart): PlayerTitleStyle {
    val targetIndex = PlayerTitleStyle.DEFAULT.positionOf(part)
    return if (part.isTitlePart()) {
        copy(titleOrder = titleOrder.swapToIndex(part.titleUnit(), targetIndex))
    } else {
        copy(clockOrder = clockOrder.swapToIndex(part.clockUnit(), targetIndex))
    }
}

internal fun PlayerTitlePart.isTitlePart(): Boolean = when (this) {
    PlayerTitlePart.SEASON,
    PlayerTitlePart.EPISODE_NUMBER,
    PlayerTitlePart.TITLE,
    PlayerTitlePart.EPISODE_TITLE -> true
    PlayerTitlePart.DATE,
    PlayerTitlePart.CLOCK,
    PlayerTitlePart.ENDS_AT -> false
}

private fun PlayerTitlePart.titleUnit(): PlayerTitleUnit = when (this) {
    PlayerTitlePart.SEASON,
    PlayerTitlePart.EPISODE_NUMBER -> PlayerTitleUnit.CONTEXT
    PlayerTitlePart.TITLE -> PlayerTitleUnit.TITLE
    PlayerTitlePart.EPISODE_TITLE -> PlayerTitleUnit.EPISODE_TITLE
    else -> error("Clock text does not have a title position")
}

private fun PlayerTitlePart.clockUnit(): PlayerClockUnit = when (this) {
    PlayerTitlePart.DATE -> PlayerClockUnit.DATE
    PlayerTitlePart.CLOCK -> PlayerClockUnit.CLOCK
    PlayerTitlePart.ENDS_AT -> PlayerClockUnit.ENDS_AT
    else -> error("Title text does not have a clock position")
}

private fun <T> List<T>.swapBy(value: T, delta: Int): List<T> {
    val currentIndex = indexOf(value).takeIf { it >= 0 } ?: return this
    return swapIndexes(currentIndex, Math.floorMod(currentIndex + delta, size))
}

private fun <T> List<T>.swapToIndex(value: T, targetIndex: Int): List<T> {
    val currentIndex = indexOf(value).takeIf { it >= 0 } ?: return this
    return swapIndexes(currentIndex, targetIndex)
}

private fun <T> List<T>.swapIndexes(currentIndex: Int, targetIndex: Int): List<T> {
    val currentValue = this[currentIndex]
    return toMutableList().apply {
        this[currentIndex] = this[targetIndex]
        this[targetIndex] = currentValue
    }
}
