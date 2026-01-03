package com.keder.zply

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.keder.zply.databinding.ItemLengthRankBinding

class LengthRankAdapter(private val items: List<ScheduleItem>) :
    RecyclerView.Adapter<LengthRankAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemLengthRankBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ScheduleItem, position: Int) {
            val context = binding.root.context

            // 랭크 설정
            val rankChar = ('A'.code + position).toChar()
            binding.itemRankTv.text = rankChar.toString()
            setRankStyle(binding.itemRankTv, rankChar)

            // 시간 (Mock data)
            val mockTime = (position + 1) * 10
            binding.itemMinuteTv.text = "${mockTime}분"

            // 뱃지 로직
            if (position == 0) {
                binding.itemMinMaxTv.visibility = View.VISIBLE
                binding.itemMinMaxTv.text = "최소"
                binding.itemMinMaxTv.setBackgroundResource(R.drawable.bg_badge_min)
                binding.itemMinMaxTv.setTextColor(ContextCompat.getColor(context, R.color.brand_800))
            } else if (position == items.size - 1 && items.size > 1) {
                binding.itemMinMaxTv.visibility = View.VISIBLE
                binding.itemMinMaxTv.text = "최대"
                binding.itemMinMaxTv.setBackgroundResource(R.drawable.bg_badge_max)
                binding.itemMinMaxTv.setTextColor(ContextCompat.getColor(context, R.color.error_800))
            } else {
                binding.itemMinMaxTv.visibility = View.GONE
            }

            // 화살표 처리
            if (position == items.size - 1) {
                binding.itemArrowTv.visibility = View.GONE
            } else {
                binding.itemArrowTv.visibility = View.VISIBLE
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

        when (rank) {
            'A' -> { textView.background.setTint(brand100); textView.setTextColor(brand800) }
            'B' -> { textView.background.setTint(brand400); textView.setTextColor(white) }
            'C' -> { textView.background.setTint(brand700); textView.setTextColor(white) }
            'D' -> { textView.background.setTint(brand950); textView.setTextColor(white) }
            'E' -> { textView.background.setTint(white); textView.setTextColor(black) }
            'F' -> { textView.background.setTint(gray400); textView.setTextColor(white) }
            'G' -> { textView.background.setTint(gray700); textView.setTextColor(white) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLengthRankBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size
}