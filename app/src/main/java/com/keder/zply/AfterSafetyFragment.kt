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
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.keder.zply.databinding.FragmentAfterSafetyBinding
import kotlinx.coroutines.launch

class AfterSafetyFragment : Fragment() {

    private var _binding: FragmentAfterSafetyBinding? = null
    private val binding get() = _binding!!
    private lateinit var graphAdapter: GraphAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAfterSafetyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadSafetyData()
    }

    private fun loadSafetyData() {
        val activity = requireActivity() as? AfterExploreActivity ?: return
        val cardId = activity.currentCardId

        if (cardId.isEmpty()) return

        lifecycleScope.launch {
            try {
                // ★ 수정됨: getAnalysisLife가 아닌 getAnalysisSafety 호출!
                val response = RetrofitClient.getInstance(requireContext()).getAnalysisSafety(cardId)

                if (response.isSuccessful && response.body() != null) {
                    val safetyList = response.body()!!
                    Log.d("API_AFTER_SAFETY", "✅ [안전] 통신 성공! 갯수: ${safetyList.size}")

                    val uiList = safetyList.map { safety ->
                        // 치안 탭을 없앴으므로, dayScore에 safetyScore를 넣어서 그래프 하나만 깔끔하게 그립니다.
                        ScheduleItem(
                            houseId = safety.houseId,
                            address = "",
                            time = "",
                            dayScore = safety.safetyScore, // 그래프용 안전 점수
                            nightScore = 0, // 사용 안 함
                            // 메세지가 없으면 인프라 개수를 직접 텍스트로 만들어 줍니다.
                            dayDesc = safety.message ?: "CCTV ${safety.cctvCount}대 · 가로등 ${safety.streetlightCount}개 · 치안시설 ${safety.policeCount}곳",
                            nightDesc = "",
                            rankLabel = activity.getRankLabel(safety.houseId)
                        )
                    }.sortedBy { it.rankLabel }

                    if (uiList.isNotEmpty()) {
                        setupGraph(uiList)
                        updateSafetySummaryText(uiList)
                    }
                } else {
                    Log.e("API_AFTER_SAFETY", "❌ [안전] 통신 실패! 코드: ${response.code()}")
                }
            } catch (e: Exception) {
                Log.e("API_AFTER_SAFETY", "❌ [안전] 예외 발생: ${e.message}", e)
            }
        }
    }

    private fun setupGraph(list: List<ScheduleItem>) {
        binding.beforeGraphRv.layoutManager =
            LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        graphAdapter = GraphAdapter(list) { description ->
            if (description.isEmpty()) {
                binding.graphDetailTv.visibility = View.GONE
            } else {
                binding.graphDetailTv.visibility = View.VISIBLE
                binding.graphDetailTv.text = description
            }
        }
        binding.beforeGraphRv.adapter = graphAdapter

        // 치안 탭을 없앴으므로 무조건 true(하나의 모드)로 고정합니다.
        graphAdapter.setMode(true)

        val favoriteViewModel = ViewModelProvider(requireActivity())[FavoriteViewModel::class.java]
        favoriteViewModel.favoriteSet.observe(viewLifecycleOwner) { favorites ->
            graphAdapter.updateFavorites(favorites)
        }
    }

    private fun updateSafetySummaryText(list: List<ScheduleItem>) {
        if (list.isEmpty()) return

        // 가장 높은 안전 점수 찾기
        val maxItem = list.maxByOrNull { it.dayScore }
        val maxRank = maxItem?.rankLabel ?: "-"
        val text = "안전 점수는 $maxRank 가 \n가장 높아요."

        val spannable = SpannableString(text)
        val brandColor = ContextCompat.getColor(requireContext(), R.color.brand_600)

        val rankIndex = text.indexOf(maxRank)
        if (rankIndex != -1) {
            spannable.setSpan(ForegroundColorSpan(brandColor), rankIndex, rankIndex + maxRank.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        binding.afterSafetyTv.text = spannable
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}