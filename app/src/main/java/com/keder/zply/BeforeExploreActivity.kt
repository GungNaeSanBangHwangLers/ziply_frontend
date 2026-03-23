package com.keder.zply

import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.keder.zply.databinding.ActivityBeforeExploreBinding
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapFragment
import com.naver.maps.map.NaverMap
import com.naver.maps.map.NaverMapSdk
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.overlay.Marker
import kotlinx.coroutines.launch

class BeforeExploreActivity : AppCompatActivity(), OnMapReadyCallback {

    private val TAG = "BeforeExplore"
    private lateinit var binding: ActivityBeforeExploreBinding
    private lateinit var mainListAdapter: BeforeExploreAdapter
    private lateinit var lengthAdapter: LengthRankAdapter
    private lateinit var graphAdapter: GraphAdapter

    private var currentCardId: String = ""
    private var isMapExpanded = false
    private var isDistanceTabSelected = true
    private var isDayMode = true

    private var originalScheduleList: List<ScheduleItem> = emptyList()
    private var transportMessage: String = ""
    private var bicycleMessage: String = ""

    private var naverMap: NaverMap? = null
    private val markerList = mutableListOf<Marker>()
    private var mapInfoList: List<MapInfoResponse> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            val appInfo = packageManager.getApplicationInfo(packageName, android.content.pm.PackageManager.GET_META_DATA)
            val clientId = appInfo.metaData?.getString("com.naver.maps.map.CLIENT_ID")
            Log.e("MAP_DEBUG_TEST", "=======================================")
            Log.e("MAP_DEBUG_TEST", "1. 실제 구동 중인 패키지명: $packageName")
            Log.e("MAP_DEBUG_TEST", "2. 매니페스트에 박힌 ID: $clientId")
            Log.e("MAP_DEBUG_TEST", "=======================================")
        } catch (e: Exception) {
            Log.e("MAP_DEBUG_TEST", "정보 읽기 실패", e)
        }
        binding = ActivityBeforeExploreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentCardId = intent.getStringExtra("CARD_ID") ?: ""
        if (currentCardId.isEmpty()) { finish(); return }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() { goToMainActivity() }
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

    private fun goToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setupListeners() {
        binding.beforeBackIv.setOnClickListener { goToMainActivity() }
        binding.btnToggleMap.setOnClickListener { toggleMap() }
        binding.tabDistance.setOnClickListener { if (!isDistanceTabSelected) { isDistanceTabSelected = true; updateTabUI() } }
        binding.tabNoise.setOnClickListener { if (isDistanceTabSelected) { isDistanceTabSelected = false; updateTabUI() } }
        binding.chipWalk.setOnClickListener { updateTransportUI(0) }
        binding.chipPublic.setOnClickListener { updateTransportUI(1) }
        binding.chipCar.setOnClickListener { updateTransportUI(2) }
        binding.chipBike.setOnClickListener { updateTransportUI(3) }

        val context = this
        val grayColor = ContextCompat.getColor(context, R.color.gray_900)
        val blueColor = ContextCompat.getColor(context, R.color.brand_700)


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
        val targetTab = if (isDistanceTabSelected) binding.tabDistance else binding.tabNoise

        if (isDistanceTabSelected) {
            binding.tabDistance.setTextColor(selectedColor)
            binding.tabNoise.setTextColor(unselectedColor)
            binding.layoutContentDistance.visibility = View.VISIBLE
            binding.layoutContentNoise.visibility = View.GONE
        } else {
            binding.tabDistance.setTextColor(unselectedColor)
            binding.tabNoise.setTextColor(selectedColor)
            binding.layoutContentDistance.visibility = View.GONE
            binding.layoutContentNoise.visibility = View.VISIBLE
        }

        targetTab.post {
            val startX = binding.tabIndicator.translationX
            val targetX = targetTab.left.toFloat()
            val startWidth = binding.tabIndicator.width
            val targetWidth = targetTab.width

            val animator = android.animation.ValueAnimator.ofFloat(0f, 1f)
            animator.duration = 250
            animator.addUpdateListener { animation ->
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
                when (mode) {
                    0 -> item.walkingTimeMin; 1 -> item.transitTimeMin; 2 -> item.carTimeMin; 3 -> item.bicycleTimeMin; else -> item.walkingTimeMin
                }
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
            } else {
                binding.beforeLengthRankTv.text = "직주거리 정보를 찾을 수 없어요"
            }
        }
    }

    private fun loadAllDataSafe() {
        lifecycleScope.launch {
            binding.loadingLayout.visibility = View.VISIBLE
            val service = RetrofitClient.getInstance(this@BeforeExploreActivity)

            Log.d("API_TEST", "==================================================")
            Log.d("API_TEST", "데이터 로드 시작! 현재 Card ID: $currentCardId")
            var retryCount = 0
            val maxRetries = 15 // 여유롭게 최대 15번 (약 15초) 대기

            var houses = emptyList<HouseResponse>()
            var distanceMap = emptyMap<Long, DistanceResult>()
            var lifeMap = emptyMap<Long, LifeResponse>()
            var isDataReady = false

            // =========================================================
            // ★ 수정 1. 집 목록뿐만 아니라 '거리'와 '소음' 데이터도 계산이 끝났는지 같이 검사합니다!
            // =========================================================
// =========================================================
            // ★ 수정: 껍데기만 왔는지, 알맹이(results)까지 꽉 차 있는지 독하게 검사합니다!
            // =========================================================
            while (retryCount < maxRetries) {
                try {
                    Log.d("API_TEST", "데이터 계산 완료 확인 시도... (${retryCount + 1}/$maxRetries)")
                    val houseRes = service.getCardHouseList(currentCardId)
                    val distRes = service.getAnalysisDistance(currentCardId)
                    val lifeRes = service.getAnalysisLife(currentCardId)

                    val housesBody = houseRes.body()
                    val distBodyList = distRes.body()
                    val lifeBody = lifeRes.body()

                    // 1. 집 목록이 제대로 왔는가?
                    val isHouseReady = houseRes.isSuccessful && !housesBody.isNullOrEmpty()

                    // 2. ★ 핵심: 직주거리 데이터의 'results' 알맹이가 1개 이상 들어있는가?
                    val isDistReady = distRes.isSuccessful &&
                            !distBodyList.isNullOrEmpty() &&
                            !distBodyList[0].results.isNullOrEmpty()

                    // 3. 소음 데이터가 제대로 왔는가?
                    val isLifeReady = lifeRes.isSuccessful && !lifeBody.isNullOrEmpty()

                    // 세 가지 알맹이가 모두 꽉 차 있을 때만 비로소 탈출!
                    if (isHouseReady && isDistReady && isLifeReady) {
                        houses = housesBody!!

                        val distBody = distBodyList!![0]
                        transportMessage = distBody.transportMessage ?: ""
                        bicycleMessage = distBody.bicycleMessage ?: ""
                        distanceMap = distBody.results?.associateBy { it.houseId } ?: emptyMap()

                        lifeMap = lifeBody!!.associateBy { it.houseId }

                        isDataReady = true
                        Log.d("API_TEST", "모든 데이터 알맹이 수신 완료! 루프 탈출")
                        break
                    } else {
                        Log.w("API_TEST", "껍데기만 옴. 서버 계산 대기 중...")
                    }
                } catch (e: Exception) {
                    Log.e("API_TEST", "통신 지연 대기 중...", e)
                }

                // 서버가 계산할 시간을 주기 위해 1초 대기 후 다시 찔러봄
                kotlinx.coroutines.delay(1000)
                retryCount++
            }
            // 끝내 서버 계산이 안 끝났다면 에러 처리
            if (!isDataReady) {
                Toast.makeText(this@BeforeExploreActivity, "서버 분석이 지연되고 있습니다. 잠시 후 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                binding.loadingLayout.visibility = View.GONE
                return@launch
            }

            try {
                // ----------------------------------------------------
                // 주소 및 지도 마커 정보 조회 (얘네는 바로바로 옴)
                // ----------------------------------------------------
                var companyAddr = "직장 정보 없음"
                try {
                    val addrRes = service.getCardAddresses(currentCardId)
                    if (addrRes.isSuccessful && !addrRes.body().isNullOrEmpty()) {
                        companyAddr = addrRes.body()!![0].address ?: "주소 없음"
                    }
                } catch (e: Exception) { Log.e("API_TEST", "주소 예외", e) }

                try {
                    val mapRes = service.getCardMapInfo(currentCardId)
                    if (mapRes.isSuccessful && mapRes.body() != null) {
                        mapInfoList = mapRes.body()!!
                        drawMarkersIfReady()
                    }
                } catch (e: Exception) { Log.e("API_TEST", "마커 예외", e) }

                // ----------------------------------------------------
                // UI 렌더링 조립
                // ----------------------------------------------------
                binding.beforeMyAddressTv.text = companyAddr
                binding.beforeLengthRankDesTv.text = "[$companyAddr]부터 각 주거지까지의 거리예요."

                // =========================================================
                // ★ 수정 2. 마지막에 .sortedBy { it.rankLabel } 을 붙여서 무조건 A, B, C 순서로 강제 정렬합니다!
                // =========================================================
                originalScheduleList = houses.map { house ->
                    val dist = distanceMap[house.houseId]
                    val life = lifeMap[house.houseId]

                    ScheduleItem(
                        houseId = house.houseId,
                        address = house.address ?: "주소 없음",
                        time = house.visitTime ?: "",
                        rankLabel = house.label ?: "?",
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
                    )
                }.sortedBy { it.rankLabel } // <--- 핵심 정렬 코드!

                // 어댑터 연결
                setupRecyclerViews(originalScheduleList)
                updateSummaries(originalScheduleList)
                updateTransportUI(0) // 0: 도보 모드로 초기화

            } catch (e: Exception) {
                Log.e("API_TEST", "최종 렌더링 중 알 수 없는 치명적 에러", e)
            } finally {
                binding.loadingLayout.visibility = View.GONE
            }
        }
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

            // ==========================================
            // ★ 1. 직장 주소 vs 탐색 주소 마커 분기 처리
            // 백엔드에서 직장 주소 라벨을 "기존", "직장" 등으로 준다고 가정합니다.
            // (만약 id가 무조건 0이라면 mapInfo.id == 0L 로 수정해주세요)
            // ==========================================
            if (mapInfo.label == "기준지" || mapInfo.label == "직장") {
                marker.icon = com.naver.maps.map.overlay.OverlayImage.fromResource(R.drawable.ic_marker_home)
            } else {
                marker.icon = createCustomMarker(mapInfo.label)
            }

            // (선택) 마커 디자인 안에 이미 글자가 있으므로 기본 캡션은 숨깁니다.
            // marker.captionText = mapInfo.label
            // marker.captionTextSize = 14f

            marker.map = map
            markerList.add(marker)
            boundsBuilder.include(position)
        }
        if (markerList.isNotEmpty()) {
            map.moveCamera(CameraUpdate.fitBounds(boundsBuilder.build(), 100))
        }
    }

    private fun createCustomMarker(rank: String): com.naver.maps.map.overlay.OverlayImage {
        // 방금 만든 레이아웃을 메모리에 올립니다.
        val view = layoutInflater.inflate(R.layout.item_custom_marker, null)
        val bgIv = view.findViewById<android.widget.ImageView>(R.id.marker_bg_iv)
        val textTv = view.findViewById<android.widget.TextView>(R.id.marker_text_tv)

        textTv.text = rank

        val brand100 = ContextCompat.getColor(this, R.color.brand_100)
        val brand800 = ContextCompat.getColor(this, R.color.brand_800)
        val brand400 = ContextCompat.getColor(this, R.color.brand_400)
        val white = ContextCompat.getColor(this, R.color.white)
        val brand700 = ContextCompat.getColor(this, R.color.brand_700)
        val brand950 = ContextCompat.getColor(this, R.color.brand_950)
        val black = ContextCompat.getColor(this, R.color.black)
        val gray400 = ContextCompat.getColor(this, R.color.gray_400)
        val gray700 = ContextCompat.getColor(this, R.color.gray_700)
        val gray200 = ContextCompat.getColor(this, R.color.gray_200)

        val rankChar = if (rank.isNotEmpty()) rank[0] else '?'
        val bgColor: Int
        val textColor: Int

        // 라벨에 따라 배경색과 텍스트색 결정
        when (rankChar) {
            'A' -> { bgColor = brand100; textColor = brand800 }
            'B' -> { bgColor = brand400; textColor = white }
            'C' -> { bgColor = brand700; textColor = white }
            'D' -> { bgColor = brand950; textColor = white }
            'E' -> { bgColor = white; textColor = black }
            'F' -> { bgColor = gray400; textColor = white }
            'G' -> { bgColor = gray700; textColor = white }
            else -> { bgColor = gray200; textColor = black }
        }

        // 이미지뷰(핀 배경) 틴트 입히기
        bgIv.imageTintList = android.content.res.ColorStateList.valueOf(bgColor)
        // 텍스트 색상 입히기
        textTv.setTextColor(textColor)

        // 완성된 View를 네이버 마커용 OverlayImage 객체로 뽑아냄
        return com.naver.maps.map.overlay.OverlayImage.fromView(view)
    }

    private fun setupRecyclerViews(list: List<ScheduleItem>) {
        mainListAdapter = BeforeExploreAdapter(list)
        binding.beforeExploreListRv.adapter = mainListAdapter

        graphAdapter = GraphAdapter(list) { desc ->
            binding.graphDetailTv.visibility = if (desc.isNotEmpty()) View.VISIBLE else View.GONE
            binding.graphDetailTv.text = desc
        }
        binding.beforeGraphRv.adapter = graphAdapter
        graphAdapter.setMode(isDayMode)
    }

    // ★ 수정됨: 소음 점수가 가장 '높은(max)' 것이 가장 조용한 집
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
    }}