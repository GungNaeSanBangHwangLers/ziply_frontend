package com.keder.zply

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.keder.zply.databinding.ActivityBeforeExploreBinding
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapFragment
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.overlay.Marker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BeforeExploreActivity : AppCompatActivity(), OnMapReadyCallback {

    private val TAG = "BeforeExplore"
    private lateinit var binding: ActivityBeforeExploreBinding
    private lateinit var mainListAdapter: BeforeExploreAdapter
    private lateinit var lengthAdapter: LengthRankAdapter
    private lateinit var graphAdapter: GraphAdapter
    private lateinit var safetyGraphAdapter: GraphAdapter

    private var currentCardId: String = ""
    private var isMapExpanded = false
    private var currentTabIdx = 0
    private var isDayMode = true

    private var originalScheduleList: List<ScheduleItem> = emptyList()
    private var transportMessage: String = ""
    private var bicycleMessage: String = ""

    private var naverMap: NaverMap? = null
    private val markerList = mutableListOf<Marker>()
    private var mapInfoList: List<MapInfoResponse> = emptyList()

    private var isNavigatingBack = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBeforeExploreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentCardId = intent.getStringExtra("CARD_ID") ?: ""
        if (currentCardId.isEmpty()) { finish(); return }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goBackToMain() }
        })

        val fm = supportFragmentManager
        var mapFragment = fm.findFragmentById(R.id.naver_map_fragment) as MapFragment?
        if (mapFragment == null) {
            mapFragment = MapFragment.newInstance()
            fm.beginTransaction().add(R.id.naver_map_fragment, mapFragment).commit()
        }
        mapFragment.getMapAsync(this)

        setupListeners()
        updateTabUI()
        loadAllDataSafe()
    }

    override fun onMapReady(map: NaverMap) {
        naverMap = map
        naverMap?.uiSettings?.apply { isZoomControlEnabled = false; isScaleBarEnabled = false }
        drawMarkersIfReady()
    }

    private fun goBackToMain() {
        if (isNavigatingBack) return
        isNavigatingBack = true

        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    private fun setupListeners() {
        binding.beforeBackIv.setOnClickListener { goBackToMain() }
        binding.btnToggleMap.setOnClickListener { toggleMap() }

        binding.tabDistance.setOnClickListener { if (currentTabIdx != 0) { currentTabIdx = 0; updateTabUI() } }
        binding.tabNoise.setOnClickListener { if (currentTabIdx != 1) { currentTabIdx = 1; updateTabUI() } }
        binding.tabSafety.setOnClickListener { if (currentTabIdx != 2) { currentTabIdx = 2; updateTabUI() } }

        binding.chipWalk.setOnClickListener { updateTransportUI(0) }
        binding.chipPublic.setOnClickListener { updateTransportUI(1) }
        binding.chipCar.setOnClickListener { updateTransportUI(2) }
        binding.chipBike.setOnClickListener { updateTransportUI(3) }

        val grayColor = ContextCompat.getColor(this, R.color.gray_900)
        val blueColor = ContextCompat.getColor(this, R.color.brand_700)

        binding.beforeDayBtn.setOnClickListener {
            if (!isDayMode) {
                isDayMode = true
                binding.beforeDayBtn.backgroundTintList = ColorStateList.valueOf(blueColor)
                binding.beforeNightBtn.backgroundTintList = ColorStateList.valueOf(grayColor)
                if (::graphAdapter.isInitialized) graphAdapter.setMode(true)
                binding.graphDetailTv.visibility = View.GONE
            }
        }
        binding.beforeNightBtn.setOnClickListener {
            if (isDayMode) {
                isDayMode = false
                binding.beforeDayBtn.backgroundTintList = ColorStateList.valueOf(grayColor)
                binding.beforeNightBtn.backgroundTintList = ColorStateList.valueOf(blueColor)
                if (::graphAdapter.isInitialized) graphAdapter.setMode(false)
                binding.graphDetailTv.visibility = View.GONE
            }
        }
    }

    private fun toggleMap() {
        isMapExpanded = !isMapExpanded
        if (isMapExpanded) {
            binding.layoutMapContainer.visibility = View.VISIBLE
            binding.tvMapToggle.text = "접기"
            binding.ivMapArrow.setImageResource(R.drawable.ic_arrow_up)
        } else {
            binding.layoutMapContainer.visibility = View.GONE
            binding.tvMapToggle.text = "지도 보기"
            binding.ivMapArrow.setImageResource(R.drawable.ic_arrow_down)
        }
    }

    private fun updateTabUI() {
        val selectedColor = ContextCompat.getColor(this, R.color.brand_700)
        val unselectedColor = ContextCompat.getColor(this, R.color.gray_500)

        binding.tabDistance.setTextColor(if (currentTabIdx == 0) selectedColor else unselectedColor)
        binding.tabNoise.setTextColor(if (currentTabIdx == 1) selectedColor else unselectedColor)
        binding.tabSafety.setTextColor(if (currentTabIdx == 2) selectedColor else unselectedColor)

        binding.layoutContentDistance.visibility = if (currentTabIdx == 0) View.VISIBLE else View.GONE
        binding.layoutContentNoise.visibility = if (currentTabIdx == 1) View.VISIBLE else View.GONE
        binding.layoutContentSafety.visibility = if (currentTabIdx == 2) View.VISIBLE else View.GONE

        val targetTab = when (currentTabIdx) {
            0 -> binding.tabDistance
            1 -> binding.tabNoise
            else -> binding.tabSafety
        }

        targetTab.post {
            // ★ 화면 파괴 시 애니메이션 중지 방어코드
            if (isFinishing || isDestroyed) return@post

            val startX = binding.tabIndicator.translationX
            val targetX = targetTab.left.toFloat()
            val startWidth = binding.tabIndicator.width
            val targetWidth = targetTab.width

            val animator = android.animation.ValueAnimator.ofFloat(0f, 1f)
            animator.duration = 250
            animator.addUpdateListener { animation ->
                // ★ 애니메이션 실행 도중 화면 꺼지면 즉시 중지
                if (isFinishing || isDestroyed) return@addUpdateListener

                val fraction = animation.animatedFraction
                binding.tabIndicator.translationX = startX + (targetX - startX) * fraction
                val params = binding.tabIndicator.layoutParams
                params.width = (startWidth + (targetWidth - startWidth) * fraction).toInt()
                binding.tabIndicator.layoutParams = params
            }
            animator.start()
        }
    }

    private fun updateTransportUI(mode: Int) {
        val chips = listOf(binding.chipWalk, binding.chipPublic, binding.chipCar, binding.chipBike)
        val selectedTextColor = ContextCompat.getColor(this, R.color.white)
        val unselectedTextColor = ContextCompat.getColor(this, R.color.gray_500)
        val brandColor = ContextCompat.getColor(this, R.color.brand_600)
        val grayColor = ContextCompat.getColor(this, R.color.gray_800)

        chips.forEachIndexed { index, textView ->
            if (index == mode) {
                textView.backgroundTintList = ColorStateList.valueOf(brandColor)
                textView.setTextColor(selectedTextColor)
            } else {
                textView.backgroundTintList = ColorStateList.valueOf(grayColor)
                textView.setTextColor(unselectedTextColor)
            }
        }

        when (mode) {
            1 -> {
                binding.tvTransportInfo.visibility = if (transportMessage.isNotEmpty()) View.VISIBLE else View.GONE
                binding.tvTransportInfo.text = transportMessage
            }
            3 -> {
                binding.tvTransportInfo.visibility = if (bicycleMessage.isNotEmpty()) View.VISIBLE else View.GONE
                binding.tvTransportInfo.text = bicycleMessage
            }
            else -> binding.tvTransportInfo.visibility = View.GONE
        }

        if (originalScheduleList.isNotEmpty()) {
            val sortedList = originalScheduleList.sortedBy { item ->
                when (mode) { 0 -> item.walkingTimeMin; 1 -> item.transitTimeMin; 2 -> item.carTimeMin; 3 -> item.bicycleTimeMin; else -> item.walkingTimeMin }
            }
            lengthAdapter = LengthRankAdapter(sortedList)
            lengthAdapter.setMode(mode)
            binding.beforeLengthRankRv.adapter = lengthAdapter

            val shortestItem = originalScheduleList.filter { it.walkingDistanceKm > 0.0 || it.walkingTimeMin > 0 }
                .minByOrNull { if (it.walkingDistanceKm > 0.0) it.walkingDistanceKm else it.walkingTimeMin.toDouble() }

            if (shortestItem != null) {
                val rank = shortestItem.rankLabel
                val text = "직주거리는 $rank 가 \n가장 짧아요"
                val spannable = SpannableString(text)
                val idx = text.indexOf(rank)
                if (idx != -1) spannable.setSpan(ForegroundColorSpan(ContextCompat.getColor(this, R.color.brand_700)), idx, idx + rank.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                binding.beforeLengthRankTv.text = spannable
            }
        }
    }

    private fun loadAllDataSafe(isReload: Boolean = false) {
        lifecycleScope.launch {
            binding.loadingLayout.visibility = View.VISIBLE
            val service = RetrofitClient.getInstance(this@BeforeExploreActivity)

            var retryCount = 0
            val maxRetries = 10

            var finalHouses: List<HouseResponse>? = null
            var finalDistanceMap: Map<Long, DistanceResult>? = null
            var finalLifeMap: Map<Long, LifeResponse>? = null
            var finalSafetyList: List<SafetyResponse>? = null

            while (retryCount < maxRetries) {
                try {
                    val hResResponse = service.getCardHouseList(currentCardId)
                    val hRes = hResResponse.body()

                    val isCardDeleted = hResResponse.code() == 404 || (hResResponse.isSuccessful && hRes != null && hRes.isEmpty())

                    if (isReload && isCardDeleted) {
                        binding.loadingLayout.visibility = View.GONE
                        showCustomToast2("해당 탐색 스케줄에 대한 모든 일정이 삭제됐습니다.")
                        goBackToMain()
                        return@launch
                    }

                    if (!hRes.isNullOrEmpty()) finalHouses = hRes

                    val dResList = service.getAnalysisDistance(currentCardId).body()
                    val dRes = dResList?.firstOrNull()
                    if (dRes != null && !dRes.results.isNullOrEmpty()) {
                        transportMessage = dRes.transportMessage ?: ""
                        bicycleMessage = dRes.bicycleMessage ?: ""
                        finalDistanceMap = dRes.results.associateBy { it.houseId }
                    }

                    val lRes = service.getAnalysisLife(currentCardId).body()
                    if (!lRes.isNullOrEmpty()) finalLifeMap = lRes.associateBy { it.houseId }

                    val sRes = service.getAnalysisSafety(currentCardId).body()
                    if (!sRes.isNullOrEmpty()) finalSafetyList = sRes

                    val targetHouseCount = finalHouses?.size ?: 0
                    val currentHouseIds = finalHouses?.map { it.houseId } ?: emptyList()

                    val isDistReady = finalDistanceMap != null && finalDistanceMap.keys.containsAll(currentHouseIds)
                    val isLifeReady = finalLifeMap != null && finalLifeMap.keys.containsAll(currentHouseIds)
                    val isSafetyReady = finalSafetyList != null && finalSafetyList.map { it.houseId }.containsAll(currentHouseIds)

                    if (targetHouseCount >= 1 && isDistReady && isLifeReady && isSafetyReady) {
                        break
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "데이터 대기 중 예외 발생", e)
                }

                kotlinx.coroutines.delay(1500)
                retryCount++
            }

            if (finalHouses != null && finalHouses.isNotEmpty()) {
                renderUI(finalHouses, finalDistanceMap, finalLifeMap, finalSafetyList)
            } else if (!isReload) {
                showCustomToast("데이터를 불러오는 중입니다.")
            }
            binding.loadingLayout.visibility = View.GONE
        }
    }

    private fun renderUI(houses: List<HouseResponse>, distMap: Map<Long, DistanceResult>?, lifeMap: Map<Long, LifeResponse>?, safetyList: List<SafetyResponse>?) {
        val service = RetrofitClient.getInstance(this)
        lifecycleScope.launch {
            var companyAddr = "직장 정보 없음"
            try {
                val addrRes = service.getCardAddresses(currentCardId)
                if (addrRes.isSuccessful && !addrRes.body().isNullOrEmpty()) companyAddr = addrRes.body()!![0].address ?: ""
                val mapRes = service.getCardMapInfo(currentCardId)
                if (mapRes.isSuccessful && mapRes.body() != null) {
                    mapInfoList = mapRes.body()!!
                    drawMarkersIfReady()
                }
            } catch (e: Exception) {}

            binding.beforeMyAddressTv.text = companyAddr
            binding.beforeLengthRankDesTv.text = "[$companyAddr]부터 각 주거지까지의 거리예요."

            originalScheduleList = houses.map { house ->
                val dist = distMap?.get(house.houseId)
                val life = lifeMap?.get(house.houseId)
                ScheduleItem(
                    houseId = house.houseId, address = house.address ?: "",
                    // ★ 서버에서 온 T 제거 및 포맷팅 (초 자르기)
                    time = house.visitTime?.replace("T", " ")?.take(16) ?: "", rankLabel = house.label ?: "?",
                    walkingTimeMin = dist?.walkingTimeMin ?: 0, walkingDistanceKm = dist?.walkingDistanceKm ?: 0.0,
                    transitTimeMin = dist?.transitTimeMin ?: 0, transitPayment = dist?.transitPaymentStr ?: "",
                    carTimeMin = dist?.carTimeMin ?: 0, bicycleTimeMin = dist?.bicycleTimeMin ?: 0,
                    dayScore = life?.dayScore ?: 0, nightScore = life?.nightScore ?: 0,
                    dayDesc = life?.message ?: "", nightDesc = life?.message ?: ""
                )
            }.sortedBy { it.rankLabel }

            setupRecyclerViews(originalScheduleList)
            updateSummaries(originalScheduleList)
            updateTransportUI(0)

            safetyList?.let { list ->
                val safetyUiList = list.map { s ->
                    ScheduleItem(
                        houseId = s.houseId, address = "", time = "",
                        dayScore = s.safetyScore, nightScore = 0,
                        dayDesc = s.message ?: "CCTV ${s.cctvCount}대 · 가로등 ${s.streetlightCount}개 · 치안시설 ${s.policeCount}곳",
                        rankLabel = originalScheduleList.find { it.houseId == s.houseId }?.rankLabel ?: "?"
                    )
                }.sortedBy { it.rankLabel }
                setupSafetyGraph(safetyUiList)
                updateSafetySummaryText(safetyUiList)
            }
        }
    }

    private fun setupSafetyGraph(list: List<ScheduleItem>) {
        binding.beforeSafetyGraphRv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        safetyGraphAdapter = GraphAdapter(list) { desc ->
            binding.beforeSafetyGraphDetailTv.visibility = if (desc.isNotEmpty()) View.VISIBLE else View.GONE
            binding.beforeSafetyGraphDetailTv.text = desc
        }
        binding.beforeSafetyGraphRv.adapter = safetyGraphAdapter
        safetyGraphAdapter.setMode(true)
    }

    private fun updateSafetySummaryText(list: List<ScheduleItem>) {
        val maxItem = list.maxByOrNull { it.dayScore }
        val maxRank = maxItem?.rankLabel ?: "-"
        val text = "안전 점수는 $maxRank 가 \n가장 높아요."
        val spannable = SpannableString(text)
        val brandColor = ContextCompat.getColor(this, R.color.brand_600)
        val rankIndex = text.indexOf(maxRank)
        if (rankIndex != -1) spannable.setSpan(ForegroundColorSpan(brandColor), rankIndex, rankIndex + maxRank.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.beforeSafetyTv.text = spannable
    }

    private fun drawMarkersIfReady() {
        val map = naverMap ?: return
        if (mapInfoList.isEmpty()) return
        markerList.forEach { it.map = null }
        markerList.clear()
        val boundsBuilder = LatLngBounds.Builder()
        mapInfoList.forEach { mapInfo ->
            val position = LatLng(mapInfo.latitude, mapInfo.longitude)
            val marker = Marker()
            marker.position = position
            marker.icon = if (mapInfo.label == "기준지" || mapInfo.label == "직장") com.naver.maps.map.overlay.OverlayImage.fromResource(R.drawable.ic_marker_home) else createCustomMarker(mapInfo.label)
            marker.map = map
            markerList.add(marker)
            boundsBuilder.include(position)
        }
        if (markerList.isNotEmpty()) map.moveCamera(CameraUpdate.fitBounds(boundsBuilder.build(), 100))
    }

    private fun createCustomMarker(rank: String): com.naver.maps.map.overlay.OverlayImage {
        val view = layoutInflater.inflate(R.layout.item_custom_marker, null)
        val bgIv = view.findViewById<android.widget.ImageView>(R.id.marker_bg_iv)
        val textTv = view.findViewById<android.widget.TextView>(R.id.marker_text_tv)
        textTv.text = rank
        val rankChar = if (rank.isNotEmpty()) rank[0] else '?'
        val bgColor = when (rankChar) {
            'A' -> R.color.brand_100; 'B' -> R.color.brand_400; 'C' -> R.color.brand_700; 'D' -> R.color.brand_950; 'E' -> R.color.white; 'F' -> R.color.gray_400; 'G' -> R.color.gray_700; else -> R.color.gray_200
        }
        val textColor = if (rankChar == 'A' || rankChar == 'E' || rankChar == '?') R.color.brand_800 else R.color.white
        bgIv.imageTintList = ColorStateList.valueOf(ContextCompat.getColor(this, bgColor))
        textTv.setTextColor(ContextCompat.getColor(this, textColor))
        return com.naver.maps.map.overlay.OverlayImage.fromView(view)
    }

    private fun setupRecyclerViews(list: List<ScheduleItem>) {
        mainListAdapter = BeforeExploreAdapter(list) { clickedItem ->
            val bottomSheet = AddScheduleBottomSheet.newInstance(clickedItem.houseId, clickedItem.address, clickedItem.time)

            // ★ 수정/삭제 완료 시 API 재호출하여 화면 갱신
            bottomSheet.onSaveCompleted = {
                lifecycleScope.launch {
                    // 서버 DB 처리가 완료될 수 있도록 아주 짧은 딜레이 부여 (안전장치)
                    kotlinx.coroutines.delay(300)
                    loadAllDataSafe(isReload = true)
                }
            }
            bottomSheet.show(supportFragmentManager, "EditScheduleBottomSheet")
        }
        binding.beforeExploreListRv.adapter = mainListAdapter

        graphAdapter = GraphAdapter(list) { desc ->
            binding.graphDetailTv.visibility = if (desc.isNotEmpty()) View.VISIBLE else View.GONE
            binding.graphDetailTv.text = desc
        }
        binding.beforeGraphRv.adapter = graphAdapter
        graphAdapter.setMode(isDayMode)
    }

    private fun updateSummaries(list: List<ScheduleItem>) {
        val bestDay = list.filter { it.dayScore > 0 }.maxByOrNull { it.dayScore }
        val bestNight = list.filter { it.nightScore > 0 }.maxByOrNull { it.nightScore }
        val dayRank = bestDay?.rankLabel ?: "-"
        val nightRank = bestNight?.rankLabel ?: "-"
        val brandColor = ContextCompat.getColor(this, R.color.brand_700)
        val noiseText = "낮에는 $dayRank, 밤에는 $nightRank 가 \n소음이 가장 낮아요."
        val noiseSpan = SpannableString(noiseText)
        val dayIdx = noiseText.indexOf(dayRank)
        if (dayIdx != -1) noiseSpan.setSpan(ForegroundColorSpan(brandColor), dayIdx, dayIdx + dayRank.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        val nightIdx = noiseText.lastIndexOf(nightRank)
        if (nightIdx != -1) noiseSpan.setSpan(ForegroundColorSpan(brandColor), nightIdx, nightIdx + nightRank.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.beforeNoiseTv.text = noiseSpan
    }
}