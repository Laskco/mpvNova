package app.mpvnova.player

import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import app.mpvnova.player.databinding.DialogShaderManagerBinding
import app.mpvnova.player.databinding.DialogShaderItemBinding
import app.mpvnova.player.preferences.showSettingsConfirmationDialog
import java.util.concurrent.Executors
import kotlin.math.roundToInt

@Suppress("TooManyFunctions")
internal class ShaderManagerDialog(
    private val activity: AppCompatActivity,
    private val requestImport: ((List<Uri>) -> Unit) -> Unit,
    private val onChanged: () -> Unit,
    private val onDismiss: () -> Unit = {},
) {
    private val binding = DialogShaderManagerBinding.inflate(activity.layoutInflater)
    private val dialog = AlertDialog.Builder(activity).setView(binding.root).create()

    fun show() {
        bindActions()
        render()
        dialog.setOnDismissListener { onDismiss() }
        if (activity is MPVActivity) {
            activity.showWidePlayerDialog(dialog, SHADER_MANAGER_DIALOG_LAYOUT)
        } else {
            dialog.show()
            configureSettingsWindow(dialog.window)
        }
        UiFont.applyToViewTree(binding.root)
        binding.shaderManagerEnabled.post { binding.shaderManagerEnabled.requestFocus() }
    }

    private fun bindActions() {
        binding.shaderManagerEnabled.setOnCheckedChangeListener(null)
        binding.shaderManagerEnabled.isChecked = UserShaderManager.isEnabled(activity)
        binding.shaderManagerEnabled.setOnCheckedChangeListener { _, checked ->
            UserShaderManager.setEnabled(activity, checked)
            notifyChanged()
            renderStatus()
        }
        binding.shaderManagerAdd.setOnClickListener {
            requestImport { uris ->
                if (uris.isNotEmpty()) importShaders(uris)
            }
        }
        binding.shaderManagerRefresh.setOnClickListener { refreshFolders() }
        binding.shaderManagerDisableAll.setOnClickListener {
            UserShaderManager.disableAll(activity)
            notifyChanged()
            render()
        }
        binding.shaderManagerDone.setOnClickListener { dialog.dismiss() }
    }

    private fun render(focusShaderId: String? = null) {
        val shaders = UserShaderManager.shaders(activity)
        binding.shaderManagerRows.removeAllViews()
        var focusView: View? = null
        if (shaders.isEmpty()) {
            binding.shaderManagerRows.addView(emptyState())
        } else {
            shaders.forEachIndexed { index, shader ->
                val row = shaderRow(shader, index, shaders.lastIndex)
                binding.shaderManagerRows.addView(row)
                if (shader.id == focusShaderId) {
                    focusView = row.findViewById(R.id.shaderEnabled)
                }
            }
        }
        binding.shaderManagerDisableAll.isEnabled = shaders.any { it.enabled }
        binding.shaderManagerRefresh.isEnabled = UserShaderManager.hasRememberedFolders(activity)
        updateActionFocusOrder()
        renderStatus(shaders)
        UiFont.applyToViewTree(binding.root)
        focusView?.let { target -> target.post { target.requestFocus() } }
    }

    private fun shaderRow(shader: UserShader, index: Int, lastIndex: Int): View {
        val row = DialogShaderItemBinding.inflate(
            activity.layoutInflater,
            binding.shaderManagerRows,
            false,
        )
        row.shaderEnabled.text = shader.displayName
        row.shaderEnabled.isChecked = shader.enabled
        row.shaderEnabled.setOnCheckedChangeListener { _, checked ->
            UserShaderManager.setShaderEnabled(activity, shader.id, checked)
            notifyChanged()
            updateActionState()
            renderStatus()
        }
        bindMoveButton(row.shaderMoveUp, index > 0, shader.id, -1)
        bindMoveButton(row.shaderMoveDown, index < lastIndex, shader.id, 1)
        row.shaderRemove.setOnClickListener { confirmRemove(shader) }
        return row.root
    }

    private fun bindMoveButton(button: ImageButton, enabled: Boolean, id: String, offset: Int) {
        button.isEnabled = enabled
        button.alpha = if (enabled) 1f else DISABLED_ALPHA
        button.setOnClickListener {
            UserShaderManager.move(activity, id, offset)
            notifyChanged()
            render(id)
        }
    }

    private fun emptyState(): View = TextView(activity).apply {
        layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
        )
        setPadding(
            EMPTY_HORIZONTAL_PADDING_DP.dp(),
            EMPTY_VERTICAL_PADDING_DP.dp(),
            EMPTY_HORIZONTAL_PADDING_DP.dp(),
            EMPTY_VERTICAL_PADDING_DP.dp(),
        )
        text = activity.getString(R.string.shader_empty)
        setTextColor(activity.getColor(R.color.tv_text_dim))
        textSize = EMPTY_TEXT_SIZE_SP
        textAlignment = View.TEXT_ALIGNMENT_CENTER
    }

    private fun confirmRemove(shader: UserShader) {
        activity.showSettingsConfirmationDialog(
            title = activity.getString(R.string.shader_remove_title),
            message = activity.getString(R.string.shader_remove_message, shader.displayName),
            confirmText = activity.getString(R.string.shader_remove),
        ) {
            UserShaderManager.remove(activity, shader.id)
            notifyChanged()
            render()
        }
    }

    private fun importShaders(uris: List<Uri>) {
        setBusy(true, refreshing = false)
        SHADER_IO_EXECUTOR.execute {
            val result = runCatching { UserShaderManager.import(activity.applicationContext, uris) }
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                setBusy(false, refreshing = false)
                result.onSuccess { imported ->
                    val message = when {
                        imported.errors.isNotEmpty() -> imported.errors.first()
                        imported.imported > 0 && imported.skipped > 0 -> activity.getString(
                            R.string.shader_import_result_with_skipped,
                            imported.imported,
                            imported.skipped,
                        )
                        imported.imported > 0 -> activity.resources.getQuantityString(
                            R.plurals.shader_import_result,
                            imported.imported,
                            imported.imported,
                        )
                        imported.skipped > 0 -> activity.getString(R.string.shader_import_duplicates)
                        else -> activity.getString(R.string.shader_import_none)
                    }
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                    notifyChanged()
                    render()
                }.onFailure { error ->
                    Toast.makeText(
                        activity,
                        error.message ?: activity.getString(R.string.shader_import_failed),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private fun refreshFolders() {
        setBusy(true, refreshing = true)
        SHADER_IO_EXECUTOR.execute {
            val result = runCatching {
                UserShaderManager.refreshFolders(activity.applicationContext)
            }
            activity.runOnUiThread {
                if (activity.isFinishing || activity.isDestroyed) return@runOnUiThread
                setBusy(false, refreshing = true)
                result.onSuccess { refreshed ->
                    val message = when {
                        refreshed.imported > 0 && refreshed.errors.isNotEmpty() ->
                            activity.getString(
                                R.string.shader_refresh_partial,
                                refreshed.imported,
                                refreshed.errors.size,
                            )
                        refreshed.imported > 0 -> activity.resources.getQuantityString(
                            R.plurals.shader_refresh_result,
                            refreshed.imported,
                            refreshed.imported,
                        )
                        refreshed.errors.isNotEmpty() -> activity.resources.getQuantityString(
                            R.plurals.shader_refresh_unavailable,
                            refreshed.errors.size,
                            refreshed.errors.size,
                        )
                        else -> activity.getString(R.string.shader_refresh_none)
                    }
                    Toast.makeText(activity, message, Toast.LENGTH_LONG).show()
                    if (refreshed.imported > 0) notifyChanged()
                    render()
                }.onFailure { error ->
                    Toast.makeText(
                        activity,
                        error.message ?: activity.getString(R.string.shader_refresh_failed),
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }

    private fun setBusy(busy: Boolean, refreshing: Boolean) {
        binding.shaderManagerAdd.isEnabled = !busy
        binding.shaderManagerAdd.text = activity.getString(
            if (busy && !refreshing) R.string.shader_importing else R.string.shader_add
        )
        binding.shaderManagerRefresh.isEnabled = !busy && UserShaderManager.hasRememberedFolders(activity)
        binding.shaderManagerRefresh.text = activity.getString(
            if (busy && refreshing) R.string.shader_refreshing else R.string.shader_refresh
        )
        binding.shaderManagerDisableAll.isEnabled = !busy &&
            UserShaderManager.shaders(activity).any { it.enabled }
        updateActionFocusOrder()
    }

    private fun updateActionState() {
        binding.shaderManagerDisableAll.isEnabled =
            UserShaderManager.shaders(activity).any { it.enabled }
        updateActionFocusOrder()
    }

    private fun updateActionFocusOrder() {
        val buttons = listOf(
            binding.shaderManagerAdd,
            binding.shaderManagerRefresh,
            binding.shaderManagerDisableAll,
            binding.shaderManagerDone,
        ).filter { it.isEnabled }
        buttons.forEachIndexed { index, button ->
            button.nextFocusLeftId = buttons[(index - 1 + buttons.size) % buttons.size].id
            button.nextFocusRightId = buttons[(index + 1) % buttons.size].id
        }
    }

    private fun notifyChanged() {
        onChanged()
    }

    private fun renderStatus(shaders: List<UserShader> = UserShaderManager.shaders(activity)) {
        val active = shaders.count { it.enabled }
        val state = if (UserShaderManager.isEnabled(activity)) {
            activity.getString(R.string.shader_status, active, shaders.size)
        } else {
            activity.getString(R.string.shader_status_manager_off, shaders.size)
        }
        binding.shaderManagerStatus.text = "$state\n${activity.getString(R.string.shader_performance_warning)}"
    }

    private fun configureSettingsWindow(window: Window?) {
        val metrics = activity.resources.displayMetrics
        window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            decorView.setPadding(0, 0, 0, 0)
            setGravity(Gravity.CENTER)
            setLayout(
                (metrics.widthPixels * SETTINGS_WIDTH_FRACTION).roundToInt(),
                (metrics.heightPixels * SETTINGS_HEIGHT_FRACTION).roundToInt(),
            )
        }
    }

    private fun Int.dp(): Int = (this * activity.resources.displayMetrics.density).roundToInt()

    private companion object {
        const val DISABLED_ALPHA = 0.35f
        const val SETTINGS_WIDTH_FRACTION = 0.64f
        const val SETTINGS_HEIGHT_FRACTION = 0.82f
        const val EMPTY_HORIZONTAL_PADDING_DP = 18
        const val EMPTY_VERTICAL_PADDING_DP = 28
        const val EMPTY_TEXT_SIZE_SP = 13f
        val SHADER_IO_EXECUTOR = Executors.newSingleThreadExecutor { runnable ->
            Thread(runnable, "mpvNova-shader-io")
        }
    }
}

internal val SHADER_MANAGER_DIALOG_LAYOUT = PlayerDialogLayout(
    widthFraction = 0.64f,
    maxWidthDp = 760f,
    heightFraction = 0.86f,
    maxHeightDp = 620f,
)
