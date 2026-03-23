package com.keder.zply

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.keder.zply.databinding.ItemExistingInfoCardBinding // XML 이름에 맞게 변경하세요

class ExistingInfoCardAdapter(
    private val items: List<ScheduleItem>
) : RecyclerView.Adapter<ExistingInfoCardAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemExistingInfoCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ScheduleItem) {
            val context = binding.root.context

            // 1. 라벨 색상 및 텍스트 적용
            binding.cardRankTv.text = item.rankLabel
            setRankStyle(binding.cardRankTv, item.rankLabel)

            binding.cardDateTv.text = "${item.time} 탐색"
            binding.cardAddressTv.text = item.address

            // 2. 하단 까만 배지 텍스트 조합 (방 갯수 + 채광)
            val roomText = if (item.measuredRoomCount > 0) "방 ${item.measuredRoomCount}개 향 측정" else "방향 미측정"
            val lightText = if (item.measuredLightLux >= 0f) "채광 ${item.measuredLightLux.toInt()} lx" else "채광 미측정"
            binding.cardMeasureSummaryTv.text = "$roomText • $lightText"

            // 3. 사진 리스트 유무에 따른 상태 전환 및 외부 어댑터(IngImageAdapter) 연결
            if (item.imageList.isNotEmpty()) {
                binding.cardImgRv.visibility = View.VISIBLE
                binding.cardEmptyImgTv.visibility = View.GONE

                binding.cardImgRv.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                binding.cardImgRv.adapter = IngImageAdapter(item.imageList)
            } else {
                binding.cardImgRv.visibility = View.GONE
                binding.cardEmptyImgTv.visibility = View.VISIBLE
            }
        }
    }

    // A~G별 색상 입히는 함수
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
        // 본인의 XML 파일명(Binding 클래스명)에 맞는지 확인해주세요!
        val binding = ItemExistingInfoCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}