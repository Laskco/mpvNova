package app.mpvnova.player

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import app.mpvnova.player.databinding.DialogOptionPickerBinding
import app.mpvnova.player.databinding.DialogSettingOptionItemBinding

internal class OptionPickerDialog(
    @param:StringRes private val eyebrowRes: Int,
    @param:StringRes private val titleRes: Int,
    private val items: List<Item>,
) {
    private lateinit var binding: DialogOptionPickerBinding
    var onItemPicked: ((Item) -> Unit)? = null
    var onCancelClick: (() -> Unit)? = null

    fun buildView(layoutInflater: LayoutInflater): View {
        binding = DialogOptionPickerBinding.inflate(layoutInflater)
        binding.optionPickerEyebrow.setText(eyebrowRes)
        binding.optionPickerTitle.setText(titleRes)
        binding.optionPickerCountText.text = selectedCountText()
        binding.cancelBtn.setOnClickListener { onCancelClick?.invoke() }
        binding.list.adapter = Adapter(this)
        binding.list.setHasFixedSize(true)
        TvScrollbars.bind(binding.list, binding.optionScrollbarThumb)
        scrollToSelectedItem()
        handleInsetsAsPadding(binding.root)
        return binding.root
    }

    private fun selectedCountText(): String {
        val selectedPosition = selectedPosition()
        return if (selectedPosition >= 0) {
            "#${selectedPosition + 1} / ${items.size}"
        } else {
            items.size.toString()
        }
    }

    private fun selectedPosition(): Int = items.indexOfFirst { it.selected }

    private fun scrollToSelectedItem() {
        val selectedPosition = selectedPosition()
        if (selectedPosition < 0) return
        binding.list.scrollToPosition(selectedPosition)
        binding.list.post {
            binding.list.findViewHolderForAdapterPosition(selectedPosition)
                ?.itemView
                ?.requestFocus()
        }
    }

    private fun clickItem(position: Int) {
        val item = items.getOrNull(position) ?: return
        onItemPicked?.invoke(item)
    }

    data class Item(
        val id: String,
        val title: String,
        val detail: String,
        val selected: Boolean,
    )

    private class Adapter(private val parent: OptionPickerDialog) :
        RecyclerView.Adapter<Adapter.ViewHolder>() {

        class ViewHolder(
            private val parent: OptionPickerDialog,
            private val binding: DialogSettingOptionItemBinding,
        ) : RecyclerView.ViewHolder(binding.root) {
            init {
                binding.root.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION)
                        parent.clickItem(position)
                }
            }

            fun bind(item: Item) = with(binding) {
                root.isActivated = item.selected
                optionTitleText.text = item.title
                optionDetailText.text = item.detail
                optionDetailText.isVisible = item.detail.isNotBlank()
                optionCheck.isVisible = item.selected
                UiFont.applyWeight(optionTitleText, bold = item.selected)
            }
        }

        override fun onCreateViewHolder(parentView: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parentView.context)
            val binding = DialogSettingOptionItemBinding.inflate(inflater, parentView, false)
            return ViewHolder(parent, binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            holder.bind(parent.items[position])
        }

        override fun getItemCount() = parent.items.size
    }
}
