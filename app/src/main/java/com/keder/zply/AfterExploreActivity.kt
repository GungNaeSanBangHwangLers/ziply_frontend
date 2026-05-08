package com.keder.zply

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayoutMediator
import com.keder.zply.databinding.ActivityAfterExploreBinding
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2
import com.naver.maps.geometry.LatLng
import com.naver.maps.geometry.LatLngBounds
import com.naver.maps.map.CameraUpdate
import com.naver.maps.map.MapFragment
import com.naver.maps.map.NaverMap
import com.naver.maps.map.OnMapReadyCallback
import com.naver.maps.map.overlay.Marker

class AfterExploreActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var binding: ActivityAfterExploreBinding
    private val tabTitles = listOf("직주거리", "방향", "소음", "채광", "안전")

    private val houseMap = mutableMapOf<Long, ScheduleItem>()
    val houseList: List<ScheduleItem> get() = houseMap.values.toList().sortedBy { it.rankLabel }
    var currentCardId: String = ""
    private var isNavigatingBack = false

    // ★ 지도 관련 변수 추가
    private var isMapExpanded = false
    private var naverMap: NaverMap? = null
    private val markerList = mutableListOf<Marker>()
    private var mapInfoList: List<MapInfoResponse> = emptyList()

    private val fragmentList: List<Fragment> by lazy {
        listOf(
            supportFragmentManager.findFragmentByTag("after_tab_0") as? AfterLengthFragment ?: AfterLengthFragment(),
            supportFragmentManager.findFragmentByTag("after_tab_1") as? AfterDirectionFragment ?: AfterDirectionFragment(),
            supportFragmentManager.findFragmentByTag("after_tab_2") as? AfterNoiseFragment ?: AfterNoiseFragment(),
            supportFragmentManager.findFragmentByTag("after_tab_3") as? AfterLightFragment ?: AfterLightFragment(),
            supportFragmentManager.findFragmentByTag("after_tab_4") as? AfterSafetyFragment ?: AfterSafetyFragment()
        )
    }

    private var currentFragmentIndex = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAfterExploreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.afterBackIv.setOnClickListener {
            goBackToMain()
        }

        binding.afterBtnToggleMap.setOnClickListener {
            toggleMap()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goBackToMain()
            }
        })

        val cardId = intent.getStringExtra("CARD_ID")
        if (cardId.isNullOrEmpty()) {
            showCustomToast("잘못된 접근입니다.")
            finish()
            return
        }
        currentCardId = cardId

        // ★ 지도 프래그먼트 초기화
        val fm = supportFragmentManager
        var mapFragment = fm.findFragmentById(R.id.after_naver_map_fragment) as MapFragment?
        if (mapFragment == null) {
            mapFragment = MapFragment.newInstance()
            fm.beginTransaction().add(R.id.after_naver_map_fragment, mapFragment).commit()
        }
        mapFragment.getMapAsync(this)

        loadInitialData(cardId)
    }

    override fun onMapReady(map: NaverMap) {
        naverMap = map
        naverMap?.uiSettings?.apply { isZoomControlEnabled = false; isScaleBarEnabled = false }
        drawMarkersIfReady()
    }

    private fun toggleMap() {
        isMapExpanded = !isMapExpanded
        if (isMapExpanded) {
            binding.afterLayoutMapContainer.visibility = View.VISIBLE
            binding.afterTvMapToggle.text = "접기"
            binding.afterIvMapArrow.setImageResource(R.drawable.ic_arrow_up)
        } else {
            binding.afterLayoutMapContainer.visibility = View.GONE
            binding.afterTvMapToggle.text = "지도로 보기"
            binding.afterIvMapArrow.setImageResource(R.drawable.ic_arrow_down)
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

            marker.icon = if (mapInfo.label == "기준지" || mapInfo.label == "직장") {
                com.naver.maps.map.overlay.OverlayImage.fromResource(R.drawable.ic_marker_home)
            } else {
                createCustomMarker(mapInfo.label)
            }

            marker.map = map
            markerList.add(marker)
            boundsBuilder.include(position)
        }

        if (markerList.isNotEmpty()) {
            map.moveCamera(CameraUpdate.fitBounds(boundsBuilder.build(), 100))
        }
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

    private fun goBackToMain() {
        if (isNavigatingBack) return
        isNavigatingBack = true

        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    private fun loadInitialData(cardId: String) {
        lifecycleScope.launch {
            binding.loadingLayout.visibility = View.VISIBLE
            try {
                val service = RetrofitClient.getInstance(this@AfterExploreActivity)

                val houseDeferred = async { service.getCardHouseList(cardId) }
                val addressDeferred = async { service.getCardAddresses(cardId) }
                val mapDeferred = async { service.getCardMapInfo(cardId) } // ★ 지도 데이터 호출 추가

                val houseRes = houseDeferred.await()
                val addressRes = addressDeferred.await()
                val mapRes = mapDeferred.await()

                if (addressRes.isSuccessful && addressRes.body() != null) {
                    val address = addressRes.body()!!
                    if (address.isNotEmpty()) binding.afterMyAddressTv.text = address[0].address
                    else binding.afterMyAddressTv.text = "직장 정보 없음"
                }

                // ★ 지도 데이터 저장 및 마커 그리기
                if (mapRes.isSuccessful && mapRes.body() != null) {
                    mapInfoList = mapRes.body()!!
                    drawMarkersIfReady()
                }

                if (houseRes.isSuccessful && houseRes.body() != null) {
                    val rawHouses = houseRes.body()!!
                    val sortedHouses = rawHouses.sortedBy { it.visitTime ?: "" }

                    Log.d("API_DEBUG_IMAGE", "========== 서버 이미지 렌더링 시작 (CardID: $cardId) ==========")

                    sortedHouses.forEachIndexed { index, house ->
                        val rankChar = ('A'.code + index).toChar().toString()

                        val serverImages = house.imageUrls ?: emptyList()

                        Log.d("API_DEBUG_IMAGE", "[주거지 $rankChar (ID: ${house.houseId})]")
                        Log.d("API_DEBUG_IMAGE", " - 렌더링할 서버 이미지: ${serverImages.size}장 -> $serverImages")

                        houseMap[house.houseId] = ScheduleItem(
                            houseId = house.houseId,
                            address = house.address ?: "주소 없음",
                            time = house.visitTime?.replace("T", " ")?.take(16) ?: "",
                            rankLabel = rankChar,
                            imageList = serverImages.toMutableList()
                        )
                    }

                    Log.d("API_DEBUG_IMAGE", "========== 서버 이미지 렌더링 종료 ==========")

                    val sortedList = houseMap.values.toList().sortedBy { it.rankLabel }

                    setupCardRecyclerView(sortedList)
                    setupTabs()
                } else {
                    showCustomToast("데이터를 불러오지 못했어요, 다시 시도해주세요")
                    showErrorOverlay { loadInitialData(cardId) }
                }

            } catch (e: Exception) {
                Log.e("AfterExplore", "초기화 실패", e)
                showErrorOverlay { loadInitialData(cardId) }
            } finally {
                binding.loadingLayout.visibility = View.GONE
            }
        }
    }
    fun getRankLabel(houseId: Long): String {
        return houseMap[houseId]?.rankLabel ?: "?"
    }

    fun getAddress(houseId: Long): String {
        return houseMap[houseId]?.address ?: ""
    }

    fun getAllHouseIds(): List<Long> {
        return houseMap.keys.toList()
    }

    private fun setupCardRecyclerView(items: List<ScheduleItem>) {
        val favoriteViewModel = ViewModelProvider(this)[FavoriteViewModel::class.java]
        val favPrefs = getSharedPreferences("ZplyFavorites", Context.MODE_PRIVATE)

        val currentFavs = favoriteViewModel.favoriteSet.value ?: emptySet()
        val savedFavs = favPrefs.getStringSet("fav_houses", emptySet())?.mapNotNull { it.toLongOrNull() }?.toSet() ?: emptySet()

        if (currentFavs.isEmpty() && savedFavs.isNotEmpty()) {
            savedFavs.forEach { id ->
                favoriteViewModel.toggleFavorite(id)
            }
        }

        val adapter = AfterCardAdapter(
            items = items,
            onStarClick = { houseId ->
                favoriteViewModel.toggleFavorite(houseId)
            },
            onImageClick = { clickedItem, clickedIndex ->
                showImageDialog(clickedItem, clickedIndex)
            }
        )
        binding.afterCardRv.adapter = adapter
        binding.afterCardRv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        favoriteViewModel.favoriteSet.removeObservers(this)
        favoriteViewModel.favoriteSet.observe(this) { favorites ->
            adapter.updateFavorites(favorites)
            favPrefs.edit().putStringSet("fav_houses", favorites.map { it.toString() }.toSet()).apply()
        }
    }

    private fun setupTabs() {
        tabTitles.forEach { title ->
            binding.afterTabLayout.addTab(binding.afterTabLayout.newTab().setText(title))
        }

        binding.afterTabLayout.addOnTabSelectedListener(object : com.google.android.material.tabs.TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: com.google.android.material.tabs.TabLayout.Tab?) {
                tab?.position?.let { replaceFragment(it) }
            }
            override fun onTabUnselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
            override fun onTabReselected(tab: com.google.android.material.tabs.TabLayout.Tab?) {}
        })

        replaceFragment(0)
    }


    private fun replaceFragment(position: Int) {
        if (currentFragmentIndex == position) return

        // ★ 1. 현재 스크롤 뷰의 Y축 위치를 정확히 기억해 둡니다.
        val scrollView = binding.root.getChildAt(0) as? androidx.core.widget.NestedScrollView
        val currentScrollY = scrollView?.scrollY ?: 0

        val transaction = supportFragmentManager.beginTransaction()
        val targetFragment = fragmentList[position]

        if (!targetFragment.isAdded) {
            transaction.add(R.id.after_fragment_container, targetFragment, "after_tab_$position")
        }

        if (currentFragmentIndex != -1) {
            transaction.hide(fragmentList[currentFragmentIndex])
        }

        transaction.show(targetFragment)

        // ★ 2. commitNowAllowingStateLoss: 동기 반영하되 state save 이후에도 안전하게 처리
        transaction.commitNowAllowingStateLoss()
        currentFragmentIndex = position

        // ★ 3. 리사이클러뷰가 포커스를 뺏어가기 전에 즉시 스크롤을 원래 위치로 되돌립니다.
        scrollView?.scrollTo(0, currentScrollY)
        scrollView?.post {
            // 레이아웃이 다 그려진 후 한 번 더 쐐기를 박아 스크롤 튐을 완벽 차단합니다.
            scrollView.scrollTo(0, currentScrollY)
        }
    }


    private fun showImageDialog(item: ScheduleItem, startIndex: Int) {
        if (item.imageList.isEmpty()) return

        val dialog = Dialog(this)
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
            dots[i] = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    setMargins(marginPx, 0, marginPx, 0)
                }

                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    val colorRes = if (i == startIndex) R.color.brand_800 else R.color.gray_700
                    setColor(ContextCompat.getColor(this@AfterExploreActivity, colorRes))
                }
            }
            indicatorLl.addView(dots[i])
        }

        val pageCallback = object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                for (i in 0 until dotCount) {
                    val drawable = dots[i]?.background as? GradientDrawable
                    val colorRes = if (i == position) R.color.brand_800 else R.color.gray_700
                    drawable?.setColor(ContextCompat.getColor(this@AfterExploreActivity, colorRes))
                }
            }
        }
        imageVp.registerOnPageChangeCallback(pageCallback)
        dialog.setOnDismissListener { imageVp.unregisterOnPageChangeCallback(pageCallback) }

        closeBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }
}