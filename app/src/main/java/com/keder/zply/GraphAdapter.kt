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
    // [수정 1] String? 대신 String (Non-nullable) 사용
    private val onItemClick: (String) -> Unit
) : RecyclerView.Adapter<GraphAdapter.ViewHolder>() {

    private var isDayMode = true
    private var selectedPosition = -1

    fun setMode(isDay: Boolean) {
        this.isDayMode = isDay
        selectedPosition = -1
        // [수정 1] null 대신 빈 문자열 전달
        onItemClick("")
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemGraphBarBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ScheduleItem, position: Int) {
            val context = binding.root.context

            // 1. 랭크 문자, 점수, 막대 높이 설정 (기존 동일)
            val rankChar = ('A'.code + position).toChar()
            binding.graphRankTv.text = rankChar.toString()

            val score = if (isDayMode) item.dayScore else item.nightScore
            val desc = if (isDayMode) item.dayDesc else item.nightDesc
            binding.graphScoreTv.text = "${score}점"

            val maxBarHeightDp = 100
            val heightDp = if (score > 0) (score / 100.0 * maxBarHeightDp).toInt() else 4
            val params = binding.graphBarView.layoutParams
            params.height = dpToPx(context, heightDp)
            binding.graphBarView.layoutParams = params

            // 2. 색상 및 Radius 적용 (기존 동일)
            val rankColor = getRankColor(context, position)
            val textColor = getRankTextColor(context, position)

            val drawable = GradientDrawable()
            drawable.shape = GradientDrawable.RECTANGLE
            drawable.setColor(rankColor)
            drawable.cornerRadius = dpToPx(context, 10).toFloat()

            binding.graphBarView.backgroundTintList = null
            binding.graphBarView.background = drawable

            binding.graphRankTv.background.setTint(rankColor)
            binding.graphRankTv.setTextColor(textColor)

            // 3. [수정됨] 선택 상태 UI 로직
            // 투명도(alpha) 조절 코드 삭제 -> 항상 1.0f 유지
            binding.root.alpha = 1.0f

            if (selectedPosition == position) {
                // [선택됨]
                // 배경을 어두운 색으로 설정
                binding.root.setBackgroundResource(R.drawable.bg_graph)
                // 점수 글자색 흰색으로
                binding.graphScoreTv.setTextColor(Color.WHITE)
            } else {
                // [선택 안 됨 (기본 상태)]
                // 배경 투명
                binding.root.setBackgroundColor(Color.TRANSPARENT)
                // 점수 글자색 회색으로 (어두워지는 효과 제거했으므로 기본 회색 유지)
                binding.graphScoreTv.setTextColor(ContextCompat.getColor(context, R.color.white))
            }

            // 4. 클릭 이벤트 (기존 동일)
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
    // 랭크별 색상 정의 (BeforeExploreAdapter와 통일)
    private fun getRankColor(context: Context, position: Int): Int {
        return when (position) {
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

    // 랭크별 글자 색상 (배경이 밝으면 검은색, 어두우면 흰색)
    private fun getRankTextColor(context: Context, position: Int): Int {
        return when (position) {
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
        // [오류 해결] 여기서 items[position]과 position을 정확히 넘겨줍니다.
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size
}