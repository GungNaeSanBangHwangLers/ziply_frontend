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
import androidx.recyclerview.widget.LinearLayoutManager
import com.keder.zply.databinding.FragmentAfterNoiseBinding

class AfterNoiseFragment : Fragment() {

    private var _binding: FragmentAfterNoiseBinding? = null
    private val binding get() = _binding!!

    // GraphAdapter 재사용
    private lateinit var graphAdapter: GraphAdapter
    private var isDayMode = true

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAfterNoiseBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as? AfterExploreActivity
        val session = activity?.currentSession ?: return

        // 1. 데이터 가져오기 및 정렬 (Before와 동일하게 시간순=랭크순)
        val safeList = session.scheduleList ?: emptyList()
        val sortedList = safeList.sortedBy { it.time }

        // [추가됨] 목 데이터 점수 생성 (BeforeExploreActivity와 동일 로직)
        // 실제 앱에서는 저장된 데이터가 있어야 하지만, 테스트를 위해 점수가 0이면 랜덤 부여
        sortedList.forEach {
            if (it.dayScore == 0) {
                it.dayScore = (40..95).random()
                it.nightScore = (30..80).random()
                it.dayDesc = "이 점수는 버스 운행횟수(20회), 인근 도로 트래픽, 학교(2곳), 지상 지하철역(1곳), 상권 밀도를 함께 반영해 계산됐어요."
                it.nightDesc = "이 점수는 버스 운행횟수(11회), 인근 도로 트래픽, 학교(2곳), 지상 지하철역(1곳), 상권 밀도를 함께 반영해 계산됐어요."
            }
        }

        // 2. 그래프 설정
        setupGraph(sortedList)

        // 3. 낮/밤 버튼 설정
        setupDayNightButtons()

        // 4. 상단 요약 텍스트 업데이트
        updateNoiseSummaryText(sortedList)
    }

    private fun setupGraph(list: List<ScheduleItem>) {
        // XML ID: before_graph_rv (레이아웃 ID 주의: fragment_after_noise.xml에 해당 ID가 있어야 함)
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
    }

    private fun setupDayNightButtons() {
        val context = requireContext()
        val selectedBg = ContextCompat.getDrawable(context, R.drawable.blue_bg6)
        val unselectedBg = ContextCompat.getDrawable(context, R.drawable.gray_bg6)
        val whiteColor = ContextCompat.getColor(context, R.color.white)
        val grayColor = ContextCompat.getColor(context, R.color.gray_500)

        // XML ID: after_day_btn
        binding.afterDayBtn.setOnClickListener {
            if (!isDayMode) {
                isDayMode = true
                binding.afterDayBtn.background = selectedBg
                binding.afterDayBtn.setTextColor(whiteColor)
                binding.afterNightBtn.background = unselectedBg
                binding.afterNightBtn.setTextColor(grayColor)

                graphAdapter.setMode(true)
                binding.graphDetailTv.visibility = View.GONE
            }
        }

        // XML ID: after_night_btn
        binding.afterNightBtn.setOnClickListener {
            if (isDayMode) {
                isDayMode = false
                binding.afterNightBtn.background = selectedBg
                binding.afterNightBtn.setTextColor(whiteColor)
                binding.afterDayBtn.background = unselectedBg
                binding.afterDayBtn.setTextColor(grayColor)

                graphAdapter.setMode(false)
                binding.graphDetailTv.visibility = View.GONE
            }
        }
    }

    private fun updateNoiseSummaryText(list: List<ScheduleItem>) {
        if (list.isEmpty()) return

        // 점수가 가장 낮은(소음이 적은) 아이템 찾기
        val minDayItem = list.minByOrNull { it.dayScore }
        val minDayRank = if (minDayItem != null) ('A'.code + list.indexOf(minDayItem)).toChar() else '?'

        val minNightItem = list.minByOrNull { it.nightScore }
        val minNightRank = if (minNightItem != null) ('A'.code + list.indexOf(minNightItem)).toChar() else '?'

        val text = "낮에는 $minDayRank, 밤에는 $minNightRank 가 \n소음이 가장 낮아요."
        val spannable = SpannableString(text)
        val blueColor = ContextCompat.getColor(requireContext(), R.color.brand_100)

        // 랭크 부분 색상 변경
        val dayIndex = text.indexOf(minDayRank.toString())
        if (dayIndex != -1) {
            spannable.setSpan(ForegroundColorSpan(blueColor), dayIndex, dayIndex + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val nightIndex = text.lastIndexOf(minNightRank.toString())
        if (nightIndex != -1) {
            spannable.setSpan(ForegroundColorSpan(blueColor), nightIndex, nightIndex + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        binding.afterNoiseTv.text = spannable
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}