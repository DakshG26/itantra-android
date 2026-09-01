package com.isro.itantra.ui

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.isro.itantra.R
import com.isro.itantra.p2p.RadioPacket
import java.text.SimpleDateFormat
import java.util.*

class MessageAdapter(
    private val onPlayTtsClicked: (RadioPacket) -> Unit
) : RecyclerView.Adapter<MessageAdapter.MessageViewHolder>() {

    private val messages = mutableListOf<RadioPacket>()
    private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    fun addMessage(packet: RadioPacket) {
        messages.add(packet)
        notifyItemInserted(messages.size - 1)
    }

    fun clear() {
        messages.clear()
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val msg = messages[position]
        holder.bind(msg)
    }

    override fun getItemCount(): Int = messages.size

    inner class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val cardMessage: CardView = itemView.findViewById(R.id.cardMessage)
        private val tvSosBadge: TextView = itemView.findViewById(R.id.tvSosBadge)
        private val tvMessageText: TextView = itemView.findViewById(R.id.tvMessageText)
        private val tvTokenInfo: TextView = itemView.findViewById(R.id.tvTokenInfo)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val btnPlayTts: View = itemView.findViewById(R.id.btnPlayTts)

        fun bind(packet: RadioPacket) {
            tvMessageText.text = packet.text
            tvTimestamp.text = timeFormat.format(Date(packet.timestamp))
            tvTokenInfo.text = "18B • ${packet.language.uppercase()} • RS(32,24)"

            val context = itemView.context
            val layoutParams = cardMessage.layoutParams as LinearLayout.LayoutParams

            if (packet.isSos) {
                tvSosBadge.visibility = View.VISIBLE
                cardMessage.setCardBackgroundColor(ContextCompat.getColor(context, R.color.tactical_red_light))
                tvMessageText.setTextColor(ContextCompat.getColor(context, R.color.tactical_red))
            } else {
                tvSosBadge.visibility = View.GONE
                if (packet.isSentByMe) {
                    layoutParams.gravity = Gravity.END
                    cardMessage.setCardBackgroundColor(ContextCompat.getColor(context, R.color.isro_blue))
                    tvMessageText.setTextColor(ContextCompat.getColor(context, R.color.bg_surface))
                    tvTokenInfo.setTextColor(ContextCompat.getColor(context, R.color.isro_blue_light))
                } else {
                    layoutParams.gravity = Gravity.START
                    cardMessage.setCardBackgroundColor(ContextCompat.getColor(context, R.color.bg_surface))
                    tvMessageText.setTextColor(ContextCompat.getColor(context, R.color.text_primary))
                    tvTokenInfo.setTextColor(ContextCompat.getColor(context, R.color.text_muted))
                }
            }

            cardMessage.layoutParams = layoutParams

            btnPlayTts.setOnClickListener {
                onPlayTtsClicked(packet)
            }
        }
    }
}
