package com.keder.zply

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.keder.zply.databinding.FragmentExistingInfoBinding
import kotlinx.coroutines.launch

private const val TAG_SAFETY = "API_EXISTING_SAFETY"

class ExistingInfoFragment : Fragment() {

    private var _binding: FragmentExistingInfoBinding? = null
    private val binding get() = _binding!!

    private var cardId: String = ""

    private var currentTabIdx = 0

    private var isDayMode = true
    private var originalScheduleList: List<ScheduleItem> = emptyList()

    private var isFirstTabInit = true

    private var transportMessage: String = ""
    private var bicycleMessage: String = ""

    private lateinit var cardAdapter: ExistingInfoCardAdapter
    private lateinit var lengthAdapter: LengthRankAdapter
    private lateinit var graphAdapter: GraphAdapter
    private lateinit var safetyGraphAdapter: GraphAdapter
    private lateinit var publicPeaceAdapter: PublicPeaceAdapter

    private var allNewsItems: List<NewsItem> = emptyList()
    private var selectedNewsTab = 0  // 0=생활불편, 1=안전불안, 2=신변위협
    private var isNewsExpanded = false
    private var isDataLoading = false

    companion object {
        fun newInstance(cardId: String): ExistingInfoFragment {
            val fragment = ExistingInfoFragment()
            val args = Bundle().apply { putString("CARD_ID", cardId) }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExistingInfoBinding.inflate(inflater, container, false)
        cardId = arguments?.getString("CARD_ID") ?: ""
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (cardId.isEmpty()) return

        setupListeners()
        setupPublicPeaceSection()
        updateTabUI()
    }

    override fun onResume() {
        super.onResume()
    }

    fun refreshDataIfNeeded() {
        if (cardId.isNotEmpty() && !isDataLoading) {
            loadAllDataSafe()
        }
    }

    private fun setupListeners() {
        binding.tabDistance.setOnClickListener { if (currentTabIdx != 0) { currentTabIdx = 0; updateTabUI() } }
        binding.tabNoise.setOnClickListener { if (currentTabIdx != 1) { currentTabIdx = 1; updateTabUI() } }
        binding.tabSafety.setOnClickListener { if (currentTabIdx != 2) { currentTabIdx = 2; updateTabUI() } }

        binding.chipWalk.setOnClickListener { updateTransportUI(0) }
        binding.chipPublic.setOnClickListener { updateTransportUI(1) }
        binding.chipCar.setOnClickListener { updateTransportUI(2) }
        binding.chipBike.setOnClickListener { updateTransportUI(3) }

        val ctx = requireContext()
        val grayColor = ContextCompat.getColor(ctx, R.color.gray_900)
        val blueColor = ContextCompat.getColor(ctx, R.color.brand_700)

        binding.dayBtn.setOnClickListener {
            if (!isDayMode) {
                isDayMode = true
                binding.dayBtn.backgroundTintList = ColorStateList.valueOf(blueColor)
                binding.nightBtn.backgroundTintList = ColorStateList.valueOf(grayColor)
                if (::graphAdapter.isInitialized) graphAdapter.setMode(true)
                binding.graphDetailTv.visibility = View.GONE
            }
        }
        binding.nightBtn.setOnClickListener {
            if (isDayMode) {
                isDayMode = false
                binding.dayBtn.backgroundTintList = ColorStateList.valueOf(grayColor)
                binding.nightBtn.backgroundTintList = ColorStateList.valueOf(blueColor)
                if (::graphAdapter.isInitialized) graphAdapter.setMode(false)
                binding.graphDetailTv.visibility = View.GONE
            }
        }
    }

    private fun updateTabUI() {
        val ctx = context ?: return
        val selectedColor = ContextCompat.getColor(ctx, R.color.brand_700)
        val unselectedColor = ContextCompat.getColor(ctx, R.color.gray_500)

        val safeBinding = _binding ?: return

        safeBinding.tabDistance.setTextColor(if (currentTabIdx == 0) selectedColor else unselectedColor)
        safeBinding.tabNoise.setTextColor(if (currentTabIdx == 1) selectedColor else unselectedColor)
        safeBinding.tabSafety.setTextColor(if (currentTabIdx == 2) selectedColor else unselectedColor)

        safeBinding.layoutContentDistance.visibility = if (currentTabIdx == 0) View.VISIBLE else View.GONE
        safeBinding.layoutContentNoise.visibility = if (currentTabIdx == 1) View.VISIBLE else View.GONE
        safeBinding.layoutContentSafety.visibility = if (currentTabIdx == 2) View.VISIBLE else View.GONE

        val targetTab = when (currentTabIdx) {
            0 -> safeBinding.tabDistance
            1 -> safeBinding.tabNoise
            else -> safeBinding.tabSafety
        }

        targetTab.post {
            val currentBinding = _binding ?: return@post
            val tabLocation = IntArray(2)
            targetTab.getLocationInWindow(tabLocation)

            val parentLocation = IntArray(2)
            (currentBinding.tabIndicator.parent as View).getLocationInWindow(parentLocation)

            val targetX = (tabLocation[0] - parentLocation[0]).toFloat() - currentBinding.tabIndicator.left
            val targetWidth = targetTab.width

            if (targetWidth <= 0) {
                targetTab.post { updateTabUI() }
                return@post
            }

            if (isFirstTabInit) {
                isFirstTabInit = false
                currentBinding.tabIndicator.translationX = targetX
                val params = currentBinding.tabIndicator.layoutParams
                params.width = targetWidth
                currentBinding.tabIndicator.layoutParams = params
                return@post
            }

            val startX = currentBinding.tabIndicator.translationX
            val startWidth = currentBinding.tabIndicator.width

            val animator = android.animation.ValueAnimator.ofFloat(0f, 1f)
            animator.duration = 250
            animator.addUpdateListener { animation ->
                val activeBinding = _binding ?: return@addUpdateListener
                val fraction = animation.animatedFraction
                activeBinding.tabIndicator.translationX = startX + (targetX - startX) * fraction
                val params = activeBinding.tabIndicator.layoutParams
                params.width = (startWidth + (targetWidth - startWidth) * fraction).toInt()
                activeBinding.tabIndicator.layoutParams = params
            }
            animator.start()
        }
    }

    private fun updateTransportUI(mode: Int) {
        if (originalScheduleList.isEmpty()) return

        val ctx = context ?: return
        val safeBinding = _binding ?: return

        val chips = listOf(safeBinding.chipWalk, safeBinding.chipPublic, safeBinding.chipCar, safeBinding.chipBike)
        val selectedColor = ContextCompat.getColor(ctx, R.color.white)
        val unselectedColor = ContextCompat.getColor(ctx, R.color.gray_500)
        val brandColor = ContextCompat.getColor(ctx, R.color.brand_600)
        val grayColor = ContextCompat.getColor(ctx, R.color.gray_800)

        chips.forEachIndexed { index, textView ->
            if (index == mode) {
                textView.backgroundTintList = ColorStateList.valueOf(brandColor)
                textView.setTextColor(selectedColor)
            } else {
                textView.backgroundTintList = ColorStateList.valueOf(grayColor)
                textView.setTextColor(unselectedColor)
            }
        }

        val isInfoVisible = when (mode) {
            1 -> transportMessage.isNotEmpty()
            3 -> bicycleMessage.isNotEmpty()
            else -> false
        }

        safeBinding.tvTransportInfo.visibility = if (isInfoVisible) View.VISIBLE else View.GONE
        safeBinding.tvTransportInfo.text = if (mode == 1) transportMessage else bicycleMessage

        // ★ 직주거리 설명글이 보일 때 자동 스크롤 실행 (여백 억지 추가 삭제)
        if (isInfoVisible) {
            autoScrollToView(safeBinding.tvTransportInfo)
        }

        // 1. 시간이 0분인(서버에서 값이 없는) 데이터를 1순위로 정렬합니다.
        val sortedList = originalScheduleList.sortedWith(Comparator { a, b ->
            val timeA = when (mode) { 0 -> a.walkingTimeMin; 1 -> a.transitTimeMin; 2 -> a.carTimeMin; 3 -> a.bicycleTimeMin; else -> a.walkingTimeMin }
            val timeB = when (mode) { 0 -> b.walkingTimeMin; 1 -> b.transitTimeMin; 2 -> b.carTimeMin; 3 -> b.bicycleTimeMin; else -> b.walkingTimeMin }

            if (mode != 0) {
                val aMissing = timeA == 0 // 0분인지 확인
                val bMissing = timeB == 0 // 0분인지 확인

                if (aMissing && bMissing) {
                    a.walkingTimeMin.compareTo(b.walkingTimeMin)
                } else if (aMissing) {
                    -1 // A가 0분이면 최상단으로 끌어올림
                } else if (bMissing) {
                    1  // B가 0분이면 최상단으로 끌어올림
                } else {
                    timeA.compareTo(timeB)
                }
            } else {
                timeA.compareTo(timeB)
            }
        })

        if (!::lengthAdapter.isInitialized) {
            lengthAdapter = LengthRankAdapter(sortedList)
            safeBinding.lengthRankRv.adapter = lengthAdapter
        } else {
            lengthAdapter.updateList(sortedList)
        }
        lengthAdapter.setMode(mode)

        val brand700 = ContextCompat.getColor(ctx, R.color.brand_700)
        val shortestItem = sortedList.firstOrNull()

        if (shortestItem != null) {
            val rank = shortestItem.rankLabel
            val time = when (mode) {
                0 -> shortestItem.walkingTimeMin
                1 -> shortestItem.transitTimeMin
                2 -> shortestItem.carTimeMin
                3 -> shortestItem.bicycleTimeMin
                else -> shortestItem.walkingTimeMin
            }

            // 2. 도보가 아닌데 시간이 0분이면 "도보가 더 빨라요" 문구를 띄웁니다!
            if (mode != 0 && time == 0) {
                val text = "${rank}는 경로가 없어\n도보가 더 빨라요"
                val spannable = SpannableString(text)
                val idx = text.indexOf(rank)
                if (idx != -1) spannable.setSpan(ForegroundColorSpan(brand700), idx, idx + rank.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                safeBinding.lengthRankTv.text = spannable
            } else {
                val text = "직주거리는 $rank 가 \n가장 짧아요"
                val spannable = SpannableString(text)
                val idx = text.indexOf(rank)
                if (idx != -1) spannable.setSpan(ForegroundColorSpan(brand700), idx, idx + rank.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                safeBinding.lengthRankTv.text = spannable
            }
        } else {
            safeBinding.lengthRankTv.text = "경로를 찾을 수 없어요"
        }
    }

    private fun setupPublicPeaceSection() {
        publicPeaceAdapter = PublicPeaceAdapter(emptyList())
        binding.lengthPublicPeaceRv.layoutManager = LinearLayoutManager(requireContext())
        binding.lengthPublicPeaceRv.adapter = publicPeaceAdapter

        val ctx = requireContext()
        val brandColor = ContextCompat.getColor(ctx, R.color.brand_700)
        val grayColor = ContextCompat.getColor(ctx, R.color.gray_800)

        binding.lifeBtn.setOnClickListener {
            if (selectedNewsTab != 0) {
                selectedNewsTab = 0
                updateNewsTabTint(brandColor, grayColor, grayColor)
                updateNewsFilter()
            }
        }
        binding.safeBtn.setOnClickListener {
            if (selectedNewsTab != 1) {
                selectedNewsTab = 1
                updateNewsTabTint(grayColor, brandColor, grayColor)
                updateNewsFilter()
            }
        }
        binding.oneselfBtn.setOnClickListener {
            if (selectedNewsTab != 2) {
                selectedNewsTab = 2
                updateNewsTabTint(grayColor, grayColor, brandColor)
                updateNewsFilter()
            }
        }

        binding.monthLl.setOnClickListener {
            isNewsExpanded = !isNewsExpanded
            binding.lengthPublicPeaceRv.visibility = if (isNewsExpanded) View.VISIBLE else View.GONE
            binding.arrowDownIv.rotation = if (isNewsExpanded) 180f else 0f
        }
    }

    private fun updateNewsTabTint(lifeTint: Int, safeTint: Int, oneselfTint: Int) {
        val b = _binding ?: return
        b.lifeBtn.backgroundTintList = ColorStateList.valueOf(lifeTint)
        b.safeBtn.backgroundTintList = ColorStateList.valueOf(safeTint)
        b.oneselfBtn.backgroundTintList = ColorStateList.valueOf(oneselfTint)
    }

    private fun updateNewsFilter() {
        val filtered = allNewsItems.filter { item ->
            val level = item.categoryLevel
            when (selectedNewsTab) {
                0 -> level.contains("생활 불편") || level.contains("무질서")
                1 -> level.contains("안전 불안")
                2 -> level.contains("신변 위협") || level.contains("강력 범죄")
                else -> false
            }
        }
        Log.d(TAG_SAFETY, "뉴스 탭=$selectedNewsTab 필터 결과: ${filtered.size}개")
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

    private fun loadNewsData() {
        Log.d(TAG_SAFETY, "loadNewsData() 진입 - cardId=$cardId, context=${context != null}")
        val ctx = context ?: run {
            Log.e(TAG_SAFETY, "❌ loadNewsData() context null → 조기 종료")
            return
        }
        lifecycleScope.launch {
            try {
                Log.d(TAG_SAFETY, "========== 치안 뉴스 API 호출 시작 (CardID: $cardId) ==========")
                val response = RetrofitClient.getInstance(ctx)
                    .getAnalysisNews(cardId, period = 3, level = 3, page = 0)

                Log.d(TAG_SAFETY, "치안 뉴스 응답 코드: ${response.code()}")

                if (response.isSuccessful) {
                    val body = response.body() ?: emptyList()
                    Log.d(TAG_SAFETY, "치안 뉴스 총 그룹 수: ${body.size}")

                    body.forEachIndexed { idx, newsResponse ->
                        Log.d(TAG_SAFETY, "  [그룹 $idx] label=${newsResponse.label}, region=${newsResponse.regionName}, " +
                                "totalCount=${newsResponse.totalCount}, newsCount=${newsResponse.news.size}")
                        Log.d(TAG_SAFETY, "  level1Count=${newsResponse.level1Count}, level2Count=${newsResponse.level2Count}, level3Count=${newsResponse.level3Count}")
                        newsResponse.news.forEachIndexed { nIdx, item ->
                            Log.d(TAG_SAFETY, "    [뉴스 $nIdx] categoryLevel=${item.categoryLevel}, categoryTag=${item.categoryTag}, " +
                                    "publishedAt=${item.publishedAt}, title=${item.title}")
                            Log.d(TAG_SAFETY, "    summary=${item.summary}")
                        }
                    }

                    allNewsItems = body.flatMap { it.news }
                    Log.d(TAG_SAFETY, "치안 뉴스 합산 아이템 수: ${allNewsItems.size}")
                    Log.d(TAG_SAFETY, "========== 치안 뉴스 API 호출 종료 ==========")

                    val period = body.firstOrNull()?.period ?: 3
                    _binding?.monthTv?.text = "${period}개월 이내"
                    updateNewsFilter()
                } else {
                    Log.e(TAG_SAFETY, "❌ 치안 뉴스 API 실패: ${response.code()} ${response.errorBody()?.string()}")
                }
            } catch (e: Exception) {
                Log.e(TAG_SAFETY, "❌ 치안 뉴스 예외 발생: ${e.message}", e)
            }
        }
    }

    private fun loadAllDataSafe() {
        val ctx = context ?: return
        isDataLoading = true
        lifecycleScope.launch {
            _binding?.loadingLayout?.visibility = View.VISIBLE
            val service = RetrofitClient.getInstance(ctx)
            val prefs = ctx.getSharedPreferences("ZplyMeasurementPrefs", Context.MODE_PRIVATE)

            try {
                val houses = try { service.getCardHouseList(cardId).body() ?: emptyList() } catch (e: Exception) { emptyList() }

                val addressRes = try { service.getCardAddresses(cardId).body() } catch (e: Exception) { null }
                var companyAddr = "직장 정보 없음"
                if (!addressRes.isNullOrEmpty()) {
                    companyAddr = addressRes[0].address ?: "직장 정보 없음"
                }
                _binding?.lengthRankDesTv?.text = "[$companyAddr]부터 각 주거지까지의 거리예요."

                Log.d("API_DEBUG_DISTANCE", "========== 직주거리 데이터 로드 시작 (CardID: $cardId) ==========")
                val distBody = try {
                    service.getAnalysisDistance(cardId).body()?.firstOrNull()
                } catch (e: Exception) {
                    Log.e("API_DEBUG_DISTANCE", "거리 데이터 API 호출 에러", e)
                    null
                }

                Log.d("API_DEBUG_DISTANCE", "서버 응답 Body 전체: $distBody")

                transportMessage = distBody?.transportMessage ?: ""
                bicycleMessage = distBody?.bicycleMessage ?: ""
                Log.d("API_DEBUG_DISTANCE", "대중교통 메시지: $transportMessage")
                Log.d("API_DEBUG_DISTANCE", "자전거 메시지: $bicycleMessage")

                val distanceMap = distBody?.results?.associateBy { it.houseId } ?: emptyMap()

                distanceMap.forEach { (houseId, dist) ->
                    Log.d("API_DEBUG_DISTANCE", "[House ID: $houseId] 도보: ${dist.walkingTimeMin}분, 대중교통: ${dist.transitTimeMin}분, 자동차: ${dist.carTimeMin}분, 자전거: ${dist.bicycleTimeMin}분")
                }
                Log.d("API_DEBUG_DISTANCE", "========== 직주거리 데이터 로드 종료 ==========")

                val lifeMap = try { service.getAnalysisLife(cardId).body()?.associateBy { it.houseId } ?: emptyMap() } catch (e: Exception) { emptyMap() }

                val safetyRes = try { service.getAnalysisSafety(cardId).body() } catch (e: Exception) { null }

                val sortedHouses = houses.sortedBy { it.visitTime ?: "" }
                val detailList = mutableListOf<ScheduleItem>()

                for (house in sortedHouses) {
                    var displayLight = -1f
                    var measuredRooms = 0
                    val serverImageUrls = mutableListOf<String>()

                    try {
                        val detailRes = service.getHouseCardDetail(house.houseId).body()
                        if (detailRes != null) {
                            val cards = detailRes.measurementCards ?: emptyList()
                            measuredRooms = cards.count { it.isDirectionDone }
                            val validLights = cards.filter { it.isLightDone && it.lightLevel != null }
                            if (validLights.isNotEmpty()) displayLight = validLights.map { it.lightLevel!! }.average().toFloat()
                            if (!detailRes.imageUrls.isNullOrEmpty()) serverImageUrls.addAll(detailRes.imageUrls)
                        }
                    } catch (e: Exception) {}

                    val savedRoomCount = prefs.getInt("room_${house.houseId}", -1)
                    val savedLux = prefs.getFloat("lux_${house.houseId}", -1f)
                    val savedPhotosStr = prefs.getString("photos_${house.houseId}", "") ?: ""
                    val localPhotos = if (savedPhotosStr.isNotEmpty()) savedPhotosStr.split(",") else emptyList()

                    if (savedRoomCount >= 0) measuredRooms = savedRoomCount
                    if (savedLux >= 0f) displayLight = savedLux

                    val combinedImages = (serverImageUrls + localPhotos).distinct().toMutableList()

                    val dist = distanceMap[house.houseId]
                    val life = lifeMap[house.houseId]

                    detailList.add(ScheduleItem(
                        houseId = house.houseId,
                        address = house.address ?: "주소 없음",
                        time = house.visitTime?.replace("T", " ")?.take(16) ?: "",
                        rankLabel = house.label ?: "?",
                        measuredLightLux = displayLight,
                        measuredRoomCount = measuredRooms,
                        imageList = combinedImages,
                        walkingTimeMin = dist?.walkingTimeMin ?: 0,
                        walkingDistanceKm = dist?.walkingDistanceKm ?: 0.0,
                        transitTimeMin = dist?.transitTimeMin ?: 0,
                        transitPayment = dist?.transitPaymentStr ?: "",
                        carTimeMin = dist?.carTimeMin ?: 0,
                        bicycleTimeMin = dist?.bicycleTimeMin ?: 0,
                        dayScore = life?.dayScore ?: 0,
                        nightScore = life?.nightScore ?: 0,
                        dayDesc = life?.message ?: "",
                        nightDesc = life?.message ?: ""
                    ))
                }

                originalScheduleList = detailList.sortedBy { it.rankLabel }
                setupRecyclerViews(originalScheduleList)
                updateSummaries(originalScheduleList)
                updateTransportUI(0)

                val safetyUiList = safetyRes?.map { safety ->
                    ScheduleItem(
                        houseId = safety.houseId,
                        address = "", time = "",
                        dayScore = safety.safetyScore,
                        nightScore = 0,
                        dayDesc = safety.message ?: "CCTV ${safety.cctvCount}대 · 가로등 ${safety.streetlightCount}개 · 치안시설 ${safety.policeCount}곳",
                        nightDesc = "",
                        rankLabel = originalScheduleList.find { it.houseId == safety.houseId }?.rankLabel ?: "?"
                    )
                }?.sortedBy { it.rankLabel } ?: emptyList()

                if (safetyUiList.isNotEmpty()) {
                    setupSafetyGraph(safetyUiList)
                    updateSafetySummaryText(safetyUiList)
                }

                loadNewsData()

            } catch (e: Exception) {
                Log.e("ExistingInfo", "렌더링 에러", e)
                isDataLoading = false
                showErrorOverlay { loadAllDataSafe() }
            } finally {
                isDataLoading = false
                _binding?.loadingLayout?.visibility = View.GONE
            }
        }
    }

    // [1] 소음 그래프 처리 부분
    private fun setupRecyclerViews(list: List<ScheduleItem>) {
        cardAdapter = ExistingInfoCardAdapter(list, onImageClick = { clickedItem, clickedIndex ->
            showImageDialog(clickedItem, clickedIndex)
        })
        _binding?.existingCardRv?.adapter = cardAdapter

        graphAdapter = GraphAdapter(list) { desc ->
            if (desc.isNotEmpty()) {
                _binding?.graphDetailTv?.visibility = View.VISIBLE
                _binding?.graphDetailTv?.text = desc
                // ★ 자동 스크롤 실행
                _binding?.graphDetailTv?.let { autoScrollToView(it) }
            } else {
                _binding?.graphDetailTv?.visibility = View.GONE
            }
        }
        _binding?.graphRv?.adapter = graphAdapter
        graphAdapter.setMode(isDayMode)
    }

    // [2] 안전 그래프 처리 부분
    private fun setupSafetyGraph(list: List<ScheduleItem>) {
        _binding?.safetyGraphRv?.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        safetyGraphAdapter = GraphAdapter(list) { desc ->
            if (desc.isNotEmpty()) {
                _binding?.safetyGraphDetailTv?.visibility = View.VISIBLE
                _binding?.safetyGraphDetailTv?.text = desc
                // ★ 자동 스크롤 실행
                _binding?.safetyGraphDetailTv?.let { autoScrollToView(it) }
            } else {
                _binding?.safetyGraphDetailTv?.visibility = View.GONE
            }
        }
        _binding?.safetyGraphRv?.adapter = safetyGraphAdapter
        safetyGraphAdapter.setMode(true)
    }

    private fun showImageDialog(item: ScheduleItem, startIndex: Int) {
        if (item.imageList.isEmpty()) return
        val ctx = context ?: return

        val dialog = Dialog(ctx)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_after_image)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        dialog.window?.apply {
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.8f)
        }

        val rankTv = dialog.findViewById<TextView>(R.id.dialog_rank_tv)
        val dateTv = dialog.findViewById<TextView>(R.id.dialog_date_tv)
        val addressTv = dialog.findViewById<TextView>(R.id.dialog_address_tv)
        val closeBtn = dialog.findViewById<ImageView>(R.id.dialog_close_btn)
        val imageVp = dialog.findViewById<ViewPager2>(R.id.dialog_image_vp)
        val indicatorLl = dialog.findViewById<LinearLayout>(R.id.dialog_indicator_ll)

        rankTv.text = item.rankLabel
        dateTv.text = "${item.time} 탐색"
        addressTv.text = item.address

        imageVp.adapter = DialogImageAdapter(item.imageList)
        imageVp.setCurrentItem(startIndex, false)

        val dotCount = item.imageList.size
        val dots = arrayOfNulls<ImageView>(dotCount)
        val dpToPx = { dp: Int -> (dp * resources.displayMetrics.density).toInt() }
        val sizePx = dpToPx(4)
        val marginPx = dpToPx(4)

        for (i in 0 until dotCount) {
            dots[i] = ImageView(ctx).apply {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    setMargins(marginPx, 0, marginPx, 0)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    val colorRes = if (i == startIndex) R.color.brand_800 else R.color.gray_700
                    setColor(ContextCompat.getColor(ctx, colorRes))
                }
            }
            indicatorLl.addView(dots[i])
        }

        val pageCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                for (i in 0 until dotCount) {
                    val drawable = dots[i]?.background as? GradientDrawable
                    val colorRes = if (i == position) R.color.brand_800 else R.color.gray_700
                    drawable?.setColor(ContextCompat.getColor(ctx, colorRes))
                }
            }
        }
        imageVp.registerOnPageChangeCallback(pageCallback)
        dialog.setOnDismissListener { imageVp.unregisterOnPageChangeCallback(pageCallback) }

        closeBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun updateSafetySummaryText(list: List<ScheduleItem>) {
        val maxItem = list.maxByOrNull { it.dayScore }
        val maxRank = maxItem?.rankLabel ?: "-"
        val text = "안전 점수는 $maxRank 가 \n가장 높아요."
        val spannable = SpannableString(text)
        val brandColor = ContextCompat.getColor(requireContext(), R.color.brand_600)

        val rankIndex = text.indexOf(maxRank)
        if (rankIndex != -1) {
            spannable.setSpan(ForegroundColorSpan(brandColor), rankIndex, rankIndex + maxRank.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        _binding?.safetyTv?.text = spannable
    }

    private fun updateSummaries(list: List<ScheduleItem>) {
        val bestDay = list.filter { it.dayScore > 0 }.maxByOrNull { it.dayScore }
        val bestNight = list.filter { it.nightScore > 0 }.maxByOrNull { it.nightScore }

        val dayRank = bestDay?.rankLabel ?: "-"
        val nightRank = bestNight?.rankLabel ?: "-"
        val ctx = context ?: return
        val brandColor = ContextCompat.getColor(ctx, R.color.brand_700)

        val noiseText = "낮에는 $dayRank, 밤에는 $nightRank 가 \n소음이 가장 낮아요."
        val noiseSpan = SpannableString(noiseText)
        val dayIdx = noiseText.indexOf(dayRank)
        if (dayIdx != -1) noiseSpan.setSpan(ForegroundColorSpan(brandColor), dayIdx, dayIdx + dayRank.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        val nightIdx = noiseText.lastIndexOf(nightRank)
        if (nightIdx != -1) noiseSpan.setSpan(ForegroundColorSpan(brandColor), nightIdx, nightIdx + nightRank.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        _binding?.noiseTv?.text = noiseSpan
    }

    fun updateMeasurementLocal(houseId: Long, roomCount: Int = -1, lightLux: Float = -1f, newImagePath: String? = null) {
        val index = originalScheduleList.indexOfFirst { it.houseId == houseId }

        if (index != -1) {
            val item = originalScheduleList[index]
            if (roomCount >= 0) item.measuredRoomCount = roomCount
            if (lightLux >= 0f) item.measuredLightLux = lightLux

            if (newImagePath != null) {
                if (!item.imageList.contains(newImagePath)) {
                    item.imageList.add(newImagePath)
                }
            }

            if (::cardAdapter.isInitialized) {
                activity?.runOnUiThread {
                    cardAdapter.notifyItemChanged(index)
                }
            }
        }
    }

    // ★ 자동 스크롤 함수 (여백 강제 할당 없이 순수하게 뷰를 찾아 스크롤)
    private fun autoScrollToView(targetView: View) {
        targetView.postDelayed({
            var parent = targetView.parent
            while (parent != null) {
                if (parent is androidx.core.widget.NestedScrollView) {
                    val rect = android.graphics.Rect()
                    targetView.getDrawingRect(rect)
                    parent.offsetDescendantRectToMyCoords(targetView, rect)

                    // 부모 스크롤뷰가 내려갈 수 있는 최대 한계치를 구합니다.
                    val maxScrollY = parent.getChildAt(0).height - parent.height

                    // 목표 위치를 계산하되, 스크롤의 최대 한계치를 넘지 않도록 안전장치를 겁니다.
                    val targetY = (rect.bottom - parent.height + 100).coerceAtMost(maxScrollY)

                    // 현재보다 더 아래에 내용이 있을 때만 스크롤을 내립니다.
                    if (targetY > parent.scrollY) {
                        parent.smoothScrollTo(0, targetY)
                    }
                    break
                }
                parent = parent.parent
            }
        }, 100)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}