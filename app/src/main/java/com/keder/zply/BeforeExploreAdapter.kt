package com.keder.zply

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.keder.zply.databinding.ItemExploreBinding
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

class BeforeExploreAdapter(private val items : List<ScheduleItem>) : RecyclerView.Adapter<BeforeExploreAdapter.ViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemExploreBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(
        val binding: ItemExploreBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        private val inputFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
        private val outputFormatter =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

        fun bind(item: ScheduleItem) {

            val rankString = item.rankLabel
            val rankChar = if (rankString.isNotEmpty()) rankString[0] else '?'

            binding.exploreRankTv.text = rankString
            setRankStyle(binding.exploreRankTv, rankChar)

            binding.exploreAddress.text = item.address

            // 시간 변환
            val formattedTime = try {
                val dateTime = LocalDateTime.parse(item.time, inputFormatter)
                dateTime.format(outputFormatter)
            } catch (e: Exception) {
                item.time // 파싱 실패 시 원본 표시
            }

            binding.exploreDateTv.text = "$formattedTime 탐색"

            binding.exploreWriteIv.setOnClickListener {
                // 수정 아이콘 클릭 이벤트
            }
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
        val gray200 = ContextCompat.getColor(context, R.color.gray_200)

        // backgroundTintList 사용 권장
        when(rank){
            'A' -> { textView.backgroundTintList = ColorStateList.valueOf(brand100); textView.setTextColor(brand800) }
            'B' -> { textView.backgroundTintList = ColorStateList.valueOf(brand400); textView.setTextColor(white) }
            'C' -> { textView.backgroundTintList = ColorStateList.valueOf(brand700); textView.setTextColor(white) }
            'D' -> { textView.backgroundTintList = ColorStateList.valueOf(brand950); textView.setTextColor(white) }
            'E' -> { textView.backgroundTintList = ColorStateList.valueOf(white); textView.setTextColor(black) }
            'F' -> { textView.backgroundTintList = ColorStateList.valueOf(gray400); textView.setTextColor(white) }
            'G' -> { textView.backgroundTintList = ColorStateList.valueOf(gray700); textView.setTextColor(white) }
            else -> { textView.backgroundTintList = ColorStateList.valueOf(gray200); textView.setTextColor(black) }
        }
    }
}