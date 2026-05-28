package app.mpvnova.player

import app.mpvnova.player.databinding.DialogChapterItemBinding
import app.mpvnova.player.databinding.DialogChapterPickerBinding
import android.graphics.Typeface
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

internal class ChapterPickerDialog(
    private val items: List<Item>,
    private val selectedChapterIndex: Int
) {
    private lateinit var binding: DialogChapterPickerBinding
    var onItemPicked: ((Item) -> Unit)? = null
    var onCancelClick: (() -> Unit)? = null

    fun buildView(layoutInflater: LayoutInflater): View {
        binding = DialogChapterPickerBinding.inflate(layoutInflater)
        binding.chapterCountText.text = currentChapterCountText()
        binding.cancelBtn.setOnClickListener { onCancelClick?.invoke() }
        binding.list.adapter = Adapter(this)
        binding.list.setHasFixedSize(true)
        TvScrollbars.bind(binding.list, binding.chapterScrollbarThumb)
        scrollToSelectedChapter()
        handleInsetsAsPadding(binding.root)
        return binding.root
    }

    private fun currentChapterCountText(): String {
        val selectedPosition = selectedPosition()
        return if (selectedPosition >= 0) {
            "#${selectedPosition + 1} / ${items.size}"
        } else {
            items.size.toString()
        }
    }

    private fun selectedPosition(): Int {
        return items.indexOfFirst { it.index == selectedChapterIndex }
    }

    private fun scrollToSelectedChapter() {
        val selectedPosition = selectedPosition()
        if (selectedPosition < 0)
            return
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
        val index: Int,
        val title: String,
        val timecode: String
    )

    private class Adapter(private val parent: ChapterPickerDialog) :
        RecyclerView.Adapter<Adapter.ViewHolder>() {

        class ViewHolder(
            private val parent: ChapterPickerDialog,
            private val binding: DialogChapterItemBinding
        ) : RecyclerView.ViewHolder(binding.root) {
            init {
                binding.root.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION)
                        parent.clickItem(position)
                }
            }

            fun bind(item: Item, selected: Boolean) = with(binding) {
                root.isActivated = selected
                chapterNumberText.text = "#${item.index + 1}"
                chapterTitleText.text = item.title
                chapterTimeText.text = item.timecode
                chapterTitleText.setTypeface(null, if (selected) Typeface.BOLD else Typeface.NORMAL)
            }
        }

        override fun onCreateViewHolder(parentView: ViewGroup, viewType: Int): ViewHolder {
            val inflater = LayoutInflater.from(parentView.context)
            val binding = DialogChapterItemBinding.inflate(inflater, parentView, false)
            return ViewHolder(parent, binding)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = parent.items[position]
            holder.bind(item, item.index == parent.selectedChapterIndex)
        }

        override fun getItemCount() = parent.items.size
    }
}
