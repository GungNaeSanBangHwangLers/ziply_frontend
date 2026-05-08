package com.keder.zply

import android.content.res.ColorStateList
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
import androidx.lifecycle.ViewModelProvider
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
        loadNoiseData()
        setupDayNightButtons()
    }

    private fun loadNoiseData() {
        val activity = requireActivity() as? AfterExploreActivity ?: return
        val cardId = activity.currentCardId

        if (cardId.isEmpty()) return

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getInstance(requireContext()).getAnalysisLife(cardId)
                if (response.isSuccessful && response.body() != null) {
                    val lifeList = response.body()!!
                    val uiList = lifeList.map { life ->
                        ScheduleItem(
                            houseId = life.houseId, address = "", time = "",
                            dayScore = life.dayScore, nightScore = life.nightScore,
                            dayDesc = life.message ?: "", nightDesc = life.message ?: "",
                            rankLabel = activity.getRankLabel(life.houseId)
                        )
                    }.sortedBy { it.rankLabel }

                    if (uiList.isNotEmpty()) {
                        setupGraph(uiList)
                        updateNoiseSummaryText(uiList)
                    }
                }
            } catch (e: Exception) {
                Log.e("API_AFTER_NOISE", "❌ [소음] 예외 발생: ${e.message}", e)
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
                // ★ 자동 스크롤 실행
                autoScrollToView(binding.graphDetailTv)
            }
        }
        binding.beforeGraphRv.adapter = graphAdapter
        graphAdapter.setMode(isDayMode)

        val favoriteViewModel = ViewModelProvider(requireActivity())[FavoriteViewModel::class.java]
        favoriteViewModel.favoriteSet.observe(viewLifecycleOwner) { favorites ->
            graphAdapter.updateFavorites(favorites)
        }
    }

    // ★ 자동 스크롤 함수
    private fun autoScrollToView(targetView: View) {
        targetView.postDelayed({
            var parent = targetView.parent
            while (parent != null) {
                if (parent is androidx.core.widget.NestedScrollView) {
                    val rect = android.graphics.Rect()
                    targetView.getDrawingRect(rect)
                    parent.offsetDescendantRectToMyCoords(targetView, rect)

                    val maxScrollY = parent.getChildAt(0).height - parent.height
                    val targetY = (rect.bottom - parent.height + 100).coerceAtMost(maxScrollY)

                    if (targetY > parent.scrollY) {
                        parent.smoothScrollTo(0, targetY)
                    }
                    break
                }
                parent = parent.parent
            }
        }, 100) // 레이아웃이 그려질 시간을 0.1초 확보
    }

    private fun setupDayNightButtons() {
        val context = requireContext()
        val grayColor = ContextCompat.getColor(context, R.color.gray_900)
        val blueColor = ContextCompat.getColor(context, R.color.brand_700)

        binding.afterDayTv.setOnClickListener {
            if (!isDayMode) {
                isDayMode = true
                binding.afterDayTv.backgroundTintList = ColorStateList.valueOf(blueColor)
                binding.afterNightTv.backgroundTintList = ColorStateList.valueOf(grayColor)
                if (::graphAdapter.isInitialized) graphAdapter.setMode(true)
                binding.graphDetailTv.visibility = View.GONE
            }
        }
        binding.afterNightTv.setOnClickListener {
            if (isDayMode) {
                isDayMode = false
                binding.afterDayTv.backgroundTintList = ColorStateList.valueOf(grayColor)
                binding.afterNightTv.backgroundTintList = ColorStateList.valueOf(blueColor)
                if (::graphAdapter.isInitialized) graphAdapter.setMode(false)
                binding.graphDetailTv.visibility = View.GONE
            }
        }
    }

    private fun updateNoiseSummaryText(list: List<ScheduleItem>) {
        if (list.isEmpty()) return
        val minDayItem = list.minByOrNull { it.dayScore }
        val minNightItem = list.minByOrNull { it.nightScore }
        val minDayRank = minDayItem?.rankLabel ?: "-"
        val minNightRank = minNightItem?.rankLabel ?: "-"
        val text = "낮에는 $minDayRank, 밤에는 $minNightRank 가 \n소음이 가장 낮아요."
        val spannable = SpannableString(text)
        val blueColor = ContextCompat.getColor(requireContext(), R.color.brand_600)
        val dayIndex = text.indexOf(minDayRank)
        if (dayIndex != -1) spannable.setSpan(ForegroundColorSpan(blueColor), dayIndex, dayIndex + minDayRank.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        val nightIndex = text.lastIndexOf(minNightRank)
        if (nightIndex != -1) spannable.setSpan(ForegroundColorSpan(blueColor), nightIndex, nightIndex + minNightRank.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.afterNoiseTv.text = spannable
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}