package com.keder.zply

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.keder.zply.databinding.FragmentAfterLengthBinding
import kotlinx.coroutines.launch

class AfterLengthFragment : Fragment() {
    private var _binding: FragmentAfterLengthBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAfterLengthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadDistanceData()
    }

    private fun loadDistanceData() {
        val activity = requireActivity() as? AfterExploreActivity ?: return
        val cardId = activity.currentCardId
        if (cardId.isEmpty()) return

        lifecycleScope.launch {
            try {
                // API 호출
                val response = RetrofitClient.getInstance(requireContext()).getAnalysisDistance(cardId)

                if (response.isSuccessful && response.body() != null) {
                    val basePoints = response.body()!!.basePoints
                    if (basePoints.isNotEmpty()) {
                        val results = basePoints[0].results // 첫 번째 기준지(회사) 사용

                        // 데이터를 UI 모델로 변환
                        val uiList = results.map { res ->
                            ScheduleItem(
                                houseId = res.houseId,
                                walkingTimeMin = res.walkingTimeMin,
                                walkingDistanceKm = res.walkingDistanceKm,
                                // 액티비티의 마스터 리스트에서 랭크와 주소 가져오기
                                rankLabel = activity.getRankLabel(res.houseId),
                                address = activity.getAddress(res.houseId),
                                time = "" // 랭킹 화면에선 시간 불필요
                            )
                        }.sortedBy { it.rankLabel } // A, B, C 순서 정렬

                        binding.afterLengthRankRv.adapter = LengthRankAdapter(uiList)
                        binding.afterLengthRankRv.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

                        updateShortestText(uiList)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateShortestText(list: List<ScheduleItem>) {
        if (list.isNotEmpty()) {
            // 시간이 가장 짧은 항목 찾기
            val bestItem = list.minByOrNull { it.walkingTimeMin }
            val shortestRank = bestItem?.rankLabel ?: "-"

            val fullText = "직주거리는 $shortestRank 가 \n가장 짧아요"
            val spannable = SpannableString(fullText)
            val index = fullText.indexOf(shortestRank)
            if (index != -1) {
                spannable.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.brand_500)),
                    index, index + shortestRank.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            binding.afterLengthRankTv.text = spannable
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}