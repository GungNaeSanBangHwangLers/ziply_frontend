package com.keder.zply

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.keder.zply.databinding.ItemLengthRankBinding

class LengthRankAdapter(
    private var items: List<ScheduleItem>
) : RecyclerView.Adapter<LengthRankAdapter.ViewHolder>() {

    private var currentMode: Int = 0 // 0: 도보, 1: 대중교통, 2: 자동차, 3: 자전거

    // ★ 1. 즐겨찾기 목록을 저장할 변수 추가
    private var favoriteSet: Set<Long> = emptySet()

    fun setMode(mode: Int) {
        this.currentMode = mode
        notifyDataSetChanged()
    }

    fun updateList(newList: List<ScheduleItem>) {
        this.items = newList
        notifyDataSetChanged()
    }

    // ★ 2. 뷰모델에서 즐겨찾기 목록이 바뀌면 어댑터를 새로고침하는 함수 추가
    fun updateFavorites(newSet: Set<Long>) {
        this.favoriteSet = newSet
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemLengthRankBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ScheduleItem, isMin: Boolean, isMax: Boolean, isLast: Boolean) {
            val context = binding.root.context

            // ==========================================
            // ★ 3. 즐겨찾기 유무에 따른 하얀 테두리 배경 적용
            // ==========================================
            if (favoriteSet.contains(item.houseId)) {
                // 즐겨찾기 된 아이템이면 하얀 테두리 배경 씌우기
                binding.rankCardLayout.setBackgroundResource(R.drawable.stroke_2dp_white)
            } else {
                // 아니면 배경을 투명하게(없게) 초기화
                binding.rankCardLayout.setBackgroundResource(R.drawable.gray_bg16)
            }

            // 1. 랭크 텍스트 & A~G 맞춤 색상 적용
            binding.itemRankTv.text = item.rankLabel
            setRankStyle(binding.itemRankTv, item.rankLabel)

            // 2. 마지막 아이템의 화살표(>) 숨기기
            if (isLast) {
                binding.itemArrowTv.visibility = View.GONE
            } else {
                binding.itemArrowTv.visibility = View.VISIBLE
            }

            // 3. 시간에 맞게 텍스트 설정
            val time = when (currentMode) {
                0 -> item.walkingTimeMin
                1 -> item.transitTimeMin
                2 -> item.carTimeMin
                3 -> item.bicycleTimeMin
                else -> item.walkingTimeMin
            }

            if (time > 0) {
                binding.itemMinuteTv.text = "${time}분"
            } else {
                binding.itemMinuteTv.text = "-"
            }

            // 4. 최소 / 최대 뱃지 처리
            if (time > 0 && isMin) {
                binding.itemMinMaxTv.visibility = View.VISIBLE
                binding.itemMinMaxTv.text = "최소"
                binding.itemMinMaxTv.setTextColor(ContextCompat.getColor(context, R.color.brand_800))
                binding.itemMinMaxTv.background.setTint(ContextCompat.getColor(context, R.color.brand_100))
            } else if (time > 0 && isMax) {
                binding.itemMinMaxTv.visibility = View.VISIBLE
                binding.itemMinMaxTv.text = "최대"
                binding.itemMinMaxTv.setTextColor(ContextCompat.getColor(context, R.color.error_800))
                binding.itemMinMaxTv.background.setTint(Color.parseColor("#FFE5E5")) // 연한 붉은색 배경
            } else {
                // 중간 값은 뱃지 숨김
                binding.itemMinMaxTv.visibility = View.INVISIBLE
            }
        }
    }

    // A~G 라벨별 색상을 지정하는 함수
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
        val validTimes = items.map {
            when (currentMode) {
                0 -> it.walkingTimeMin
                1 -> it.transitTimeMin
                2 -> it.carTimeMin
                3 -> it.bicycleTimeMin
                else -> it.walkingTimeMin
            }
        }.filter { it > 0 }

        val minTime = validTimes.minOrNull()
        val maxTime = validTimes.maxOrNull()

        val currentItemTime = when (currentMode) {
            0 -> items[position].walkingTimeMin
            1 -> items[position].transitTimeMin
            2 -> items[position].carTimeMin
            3 -> items[position].bicycleTimeMin
            else -> items[position].walkingTimeMin
        }

        val isMin = (currentItemTime == minTime) && (minTime != null) && (minTime != maxTime)
        val isMax = (currentItemTime == maxTime) && (maxTime != null) && (minTime != maxTime)

        val isLast = (position == items.size - 1)

        holder.bind(items[position], isMin, isMax, isLast)
    }

    override fun getItemCount(): Int = items.size
}