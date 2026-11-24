package app.mpvnova.player

import android.content.Context
import android.util.AttributeSet

// ScrollView that caps its measured height so a long list scrolls internally instead of
// pushing a dialog's pinned buttons off-screen. Set [maxHeightPx] before layout.
class MaxHeightScrollView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : TvSmoothScrollView(context, attrs, defStyleAttr) {
    var maxHeightPx: Int = 0

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val spec = if (maxHeightPx > 0) {
            MeasureSpec.makeMeasureSpec(maxHeightPx, MeasureSpec.AT_MOST)
        } else {
            heightMeasureSpec
        }
        super.onMeasure(widthMeasureSpec, spec)
    }
}
