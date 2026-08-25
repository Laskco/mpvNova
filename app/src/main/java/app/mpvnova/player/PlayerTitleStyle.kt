@file:Suppress("MagicNumber")

package app.mpvnova.player

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat

internal enum class PlayerTitlePart {
    SEASON,
    EPISODE_NUMBER,
    TITLE,
    EPISODE_TITLE,
    DATE,
    CLOCK,
    ENDS_AT,
}

internal enum class PlayerTitleWeight(val value: Int) {
    LIGHT(300),
    REGULAR(400),
    MEDIUM(500),
    SEMIBOLD(600),
    BOLD(700),
    EXTRABOLD(800),
    BLACK(900),
}

internal enum class PlayerTitleColor(val appearanceValue: String?) {
    APP_COLOR(null),
    WHITE("nova"),
    CRIMSON("crimson"),
    OCEAN("ocean"),
    CYAN("cyan"),
    VIOLET("violet"),
    EMERALD("emerald"),
    LIME("lime"),
    AMBER("amber"),
    GOLD("gold"),
    COPPER("copper"),
    INDIGO("indigo"),
    ROSE("rose"),
    SLATE("slate"),
    CHROME("chrome"),
    OYSTER("oyster"),
    IVORY("ivory"),
}

internal enum class PlayerTitleShadow(val radius: Float, val dy: Float, val alpha: Int) {
    OFF(0f, 0f, 0),
    SUBTLE(1f, 0.5f, 0x78),
    SOFT(1.5f, 1f, 0xA8),
    STRONG(2.5f, 1f, 0xD8),
    HEAVY(3.5f, 1.5f, 0xEE),
}

internal enum class PlayerTitleSeparator(val text: String) {
    DOT("\u2022"),
    DASH("-"),
    BAR("|"),
    SLASH("/"),
    NONE(""),
}

internal enum class PlayerTitleUnit {
    CONTEXT,
    TITLE,
    EPISODE_TITLE,
}

internal enum class PlayerClockUnit {
    DATE,
    CLOCK,
    ENDS_AT,
}

internal data class PlayerTitleTextStyle(
    val font: String,
    val sizeSp: Float,
    val weight: PlayerTitleWeight,
    val letterSpacing: Float,
    val color: PlayerTitleColor,
    val shadow: PlayerTitleShadow,
    val italic: Boolean,
    val opacityPercent: Int,
    val visible: Boolean,
    val uppercase: Boolean,
)

