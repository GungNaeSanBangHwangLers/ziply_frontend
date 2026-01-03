package com.keder.zply

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.marginStart
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.keder.zply.databinding.ItemIngCardBinding

class IngCardAdapter(
    private val items: List<ScheduleItem>,
    private val onMeasureClick : (Int) -> Unit
) : RecyclerView.Adapter<IngCardAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemIngCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ScheduleItem, position: Int) {
            val context = binding.root.context
            // 1. 랭크 문자 계산 (A, B, C...)
            val rankChar = ('A'.code + position).toChar()
            binding.ingCardRankTv.text = rankChar.toString()

            setRankStyle(binding.ingCardRankTv, rankChar)

            binding.ingCardAddressTv.text = item.address
            binding.ingCardDateTv.text = "${item.time} 탐색 예정"

            if (item.imageList.isNotEmpty()) {
                // 이미지가 있으면 보여줌
                binding.ingCardImgRv.visibility = View.VISIBLE

                val imgAdapter = IngImageAdapter(item.imageList)
                binding.ingCardImgRv.adapter = imgAdapter
                binding.ingCardImgRv.layoutManager =
                    LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            } else {
                // ★ 이미지가 없으면 아예 숨겨서(GONE) 공간을 없앰 ★
                binding.ingCardImgRv.visibility = View.GONE
            }

            val isMeasured = item.isMeasured || item.measuredAzimuths.isNotEmpty()

            val brand200 = ContextCompat.getColor(context, R.color.brand_200)
            val brand700 = ContextCompat.getColor(context, R.color.brand_800)

            val gray400 = ContextCompat.getColor(context, R.color.gray_400)
            val gray600 = ContextCompat.getColor(context, R.color.gray_600)

            if (isMeasured) {
                // --- 측정 완료 상태 ---
                val directionText = item.measuredAzimuths.joinToString(", ") { "${it}°" }
                binding.ingCardDirectionTv.text = directionText


                val lightText = "${String.format("%.1f", item.measuredLight)} lx"
                binding.ingCardLightTv.text = lightText


                // 방향 배지: 파란 배경 + 파란 글씨
                binding.ingCardDirectionBadgeTv.backgroundTintList = ColorStateList.valueOf(brand200)
                binding.ingCardDirectionBadgeTv.setTextColor(brand700)
                binding.ingCardDirectionBadgeTv.text = "측정 완료"

                // 채광 배지: 파란 배경 + 파란 글씨
                binding.ingCardLightBadgeTv.backgroundTintList = ColorStateList.valueOf(brand200)
                binding.ingCardLightBadgeTv.setTextColor(brand700)
                binding.ingCardLightBadgeTv.text = "측정 완료"

                binding.ingCardBtn.text = "다시 측정하기"
            } else {

                // 방향 배지: 회색 배경 + 회색 글씨
                binding.ingCardDirectionBadgeTv.backgroundTintList = ColorStateList.valueOf(gray400)
                binding.ingCardDirectionBadgeTv.setTextColor(gray600)
                binding.ingCardDirectionBadgeTv.text = "방향 측정 미완료"

                // 채광 배지: 회색 배경 + 회색 글씨
                binding.ingCardLightBadgeTv.backgroundTintList = ColorStateList.valueOf(gray400)
                binding.ingCardLightBadgeTv.setTextColor(gray600)
                binding.ingCardLightBadgeTv.text = "채광 측정 미완료"
            }

            // 5. 버튼 클릭 리스너 (Position 전달)
            binding.ingCardBtn.setOnClickListener {
                onMeasureClick(position) // [수정] position 전달
            }
        }
    }

    private fun setRankStyle(textView: TextView, rank: Char) {
        val context = textView.context

        // colors.xml에 해당 색상들이 정의되어 있어야 합니다.
        val brand100 = ContextCompat.getColor(context, R.color.brand_100)
        val brand800 = ContextCompat.getColor(context, R.color.brand_800)
        val brand400 = ContextCompat.getColor(context, R.color.brand_400)
        val white = ContextCompat.getColor(context, R.color.white)
        val brand700 = ContextCompat.getColor(context, R.color.brand_700)
        val brand950 = ContextCompat.getColor(context, R.color.brand_950)
        val black = ContextCompat.getColor(context, R.color.black)
        val gray400 = ContextCompat.getColor(context, R.color.gray_400)
        val gray700 = ContextCompat.getColor(context, R.color.gray_700)
        val gray200 = ContextCompat.getColor(context, R.color.gray_200) // 기본값용

        when (rank) {
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
            else -> {
                // 안전장치: 예상치 못한 랭크일 경우 기본색(회색 등)으로 초기화
                textView.background.setTint(gray200)
                textView.setTextColor(black)
            }
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