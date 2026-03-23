package com.keder.zply

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.keder.zply.databinding.AfterExploreCardBinding
import java.text.SimpleDateFormat
import java.util.Locale

class AfterCardAdapter(private val items: List<ScheduleItem>) : RecyclerView.Adapter<AfterCardAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: AfterExploreCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ScheduleItem) { // [수정] position 제거
            val context = binding.root.context

            // [수정] position 대신 item.rankLabel 사용
            // 예: "A" -> 'A'
            val rankString = item.rankLabel
            val rankChar = if (rankString.isNotEmpty()) rankString[0] else '?'

            binding.exploreRankTv.text = rankString
            setRankStyle(binding.exploreRankTv, rankChar)

            binding.exploreAddress.text = item.address
            val formattedDate = formatDate(item.time)
            binding.exploreDateTv.text = "$formattedDate 탐색"

            if (item.imageList.isNotEmpty()) {
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

    private fun formatDate(inputDate: String): String {
        return try {
            // 입력: 2026-03-10T14:00:00
            // T가 포함된 경우와 아닌 경우 모두 대응
            val inputFormatStr = if (inputDate.contains("T")) "yyyy-MM-dd'T'HH:mm:ss" else "yyyy-MM-dd HH:mm:ss"

            val inputFormat = SimpleDateFormat(inputFormatStr, Locale.KOREA)
            val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA) // 출력: 2026-03-10 14:00

            val date = inputFormat.parse(inputDate)
            if (date != null) outputFormat.format(date) else inputDate
        } catch (e: Exception) {
            // 파싱 실패 시 원본 그대로 반환 (앱 죽음 방지)
            inputDate
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
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}