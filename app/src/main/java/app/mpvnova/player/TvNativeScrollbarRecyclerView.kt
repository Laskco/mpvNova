package app.mpvnova.player

import android.content.Context
import android.util.AttributeSet
import androidx.recyclerview.widget.RecyclerView

class TvNativeScrollbarRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : RecyclerView(context, attrs, defStyleAttr) {
    fun revealNativeScrollbar() {
        awakenScrollBars()
    }
}
