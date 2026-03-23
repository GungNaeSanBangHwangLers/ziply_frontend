package com.keder.zply

import android.content.res.ColorStateList
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
        fun bind(item: ScheduleItem, position: Int) { // position은 뱃지(최소/최대) 로직에만 사용
            val context = binding.root.context

            // 1. [핵심 수정] 랭크 설정 (position 기반 X -> item.rankLabel 기반 O)
            val rankString = item.rankLabel
            val rankChar = if (rankString.isNotEmpty()) rankString[0] else '?'

            binding.itemRankTv.text = rankString
            setRankStyle(binding.itemRankTv, rankChar)

            // 2. [핵심 수정] 실제 데이터 연결 (Mock Data 제거)
            // walkingTimeMin이 0일 경우를 대비해 예외 처리
            val timeText = if (item.walkingTimeMin > 0) "${item.walkingTimeMin}분" else "-"
            binding.itemMinuteTv.text = timeText

            // 3. 뱃지 로직 (정렬된 리스트이므로 position 0이 최소, 마지막이 최대가 맞음)
            if (position == 0) {
                // 가장 짧은 시간 (첫 번째 아이템)
                binding.itemMinMaxTv.visibility = View.VISIBLE
                binding.itemMinMaxTv.text = "최소"
                binding.itemMinMaxTv.setBackgroundResource(R.drawable.bg_badge_min)
                binding.itemMinMaxTv.setTextColor(ContextCompat.getColor(context, R.color.brand_800))
            } else if (position == items.size - 1 && items.size > 1) {
                // 가장 긴 시간 (마지막 아이템, 단 아이템이 2개 이상일 때만)
                binding.itemMinMaxTv.visibility = View.VISIBLE
                binding.itemMinMaxTv.text = "최대"
                binding.itemMinMaxTv.setBackgroundResource(R.drawable.bg_badge_max)
                binding.itemMinMaxTv.setTextColor(ContextCompat.getColor(context, R.color.error_800))
            } else {
                binding.itemMinMaxTv.visibility = View.GONE
            }

            // 4. 화살표 처리 (마지막 아이템엔 화살표 숨김)
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
        val gray200 = ContextCompat.getColor(context, R.color.gray_200)

        // setTint 대신 backgroundTintList 사용 (RecyclerView 재사용 문제 방지 및 호환성)
        when (rank) {
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

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemLengthRankBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size
}