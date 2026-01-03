package com.keder.zply

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.keder.zply.databinding.AfterExploreCardBinding

class AfterCardAdapter(private val items: List<ScheduleItem>) : RecyclerView.Adapter<AfterCardAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: AfterExploreCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ScheduleItem, position: Int) {
            val context = binding.root.context

            // 1. 랭크 및 텍스트 설정
            val rankChar = ('A'.code + position).toChar()
            binding.exploreRankTv.text = rankChar.toString()
            setRankStyle(binding.exploreRankTv, rankChar)

            binding.exploreAddress.text = item.address
            binding.exploreDateTv.text = "${item.time} 탐색"

            if (item.imageList.isNotEmpty()) {
                // 사진이 있으면 공간 보이기
                binding.afterCardImgRv.visibility = View.VISIBLE

                val imgAdapter = IngImageAdapter(item.imageList)
                binding.afterCardImgRv.adapter = imgAdapter
                binding.afterCardImgRv.layoutManager =
                    LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            } else {
                binding.afterCardImgRv.visibility = View.GONE
            }
        }
    }

    private fun setRankStyle(textView: TextView, rank: Char) {
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
        val gray200 = ContextCompat.getColor(context, R.color.gray_200)

        when (rank) {
            'A' -> { textView.background.setTint(brand100); textView.setTextColor(brand800) }
            'B' -> { textView.background.setTint(brand400); textView.setTextColor(white) }
            'C' -> { textView.background.setTint(brand700); textView.setTextColor(white) }
            'D' -> { textView.background.setTint(brand950); textView.setTextColor(white) }
            'E' -> { textView.background.setTint(white); textView.setTextColor(black) }
            'F' -> { textView.background.setTint(gray400); textView.setTextColor(white) }
            'G' -> { textView.background.setTint(gray700); textView.setTextColor(white) }
            else -> { textView.background.setTint(gray200); textView.setTextColor(black) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = AfterExploreCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size
}