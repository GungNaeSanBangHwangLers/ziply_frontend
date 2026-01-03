package com.keder.zply

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import com.keder.zply.databinding.FragmentAfterDirectionBinding

class AfterDirectionFragment : Fragment() {

    private var _binding: FragmentAfterDirectionBinding? = null
    private val binding get() = _binding!!

    private val directionInfoMap = mapOf(
        "남향" to Triple(
            "하루 종일 햇빛이 고르게 들어와요. 집에 머무는 시간이 많고 쾌적한 주거환경을 선호하는 분께 추천드려요.",
            "겨울에는 따뜻하고 여름에는 시원해서 냉난방비를 줄일 수 있어요.",
            "자외선으로 인해 가구, 책 등이 변색될 수 있어요."
        ),
        "동향" to Triple(
            "아침 햇살이 가득 들어와요. 아침형 인간이나 맞벌이 부부에게 추천드려요.",
            "여름에는 시원하지만 겨울에는 조금 추울 수 있어요.",
            "오후에는 해가 빨리 져서 일조 시간이 짧아요."
        ),
        "서향" to Triple(
            "오후에 햇살이 깊게 들어와요. 유치원생이나 초등학생 자녀가 있는 가정에 좋아요.",
            "겨울에는 따뜻해서 난방비를 아낄 수 있어요.",
            "여름에는 오후 늦게까지 더울 수 있어 커튼이나 블라인드가 필수예요."
        ),
        "북향" to Triple(
            "햇빛이 일정하게 들어와 집중력이 필요한 분이나 재택근무자에게 좋아요.",
            "여름에는 시원해서 냉방비 걱정이 적어요.",
            "일조량이 적어 겨울에는 춥고 습기가 찰 수 있어 환기에 신경 써야 해요."
        )
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAfterDirectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as? AfterExploreActivity
        val session = activity?.currentSession
        val safeList = session?.scheduleList ?: emptyList()
        val sortedList = safeList.sortedBy { it.time }

        val groupedRanks = mutableMapOf<String, MutableList<Char>>()
        var hasMeasuredData = false

        sortedList.forEachIndexed { index, item ->
            // [중요] 측정된 방위각이 있는지 확인
            if (item.measuredAzimuths.isNotEmpty()) {
                hasMeasuredData = true
                val direction = calculateDirection(item.measuredAzimuths)
                val rank = ('A'.code + index).toChar()

                if (!groupedRanks.containsKey(direction)) {
                    groupedRanks[direction] = mutableListOf()
                }
                groupedRanks[direction]?.add(rank)
            }
        }

        // [UI 갱신] 측정 데이터 유무에 따라 분기
        if (hasMeasuredData) {
            // 1. 데이터 있음
            binding.afterDirectionRv.visibility = View.VISIBLE
            binding.emptyStateTv.visibility = View.GONE

            // 타이틀과 설명이 숨겨져 있었다면 다시 보여줌
            binding.afterDirectionDesTv.visibility = View.VISIBLE
            binding.afterDirectionTitleTv.visibility = View.VISIBLE

            val adapterItems = mutableListOf<DirectionGroupItem>()
            val order = listOf("남향", "동향", "서향", "북향")

            for (dir in order) {
                val ranks = groupedRanks[dir]
                if (ranks != null && ranks.isNotEmpty()) {
                    val info = directionInfoMap[dir]
                    if (info != null) {
                        adapterItems.add(
                            DirectionGroupItem(dir, ranks, info.first, info.second, info.third)
                        )
                    }
                }
            }
            binding.afterDirectionRv.layoutManager = LinearLayoutManager(
                requireContext(),
                LinearLayoutManager.HORIZONTAL, // 가로 방향 설정
                false
            )
            binding.afterDirectionRv.adapter = AfterDirectionAdapter(adapterItems)

             val snapHelper = PagerSnapHelper()
             snapHelper.attachToRecyclerView(binding.afterDirectionRv)

        } else {
            // 2. 데이터 없음 -> 안내 멘트 표시
            binding.afterDirectionRv.visibility = View.GONE
            binding.afterDirectionDesTv.visibility = View.GONE
            binding.afterDirectionTitleTv.visibility = View.GONE

            binding.emptyStateTv.visibility = View.VISIBLE
        }
    }

    private fun calculateDirection(azimuths: List<Int>): String {
        if (azimuths.isEmpty()) return "북향"
        val avg = azimuths.average().toInt()
        return when (avg) {
            in 0..45, in 315..360 -> "북향"
            in 45..135 -> "동향"
            in 135..225 -> "남향"
            in 225..315 -> "서향"
            else -> "북향"
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}