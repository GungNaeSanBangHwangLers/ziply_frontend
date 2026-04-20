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

    private var favoriteSet: Set<Long> = emptySet()

    fun updateFavorites(newSet: Set<Long>) {
        this.favoriteSet = newSet
        notifyDataSetChanged()
    }

    fun setMode(isDay: Boolean) {
        this.isDayMode = isDay
        selectedPosition = -1
        onItemClick("")
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemGraphBarBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ScheduleItem, position: Int) {
            val context = binding.root.context

            val rankString = item.rankLabel
            val rankChar = if (rankString.isNotEmpty()) rankString[0] else '?'
            binding.graphRankTv.text = rankString

            val rankIndex = if (rankChar in 'A'..'Z') rankChar - 'A' else 7

            val score = if (isDayMode) item.dayScore else item.nightScore
            val desc = if (isDayMode) item.dayDesc else item.nightDesc
            binding.graphScoreTv.text = "${score}점"

            val maxBarHeightDp = 100
            val heightDp = if (score > 0) (score / 100.0 * maxBarHeightDp).toInt() else 4
            val params = binding.graphBarView.layoutParams
            params.height = dpToPx(context, heightDp)
            binding.graphBarView.layoutParams = params

            val rankColor = getRankColor(context, rankIndex)
            val textColor = getRankTextColor(context, rankIndex)

            val barDrawable = GradientDrawable()
            barDrawable.shape = GradientDrawable.RECTANGLE
            barDrawable.setColor(rankColor)
            barDrawable.cornerRadius = dpToPx(context, 10).toFloat()

            binding.graphBarView.backgroundTintList = null
            binding.graphBarView.background = barDrawable

            binding.graphRankTv.background.setTint(rankColor)
            binding.graphRankTv.setTextColor(textColor)

            binding.root.alpha = 1.0f

            val isSelectedItem = (selectedPosition == position)
            val isFavoriteItem = favoriteSet.contains(item.houseId)

            // ★ 해결 포인트: 회색 배경(선택)과 흰색 테두리(즐겨찾기)를 코드로 완벽하게 결합
            val combinedDrawable = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dpToPx(context, 16).toFloat() // 기존 배경의 둥글기 유지

                // 1. 터치 여부에 따른 배경색
                if (isSelectedItem) {
                    setColor(ContextCompat.getColor(context, R.color.gray_900))
                } else {
                    setColor(Color.TRANSPARENT)
                }

                // 2. 즐겨찾기 여부에 따른 테두리
                if (isFavoriteItem) {
                    setStroke(dpToPx(context, 2), Color.WHITE)
                } else {
                    setStroke(0, Color.TRANSPARENT)
                }
            }

            // 통합된 디자인을 내부 컨테이너에 적용하고, 최상위 루트 배경은 비움 (충돌 방지)
            binding.graphContentContainer.background = combinedDrawable
            binding.root.background = null

            if (isSelectedItem) {
                binding.graphScoreTv.setTextColor(Color.WHITE)
            } else {
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