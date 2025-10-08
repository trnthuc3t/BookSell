package com.pro.book.adapter

import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.pro.book.R
import com.pro.book.model.Message

class ChatAdapter(private val messages: MutableList<Message>) :
    RecyclerView.Adapter<ChatAdapter.MessageViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_chat_message, parent, false)
        return MessageViewHolder(view)
    }

    override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
        val message = messages[position]
        holder.bind(message)
    }

    override fun getItemCount(): Int = messages.size

    fun addMessage(message: Message) {
        messages.add(message)
        notifyItemInserted(messages.size - 1)
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tv_message)
        private val layoutMessage: LinearLayout = itemView.findViewById(R.id.layout_message)
        private val containerLayout: LinearLayout = itemView.findViewById(R.id.container_layout)

        fun bind(message: Message) {
            tvMessage.text = message.content

            if (message.isFromUser) {
                // User message - align right
                containerLayout.gravity = Gravity.END
                layoutMessage.setBackgroundResource(R.drawable.bg_message_user)
                tvMessage.setTextColor(ContextCompat.getColor(itemView.context, R.color.white))
            } else {
                // Bot message - align left
                containerLayout.gravity = Gravity.START
                layoutMessage.setBackgroundResource(R.drawable.bg_message_bot)
                tvMessage.setTextColor(ContextCompat.getColor(itemView.context, R.color.textColorHeading))
            }
        }
    }
}