internal data class PlayerTitleStyle(
    val season: PlayerTitleTextStyle,
    val episodeNumber: PlayerTitleTextStyle,
    val title: PlayerTitleTextStyle,
    val episodeTitle: PlayerTitleTextStyle,
    val date: PlayerTitleTextStyle,
    val clock: PlayerTitleTextStyle,
    val endsAt: PlayerTitleTextStyle,
    val separator: PlayerTitleSeparator,
    val titleOrder: List<PlayerTitleUnit>,
    val clockOrder: List<PlayerClockUnit>,
) {
    fun forPart(part: PlayerTitlePart): PlayerTitleTextStyle = when (part) {
        PlayerTitlePart.SEASON -> season
        PlayerTitlePart.EPISODE_NUMBER -> episodeNumber
        PlayerTitlePart.TITLE -> title
        PlayerTitlePart.EPISODE_TITLE -> episodeTitle
        PlayerTitlePart.DATE -> date
        PlayerTitlePart.CLOCK -> clock
        PlayerTitlePart.ENDS_AT -> endsAt
    }

    fun withPart(part: PlayerTitlePart, value: PlayerTitleTextStyle): PlayerTitleStyle = when (part) {
        PlayerTitlePart.SEASON -> copy(season = value)
        PlayerTitlePart.EPISODE_NUMBER -> copy(episodeNumber = value)
        PlayerTitlePart.TITLE -> copy(title = value)
        PlayerTitlePart.EPISODE_TITLE -> copy(episodeTitle = value)
        PlayerTitlePart.DATE -> copy(date = value)
        PlayerTitlePart.CLOCK -> copy(clock = value)
        PlayerTitlePart.ENDS_AT -> copy(endsAt = value)
    }

    companion object {
        const val INHERIT_FONT = "inherit"

        val DEFAULT = PlayerTitleStyle(
            season = PlayerTitleTextStyle(
                font = INHERIT_FONT,
                sizeSp = 14f,
                weight = PlayerTitleWeight.SEMIBOLD,
                letterSpacing = 0.06f,
                color = PlayerTitleColor.APP_COLOR,
                shadow = PlayerTitleShadow.SOFT,
                italic = false,
                opacityPercent = 100,
                visible = true,
                uppercase = false,
            ),
            episodeNumber = PlayerTitleTextStyle(
                font = INHERIT_FONT,
                sizeSp = 14f,
                weight = PlayerTitleWeight.SEMIBOLD,
                letterSpacing = 0.06f,
                color = PlayerTitleColor.APP_COLOR,
                shadow = PlayerTitleShadow.SOFT,
                italic = false,
                opacityPercent = 100,
                visible = true,
                uppercase = false,
            ),
            title = PlayerTitleTextStyle(
                font = INHERIT_FONT,
                sizeSp = PLAYER_TITLE_MAX_TEXT_SIZE_SP,
                weight = PlayerTitleWeight.SEMIBOLD,
                letterSpacing = 0f,
                color = PlayerTitleColor.WHITE,
                shadow = PlayerTitleShadow.SOFT,
                italic = false,
                opacityPercent = 100,
                visible = true,
                uppercase = false,
            ),
            episodeTitle = PlayerTitleTextStyle(
                font = INHERIT_FONT,
                sizeSp = 13.75f,
                weight = PlayerTitleWeight.SEMIBOLD,
                letterSpacing = 0f,
                color = PlayerTitleColor.WHITE,
                shadow = PlayerTitleShadow.SOFT,
                italic = false,
                opacityPercent = 100,
                visible = true,
                uppercase = false,
            ),
            date = clockTextDefault(
                sizeSp = 12f,
                weight = PlayerTitleWeight.SEMIBOLD,
                color = PlayerTitleColor.CHROME,
            ),
            clock = clockTextDefault(
                sizeSp = PLAYER_TITLE_MAX_TEXT_SIZE_SP,
                weight = PlayerTitleWeight.BLACK,
                color = PlayerTitleColor.WHITE,
            ),
            endsAt = clockTextDefault(
                sizeSp = 12f,
                weight = PlayerTitleWeight.SEMIBOLD,
                color = PlayerTitleColor.CHROME,
            ),
            separator = PlayerTitleSeparator.DOT,
            titleOrder = PlayerTitleUnit.entries,
            clockOrder = PlayerClockUnit.entries,
        )

        fun defaultFor(part: PlayerTitlePart): PlayerTitleTextStyle = DEFAULT.forPart(part)

        private fun clockTextDefault(
            sizeSp: Float,
            weight: PlayerTitleWeight,
            color: PlayerTitleColor,
        ) = PlayerTitleTextStyle(
            font = INHERIT_FONT,
            sizeSp = sizeSp,
            weight = weight,
            letterSpacing = 0f,
            color = color,
            shadow = PlayerTitleShadow.SOFT,
            italic = false,
            opacityPercent = 100,
            visible = true,
            uppercase = false,
        )
    }
}

internal object PlayerTitleStyleStore {
    private const val PREFIX = "player_title_style"

    fun read(prefs: SharedPreferences): PlayerTitleStyle = PlayerTitleStyle(
        season = readPart(prefs, PlayerTitlePart.SEASON),
        episodeNumber = readPart(prefs, PlayerTitlePart.EPISODE_NUMBER),
        title = readPart(prefs, PlayerTitlePart.TITLE),
        episodeTitle = readPart(prefs, PlayerTitlePart.EPISODE_TITLE),
        date = readPart(prefs, PlayerTitlePart.DATE),
        clock = readPart(prefs, PlayerTitlePart.CLOCK),
        endsAt = readPart(prefs, PlayerTitlePart.ENDS_AT),
        separator = enumValueOrDefault(
            prefs.getString(SEPARATOR_KEY, PlayerTitleStyle.DEFAULT.separator.name),
            PlayerTitleStyle.DEFAULT.separator,
        ),
        titleOrder = readOrder(
            prefs.getString(TITLE_ORDER_KEY, null),
            PlayerTitleUnit.entries,
        ),
        clockOrder = readOrder(
            prefs.getString(CLOCK_ORDER_KEY, null),
            PlayerClockUnit.entries,
        ),
    )

