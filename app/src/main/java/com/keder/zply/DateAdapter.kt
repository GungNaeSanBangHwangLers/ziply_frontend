package com.keder.zply

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale


class DateAdapter(
    private val startDate: LocalDate,
    private val endDate: LocalDate,
    private val onDateClick: (LocalDate) -> Unit
) : RecyclerView.Adapter<DateAdapter.DateViewHolder>() {

    // 총 날짜 수 미리 계산 (매번 계산하지 않음)
    private val totalDays = ChronoUnit.DAYS.between(startDate, endDate).toInt() + 1

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DateViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_calendar_date, parent, false) // XML 파일명 확인 (item_calender.xml -> item_calendar)
        return DateViewHolder(view)
    }

    override fun onBindViewHolder(holder: DateViewHolder, position: Int) {
        // 날짜 계산은 가벼운 연산이므로 여기서 수행해도 괜찮습니다.
        val date = startDate.plusDays(position.toLong())
        holder.bind(date)
    }

    override fun getItemCount(): Int = totalDays

    // 외부(Fragment)에서 특정 날짜의 포지션을 찾을 때 사용
    fun getPositionOfDate(date: LocalDate): Int {
        return ChronoUnit.DAYS.between(startDate, date).toInt()
            .coerceIn(0, totalDays - 1) // 범위 밖으로 나가는 것 방지
    }

    // 외부(Fragment)에서 포지션으로 날짜를 가져올 때 사용
    fun getDateAt(position: Int): LocalDate {
        return startDate.plusDays(position.toLong())
    }

    inner class DateViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {

        private val dayText: TextView = itemView.findViewById(R.id.day_tv)
        private val weekText: TextView = itemView.findViewById(R.id.date_tv)

        init {
            // 리스너는 뷰홀더 생성 시 한 번만 등록 (메모리 최적화)
            itemView.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    val clickedDate = startDate.plusDays(position.toLong())
                    onDateClick(clickedDate)
                }
            }
        }

        fun bind(date: LocalDate) {
            dayText.text = date.dayOfMonth.toString()

            // 요일 처리는 Locale 객체를 계속 생성하지 않도록 주의 (기본 제공 함수 사용은 OK)
            weekText.text = date.dayOfWeek
                .getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
                .uppercase()
        }
    }
}