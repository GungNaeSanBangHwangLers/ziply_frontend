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

    // ★ 달력의 정중앙(15번째 데이터)을 기준으로 '현재 보여지는 달(Month)'이 몇 월인지 파악합니다.
    private val currentDisplayedMonth = dayList.getOrNull(15)?.monthValue

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
                dayTv.background = null
                itemView.isEnabled = false
                itemView.setOnClickListener(null)
            } else {
                itemView.isEnabled = true
                dayTv.text = date.dayOfMonth.toString()
                dayTv.setBackgroundResource(R.drawable.bg_gray900_8)

                val context = itemView.context

                // ★ 해당 칸의 날짜가 '현재 보여지는 달'에 속하는지 판별
                val isCurrentMonth = (date.monthValue == currentDisplayedMonth)

                if (date == selectedDate) {
                    // [선택된 날짜]
                    val color900 = ContextCompat.getColor(context, R.color.gray_900)
                    dayTv.background.setTint(color900)
                    dayTv.setTextColor(Color.WHITE)
                } else {
                    // [선택되지 않은 날짜]
                    val color800 = ContextCompat.getColor(context, R.color.gray_800)
                    dayTv.background.setTint(color800)

                    // ★ 이전 달 / 다음 달 날짜 처리 분기
                    if (isCurrentMonth) {
                        // 이번 달 날짜 (기존 색상 유지)
                        dayTv.setTextColor(Color.parseColor("#888888"))
                    } else {
                        // 이전 달 & 다음 달 날짜 (gray_700으로 더 흐리게)
                        dayTv.setTextColor(ContextCompat.getColor(context, R.color.gray_700))
                    }
                }

                itemView.setOnClickListener {
                    onDateClick(date)
                }
            }
        }
    }
}