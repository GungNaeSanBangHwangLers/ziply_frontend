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

        Log.d("API_AFTER_LIGHT", "[채광] 점수 API 로드 시작. CardID: $cardId")

        if (cardId.isEmpty()) {
            showEmptyState("측정된 데이터가 없어 \n결과를 준비하지 못했어요")
            return
        }

        lifecycleScope.launch {
            try {
                // ★ 원래 회원님이 작성하셨던 '점수 산출 전용 API'로 원상 복구!
                val response = RetrofitClient.getInstance(requireContext()).getLightScoreList(cardId)

                if (response.isSuccessful && response.body() != null) {
                    val scoreList = response.body()!!
                    Log.d("API_AFTER_LIGHT", "[채광] 데이터 수신 성공! 갯수: ${scoreList.size}개")

                    if (scoreList.isNotEmpty()) {
                        val uiList = scoreList.map { item ->
                            ScheduleItem(
                                houseId = item.houseId,
                                address = "",
                                rankLabel = activity.getRankLabel(item.houseId),
                                measuredLight = item.score.toFloat(), // ★ 서버가 준 0~100 점수 그대로 사용!
                                time = ""
                            )
                        }.sortedBy { it.rankLabel }

                        // 점수가 0점 초과인 데이터가 하나라도 있는지 확인
                        val hasData = uiList.any { it.measuredLight > 0f }

                        if (hasData) {
                            showDataState() // 숨겼던 UI 살리기

                            val adapter = LightGraphAdapter(uiList)
                            binding.beforeGraphRv.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
                            binding.beforeGraphRv.adapter = adapter

                            val favoriteViewModel = androidx.lifecycle.ViewModelProvider(requireActivity())[FavoriteViewModel::class.java]
                            favoriteViewModel.favoriteSet.observe(viewLifecycleOwner) { favorites ->
                                adapter.updateFavorites(favorites)
                            }

                            updateBestLightText(uiList)
                        } else {
                            Log.w("API_AFTER_LIGHT", "[채광] 점수가 모두 0점입니다.")
                            showEmptyState("측정된 데이터가 없어 \n결과를 준비하지 못했어요")
                        }
                    } else {
                        Log.w("API_AFTER_LIGHT", "[채광] 빈 리스트 응답을 받았습니다.")
                        showEmptyState("측정된 데이터가 없어 \n결과를 준비하지 못했어요")
                    }
                } else {
                    Log.e("API_AFTER_LIGHT", "[채광] 통신 실패! 코드: ${response.code()}")
                    showEmptyState("데이터를 불러오지 못했습니다.")
                }
            } catch (e: Exception) {
                Log.e("API_AFTER_LIGHT", "[채광] 예외 발생: ${e.message}", e)
                showEmptyState("오류가 발생했습니다.")
            }
        }
    }

    // 데이터가 있을 때 뷰 복구
    private fun showDataState() {
        binding.afterLightTv.visibility = View.VISIBLE
        binding.afterLightDesTv.visibility = View.VISIBLE
        binding.graphContainer.visibility = View.VISIBLE

        binding.emptyStateTv.visibility = View.GONE
    }

    // 데이터가 없을 때 뷰 숨김 및 텍스트 노출
    private fun showEmptyState(msg: String) {
        binding.afterLightTv.visibility = View.GONE
        binding.afterLightDesTv.visibility = View.GONE
        binding.graphContainer.visibility = View.GONE

        binding.emptyStateTv.text = msg
        binding.emptyStateTv.visibility = View.VISIBLE
    }

    private fun updateBestLightText(list: List<ScheduleItem>) {
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