    fun writePart(
        prefs: SharedPreferences,
        part: PlayerTitlePart,
        style: PlayerTitleTextStyle,
    ) {
        val key = part.keySegment()
        prefs.edit()
            .putString("${PREFIX}_${key}_font", style.font)
            .putFloat("${PREFIX}_${key}_size", style.sizeSp)
            .putInt("${PREFIX}_${key}_weight", style.weight.value)
            .putFloat("${PREFIX}_${key}_spacing", style.letterSpacing)
            .putString("${PREFIX}_${key}_color", style.color.name)
            .putString("${PREFIX}_${key}_shadow", style.shadow.name)
            .putBoolean("${PREFIX}_${key}_italic", style.italic)
            .putInt("${PREFIX}_${key}_opacity", style.opacityPercent)
            .putBoolean("${PREFIX}_${key}_visible", style.visible)
            .putBoolean("${PREFIX}_${key}_uppercase", style.uppercase)
            .apply()
    }

    fun writeSeparator(prefs: SharedPreferences, separator: PlayerTitleSeparator) {
        prefs.edit().putString(SEPARATOR_KEY, separator.name).apply()
    }

    fun writeOrders(prefs: SharedPreferences, style: PlayerTitleStyle) {
        prefs.edit()
            .putString(TITLE_ORDER_KEY, style.titleOrder.joinToString(",") { it.name })
            .putString(CLOCK_ORDER_KEY, style.clockOrder.joinToString(",") { it.name })
            .apply()
    }

    fun resetPart(prefs: SharedPreferences, part: PlayerTitlePart) {
        writePart(prefs, part, PlayerTitleStyle.defaultFor(part))
        resetOrderForPart(prefs, part)
    }

    fun resetAll(prefs: SharedPreferences) {
        PlayerTitlePart.entries.forEach { resetPart(prefs, it) }
        writeSeparator(prefs, PlayerTitleStyle.DEFAULT.separator)
    }

    private fun readPart(
        prefs: SharedPreferences,
        part: PlayerTitlePart,
    ): PlayerTitleTextStyle {
        val defaults = PlayerTitleStyle.defaultFor(part)
        val key = part.keySegment()
        return PlayerTitleTextStyle(
            font = prefs.getString("${PREFIX}_${key}_font", defaults.font)
                ?.takeIf { it == PlayerTitleStyle.INHERIT_FONT || UiFont.hasChoice(it) }
                ?: defaults.font,
            sizeSp = prefs.getFloat("${PREFIX}_${key}_size", defaults.sizeSp)
                .coerceIn(PLAYER_TITLE_MIN_CUSTOM_SIZE_SP, PLAYER_TITLE_MAX_CUSTOM_SIZE_SP),
            weight = PlayerTitleWeight.entries.firstOrNull {
                it.value == prefs.getInt("${PREFIX}_${key}_weight", defaults.weight.value)
            } ?: defaults.weight,
            letterSpacing = prefs.getFloat(
                "${PREFIX}_${key}_spacing",
                defaults.letterSpacing,
            ).coerceIn(PLAYER_TITLE_MIN_LETTER_SPACING, PLAYER_TITLE_MAX_LETTER_SPACING),
            color = enumValueOrDefault(
                prefs.getString("${PREFIX}_${key}_color", defaults.color.name),
                defaults.color,
            ),
            shadow = enumValueOrDefault(
                prefs.getString("${PREFIX}_${key}_shadow", defaults.shadow.name),
                defaults.shadow,
            ),
            italic = prefs.getBoolean("${PREFIX}_${key}_italic", defaults.italic),
            opacityPercent = prefs.getInt("${PREFIX}_${key}_opacity", defaults.opacityPercent)
                .coerceIn(PLAYER_TITLE_MIN_OPACITY_PERCENT, PLAYER_TITLE_MAX_OPACITY_PERCENT),
            visible = prefs.getBoolean("${PREFIX}_${key}_visible", defaults.visible),
            uppercase = prefs.getBoolean("${PREFIX}_${key}_uppercase", defaults.uppercase),
        )
    }

