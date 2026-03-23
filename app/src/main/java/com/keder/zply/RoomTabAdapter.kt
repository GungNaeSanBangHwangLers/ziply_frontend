package com.keder.zply

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView

class RoomTabAdapter(
    val rooms: MutableList<String>,
    private val onTabClick: (Int) -> Unit
) : RecyclerView.Adapter<RoomTabAdapter.ViewHolder>() {

    var selectedPosition = 0

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tabTv: TextView = view.findViewById(R.id.tab_tv)
        val tabRootLayout: ConstraintLayout = view.findViewById(R.id.tab_root_layout)

        fun bind(roomName: String, position: Int) {
            tabTv.text = roomName
            val context = itemView.context

            // 탭 선택/미선택 상태에 따른 디자인 변경
            if (position == selectedPosition) {
                // 선택됨: 파란색 배경, 흰색 글자
                tabRootLayout.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.brand_600))
                tabTv.setTextColor(ContextCompat.getColor(context, R.color.white))
            } else {
                // 선택 안 됨: 어두운 회색 배경, 회색 글자 (앱 테마에 맞춰 gray_800/gray_400 사용)
                tabRootLayout.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.gray_800))
                tabTv.setTextColor(ContextCompat.getColor(context, R.color.gray_400))
            }

            itemView.setOnClickListener {
                val previousPosition = selectedPosition
                selectedPosition = position
                // 이전 탭과 현재 누른 탭의 색상을 갱신
                notifyItemChanged(previousPosition)
                notifyItemChanged(selectedPosition)

                // 클릭 이벤트 전달
                onTabClick(position)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        // ★ 기존에 만드신 item_ing_room 레이아웃 사용
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_ing_room, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(rooms[position], position)
    }

    override fun getItemCount(): Int = rooms.size
}