package com.keder.zply

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.keder.zply.databinding.ItemChecklistGroupBinding
import com.keder.zply.databinding.ItemChecklistHouseBinding
import com.keder.zply.databinding.ItemMainCardBinding
import com.keder.zply.databinding.ItemMainPlusBinding

class MainCardRVAdapter(
    private var items: List<MainCardData>,
    private val onItemClick: (MainCardData, ExploreStatus) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val VIEW_TYPE_NORMAL = 0
        private const val VIEW_TYPE_PLUS = 1
    }

    fun updateList(newItems: List<MainCardData>) {
        val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize() = items.size
            override fun getNewListSize() = newItems.size
            override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                items[oldPos].cardId == newItems[newPos].cardId
            override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                items[oldPos] == newItems[newPos]
        })
        this.items = newItems
        diff.dispatchUpdatesTo(this)
    }

    override fun getItemViewType(position: Int): Int {
        return if (items[position].status == "PLUS_BTN") VIEW_TYPE_PLUS else VIEW_TYPE_NORMAL
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        return if (viewType == VIEW_TYPE_PLUS) {
            val binding = ItemMainPlusBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            PlusViewHolder(binding)
        } else {
            val binding = ItemMainCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            NormalViewHolder(binding)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val item = items[position]
        if (holder is NormalViewHolder) {
            holder.bind(item)
        } else if (holder is PlusViewHolder) {
            holder.bind(item)
        }
    }

    override fun getItemCount(): Int = items.size

    inner class NormalViewHolder(private val binding: ItemMainCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MainCardData) {
            binding.tvDate.text = item.date
            binding.tvLocation.text = "현재 ${item.location} 인근"
            binding.tvCountDesc.text = "${item.count}개 주거를 탐색 중이에요"

            binding.root.setOnClickListener {
                val statusEnum = if (item.status == "AFTER") ExploreStatus.AFTER else ExploreStatus.ING
                onItemClick(item, statusEnum)
            }
        }
    }

    inner class PlusViewHolder(private val binding: ItemMainPlusBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: MainCardData) {
            binding.root.setOnClickListener {
                onItemClick(item, ExploreStatus.ING)
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
        binding.rvGroupHouses.layoutManager = LinearLayoutManager(parent.context)
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

        val existing = holder.binding.rvGroupHouses.adapter as? ChecklistHouseAdapter
        if (existing != null) {
            existing.updateHouses(group.houses)
        } else {
            holder.binding.rvGroupHouses.adapter = ChecklistHouseAdapter(group.houses, onHouseClick)
        }
    }

    override fun getItemCount() = groups.size
    inner class GroupViewHolder(val binding: ItemChecklistGroupBinding) : RecyclerView.ViewHolder(binding.root)
}

// ==========================================
// 3. 하단 체크리스트 하우스 어댑터 (집 목록 표시용)
// ==========================================
class ChecklistHouseAdapter(private var houses: List<ChecklistHouseResponse>,
                            private val onHouseClick: (ChecklistHouseResponse) -> Unit) : RecyclerView.Adapter<ChecklistHouseAdapter.HouseViewHolder>() {

    fun updateHouses(newHouses: List<ChecklistHouseResponse>) {
        this.houses = newHouses
        notifyDataSetChanged()
    }

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