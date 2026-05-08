package com.keder.zply

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.keder.zply.databinding.ItemPublicPeaceBinding

class PublicPeaceAdapter(private var items: List<NewsItem>) :
    RecyclerView.Adapter<PublicPeaceAdapter.ViewHolder>() {

    inner class ViewHolder(private val binding: ItemPublicPeaceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: NewsItem) {
            binding.publicPeaceDesc.text = item.summary
            val date = item.publishedAt.replace("-", ".")
            binding.publicPeaceDateTitle.text = if (date.isNotEmpty()) "$date  ${item.title}" else item.title
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemPublicPeaceBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount() = items.size

    fun updateItems(newItems: List<NewsItem>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                items[oldPos].contentUrl == newItems[newPos].contentUrl
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                items[oldPos] == newItems[newPos]
        })
        items = newItems
        diff.dispatchUpdatesTo(this)
    }
}
