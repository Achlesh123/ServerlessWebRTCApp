package co.netguru.android.chatandroll.feature.main

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.support.v7.widget.RecyclerView
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import co.netguru.android.chatandroll.R

/**
 * This is just to do the printing into the RecyclerView.
 */
class ConsoleAdapter(val items: List<String>) : RecyclerView.Adapter<ConsoleAdapter.ConsoleVH>() {

    @Suppress("DEPRECATION")
    override fun onBindViewHolder(holder: ConsoleVH, position: Int) {
        holder.tvText.text = Html.fromHtml(items[position])
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ConsoleVH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.l_item, parent, false)
        return ConsoleVH(view)
    }

    override fun getItemCount(): Int = items.count()

    class ConsoleVH(view: View) : RecyclerView.ViewHolder(view) {
        var tvText: TextView = view.findViewById(R.id.tvText)

        init {
            tvText.setOnLongClickListener {
                //clipboard on long touch
                val text = tvText.text.toString()
                val clipboard = tvText.context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                clipboard.primaryClip = ClipData.newPlainText("text", text)
                Toast.makeText(tvText.context, R.string.clipboard_copy, Toast.LENGTH_SHORT).show()
                true
            }
        }


    }
}