    private inline fun <reified T : Enum<T>> enumValueOrDefault(raw: String?, fallback: T): T =
        enumValues<T>().firstOrNull { it.name == raw } ?: fallback

    private inline fun <reified T : Enum<T>> readOrder(
        raw: String?,
        defaults: List<T>,
    ): List<T> {
        val parsed = raw?.split(',')?.mapNotNull { name ->
            enumValues<T>().firstOrNull { it.name == name }
        }.orEmpty()
        return parsed.takeIf { it.size == defaults.size && it.toSet() == defaults.toSet() }
            ?: defaults
    }

    private fun resetOrderForPart(prefs: SharedPreferences, part: PlayerTitlePart) {
        writeOrders(prefs, read(prefs).resetPartPosition(part))
    }

    private fun PlayerTitlePart.keySegment(): String = when (this) {
        PlayerTitlePart.SEASON -> "season"
        PlayerTitlePart.EPISODE_NUMBER -> "episode_number"
        PlayerTitlePart.TITLE -> "title"
        PlayerTitlePart.EPISODE_TITLE -> "episode_title"
        PlayerTitlePart.DATE -> "date"
        PlayerTitlePart.CLOCK -> "clock"
        PlayerTitlePart.ENDS_AT -> "ends_at"
    }

    private const val SEPARATOR_KEY = "${PREFIX}_separator"
    private const val TITLE_ORDER_KEY = "${PREFIX}_title_order"
    private const val CLOCK_ORDER_KEY = "${PREFIX}_clock_order"
}

internal fun MPVActivity.applyPlayerTitleStyle(force: Boolean = false) {
    applyPlayerTitleVisibility()
    if (!force && appliedPlayerTitleStyle == playerTitleStyle)
        return
    applyPlayerTextOrder()
    applyPlayerTitleTextStyle(binding.playerTitleSeason, playerTitleStyle.season)
    applyPlayerTitleTextStyle(binding.playerTitleEpisodeNumber, playerTitleStyle.episodeNumber)
    val separatorStyle = listOf(playerTitleStyle.season, playerTitleStyle.episodeNumber)
        .maxBy(PlayerTitleTextStyle::sizeSp)
    applyPlayerTitleTextStyle(
        binding.playerTitleContextSeparator,
        separatorStyle.copy(
            letterSpacing = 0f,
            color = PlayerTitleColor.CHROME,
            italic = false,
        ),
    )
    applyPlayerTitleTextStyle(binding.playerTitlePrimary, playerTitleStyle.title)
    applyPlayerTitleTextStyle(binding.playerTitleSecondary, playerTitleStyle.episodeTitle)
    applyPlayerTitleTextStyle(binding.dateTextView, playerTitleStyle.date)
    applyPlayerTitleTextStyle(binding.clockTextView, playerTitleStyle.clock)
    applyPlayerTitleTextStyle(binding.endsAtTextView, playerTitleStyle.endsAt)
    fittedPlayerTitleText = null
    fittedPlayerTitleWidth = 0
    fittedPlayerTitleFontScale = 0f
    fittedPlayerTitlePreferredSizeSp = 0f
    appliedPlayerTitleStyle = playerTitleStyle
    updatePlayerTitleWidth()
}

