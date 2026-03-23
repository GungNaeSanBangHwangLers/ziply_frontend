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
import com.keder.zply.databinding.FragmentAfterNoiseBinding
import kotlinx.coroutines.launch

class AfterNoiseFragment : Fragment() {

    private var _binding: FragmentAfterNoiseBinding? = null
    private val binding get() = _binding!!

    private lateinit var graphAdapter: GraphAdapter
    private var isDayMode = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAfterNoiseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. API 데이터 로드 시작
        loadNoiseData()

        // 2. 버튼 리스너 설정
        setupDayNightButtons()
    }

    private fun loadNoiseData() {
        val activity = requireActivity() as? AfterExploreActivity ?: return
        val cardId = activity.currentCardId

        if (cardId.isEmpty()) return

        lifecycleScope.launch {
            try {
                // [API 호출] 소음 점수 가져오기
                val response = RetrofitClient.getInstance(requireContext()).getAnalysisScore(cardId)

                if (response.isSuccessful && response.body() != null) {
                    val scoreList = response.body()!!

                    // [데이터 매핑] API 응답 -> UI 모델(ScheduleItem) 변환
                    val uiList = scoreList.map { score ->
                        ScheduleItem(
                            houseId = score.houseId,
                            address = "", // 그래프에서는 주소 안 씀
                            time = "",    // 그래프에서는 시간 안 씀

                            // 점수 및 설명 매핑
                            dayScore = score.dayScore,
                            nightScore = score.nightScore,
                            dayDesc = score.message,   // API 메시지를 설명으로 사용
                            nightDesc = score.message, // 낮/밤 메시지가 같다면 동일하게 설정

                            // [중요] 액티비티의 마스터 리스트와 동일한 랭크(A, B, C) 가져오기
                            rankLabel = activity.getRankLabel(score.houseId)
                        )
                    }.sortedBy { it.rankLabel } // A, B, C 순서대로 정렬

                    if (uiList.isNotEmpty()) {
                        setupGraph(uiList)
                        updateNoiseSummaryText(uiList)
                    } else {
                        // 데이터 없을 때 처리 (옵션)
                    }
                } else {
                    Log.e("AfterNoise", "Load failed: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("AfterNoise", "Network error", e)
            }
        }
    }

    private fun setupGraph(list: List<ScheduleItem>) {
        binding.beforeGraphRv.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        graphAdapter = GraphAdapter(list) { description ->
            if (description.isEmpty()) {
                binding.graphDetailTv.visibility = View.GONE
            } else {
                binding.graphDetailTv.visibility = View.VISIBLE
                binding.graphDetailTv.text = description
            }
        }
        binding.beforeGraphRv.adapter = graphAdapter

        // 초기 모드 설정 (낮)
        graphAdapter.setMode(isDayMode)
    }

    private fun setupDayNightButtons() {
        val context = requireContext()
        val selectedBg = ContextCompat.getDrawable(context, R.drawable.blue_bg6)
        val unselectedBg = ContextCompat.getDrawable(context, R.drawable.gray_bg6)
        val whiteColor = ContextCompat.getColor(context, R.color.white)
        val grayColor = ContextCompat.getColor(context, R.color.gray_500)

        binding.afterDayBtn.setOnClickListener {
            if (!isDayMode) {
                isDayMode = true
                binding.afterDayBtn.background = selectedBg
                binding.afterDayBtn.setTextColor(whiteColor)
                binding.afterNightBtn.background = unselectedBg
                binding.afterNightBtn.setTextColor(grayColor)

                if (::graphAdapter.isInitialized) {
                    graphAdapter.setMode(true)
                }
                binding.graphDetailTv.visibility = View.GONE
            }
        }

        binding.afterNightBtn.setOnClickListener {
            if (isDayMode) {
                isDayMode = false
                binding.afterNightBtn.background = selectedBg
                binding.afterNightBtn.setTextColor(whiteColor)
                binding.afterDayBtn.background = unselectedBg
                binding.afterDayBtn.setTextColor(grayColor)

                if (::graphAdapter.isInitialized) {
                    graphAdapter.setMode(false)
                }
                binding.graphDetailTv.visibility = View.GONE
            }
        }
    }

    private fun updateNoiseSummaryText(list: List<ScheduleItem>) {
        if (list.isEmpty()) return

        // 소음 점수가 가장 낮은(좋은) 아이템 찾기
        val minDayItem = list.minByOrNull { it.dayScore }
        val minNightItem = list.minByOrNull { it.nightScore }

        // 해당 아이템의 랭크 라벨(A, B, C...) 가져오기
        val minDayRank = minDayItem?.rankLabel ?: "-"
        val minNightRank = minNightItem?.rankLabel ?: "-"

        val text = "낮에는 $minDayRank, 밤에는 $minNightRank 가 \n소음이 가장 낮아요."
        val spannable = SpannableString(text)
        val blueColor = ContextCompat.getColor(requireContext(), R.color.brand_600)

        // 랭크 텍스트 색상 변경
        val dayIndex = text.indexOf(minDayRank)
        if (dayIndex != -1) {
            spannable.setSpan(ForegroundColorSpan(blueColor), dayIndex, dayIndex + minDayRank.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        // 밤 랭크는 뒤에서부터 검색 (혹시 A, A 일 경우 대비)
        val nightIndex = text.lastIndexOf(minNightRank)
        if (nightIndex != -1) {
            spannable.setSpan(ForegroundColorSpan(blueColor), nightIndex, nightIndex + minNightRank.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        binding.afterNoiseTv.text = spannable
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}