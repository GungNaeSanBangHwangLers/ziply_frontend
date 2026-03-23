package com.keder.zply

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import java.time.LocalDate

class DateAdapter(
    private val dayList: List<LocalDate?>,
    private val selectedDate: LocalDate?,
    private val onDateClick: (LocalDate) -> Unit
) : RecyclerView.Adapter<DateAdapter.DateViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DateViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_calendar_date, parent, false)
        return DateViewHolder(view)
    }

    override fun onBindViewHolder(holder: DateViewHolder, position: Int) {
        holder.bind(dayList[position])
    }

    override fun getItemCount(): Int = dayList.size

    inner class DateViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val dayTv: TextView = itemView.findViewById(R.id.day_tv)

        fun bind(date: LocalDate?) {
            if (date == null) {
                dayTv.text = ""
                // 빈칸일 때는 배경을 투명하게 하거나 숨김
                dayTv.background = null
                itemView.isEnabled = false
                itemView.setOnClickListener(null)
            } else {
                itemView.isEnabled = true
                dayTv.text = date.dayOfMonth.toString()

                // ★ 핵심: 배경 리소스 재설정 (재사용 문제 방지)
                dayTv.setBackgroundResource(R.drawable.bg_gray900_8)

                val context = itemView.context
                if (date == selectedDate) {
                    // ★ 선택됨: Gray 800 (원래 색상)
                    // (R.color.gray800이 정확한지 확인해주세요. 없다면 #424242 등)
                    val color800 = ContextCompat.getColor(context, R.color.gray_900)
                    dayTv.background.setTint(color800)

                    dayTv.setTextColor(Color.WHITE)
                } else {
                    // ★ 선택 안됨: Gray 900 (더 어두운 색)
                    // (R.color.gray900이 정확한지 확인해주세요)
                    val color900 = ContextCompat.getColor(context, R.color.gray_800)
                    dayTv.background.setTint(color900)

                    // 선택 안된 날짜 텍스트 색상 (약간 흐리게)
                    dayTv.setTextColor(Color.parseColor("#888888"))
                }

                itemView.setOnClickListener {
                    onDateClick(date)
                }
            }
        }
    }
}