package com.keder.zply

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.keder.zply.databinding.ItemChecklistGroupBinding
import com.keder.zply.databinding.ItemChecklistHouseBinding
import com.keder.zply.databinding.ItemMainCardBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// ==========================================
// 1. 상단 메인 카드 어댑터 (가로 스크롤)
// ==========================================
class MainCardRVAdapter(
    private var items: List<MainCardData>,
    private val onItemClick : (MainCardData, ExploreStatus) -> Unit
) : RecyclerView.Adapter<MainCardRVAdapter.Holder>(){

    fun updateList(newItems: List<MainCardData>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemMainCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class Holder(private val binding : ItemMainCardBinding) : RecyclerView.ViewHolder(binding.root){
        fun bind(item: MainCardData){
            binding.tvDate.text = item.date
            binding.tvLocation.text = "현재 ${item.location} 인근"

            val currentStatus = calculateStatusFromDateText(item.date)

            val statusText = when(currentStatus){
                ExploreStatus.ING -> "${item.count}개 주거를 탐색 중이에요"
                ExploreStatus.AFTER -> "${item.count}개 주거를 탐색 완료했어요"
                ExploreStatus.BEFORE -> "${item.count}개 주거를 탐색할 예정이에요"
            }
            binding.tvCountDesc.text = statusText

            val bgResId = when(currentStatus) {
//                ExploreStatus.ING -> R.drawable.gradient_card_ing       // 진행 중
//                ExploreStatus.BEFORE -> R.drawable.gradient_card_will   // 진행 예정
//                ExploreStatus.AFTER -> R.drawable.gradient_card_ed      // 종료
                ExploreStatus.ING -> R.drawable.bg_brand_800       // 진행 중
                ExploreStatus.BEFORE -> R.drawable.bg_brand_800  // 진행 예정
                ExploreStatus.AFTER -> R.drawable.bg_gray_600     // 종료
            }

            binding.exploreCardCl.backgroundTintList = null
            binding.exploreCardCl.setBackgroundResource(bgResId)

            binding.root.setOnClickListener{
                onItemClick(item, currentStatus)
            }
        }

        private fun calculateStatusFromDateText(dateString: String): ExploreStatus {
            return try {
                val parts = dateString.split("~").map { it.trim() }
                if (parts.isEmpty()) return ExploreStatus.BEFORE

                val startDateStr = parts[0]
                val endDateStr = if (parts.size > 1) parts[1] else startDateStr

                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
                val todayFormat = SimpleDateFormat("yyyyMMdd", Locale.KOREA)

                val start = sdf.parse(startDateStr) ?: return ExploreStatus.BEFORE
                val end = sdf.parse(endDateStr) ?: start
                val today = Date()

                val startInt = todayFormat.format(start).toInt()
                val endInt = todayFormat.format(end).toInt()
                val todayInt = todayFormat.format(today).toInt()

                when {
                    todayInt < startInt -> ExploreStatus.BEFORE
                    todayInt > endInt -> ExploreStatus.AFTER
                    else -> ExploreStatus.ING
                }
            } catch (e: Exception) {
                ExploreStatus.BEFORE
            }
        }
    }
}

enum class ExploreStatus {
    BEFORE, // 탐색 예정
    ING,    // 탐색 중
    AFTER   // 탐색 완료
}

// ==========================================
// 2. 하단 체크리스트 그룹 어댑터 (날짜 표시용)
// ==========================================
class ChecklistGroupAdapter(private var groups: List<ChecklistGroupResponse>,
                            private val onHouseClick: (ChecklistHouseResponse) -> Unit) : RecyclerView.Adapter<ChecklistGroupAdapter.GroupViewHolder>() {

    fun updateData(newGroups: List<ChecklistGroupResponse>) {
        this.groups = newGroups
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GroupViewHolder {
        val binding = ItemChecklistGroupBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return GroupViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GroupViewHolder, position: Int) {
        val group = groups[position]

        // 날짜 포맷 변환 (2026-03-23 -> 2026년 03월 23일)
        val parts = group.date.split("-")
        if (parts.size == 3) {
            holder.binding.tvGroupDate.text = "${parts[0]}년 ${parts[1]}월 ${parts[2]}일"
        } else {
            holder.binding.tvGroupDate.text = group.date
        }

        val houseAdapter = ChecklistHouseAdapter(group.houses, onHouseClick)
        holder.binding.rvGroupHouses.layoutManager = LinearLayoutManager(holder.binding.root.context)
        holder.binding.rvGroupHouses.adapter = houseAdapter
    }

    override fun getItemCount() = groups.size
    inner class GroupViewHolder(val binding: ItemChecklistGroupBinding) : RecyclerView.ViewHolder(binding.root)
}

// ==========================================
// 3. 하단 체크리스트 하우스 어댑터 (집 목록 표시용)
// ==========================================
class ChecklistHouseAdapter(private val houses: List<ChecklistHouseResponse>,
                            private val onHouseClick: (ChecklistHouseResponse) -> Unit) : RecyclerView.Adapter<ChecklistHouseAdapter.HouseViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HouseViewHolder {
        val binding = ItemChecklistHouseBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return HouseViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HouseViewHolder, position: Int) {
        val house = houses[position]

        // ★ 시간 반영 해결: 스웨거 명세서에 맞게 visitDateTime 필드 사용 (ex: "18:30")
        val timeStr = house.visitDateTime ?: "00:00"
        val addressStr = house.address ?: "주소 없음"

        holder.binding.tvHouseInfo.text = "$timeStr $addressStr 탐색"
        holder.itemView.setOnClickListener { onHouseClick(house) }

        // ★ 완료 여부에 따라 아이콘 색상 변경
        if (house.isMeasurementCompleted) {
            holder.binding.ivCheck.setImageResource(R.drawable.ic_blue_check)
            holder.binding.ivCheck.imageTintList = null
        } else {
            holder.binding.ivCheck.setImageResource(R.drawable.ic_gray_check)
            holder.binding.ivCheck.imageTintList = null
        }
    }

    override fun getItemCount() = houses.size
    inner class HouseViewHolder(val binding: ItemChecklistHouseBinding) : RecyclerView.ViewHolder(binding.root)
}