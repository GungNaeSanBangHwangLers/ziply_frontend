package com.keder.zply

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.keder.zply.databinding.FragmentAfterLightBinding
import kotlinx.coroutines.launch

class AfterLightFragment : Fragment() {
    private var _binding: FragmentAfterLightBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAfterLightBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadLightData()
    }

    private fun loadLightData() {
        val activity = requireActivity() as? AfterExploreActivity ?: return
        val cardId = activity.currentCardId

        Log.d("API_DEBUG", "[AfterLight] 로드 시작. CardID: $cardId")

        lifecycleScope.launch {
            try {
                // ★ [수정] 전용 API 호출 (한방에 가져옴)
                val response = RetrofitClient.getInstance(requireContext()).getLightScoreList(cardId)

                if (response.isSuccessful && response.body() != null) {
                    val scoreList = response.body()!!
                    Log.d("API_DEBUG", "[AfterLight] 데이터 수신: ${scoreList.size}개")

                    if (scoreList.isNotEmpty()) {
                        // API 응답 -> UI 모델 변환
                        val uiList = scoreList.map { item ->
                            val rank = activity.getRankLabel(item.houseId) // A, B, C... 가져오기

                            ScheduleItem(
                                houseId = item.houseId,
                                address = "",
                                rankLabel = rank,
                                measuredLight = item.score.toFloat(), // 점수를 바로 넣음
                                time = ""
                            )
                        }.sortedBy { it.rankLabel } // 랭크 순 정렬

                        // 데이터가 0점보다 큰 게 있는지 확인 (그래프 그릴 필요가 있는지)
                        val hasData = uiList.any { it.measuredLight > 0f }

                        if (hasData) {
                            binding.beforeGraphRv.visibility = View.VISIBLE
                            binding.emptyStateTv.visibility = View.GONE

                            val adapter = LightGraphAdapter(uiList)
                            binding.beforeGraphRv.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
                            binding.beforeGraphRv.adapter = adapter

                            updateBestLightText(uiList)
                        } else {
                            Log.w("API_DEBUG", "[AfterLight] 점수가 모두 0점임")
                            showEmptyState("아직 측정된 채광 데이터가 없습니다.")
                        }
                    } else {
                        Log.w("API_DEBUG", "[AfterLight] 리스트가 비어있음")
                        showEmptyState("채광 데이터가 없습니다.")
                    }
                } else {
                    Log.e("API_DEBUG", "[AfterLight] API 실패: ${response.code()}")
                    showEmptyState("데이터를 불러오지 못했습니다.")
                }
            } catch (e: Exception) {
                Log.e("API_DEBUG", "[AfterLight] 에러 발생", e)
                showEmptyState("오류가 발생했습니다.")
            }
        }
    }

    private fun showEmptyState(msg: String) {
        binding.emptyStateTv.text = msg
        binding.emptyStateTv.visibility = View.VISIBLE
        binding.beforeGraphRv.visibility = View.GONE
        binding.afterLightTv.text = ""
    }

    private fun updateBestLightText(list: List<ScheduleItem>) {
        // 점수가 가장 높은 아이템 찾기
        val bestItem = list.maxByOrNull { it.measuredLight } ?: return

        if (bestItem.measuredLight <= 0) {
            binding.afterLightTv.text = ""
            return
        }

        val bestRank = bestItem.rankLabel
        val fullText = "채광은 $bestRank 가 \n가장 좋네요!"
        val spannable = SpannableString(fullText)

        val index = fullText.indexOf(bestRank)
        if (index != -1) {
            val color = ContextCompat.getColor(requireContext(), R.color.brand_700)
            spannable.setSpan(
                ForegroundColorSpan(color),
                index, index + bestRank.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        binding.afterLightTv.text = spannable
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}