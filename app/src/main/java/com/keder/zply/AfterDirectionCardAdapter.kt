package com.keder.zply

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.keder.zply.databinding.ItemAfterDirectionBinding

class AfterDirectionCardAdapter(
    private val houseList: List<HouseDirectionGroupResponse>
) : RecyclerView.Adapter<AfterDirectionCardAdapter.ViewHolder>() {

    private var favoriteSet: Set<Long> = emptySet()

    fun updateFavorites(newSet: Set<Long>) {
        this.favoriteSet = newSet
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemAfterDirectionBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(house: HouseDirectionGroupResponse) {

            // A, B, C 등 라벨 텍스트 세팅
            binding.itemRankTv.text = house.houseAlias
            // ★ 색상 적용 함수 호출
            setRankStyle(binding.itemRankTv, house.houseAlias)

            val windows = house.windows
            if (windows.isEmpty()) {
                binding.afterRoomsRv.visibility = View.INVISIBLE
                binding.itemDirectionTv.text = "미측정"
                binding.itemDirectionDesTv.text = "방향 측정이 진행되지 않았어요."
                binding.itemGoodLl.visibility = View.GONE
                binding.itemBadLl.visibility = View.GONE
            } else {
                binding.afterRoomsRv.visibility = View.VISIBLE
                binding.itemGoodLl.visibility = View.VISIBLE
                binding.itemBadLl.visibility = View.VISIBLE

                // 방 버튼 리사이클러뷰 세팅
                val roomAdapter = AfterRoomAdapter(windows) { selectedWindow ->
                    // 방 누르면 서버에서 통째로 준 정보(장/단점) 바로 교체!
                    updateDirectionContent(binding, selectedWindow)
                }
                binding.afterRoomsRv.layoutManager = LinearLayoutManager(binding.root.context, LinearLayoutManager.HORIZONTAL, false)
                binding.afterRoomsRv.adapter = roomAdapter

                // 첫 번째 방 기본 출력
                updateDirectionContent(binding, windows[0])
            }

            // 즐겨찾기(별표) 테두리 세팅
            if (favoriteSet.contains(house.houseId)) {
                binding.root.setBackgroundResource(R.drawable.stroke_2dp_white)
            } else {
                binding.root.setBackgroundResource(R.drawable.gray_bg16) // 방향 카드의 기본 원래 배경
            }
        }

        private fun updateDirectionContent(binding: ItemAfterDirectionBinding, window: DirectionWindowResponse) {
            binding.itemDirectionTv.text = window.directionType
            binding.itemDirectionDesTv.text = window.features
            binding.itemGoodDesTv.text = window.pros
            binding.itemBadDesTv.text = window.cons
        }
    }

    // ==========================================================
    // ★ 랭크 라벨 뷰 디자인 적용 함수
    // ==========================================================
    private fun setRankStyle(textView: TextView, rank: String) {
        if (rank.isEmpty()) return

        val context = textView.context
        val rankChar = rank[0] // 'A', 'B', 'C' 등

        // A는 0, B는 1 ... G는 6으로 변환
        val rankIndex = if (rankChar in 'A'..'G') rankChar - 'A' else -1

        val bgColor = getRankColor(context, rankIndex)
        val textColor = getRankTextColor(context, rankIndex)

        // 배경색과 글자색 적용
        textView.backgroundTintList = ColorStateList.valueOf(bgColor)
        textView.setTextColor(textColor)
    }

    // 랭크별 배경색
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

    // 랭크별 글자색
    private fun getRankTextColor(context: Context, rankIndex: Int): Int {
        return when (rankIndex) {
            0 -> ContextCompat.getColor(context, R.color.brand_800) // A일 때
            4 -> ContextCompat.getColor(context, R.color.black)     // E(White)일 때
            else -> ContextCompat.getColor(context, R.color.white)  // 나머지
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAfterDirectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(houseList[position])
    }

    override fun getItemCount(): Int = houseList.size
}