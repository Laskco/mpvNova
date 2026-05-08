package app.mpvnova.player

import app.mpvnova.player.databinding.DialogDecimalBinding
import android.view.LayoutInflater
import android.view.View
import androidx.core.widget.addTextChangedListener

internal class DecimalPickerDialog(
    private val rangeMin: Double, private val rangeMax: Double
) : PickerDialog {
    private lateinit var binding: DialogDecimalBinding

    override fun buildView(layoutInflater: LayoutInflater): View {
        binding = DialogDecimalBinding.inflate(layoutInflater)

        // hide extraneous UI parts
        arrayOf(binding.label1, binding.label2, binding.rowSecondary).forEach {
            it.visibility = View.GONE
        }

        binding.editText.addTextChangedListener(afterTextChanged = { text ->
            val value = text?.toString()?.toDoubleOrNull() ?: return@addTextChangedListener
            val valueBounded = value.coerceIn(rangeMin, rangeMax)
            if (valueBounded != value)
                binding.editText.setText(valueBounded.toString())
        })
        val onClick = { delta: Double ->
            val value = this.number ?: 0.0
            this.number = (value + delta).coerceIn(rangeMin, rangeMax)
        }
        binding.btnMinus.setOnClickListener { onClick(-STEP) }
        binding.btnPlus.setOnClickListener { onClick(STEP) }

        return binding.root
    }

    override fun isInteger(): Boolean = false

    override var number: Double?
        set(v) = binding.editText.setText(v!!.toString())
        get() = binding.editText.text.toString().toDoubleOrNull()

    companion object {
        private const val STEP = 1.0
    }
}
