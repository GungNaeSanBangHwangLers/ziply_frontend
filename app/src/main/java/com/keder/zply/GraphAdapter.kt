package com.keder.zply

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.keder.zply.databinding.ItemGraphBarBinding

class GraphAdapter(
    private val items: List<ScheduleItem>,
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<GraphAdapter.ViewHolder>() {

    private var isDayMode = true
    private var selectedPosition = -1

    fun setMode(isDay: Boolean) {
        this.isDayMode = isDay
        selectedPosition = -1
        onItemClick("")
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemGraphBarBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ScheduleItem, position: Int) {
            val context = binding.root.context

            // [수정] 1. 랭크 문자: item.rankLabel 사용
            val rankString = item.rankLabel
            val rankChar = if (rankString.isNotEmpty()) rankString[0] else '?'
            binding.graphRankTv.text = rankString

            // [수정] 2. 색상 인덱스 계산 (A -> 0, B -> 1 ...)
            // 기존에는 position을 썼지만, 이제는 랭크 알파벳을 숫자로 변환해서 씀
            val rankIndex = if (rankChar in 'A'..'Z') rankChar - 'A' else 7

            val score = if (isDayMode) item.dayScore else item.nightScore
            val desc = if (isDayMode) item.dayDesc else item.nightDesc
            binding.graphScoreTv.text = "${score}점"

            val maxBarHeightDp = 100
            val heightDp = if (score > 0) (score / 100.0 * maxBarHeightDp).toInt() else 4
            val params = binding.graphBarView.layoutParams
            params.height = dpToPx(context, heightDp)
            binding.graphBarView.layoutParams = params

            // 3. 색상 적용 시 rankIndex 사용
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

            // 4. 선택 상태 UI
            binding.root.alpha = 1.0f

            if (selectedPosition == position) {
                binding.root.setBackgroundResource(R.drawable.bg_graph)
                binding.graphScoreTv.setTextColor(Color.WHITE)
            } else {
                binding.root.setBackgroundColor(Color.TRANSPARENT)
                binding.graphScoreTv.setTextColor(ContextCompat.getColor(context, R.color.white))
            }

            binding.root.setOnClickListener {
                if (selectedPosition == position) {
                    selectedPosition = -1
                    onItemClick("")
                } else {
                    selectedPosition = position
                    onItemClick("[$rankChar] $desc")
                }
                notifyDataSetChanged()
            }
        }
    }

    // [유지] 함수 내부의 position 변수명은 그대로 둬도 되지만, 의미는 'Rank Index'임
    private fun getRankColor(context: Context, rankIndex: Int): Int {
        return when (rankIndex) {
            0 -> ContextCompat.getColor(context, R.color.brand_100)
            1 -> ContextCompat.getColor(context, R.color.brand_400)
            2 -> ContextCompat.getColor(context, R.color.brand_700)
            3 -> ContextCompat.getColor(context, R.color.brand_950)
            4 -> ContextCompat.getColor(context, R.color.white)
            5 -> ContextCompat.getColor(context, R.color.gray_400)
            6 -> ContextCompat.getColor(context, R.color.gray_700)
            else -> ContextCompat.getColor(context, R.color.white)
        }
    }

    private fun getRankTextColor(context: Context, rankIndex: Int): Int {
        return when (rankIndex) {
            0 -> ContextCompat.getColor(context, R.color.brand_800)
            4 -> ContextCompat.getColor(context, R.color.black)
            else -> ContextCompat.getColor(context, R.color.white)
        }
    }

    private fun dpToPx(context: Context, dp: Int): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemGraphBarBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size
}