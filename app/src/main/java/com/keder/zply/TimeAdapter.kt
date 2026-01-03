package com.keder.zply

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class TimeAdapter(
    private val items: List<String>,
    private val isInfinite: Boolean,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<TimeAdapter.TimeViewHolder>() {

    override fun getItemCount(): Int = if (isInfinite) Int.MAX_VALUE else items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimeViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_time, parent, false)
        return TimeViewHolder(view)
    }

    override fun onBindViewHolder(holder: TimeViewHolder, position: Int) {
        val actualPosition = if (isInfinite) position % items.size else position
        holder.bind(items[actualPosition])
    }

    inner class TimeViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timeTv: TextView = itemView.findViewById(R.id.time_tv)
        fun bind(item: String) {
            timeTv.text = item
            itemView.setOnClickListener { onItemClick(item) }
        }
    }
}