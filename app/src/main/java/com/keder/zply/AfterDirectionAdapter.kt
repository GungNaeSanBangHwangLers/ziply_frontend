package com.keder.zply

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.keder.zply.databinding.ItemAfterDirectionBinding

// [중요] 클래스 밖, 파일 최상단에 정의
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

            // 랭크 1
            if (item.ranks.isNotEmpty()) {
                binding.itemRankTv.visibility = View.VISIBLE
                binding.itemRankTv.text = item.ranks[0].toString()
                setRankStyle(binding.itemRankTv, item.ranks[0])
            } else {
                binding.itemRankTv.visibility = View.GONE
            }

            // 랭크 2
            if (item.ranks.size > 1) {
                binding.itemRank2Tv.visibility = View.VISIBLE
                binding.itemRank2Tv.text = item.ranks[1].toString()
                setRankStyle(binding.itemRank2Tv, item.ranks[1])
            } else {
                binding.itemRank2Tv.visibility = View.GONE
            }
        }
    }

    private fun setRankStyle(textView: TextView, rank: Char) {
        val context = textView.context
        // (색상 리소스가 프로젝트에 존재하는지 확인 필요)
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