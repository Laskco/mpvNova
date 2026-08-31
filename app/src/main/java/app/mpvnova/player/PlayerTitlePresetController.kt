package app.mpvnova.player

import android.content.SharedPreferences
import app.mpvnova.player.databinding.DialogPlayerTitleStyleBinding

internal class PlayerTitlePresetController(
    private val activity: MPVActivity,
    private val panel: DialogPlayerTitleStyleBinding,
    private val preferences: SharedPreferences,
    private val onPresetApplied: (PlayerTitleStyle) -> Unit,
) {
    private var customPresets = PlayerTitleCustomPresetStore.read(preferences)
    private var activeCustomPresetName = customPresets
        .firstOrNull { it.style == activity.playerTitleStyle.normalized() }
        ?.name

    fun bind() {
        panel.titleStylePresetMinusBtn.setOnClickListener { cycle(-1) }
        panel.titleStylePresetPlusBtn.setOnClickListener { cycle(1) }
        panel.titleStylePresetSaveBtn.setOnClickListener { save() }
        panel.titleStylePresetDeleteBtn.setOnClickListener { delete() }
    }

    fun clearSelection() {
        activeCustomPresetName = null
    }

    fun render() {
        val saved = customPresets
            .firstOrNull { it.name.equals(activeCustomPresetName, ignoreCase = true) }
        val builtIn = PlayerTitleBuiltInPreset.entries.firstOrNull {
            playerTitleBuiltInStyle(it) == activity.playerTitleStyle.normalized()
        }
        panel.titleStylePresetValue.text = when {
            saved != null && saved.style == activity.playerTitleStyle -> saved.name
            saved != null -> activity.getString(R.string.custom_preset_modified, saved.name)
            builtIn != null -> activity.getString(builtIn.labelRes())
            else -> activity.getString(R.string.custom_preset_unsaved)
        }
        panel.titleStylePresetDeleteBtn.isEnabled = saved != null
        panel.titleStylePresetDeleteBtn.alpha = if (saved != null) 1f else DISABLED_ACTION_ALPHA
    }

    private fun cycle(delta: Int) {
        val builtIns = PlayerTitleBuiltInPreset.entries.map {
            TitlePresetChoice(playerTitleBuiltInStyle(it), null)
        }
        val custom = customPresets.map {
            TitlePresetChoice(it.style, it.name)
        }
        val choices = builtIns + custom
        if (choices.isEmpty()) return
        val current = activeCustomPresetName?.let { name ->
            choices.indexOfFirst { it.customName.equals(name, ignoreCase = true) }
        }?.takeIf { it >= 0 } ?: choices.indexOfFirst {
            it.customName == null && it.style == activity.playerTitleStyle
        }
        val fallback = if (delta > 0) -1 else 0
        val next = Math.floorMod((current.takeIf { it >= 0 } ?: fallback) + delta, choices.size)
        val choice = choices[next]
        activeCustomPresetName = choice.customName
        onPresetApplied(choice.style)
    }

    private fun save() {
        activity.showCustomPresetNameDialog(
            currentName = activeCustomPresetName,
            chrome = PlayerDialogChrome.TITLE_AND_CLOCK_PREVIEW,
        ) { name ->
            customPresets = customPresets.filterNot { preset ->
                preset.name.equals(name, ignoreCase = true) ||
                    preset.name.equals(activeCustomPresetName, ignoreCase = true)
            } + PlayerTitleCustomPreset(name, activity.playerTitleStyle)
            PlayerTitleCustomPresetStore.write(preferences, customPresets)
            activeCustomPresetName = name
            activity.showToast(activity.getString(R.string.custom_preset_saved), name)
            render()
        }
    }

    private fun delete() {
        val name = activeCustomPresetName ?: return
        customPresets = customPresets.filterNot { it.name.equals(name, ignoreCase = true) }
        PlayerTitleCustomPresetStore.write(preferences, customPresets)
        activeCustomPresetName = null
        activity.showToast(activity.getString(R.string.custom_preset_deleted), name)
        render()
    }
}

private data class TitlePresetChoice(
    val style: PlayerTitleStyle,
    val customName: String?,
)

private fun PlayerTitleBuiltInPreset.labelRes() = when (this) {
    PlayerTitleBuiltInPreset.DEFAULT -> R.string.player_title_preset_default
    PlayerTitleBuiltInPreset.CINEMA -> R.string.player_title_preset_cinema
    PlayerTitleBuiltInPreset.MINIMAL -> R.string.player_title_preset_minimal
    PlayerTitleBuiltInPreset.BROADCAST -> R.string.player_title_preset_broadcast
    PlayerTitleBuiltInPreset.NEON -> R.string.player_title_preset_neon
    PlayerTitleBuiltInPreset.ACCESSIBLE -> R.string.player_title_preset_accessible
}

private const val DISABLED_ACTION_ALPHA = 0.45f
