package com.keder.zply

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.keder.zply.databinding.AfterExploreCardBinding

class AfterCardAdapter(
    private val items: List<ScheduleItem>,
    private var favoriteSet: Set<Long> = emptySet(),
    private val onStarClick: (Long) -> Unit,
    private val onImageClick: (ScheduleItem, Int) -> Unit // 사진 다이얼로그용 클릭 리스너
) : RecyclerView.Adapter<AfterCardAdapter.ViewHolder>() {

    fun updateFavorites(newSet: Set<Long>) {
        this.favoriteSet = newSet
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: AfterExploreCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ScheduleItem) {
            val context = binding.root.context

            // 1. 라벨 색상 및 텍스트 적용
            binding.exploreRankTv.text = item.rankLabel
            setRankStyle(binding.exploreRankTv, item.rankLabel)

            binding.exploreDateTv.text = "${item.time} 탐색"
            binding.exploreAddress.text = item.address

            val isFavorite = favoriteSet.contains(item.houseId)
            if (isFavorite) {
                binding.exploreStarIv.setImageResource(R.drawable.ic_star_filled)
                binding.root.setBackgroundResource(R.drawable.stroke_2dp_white)
            } else {
                binding.exploreStarIv.setImageResource(R.drawable.ic_star)
                binding.root.setBackgroundResource(R.drawable.gray_bg)
            }

            // ★ 별자리(별 이미지) 클릭 이벤트
            binding.exploreStarIv.setOnClickListener {
                onStarClick(item.houseId)
            }

            // 2. 사진 리스트 유무에 따른 상태 전환
            if (item.imageList.isEmpty()) {
                binding.afterCardImgRv.visibility = View.GONE
            } else {
                binding.afterCardImgRv.visibility = View.VISIBLE
                binding.afterCardImgRv.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)

                binding.afterCardImgRv.adapter = IngImageAdapter(item.imageList) { clickedIndex ->
                    // ★ 클릭된 아이템 정보와 몇 번째 사진인지(clickedIndex) 함께 전달
                    onImageClick(item, clickedIndex)
                }
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
        val binding = AfterExploreCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}