package com.keder.zply

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.keder.zply.databinding.ItemMainCardBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainCardRVAdapter(
    private var items: List<MainCardData>,
    private val onItemClick : (MainCardData, ExploreStatus) -> Unit
) : RecyclerView.Adapter<MainCardRVAdapter.Holder>(){

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        val binding = ItemMainCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return Holder(binding)
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    fun updateList(newItems: List<MainCardData>) {
        this.items = newItems
        notifyDataSetChanged()
    }

    inner class Holder(private val binding : ItemMainCardBinding) : RecyclerView.ViewHolder(binding.root){
        fun bind(item: MainCardData){
            binding.tvDate.text = item.date
            binding.tvLocation.text = item.location
            binding.tvCount.text = "${item.count}개"

            // ★ [수정] 날짜 계산 로직 강화
            // item.date 문자열이 "2026-01-16 \n~ 2026-01-16" 형태이므로
            // 실제 날짜 비교를 위해선 원본 날짜가 필요하지만,
            // 여기서는 텍스트 뷰에 박힌 값을 파싱하거나, 아래 calculateStatus 로직을 따름
            val currentStatus = calculateStatusFromDateText(item.date)

            val (statusText, bgResId) = when(currentStatus){
                ExploreStatus.ING -> Pair("탐색 중", R.drawable.gradient_card_ing) // 초록
                ExploreStatus.AFTER -> Pair("탐색 완료", R.drawable.gradient_card_ed) // 회색
                ExploreStatus.BEFORE -> Pair("탐색 예정", R.drawable.gradient_card_will) // 파랑
            }
            binding.tvStatus.text = statusText
            binding.explorCardCl.setBackgroundResource(bgResId)

            binding.root.setOnClickListener{
                onItemClick(item, currentStatus)
            }
        }

        // ★ [핵심] 날짜 상태 계산 함수 (시간 제외, 날짜만 비교)
        private fun calculateStatusFromDateText(dateString: String): ExploreStatus {
            return try {
                // dateString 예시: "2026-01-16 \n~ 2026-01-16"
                // 줄바꿈과 물결표 제거하고 숫자만 추출해서 비교
                val parts = dateString.split("~").map { it.trim() }
                if (parts.isEmpty()) return ExploreStatus.BEFORE

                val startDateStr = parts[0].replace("\n", "").trim()
                val endDateStr = if (parts.size > 1) parts[1].replace("\n", "").trim() else startDateStr

                val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
                val todayFormat = SimpleDateFormat("yyyyMMdd", Locale.KOREA)

                // 날짜 파싱 (실패 시 BEFORE 반환)
                val start = sdf.parse(startDateStr) ?: return ExploreStatus.BEFORE
                val end = sdf.parse(endDateStr) ?: start
                val today = Date()

                // yyyyMMdd 정수형으로 변환 (시간 정보 제거됨)
                val startInt = todayFormat.format(start).toInt()
                val endInt = todayFormat.format(end).toInt()
                val todayInt = todayFormat.format(today).toInt()

                when {
                    todayInt < startInt -> ExploreStatus.BEFORE // 오늘이 시작일보다 작음 -> 예정
                    todayInt > endInt -> ExploreStatus.AFTER    // 오늘이 종료일보다 큼 -> 완료
                    else -> ExploreStatus.ING                   // 그 외 (오늘 == 시작일 포함) -> 탐색 중
                }
            } catch (e: Exception) {
                e.printStackTrace()
                ExploreStatus.BEFORE // 에러 시 기본값
            }
        }
    }
}

enum class ExploreStatus {
    BEFORE, // 탐색 예정 (파란색)
    ING,    // 탐색 중 (초록색)
    AFTER   // 탐색 완료 (회색)
}