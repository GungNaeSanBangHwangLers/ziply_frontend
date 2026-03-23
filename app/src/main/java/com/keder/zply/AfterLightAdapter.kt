package com.keder.zply

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.keder.zply.databinding.ItemGraphBarBinding

class LightGraphAdapter(
    private val items: List<ScheduleItem>
) : RecyclerView.Adapter<LightGraphAdapter.ViewHolder>() {

    private var favoriteSet: Set<Long> = emptySet()

    fun updateFavorites(newSet: Set<Long>) {
        this.favoriteSet = newSet
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemGraphBarBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ScheduleItem) {
            val context = binding.root.context

            // 1. 랭크 문자 설정 (기존 로직 유지)
            val rankString = item.rankLabel
            binding.graphRankTv.text = rankString

            // 2. 색상 인덱스 계산 (기존 로직 유지)
            val rankChar = if (rankString.isNotEmpty()) rankString[0] else '?'
            val rankIndex = if (rankChar in 'A'..'Z') {
                rankChar - 'A'
            } else {
                7 // 예외 케이스
            }

            // ★ [수정됨] 점수 계산 로직만 변경 (나누기 10 제거)
            // 서버에서 이미 0~100점 사이의 점수를 주므로 그대로 사용
            val score = item.measuredLight.toInt().coerceIn(0, 100)
            binding.graphScoreTv.text = "${score}점"

            // 3. 막대 높이 설정 (기존 로직 유지)
            val maxBarHeightDp = 100
            val heightDp = if (score > 0) (score / 100.0 * maxBarHeightDp).toInt() else 4

            val params = binding.graphBarView.layoutParams
            params.height = dpToPx(context, heightDp)
            binding.graphBarView.layoutParams = params

            // 4. 색상 적용 (기존 로직 유지)
            val rankColor = getRankColor(context, rankIndex)
            val textColor = getRankTextColor(context, rankIndex)

            val drawable = GradientDrawable()
            drawable.shape = GradientDrawable.RECTANGLE
            drawable.setColor(rankColor)
            drawable.cornerRadius = dpToPx(context, 10).toFloat()

            binding.graphBarView.backgroundTintList = null
            binding.graphBarView.background = drawable

            binding.graphRankTv.background.setTint(rankColor)
            binding.graphRankTv.setTextColor(textColor)

            binding.graphScoreTv.setTextColor(ContextCompat.getColor(context, R.color.white))

            if (favoriteSet.contains(item.houseId)) {
                binding.root.setBackgroundResource(R.drawable.stroke_2dp_white)
            } else {
                binding.root.setBackgroundResource(0)
            }
        }
    }

    // [기존 코드 유지] 랭크별 색상
    private fun getRankColor(context: Context, rankIndex: Int): Int {
        return when (rankIndex) {
            0 -> ContextCompat.getColor(context, R.color.brand_100) // A
            1 -> ContextCompat.getColor(context, R.color.brand_400) // B
            2 -> ContextCompat.getColor(context, R.color.brand_700) // C
            3 -> ContextCompat.getColor(context, R.color.brand_950) // D
            4 -> ContextCompat.getColor(context, R.color.white)     // E
            5 -> ContextCompat.getColor(context, R.color.gray_400)  // F
            6 -> ContextCompat.getColor(context, R.color.gray_700)  // G
            else -> ContextCompat.getColor(context, R.color.gray_200)
        }
    }

    // [기존 코드 유지] 랭크별 글자 색상
    private fun getRankTextColor(context: Context, rankIndex: Int): Int {
        return when (rankIndex) {
            0 -> ContextCompat.getColor(context, R.color.brand_800) // A일 때
            4 -> ContextCompat.getColor(context, R.color.black)     // E(White)일 때
            else -> ContextCompat.getColor(context, R.color.white)
        }
    }

    // [기존 코드 유지] dp 변환
    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGraphBarBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}