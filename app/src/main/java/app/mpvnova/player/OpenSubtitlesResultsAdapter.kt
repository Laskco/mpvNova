package app.mpvnova.player

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AbsListView
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.TextView

internal class OpenSubtitlesResultsAdapter(
    private val cleanText: (String) -> String,
) : BaseAdapter() {
    private class ViewHolder(
        val name: TextView,
        val release: TextView,
        val meta: TextView,
    )

    private val results = mutableListOf<OpenSubtitlesResult>()
    private var downloadedIds: Set<Int> = emptySet()
    private var cachedRowHeight = 0
    private var cachedFontKey: String? = null

    fun setDownloadedIds(ids: Set<Int>) {
        downloadedIds = ids.toSet()
        notifyDataSetChanged()
    }

    fun replace(items: List<OpenSubtitlesResult>) {
        results.clear()
        results.addAll(items)
        notifyDataSetChanged()
    }

    fun snapshot(): List<OpenSubtitlesResult> = results.toList()
    override fun getCount(): Int = results.size
    override fun getItem(position: Int): OpenSubtitlesResult = results[position]
    override fun getItemId(position: Int): Long = getItem(position).fileId.toLong()

    fun measureRowHeight(parent: ViewGroup): Int {
        val fontKey = UiFont.currentValue(parent.context)
        if (cachedRowHeight > 0 && cachedFontKey == fontKey) return cachedRowHeight
        val probe = LayoutInflater.from(parent.context)
            .inflate(R.layout.row_opensubtitles_result, parent, false)
        val holder = createHolder(probe)
        cachedRowHeight = computeRowHeight(probe, holder)
        cachedFontKey = fontKey
        return cachedRowHeight
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row: View
        val holder: ViewHolder
        if (convertView == null) {
            row = LayoutInflater.from(parent.context)
                .inflate(R.layout.row_opensubtitles_result, parent, false)
            holder = createHolder(row)
            row.tag = holder
        } else {
            row = convertView
            holder = row.tag as ViewHolder
        }

        val item = getItem(position)
        val context = parent.context
        holder.name.setTextIfChanged(
            cleanText(item.fileName).ifBlank { context.getString(R.string.os_search_unnamed) },
        )
        val releaseText = cleanText(item.release)
        holder.release.setTextIfChanged(releaseText)
        holder.release.setVisibilityIfChanged(if (releaseText.isEmpty()) View.INVISIBLE else View.VISIBLE)
        holder.meta.setTextIfChanged(formatMeta(context, item))

        val fontKey = UiFont.currentValue(context)
        if (cachedRowHeight <= 0 || cachedFontKey != fontKey) {
            cachedRowHeight = computeRowHeight(row, holder)
            cachedFontKey = fontKey
        }
        val currentLp = row.layoutParams
        if (currentLp !is AbsListView.LayoutParams || currentLp.height != cachedRowHeight) {
            row.layoutParams = AbsListView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, cachedRowHeight)
        }
        val activated = parent is ListView && parent.hasFocus() && parent.selectedItemPosition == position
        if (row.isActivated != activated) row.isActivated = activated
        if (row.isHovered) row.isHovered = false
        return row
    }

    private fun createHolder(row: View): ViewHolder = ViewHolder(
        name = row.findViewById(R.id.osResultName),
        release = row.findViewById(R.id.osResultRelease),
        meta = row.findViewById(R.id.osResultMeta),
    )

    private fun formatMeta(context: Context, item: OpenSubtitlesResult): String {
        val sb = StringBuilder()
        fun appendPart(part: String) {
            if (part.isBlank()) return
            if (sb.isNotEmpty()) sb.append(" / ")
            sb.append(part)
        }
        appendPart(cleanText(item.language))
        if (item.fileId in downloadedIds) appendPart(context.getString(R.string.os_search_downloaded))
        if (item.hearingImpaired) appendPart(context.getString(R.string.os_search_hearing_impaired))
        if (item.hashMatched) appendPart(context.getString(R.string.os_search_hash_match))
        return sb.toString()
    }

    private fun computeRowHeight(row: View, holder: ViewHolder): Int {
        val textHeight = measureLineHeight(holder.name) +
            measureLineHeight(holder.release) +
            measureLineHeight(holder.meta)
        return maxOf(row.minimumHeight, textHeight + row.paddingTop + row.paddingBottom)
    }

    private fun measureLineHeight(text: TextView): Int {
        val margins = text.layoutParams as ViewGroup.MarginLayoutParams
        val metrics = text.paint.fontMetricsInt
        val height = if (text.includeFontPadding) {
            metrics.bottom - metrics.top
        } else {
            metrics.descent - metrics.ascent
        }
        return height + text.compoundPaddingTop + text.compoundPaddingBottom + margins.topMargin + margins.bottomMargin
    }
}
