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
    private val onImageClick: (ScheduleItem) -> Unit // 사진 다이얼로그용 클릭 리스너
) : RecyclerView.Adapter<AfterCardAdapter.ViewHolder>() {

    fun updateFavorites(newSet: Set<Long>) {
        this.favoriteSet = newSet
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: AfterExploreCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ScheduleItem) {
            val context = binding.root.context

            // 1. 라벨 색상 및 텍스트 적용 (새 XML의 ID 사용: exploreRankTv)
            binding.exploreRankTv.text = item.rankLabel
            setRankStyle(binding.exploreRankTv, item.rankLabel)

            binding.exploreDateTv.text = "${item.time} 탐색"
            binding.exploreAddress.text = item.address

            val isFavorite = favoriteSet.contains(item.houseId)
            if (isFavorite) {
                binding.exploreStarIv.setImageResource(R.drawable.ic_star_filled) // 채워진 별 (본인의 파일명에 맞게 수정)
                binding.root.setBackgroundResource(R.drawable.stroke_2dp_white)   // 하얀 테두리 배경
            } else {
                binding.exploreStarIv.setImageResource(R.drawable.ic_star)        // 빈 별
                binding.root.setBackgroundResource(R.drawable.gray_bg)            // 원래 배경 (XML 기존 배경에 맞게 수정)
            }

            // ★ 별자리(별 이미지) 클릭 이벤트
            binding.exploreStarIv.setOnClickListener {
                onStarClick(item.houseId)
            }

            // 2. 사진 리스트 유무에 따른 상태 전환
            if (item.imageList.isEmpty()) {
                // 사진이 없으면 리사이클러뷰 전체를 깔끔하게 숨깁니다.
                binding.afterCardImgRv.visibility = View.GONE
            } else {
                // 사진이 있으면 나타내고, Glide 어댑터 연결
                binding.afterCardImgRv.visibility = View.VISIBLE

                binding.afterCardImgRv.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                binding.afterCardImgRv.adapter = IngImageAdapter(item.imageList)

                // ★ 사진 영역 터치 시 다이얼로그 띄우기
                binding.afterCardImgRv.setOnTouchListener { _, event ->
                    if (event.action == android.view.MotionEvent.ACTION_UP) {
                        onImageClick(item)
                    }
                    false
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