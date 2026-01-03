package com.keder.zply

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.keder.zply.databinding.ItemMainCardBinding

// 어댑터
class MainCardRVAdapter(private val items: List<MainCardData>, private val onItemClick : (MainCardData) -> Unit) :
    RecyclerView.Adapter<MainCardRVAdapter.Holder>(){

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): Holder {
        val binding = ItemMainCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class Holder(private val binding : ItemMainCardBinding) : RecyclerView.ViewHolder(binding.root){
        fun bind(item: MainCardData){
            binding.tvStatus.text = item.status
            binding.tvDate.text = item.date
            binding.tvLocation.text = item.location
            binding.tvCount.text = "${item.count}개"

            val bgResId = when(item.status){
                "탐색중" -> R.drawable.gradient_card_ing
                "탐색완료" -> R.drawable.gradient_card_ed
                "탐색예정" -> R.drawable.gradient_card_will
                else -> R.drawable.gradient_card_ing
            }

            binding.explorCardCl.setBackgroundResource(bgResId)
            binding.root.setOnClickListener{
                onItemClick(item)
            }
        }
    }
}