private fun MPVActivity.applyPlayerTitleVisibility() {
    val seasonVisible = binding.playerTitleSeason.applyTitleVisibility(
        playerTitleStyle.season.visible,
    )
    val episodeVisible = binding.playerTitleEpisodeNumber.applyTitleVisibility(
        playerTitleStyle.episodeNumber.visible,
    )
    binding.playerTitleContextSeparator.apply {
        text = playerTitleStyle.separator.text
        val separatorVisible = seasonVisible && episodeVisible &&
            playerTitleStyle.separator != PlayerTitleSeparator.NONE
        visibility = separatorVisible.toVisibility()
    }
    binding.playerTitleContextRow.visibility = (seasonVisible || episodeVisible).toVisibility()
    binding.playerTitlePrimary.applyTitleVisibility(playerTitleStyle.title.visible)
    binding.playerTitleSecondary.applyTitleVisibility(playerTitleStyle.episodeTitle.visible)
    binding.dateTextView.applyTitleVisibility(
        playerTitleStyle.date.visible && (playerTextStylePreviewActive || showClockDate),
    )
    binding.clockTextView.applyTitleVisibility(playerTitleStyle.clock.visible)
    val endsAtAvailable = playerTextStylePreviewActive || binding.endsAtTextView.visibility == View.VISIBLE
    binding.endsAtTextView.applyTitleVisibility(playerTitleStyle.endsAt.visible && endsAtAvailable)
}

private fun TextView.applyTitleVisibility(enabled: Boolean): Boolean {
    val shouldShow = enabled && text.isNotBlank()
    visibility = shouldShow.toVisibility()
    return shouldShow
}

private fun Boolean.toVisibility(): Int = if (this) View.VISIBLE else View.GONE

private fun MPVActivity.applyPlayerTitleTextStyle(
    view: TextView,
    style: PlayerTitleTextStyle,
) {
    view.setTextSize(TypedValue.COMPLEX_UNIT_SP, style.sizeSp)
    view.letterSpacing = style.letterSpacing
    view.isAllCaps = style.uppercase
    view.typeface = UiFont.typeface(
        context = this,
        value = style.font.takeUnless { it == PlayerTitleStyle.INHERIT_FONT }
            ?: UiFont.currentValue(this),
        weight = style.weight.value,
        italic = style.italic,
        fallback = view.typeface ?: Typeface.DEFAULT,
    )
    val baseColor = resolvePlayerTitleColor(style.color)
    view.setTextColor(
        Color.argb(
            PLAYER_TITLE_MAX_COLOR_CHANNEL * style.opacityPercent / PLAYER_TITLE_MAX_OPACITY_PERCENT,
            Color.red(baseColor),
            Color.green(baseColor),
            Color.blue(baseColor),
        ),
    )
    if (style.shadow == PlayerTitleShadow.OFF) {
        view.setShadowLayer(0f, 0f, 0f, Color.TRANSPARENT)
    } else {
        view.setShadowLayer(
            style.shadow.radius,
            0f,
            style.shadow.dy,
            Color.argb(style.shadow.alpha, 0, 0, 0),
        )
    }
}

private fun MPVActivity.resolvePlayerTitleColor(color: PlayerTitleColor): Int {
    val appearanceValue = color.appearanceValue
        ?: return themedColor(R.attr.mpvAccentHot, R.color.tv_text)
    return appearanceColorChoices.firstOrNull { it.value == appearanceValue }?.color
        ?: color(R.color.tv_text)
}

private fun Context.color(@ColorRes colorRes: Int): Int = ContextCompat.getColor(this, colorRes)

private fun Context.themedColor(attribute: Int, @ColorRes fallback: Int): Int {
    val value = TypedValue()
    return if (theme.resolveAttribute(attribute, value, true)) {
        if (value.resourceId != 0) ContextCompat.getColor(this, value.resourceId) else value.data
    } else {
        ContextCompat.getColor(this, fallback)
    }
}

internal const val PLAYER_TITLE_MIN_CUSTOM_SIZE_SP = 10f
internal const val PLAYER_TITLE_MAX_CUSTOM_SIZE_SP = 30f
internal const val PLAYER_TITLE_SIZE_STEP_SP = 0.5f
internal const val PLAYER_TITLE_MIN_LETTER_SPACING = -0.05f
internal const val PLAYER_TITLE_MAX_LETTER_SPACING = 0.12f
internal const val PLAYER_TITLE_LETTER_SPACING_STEP = 0.01f
internal const val PLAYER_TITLE_MIN_OPACITY_PERCENT = 40
internal const val PLAYER_TITLE_MAX_OPACITY_PERCENT = 100
internal const val PLAYER_TITLE_OPACITY_STEP_PERCENT = 10
private const val PLAYER_TITLE_MAX_COLOR_CHANNEL = 255
