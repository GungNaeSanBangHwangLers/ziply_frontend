package com.keder.zply

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.keder.zply.databinding.ItemExploreBinding

class BeforeExploreAdapter(private val items : List<ScheduleItem>) : RecyclerView.Adapter<BeforeExploreAdapter.ViewHolder>() {

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ItemExploreBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(val binding : ItemExploreBinding) : RecyclerView.ViewHolder(binding.root){
        fun bind(item: ScheduleItem, position : Int){

            val rankChar = ('A'.code + position).toChar()
            binding.exploreRankTv.text = rankChar.toString()
            setRankStyle(binding.exploreRankTv, rankChar)

            binding.exploreAddress.text = item.address
            binding.exploreDateTv.text = "${item.time} 탐색"

            //수정 아이콘 클릭 이벤트 추후 추가
            binding.exploreWriteIv.setOnClickListener {  }
        }
    }

    private fun setRankStyle(textView: TextView, rank : Char){
        val context = textView.context

        val brand100 = ContextCompat.getColor(context, R.color.brand_100)
        val brand800 = ContextCompat.getColor(context, R.color.brand_800)
        val brand400 = ContextCompat.getColor(context, R.color.brand_400)
        val white = ContextCompat.getColor(context, R.color.white)
        val brand700 = ContextCompat.getColor(context, R.color.brand_700)
        val brand950 = ContextCompat.getColor(context, R.color.brand_950)
        val black = ContextCompat.getColor(context, R.color.black)
        val gray400 = ContextCompat.getColor(context, R.color.gray_400)
        val gray700 = ContextCompat.getColor(context, R.color.gray_700)


        when(rank){
            'A' -> {
                textView.background.setTint(brand100)
                textView.setTextColor(brand800)
            }
            'B' -> {
                textView.background.setTint(brand400)
                textView.setTextColor(white)
            }
            'C' -> {
                textView.background.setTint(brand700)
                textView.setTextColor(white)
            }
            'D' -> {
                textView.background.setTint(brand950)
                textView.setTextColor(white)
            }
            'E' -> {
                textView.background.setTint(white)
                textView.setTextColor(black)
            }
            'F' -> {
                textView.background.setTint(gray400)
                textView.setTextColor(white)
            }
            'G' -> {
                textView.background.setTint(gray700)
                textView.setTextColor(white)
            }
        }
    }

}