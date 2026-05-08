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
import com.keder.zply.databinding.FragmentAfterSafetyBinding
import kotlinx.coroutines.launch

class AfterSafetyFragment : Fragment() {

    private var _binding: FragmentAfterSafetyBinding? = null
    private val binding get() = _binding!!
    private lateinit var graphAdapter: GraphAdapter
    private lateinit var publicPeaceAdapter: PublicPeaceAdapter

    private var allNewsItems: List<NewsItem> = emptyList()
    private var selectedTab = 0  // 0=생활불편, 1=안전불안, 2=신변위협
    private var isNewsExpanded = false

    companion object {
        private const val TAG = "API_AFTER_SAFETY"

        // categoryLevel 분류
        private fun getTabForCategory(categoryLevel: String): Int = when {
            categoryLevel.contains("안전 불안") -> 1
            categoryLevel.contains("생활 불편") || categoryLevel.contains("무질서") -> 0
            categoryLevel.contains("신변 위협") || categoryLevel.contains("강력 범죄") -> 2
            else -> -1
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAfterSafetyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupPublicPeaceRecycler()
        setupPublicPeaceListeners()
        loadSafetyData()
        loadNewsData()
    }

    private fun loadSafetyData() {
        val activity = requireActivity() as? AfterExploreActivity ?: return
        val cardId = activity.currentCardId
        if (cardId.isEmpty()) return

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getInstance(requireContext()).getAnalysisSafety(cardId)
                if (response.isSuccessful && response.body() != null) {
                    val safetyList = response.body()!!
                    val uiList = safetyList.map { safety ->
                        ScheduleItem(
                            houseId = safety.houseId, address = "", time = "",
                            dayScore = safety.safetyScore, nightScore = 0,
                            dayDesc = safety.message ?: "CCTV ${safety.cctvCount}대 · 가로등 ${safety.streetlightCount}개 · 치안시설 ${safety.policeCount}곳",
                            nightDesc = "", rankLabel = activity.getRankLabel(safety.houseId)
                        )
                    }.sortedBy { it.rankLabel }

                    if (uiList.isNotEmpty()) {
                        setupGraph(uiList)
                        updateSafetySummaryText(uiList)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ [안전] 예외 발생: ${e.message}", e)
            }
        }
    }

    private fun loadNewsData() {
        Log.d(TAG, "loadNewsData() 진입")
        val activity = requireActivity() as? AfterExploreActivity ?: run {
            Log.e(TAG, "❌ activity cast 실패")
            return
        }
        val cardId = activity.currentCardId
        if (cardId.isEmpty()) {
            Log.e(TAG, "❌ cardId 비어 있음 → 조기 종료")
            return
        }

        lifecycleScope.launch {
            try {
                Log.d(TAG, "========== 치안 뉴스 API 호출 시작 (CardID: $cardId) ==========")
                val response = RetrofitClient.getInstance(requireContext())
                    .getAnalysisNews(cardId, period = 3, level = 3, page = 0)

                Log.d(TAG, "치안 뉴스 응답 코드: ${response.code()}")

                if (response.isSuccessful) {
                    val body = response.body() ?: emptyList()
                    Log.d(TAG, "치안 뉴스 총 그룹 수: ${body.size}")

                    body.forEachIndexed { idx, newsResponse ->
                        Log.d(TAG, "  [그룹 $idx] label=${newsResponse.label}, region=${newsResponse.regionName}, " +
                                "totalCount=${newsResponse.totalCount}, newsCount=${newsResponse.news.size}")
                        Log.d(TAG, "  level1Count=${newsResponse.level1Count}, level2Count=${newsResponse.level2Count}, level3Count=${newsResponse.level3Count}")
                        newsResponse.news.forEachIndexed { nIdx, item ->
                            Log.d(TAG, "    [뉴스 $nIdx] categoryLevel=${item.categoryLevel}, categoryTag=${item.categoryTag}, " +
                                    "publishedAt=${item.publishedAt}, title=${item.title}")
                            Log.d(TAG, "    summary=${item.summary}")
                        }
                    }

                    allNewsItems = body.flatMap { it.news }
                    Log.d(TAG, "치안 뉴스 합산 아이템 수: ${allNewsItems.size}")
                    Log.d(TAG, "========== 치안 뉴스 API 호출 종료 ==========")

                    val period = body.firstOrNull()?.period ?: 3
                    updateNewsFilter()
                    updateMonthText(period)
                } else {
                    Log.e(TAG, "❌ 치안 뉴스 API 실패: ${response.code()} ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ 치안 뉴스 예외 발생: ${e.message}", e)
            }
        }
    }

    private fun setupPublicPeaceRecycler() {
        publicPeaceAdapter = PublicPeaceAdapter(emptyList())
        binding.lengthPublicPeaceRv.layoutManager = LinearLayoutManager(requireContext())
        binding.lengthPublicPeaceRv.adapter = publicPeaceAdapter
    }

    private fun setupPublicPeaceListeners() {
        val ctx = requireContext()
        val brandColor = ContextCompat.getColor(ctx, R.color.brand_700)
        val grayColor = ContextCompat.getColor(ctx, R.color.gray_800)

        binding.lifeBtn.setOnClickListener {
            if (selectedTab != 0) {
                selectedTab = 0
                updateTabTint(brandColor, grayColor, grayColor)
                updateNewsFilter()
            }
        }
        binding.safeBtn.setOnClickListener {
            if (selectedTab != 1) {
                selectedTab = 1
                updateTabTint(grayColor, brandColor, grayColor)
                updateNewsFilter()
            }
        }
        binding.oneselfBtn.setOnClickListener {
            if (selectedTab != 2) {
                selectedTab = 2
                updateTabTint(grayColor, grayColor, brandColor)
                updateNewsFilter()
            }
        }

        binding.monthLl.setOnClickListener {
            isNewsExpanded = !isNewsExpanded
            binding.lengthPublicPeaceRv.visibility = if (isNewsExpanded) View.VISIBLE else View.GONE
            binding.arrowDownIv.rotation = if (isNewsExpanded) 180f else 0f
        }
    }

    private fun updateTabTint(lifeTint: Int, safeTint: Int, oneselfTint: Int) {
        val b = _binding ?: return
        b.lifeBtn.backgroundTintList = ColorStateList.valueOf(lifeTint)
        b.safeBtn.backgroundTintList = ColorStateList.valueOf(safeTint)
        b.oneselfBtn.backgroundTintList = ColorStateList.valueOf(oneselfTint)
    }

    private fun updateNewsFilter() {
        val filtered = allNewsItems.filter { getTabForCategory(it.categoryLevel) == selectedTab }
        Log.d(TAG, "탭=$selectedTab 필터 결과: ${filtered.size}개")
        if (::publicPeaceAdapter.isInitialized) {
            publicPeaceAdapter.updateItems(filtered)
        }
        if (filtered.isNotEmpty()) {
            isNewsExpanded = true
            _binding?.lengthPublicPeaceRv?.visibility = View.VISIBLE
            _binding?.arrowDownIv?.rotation = 180f
        }
        updateNewsEmptyState(filtered.isEmpty())
    }

    private fun updateNewsEmptyState(isEmpty: Boolean) {
        val b = _binding ?: return
        if (isEmpty) {
            b.monthTv.visibility = View.GONE
            b.arrowDownIv.visibility = View.GONE
            b.newsEmptyTv.visibility = View.VISIBLE
            b.monthLl.isClickable = false
            // 빈 상태면 접기
            isNewsExpanded = false
            b.lengthPublicPeaceRv.visibility = View.GONE
            b.arrowDownIv.rotation = 0f
        } else {
            b.monthTv.visibility = View.VISIBLE
            b.arrowDownIv.visibility = View.VISIBLE
            b.newsEmptyTv.visibility = View.GONE
            b.monthLl.isClickable = true
        }
    }

    private fun updateMonthText(period: Int) {
        _binding?.monthTv?.text = "${period}개월 이내"
    }

    private fun setupGraph(list: List<ScheduleItem>) {
        binding.beforeGraphRv.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

        graphAdapter = GraphAdapter(list) { description ->
            if (description.isEmpty()) {
                binding.graphDetailTv.visibility = View.GONE
            } else {
                binding.graphDetailTv.visibility = View.VISIBLE
                binding.graphDetailTv.text = description
                autoScrollToView(binding.graphDetailTv)
            }
        }
        binding.beforeGraphRv.adapter = graphAdapter
        graphAdapter.setMode(true)

        val favoriteViewModel = ViewModelProvider(requireActivity())[FavoriteViewModel::class.java]
        favoriteViewModel.favoriteSet.observe(viewLifecycleOwner) { favorites ->
            graphAdapter.updateFavorites(favorites)
        }
    }

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
                    if (targetY > parent.scrollY) parent.smoothScrollTo(0, targetY)
                    break
                }
                parent = parent.parent
            }
        }, 100)
    }

    private fun updateSafetySummaryText(list: List<ScheduleItem>) {
        if (list.isEmpty()) return
        val maxItem = list.maxByOrNull { it.dayScore }
        val maxRank = maxItem?.rankLabel ?: "-"
        val text = "안전 점수는 $maxRank 가 \n가장 높아요."
        val spannable = SpannableString(text)
        val brandColor = ContextCompat.getColor(requireContext(), R.color.brand_600)
        val rankIndex = text.indexOf(maxRank)
        if (rankIndex != -1) spannable.setSpan(ForegroundColorSpan(brandColor), rankIndex, rankIndex + maxRank.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.afterSafetyTv.text = spannable
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}