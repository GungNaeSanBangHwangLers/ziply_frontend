package com.keder.zply

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.keder.zply.databinding.ItemAfterDirectionBinding

// 데이터 클래스
data class DirectionGroupItem(
    val direction: String,
    val ranks: List<Char>,
    val desc: String,
    val goodPoints: String,
    val badPoints: String
)

class AfterDirectionAdapter(
    private val items: List<DirectionGroupItem>
) : RecyclerView.Adapter<AfterDirectionAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemAfterDirectionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: DirectionGroupItem) {
            binding.itemDirectionTv.text = item.direction
            binding.itemDirectionDesTv.text = item.desc
            binding.itemGoodDesTv.text = item.goodPoints
            binding.itemBadDesTv.text = item.badPoints

            // ★ [핵심 수정] 기존 뷰 초기화 (재사용 문제 방지)
            binding.rankContainerLl.removeAllViews()

            // ★ 리스트에 있는 만큼 반복해서 뷰 생성
            val context = binding.root.context
            val inflater = LayoutInflater.from(context)

            item.ranks.forEach { rankChar ->
                // 1. item_rank_badge.xml을 inflate(생성)
                val badgeView = inflater.inflate(R.layout.item_direction_badge, binding.rankContainerLl, false) as TextView

                // 2. 텍스트 설정
                badgeView.text = rankChar.toString()

                // 3. 색상 설정 (기존 로직 활용)
                setRankStyle(badgeView, rankChar)

                // 4. 컨테이너에 추가
                binding.rankContainerLl.addView(badgeView)
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
        val binding = ItemAfterDirectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}