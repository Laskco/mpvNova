package app.mpvnova.player

import app.mpvnova.player.databinding.DialogPlaylistBinding
import android.content.res.ColorStateList
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

internal class PlaylistDialog(private val player: MPVView) {
    private lateinit var binding: DialogPlaylistBinding

    private var playlist = listOf<MPVView.PlaylistItem>()
    private var selectedIndex = -1

    interface Listeners {
        fun pickFile()
        fun openUrl()
        fun onItemPicked(item: MPVView.PlaylistItem)
    }

    var listeners: Listeners? = null

    fun buildView(layoutInflater: LayoutInflater): View {
        if (!::binding.isInitialized) {
            binding = DialogPlaylistBinding.inflate(layoutInflater)
            binding.list.adapter = CustomAdapter(this)
            binding.list.setHasFixedSize(true)

            binding.fileBtn.setOnClickListener { listeners?.pickFile() }
            binding.urlBtn.setOnClickListener { listeners?.openUrl() }

            binding.shuffleBtn.setOnClickListener {
                if (playlist.size > 1) {
                    player.changeShuffle(true)
                    refresh()
                }
            }
            binding.repeatBtn.setOnClickListener {
                player.cycleRepeat()
                refresh()
            }
            handleInsetsAsPadding(binding.root)
        } else {
            binding.root.detachFromParent()
        }

        refresh()
        return binding.root
    }

    fun refresh() {
        selectedIndex = mpvGetPropertyInt("playlist-pos") ?: -1
        val oldCount = playlist.size
        playlist = player.loadPlaylist()
        Log.v(TAG, "PlaylistDialog: loaded ${playlist.size} items")
        binding.list.adapter?.notifyRangeRefresh(oldCount, playlist.size)
        binding.list.scrollToPosition(playlist.indexOfFirst { it.index == selectedIndex })

        /*
         * At least on api 33 there is in some cases a (reproducible) bug, where the space below the
         * recycler view for the two buttons is not taken into account and they go out-of-bounds of the
         * alert dialog. This fixes it.
         */
        binding.list.post {
            binding.list.parent.requestLayout()
        }

        val context = binding.root.context
        val accent = ContextCompat.getColor(context, R.color.accent)
        val normal = ContextCompat.getColor(context, R.color.tv_text)
        val disabled = ContextCompat.getColor(context, R.color.tv_text_dim)
        val shuffleState = player.getShuffle()
        binding.shuffleBtn.apply {
            isEnabled = true
            isSelected = shuffleState
            alpha = if (playlist.size > 1) 1f else UNAVAILABLE_ACTION_ALPHA
            imageTintList = ColorStateList.valueOf(
                when {
                    shuffleState -> accent
                    playlist.size > 1 -> normal
                    else -> disabled
                }
            )
        }
        val repeatState = player.getRepeat()
        binding.repeatBtn.apply {
            isSelected = repeatState > 0
            imageTintList = ColorStateList.valueOf(if (repeatState > 0) accent else normal)
            setImageResource(if (repeatState == 2) R.drawable.ic_repeat_one_24dp else R.drawable.ic_repeat_24dp)
        }
    }

    private fun clickItem(position: Int) {
        val item = playlist[position]
        listeners?.onItemPicked(item)
    }

    class CustomAdapter(private val parent: PlaylistDialog) :
        RecyclerView.Adapter<CustomAdapter.ViewHolder>() {

        class ViewHolder(private val parent: PlaylistDialog, view: View) :
            RecyclerView.ViewHolder(view) {
            private val textView: TextView
            private val subtitleView: TextView

            init {
                textView = view.findViewById(android.R.id.text1)
                subtitleView = view.findViewById(R.id.subtitleText)
                view.setOnClickListener {
                    val position = bindingAdapterPosition
                    if (position != RecyclerView.NO_POSITION)
                        parent.clickItem(position)
                }
            }

            fun bind(item: MPVView.PlaylistItem, selected: Boolean) {
                textView.text = item.title ?: Utils.fileBasename(item.filename)
                itemView.isActivated = selected
                UiFont.applyWeight(textView, bold = selected)
                val subtitle = if (item.title.isNullOrBlank()) {
                    "#${item.index + 1}"
                } else {
                    Utils.fileBasename(item.filename)
                }
                subtitleView.text = subtitle
                subtitleView.visibility = if (subtitle.isBlank()) View.GONE else View.VISIBLE
            }
        }

        override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(viewGroup.context)
                .inflate(R.layout.dialog_playlist_item, viewGroup, false)
            return ViewHolder(parent, view)
        }

        override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
            val item = parent.playlist[position]
            viewHolder.bind(item, item.index == parent.selectedIndex)
        }

        override fun getItemCount() = parent.playlist.size
    }

    companion object {
        private const val TAG = "mpv"
        private const val UNAVAILABLE_ACTION_ALPHA = 0.55f
    }
}
