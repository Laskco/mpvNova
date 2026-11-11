package app.mpvnova.player.preferences

import android.widget.Button
import androidx.annotation.StringRes

internal fun Button.bindSettingsChoices(
    @StringRes title: Int,
    entries: Array<String>,
    onSelected: (String) -> Unit = {},
) {
    setOnClickListener {
        val items = entries.map { value ->
            SettingsChoiceItem(title = value, checked = text.toString() == value) {
                text = value
                onSelected(value)
            }
        }
        context.showSettingsChoiceDialog(resources.getText(title), items, opaque = true)
    }
}
