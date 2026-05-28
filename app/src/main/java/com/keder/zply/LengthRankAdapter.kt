package com.keder.zply

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat // ★ 스타일 동적 적용을 위한 필수 임포트
import androidx.recyclerview.widget.RecyclerView
import com.keder.zply.databinding.ItemLengthRankBinding

class LengthRankAdapter(
    private var items: List<ScheduleItem>
) : RecyclerView.Adapter<LengthRankAdapter.ViewHolder>() {

    private var currentMode: Int = 0 // 0: 도보, 1: 대중교통, 2: 자동차, 3: 자전거

    private var favoriteSet: Set<Long> = emptySet()
    private var cachedMinTime: Int? = null
    private var cachedMaxTime: Int? = null

    private fun getTimeByMode(item: ScheduleItem): Int = when (currentMode) {
        0 -> item.walkingTimeMin
        1 -> item.transitTimeMin
        2 -> item.carTimeMin
        3 -> item.bicycleTimeMin
        else -> item.walkingTimeMin
    }

    private fun recomputeMinMax() {
        val allTimes = items.map { getTimeByMode(it) }
        cachedMinTime = allTimes.minOrNull()
        cachedMaxTime = allTimes.maxOrNull()
    }

    fun setMode(mode: Int) {
        this.currentMode = mode
        recomputeMinMax()
        notifyItemRangeChanged(0, items.size)
    }

    fun updateList(newList: List<ScheduleItem>) {
        this.items = newList
        recomputeMinMax()
        notifyDataSetChanged()
    }

    fun updateFavorites(newSet: Set<Long>) {
        val oldSet = this.favoriteSet
        this.favoriteSet = newSet
        items.forEachIndexed { i, item ->
            if (oldSet.contains(item.houseId) != newSet.contains(item.houseId)) {
                notifyItemChanged(i)
            }
        }
    }

    inner class ViewHolder(val binding: ItemLengthRankBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ScheduleItem, time: Int, isMin: Boolean, isMax: Boolean, isLast: Boolean) {
            val context = binding.root.context

            if (favoriteSet.contains(item.houseId)) {
                binding.rankCardLayout.setBackgroundResource(R.drawable.stroke_2dp_white)
            } else {
                binding.rankCardLayout.setBackgroundResource(R.drawable.gray_bg16)
            }

            binding.itemRankTv.text = item.rankLabel
            setRankStyle(binding.itemRankTv, item.rankLabel)

            if (isLast) {
                binding.itemArrowTv.visibility = View.GONE
            } else {
                binding.itemArrowTv.visibility = View.VISIBLE
            }

            // ==========================================
            // ★ 수정: 시간에 따라 텍스트 및 TextAppearance 변경
            // ==========================================
            if (time == 0) {
                binding.itemMinuteTv.text = "도보가 더 빨라요"
                TextViewCompat.setTextAppearance(binding.itemMinuteTv, R.style.caption2_Semibold)
                binding.itemMinuteTv.setTextColor(ContextCompat.getColor(context, R.color.white))
            } else {
                binding.itemMinuteTv.text = "${time}분"
                TextViewCompat.setTextAppearance(binding.itemMinuteTv, R.style.caption1_Semibold)
                binding.itemMinuteTv.setTextColor(ContextCompat.getColor(context, R.color.white))
            }

            // ==========================================
            // 5. 최소 / 최대 뱃지 처리
            // ==========================================
            if (isMin) {
                binding.itemMinMaxTv.visibility = View.VISIBLE
                binding.itemMinMaxTv.text = "최소"
                binding.itemMinMaxTv.setTextColor(ContextCompat.getColor(context, R.color.brand_800))
                binding.itemMinMaxTv.background.setTint(ContextCompat.getColor(context, R.color.brand_100))
            } else if (isMax) {
                binding.itemMinMaxTv.visibility = View.VISIBLE
                binding.itemMinMaxTv.text = "최대"
                binding.itemMinMaxTv.setTextColor(ContextCompat.getColor(context, R.color.error_800))
                binding.itemMinMaxTv.background.setTint(Color.parseColor("#FFE5E5"))
            } else {
                binding.itemMinMaxTv.visibility = View.INVISIBLE
            }
        }
    }

    private fun setRankStyle(textView: TextView, rank: String) {
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

        val rankChar = if (rank.isNotEmpty()) rank[0] else '?'

        when (rankChar) {
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
        val binding = ItemLengthRankBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val currentItemTime = getTimeByMode(items[position])
        val minTime = cachedMinTime
        val maxTime = cachedMaxTime

        val isMin = (currentItemTime == minTime) && (minTime != null) && (minTime != maxTime)
        val isMax = (currentItemTime == maxTime) && (maxTime != null) && (minTime != maxTime)
        val isLast = (position == items.size - 1)

        holder.bind(items[position], currentItemTime, isMin, isMax, isLast)
    }

    override fun getItemCount(): Int = items.size
}