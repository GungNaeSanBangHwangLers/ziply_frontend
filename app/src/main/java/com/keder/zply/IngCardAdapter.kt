package com.keder.zply

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.keder.zply.databinding.ItemIngCardBinding
import java.text.SimpleDateFormat
import java.util.Locale

class IngCardAdapter(
    private val items: List<ScheduleItem>,
    private val onMeasureClick : (Int) -> Unit,
    // ★ [추가] 방향 정보 클릭 콜백 (HouseId 전달)
    private val onDirectionInfoClick: (Long) -> Unit
) : RecyclerView.Adapter<IngCardAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemIngCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ScheduleItem, position: Int) {
            val context = binding.root.context

            // ... (기존 코드: 랭크, 주소, 날짜 등 설정) ...
            val rankString = item.rankLabel
            val rankChar = if (rankString.isNotEmpty()) rankString[0] else '?'
            binding.ingCardRankTv.text = rankString
            setRankStyle(binding.ingCardRankTv, rankChar)
            binding.ingCardAddressTv.text = item.address
            val formattedDate = formatDate(item.time)
            binding.ingCardDateTv.text = "${formattedDate} 탐색 예정"

            // ... (기존 코드: 이미지 리사이클러뷰) ...
            if (item.imageList.isNotEmpty()) {
                binding.ingCardImgRv.visibility = View.VISIBLE
                val imgAdapter = IngImageAdapter(item.imageList)
                binding.ingCardImgRv.adapter = imgAdapter
                binding.ingCardImgRv.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            } else {
                binding.ingCardImgRv.visibility = View.GONE
            }

            // ... (기존 코드: 측정 상태 확인 및 배지 설정) ...
            val isMeasured = item.isMeasured || item.measuredAzimuths.isNotEmpty()
            // (색상 변수들... 생략)
            val brand200 = ContextCompat.getColor(context, R.color.brand_200)
            val brand700 = ContextCompat.getColor(context, R.color.brand_800)
            val gray400 = ContextCompat.getColor(context, R.color.gray_400)
            val gray600 = ContextCompat.getColor(context, R.color.gray_600)

            if (isMeasured) {
                val directionText = item.measuredAzimuths.joinToString(", ") { "${it}°" }
                binding.ingCardDirectionTv.text = directionText
                val lightText = "${String.format("%.1f", item.measuredLight)} lx"
                binding.ingCardLightTv.text = lightText

                binding.ingCardDirectionBadgeTv.backgroundTintList = ColorStateList.valueOf(brand200)
                binding.ingCardDirectionBadgeTv.setTextColor(brand700)
                binding.ingCardDirectionBadgeTv.text = "측정 완료"

                binding.ingCardLightBadgeTv.backgroundTintList = ColorStateList.valueOf(brand200)
                binding.ingCardLightBadgeTv.setTextColor(brand700)
                binding.ingCardLightBadgeTv.text = "측정 완료"
                binding.ingCardBtn.text = "다시 측정하기"
            } else {
                binding.ingCardDirectionBadgeTv.backgroundTintList = ColorStateList.valueOf(gray400)
                binding.ingCardDirectionBadgeTv.setTextColor(gray600)
                binding.ingCardDirectionBadgeTv.text = "방향 측정 미완료"

                binding.ingCardLightBadgeTv.backgroundTintList = ColorStateList.valueOf(gray400)
                binding.ingCardLightBadgeTv.setTextColor(gray600)
                binding.ingCardLightBadgeTv.text = "채광 측정 미완료"
            }

            // [기존] 측정 버튼 클릭
            binding.ingCardBtn.setOnClickListener {
                onMeasureClick(position)
            }

            // ★ [추가] 방향 정보 아이콘(ing_item_direction_iv) 클릭 리스너
            // XML에 해당 ID(ing_item_direction_iv)가 있다고 가정합니다.
            // 만약 ID가 다르다면 binding.아이디 로 변경해주세요.
            binding.ingItemDirectionIv.setOnClickListener {
                onDirectionInfoClick(item.houseId)
            }
        }
    }

    // ... (formatDate, setRankStyle, onCreateViewHolder 등 기존 함수 유지) ...
    private fun formatDate(inputDate: String): String {
        // 기존 코드 유지
        return try {
            val inputFormatStr = if (inputDate.contains("T")) "yyyy-MM-dd'T'HH:mm:ss" else "yyyy-MM-dd HH:mm:ss"
            val inputFormat = SimpleDateFormat(inputFormatStr, Locale.KOREA)
            val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
            val date = inputFormat.parse(inputDate)
            if (date != null) outputFormat.format(date) else inputDate
        } catch (e: Exception) { inputDate }
    }

    private fun setRankStyle(textView: TextView, rank: Char) {
        // 기존 코드 유지
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
        val binding = ItemIngCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size
}