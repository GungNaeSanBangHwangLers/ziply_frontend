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

    inner class ViewHolder(val binding: ItemGraphBarBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ScheduleItem, position: Int) {
            val context = binding.root.context

            // 1. 랭크 문자 설정 (A, B, C...)
            val rankChar = ('A'.code + position).toChar()
            binding.graphRankTv.text = rankChar.toString()

            // 2. 조도(lux) -> 점수 변환 로직 (기존 로직 유지)
            val lux = item.measuredLight
            val score = (lux / 10).toInt().coerceIn(0, 100)

            // 점수 텍스트 표시
            binding.graphScoreTv.text = "${score}점"

            // 3. [스타일 적용] 막대 높이 설정 (GraphAdapter와 동일한 계산 방식 적용)
            val maxBarHeightDp = 100
            // 점수가 0이어도 최소 4dp는 보이게 설정
            val heightDp = if (score > 0) (score / 100.0 * maxBarHeightDp).toInt() else 4

            val params = binding.graphBarView.layoutParams
            // ConstraintLayout Percent 대신 dp단위로 직접 높이 지정 (디자인 통일)
            params.height = dpToPx(context, heightDp)
            binding.graphBarView.layoutParams = params

            // 4. [스타일 적용] 색상 및 둥근 모서리(Radius) 적용
            val rankColor = getRankColor(context, position)
            val textColor = getRankTextColor(context, position)

            // 막대(Bar) 배경 생성 (색상 + 둥근 모서리)
            val drawable = GradientDrawable()
            drawable.shape = GradientDrawable.RECTANGLE
            drawable.setColor(rankColor)
            drawable.cornerRadius = dpToPx(context, 10).toFloat()

            // 기존 backgroundTintList 초기화 후 커스텀 drawable 적용
            binding.graphBarView.backgroundTintList = null
            binding.graphBarView.background = drawable

            // 랭크(원형) 배경 및 글자색 적용
            binding.graphRankTv.background.setTint(rankColor)
            binding.graphRankTv.setTextColor(textColor)

            // 점수 텍스트 색상 (기본 흰색/회색 처리, 필요시 수정)
            binding.graphScoreTv.setTextColor(ContextCompat.getColor(context, R.color.white))
        }
    }

    // [복사됨] 랭크별 색상 정의
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

    // [복사됨] 랭크별 글자 색상 (배경이 밝으면 검은색, 어두우면 흰색)
    private fun getRankTextColor(context: Context, position: Int): Int {
        return when (position) {
            0 -> ContextCompat.getColor(context, R.color.brand_800)
            4 -> ContextCompat.getColor(context, R.color.black)
            else -> ContextCompat.getColor(context, R.color.white)
        }
    }

    // [복사됨] dp -> px 변환
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