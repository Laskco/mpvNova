package app.mpvnova.player

import android.view.LayoutInflater
import android.view.View
import android.widget.SeekBar
import androidx.annotation.StringRes
import app.mpvnova.player.databinding.DialogVideoAdjustmentBinding

internal data class VideoAdjustmentDialogConfig(
    @param:StringRes val titleRes: Int,
    val minValue: Int,
    val maxValue: Int,
    val defaultValue: Int,
    @param:StringRes val valueFormatRes: Int
)

internal class VideoAdjustmentDialog(
    private val config: VideoAdjustmentDialogConfig,
    initialValue: Int,
    initialRemember: Boolean? = null,
    private val onPreview: (Int) -> Unit
) {
    private lateinit var binding: DialogVideoAdjustmentBinding
    private val hasRememberToggle = initialRemember != null

    var value = initialValue.coerceIn(config.minValue, config.maxValue)
        private set
    var rememberValue = initialRemember ?: false
        private set

    fun reset(initialValue: Int, initialRemember: Boolean?): VideoAdjustmentDialog {
        value = initialValue.coerceIn(config.minValue, config.maxValue)
        rememberValue = initialRemember ?: false
        return this
    }

    fun buildView(
        layoutInflater: LayoutInflater,
        onOk: () -> Unit,
        onCancel: () -> Unit
    ): View {
        if (!::binding.isInitialized) {
            binding = DialogVideoAdjustmentBinding.inflate(layoutInflater)
            binding.adjustmentTitle.setText(config.titleRes)
            binding.adjustmentSeekBar.max = config.maxValue - config.minValue
            binding.adjustmentSeekBar.setOnSeekBarChangeListener(
                object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(
                        seekBar: SeekBar?,
                        progress: Int,
                        fromUser: Boolean
                    ) {
                        if (fromUser) {
                            setValue(progress + config.minValue, notify = true)
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                    override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
                }
            )
            binding.resetBtn.setOnClickListener {
                setValue(config.defaultValue, notify = true)
            }
        } else {
            binding.root.detachFromParent()
        }
        binding.rememberRow.visibility = if (hasRememberToggle) View.VISIBLE else View.GONE
        binding.rememberRow.setOnClickListener {
            rememberValue = !rememberValue
            bindRememberState()
        }
        binding.cancelBtn.setOnClickListener { onCancel() }
        binding.okBtn.setOnClickListener { onOk() }

        bindRememberState()
        setValue(value, notify = false)
        binding.adjustmentSeekBar.post { binding.adjustmentSeekBar.requestFocus() }
        return binding.root
    }

    private fun setValue(newValue: Int, notify: Boolean) {
        value = newValue.coerceIn(config.minValue, config.maxValue)
        binding.adjustmentValue.setTextIfChanged(
            binding.root.context.getString(config.valueFormatRes, value.toDouble()),
        )
        val progress = value - config.minValue
        if (binding.adjustmentSeekBar.progress != progress) {
            binding.adjustmentSeekBar.progress = progress
        }
        if (notify) {
            onPreview(value)
        }
    }

    private fun bindRememberState() {
        binding.rememberCheck.setVisibilityIfChanged(if (rememberValue) View.VISIBLE else View.INVISIBLE)
        binding.rememberRow.isActivated = rememberValue
    }
}
