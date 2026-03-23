package com.keder.zply

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2

class AfterExploreActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAfterExploreBinding
    private val tabTitles = listOf("직주거리", "방향", "소음", "채광", "안전")

    // ★ 전역에서 공유할 "랭크 기준표"
    // Key: HouseId, Value: ScheduleItem (랭크라벨, 주소 포함)
    private val houseMap = mutableMapOf<Long, ScheduleItem>()
    val houseList: List<ScheduleItem>
        get() = houseMap.values.toList().sortedBy { it.rankLabel }
    // 현재 카드 ID
    var currentCardId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAfterExploreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.afterBackIv.setOnClickListener { finish() }

        val cardId = intent.getStringExtra("CARD_ID")
        if (cardId.isNullOrEmpty()) {
            showCustomToast("잘못된 접근입니다.")
            finish()
            return
        }
        currentCardId = cardId

        // 데이터 로드 시작
        loadInitialData(cardId)
    }

    private fun loadInitialData(cardId: String) {
        lifecycleScope.launch {
            binding.loadingLayout.visibility = View.VISIBLE
            try {
                val service = RetrofitClient.getInstance(this@AfterExploreActivity)
                val prefs = getSharedPreferences("ZplyMeasurementPrefs", Context.MODE_PRIVATE)

                val houseDeferred = async { service.getCardHouseList(cardId) }
                val addressDeferred = async { service.getCardAddresses(cardId) }

                val houseRes = houseDeferred.await()
                val addressRes = addressDeferred.await()

                if (addressRes.isSuccessful && addressRes.body() != null) {
                    val address = addressRes.body()!!
                    if (address.isNotEmpty()) binding.afterMyAddressTv.text = address[0].address
                    else binding.afterMyAddressTv.text = "직장 정보 없음"
                }

                if (houseRes.isSuccessful && houseRes.body() != null) {
                    val rawHouses = houseRes.body()!!
                    val sortedHouses = rawHouses.sortedBy { it.visitTime ?: "" }

                    sortedHouses.forEachIndexed { index, house ->
                        val rankChar = ('A'.code + index).toChar().toString()

                        // ★ 로컬에 저장된 방금 찍은 사진 경로 불러오기
                        val savedPhotosStr = prefs.getString("photos_${house.houseId}", "") ?: ""
                        val localPhotos = if (savedPhotosStr.isNotEmpty()) savedPhotosStr.split(",") else emptyList()
                        val serverImages = house.imageUrls ?: emptyList()

                        // ★ 서버 URL + 로컬 파일 병합
                        val combinedImages = (serverImages + localPhotos).distinct().toMutableList()

                        houseMap[house.houseId] = ScheduleItem(
                            houseId = house.houseId,
                            address = house.address ?: "주소 없음",
                            time = house.visitTime ?: "",
                            rankLabel = rankChar,
                            imageList = combinedImages
                        )
                    }

                    val sortedList = houseMap.values.toList().sortedBy { it.rankLabel }

                    setupCardRecyclerView(sortedList)
                    setupViewPager()
                } else {
                    showCustomToast("데이터를 불러오지 못했어요, 다시 시도해주세요")
                }

            } catch (e: Exception) {
                Log.e("AfterExplore", "초기화 실패", e)
            } finally {
                binding.loadingLayout.visibility = View.GONE
            }
        }
    }

    // [Helper] 프래그먼트들이 HouseId를 주면 "A", "B" 같은 랭크를 돌려줌
    fun getRankLabel(houseId: Long): String {
        return houseMap[houseId]?.rankLabel ?: "?"
    }

    // [Helper] 프래그먼트들이 HouseId를 주면 주소를 돌려줌 (필요시)
    fun getAddress(houseId: Long): String {
        return houseMap[houseId]?.address ?: ""
    }

    // [Helper] 전체 집 목록(HouseId 리스트) 반환 (채광 탭 등에서 사용)
    fun getAllHouseIds(): List<Long> {
        return houseMap.keys.toList()
    }

    private fun setupCardRecyclerView(items: List<ScheduleItem>) {
        // ★ 뷰모델 가져오기
        val favoriteViewModel = ViewModelProvider(this)[FavoriteViewModel::class.java]

        // ★ 어댑터 생성 (클릭 시 뷰모델의 toggle 실행)
        val adapter = AfterCardAdapter(
            items = items,
            onStarClick = { houseId -> favoriteViewModel.toggleFavorite(houseId) },
            onImageClick = { clickedItem -> showImageDialog(clickedItem) }
        )
        binding.afterCardRv.adapter = adapter
        binding.afterCardRv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        // ★ 즐겨찾기 목록이 바뀔 때마다 어댑터에 알려주기
        favoriteViewModel.favoriteSet.observe(this) { favorites ->
            adapter.updateFavorites(favorites)
        }
    }

    private fun setupViewPager() {
        val pagerAdapter = AfterViewPagerAdapter(this)
        binding.afterViewpager.adapter = pagerAdapter
        binding.afterViewpager.offscreenPageLimit = 4 // 모든 탭 미리 로드 (데이터 보존)

        TabLayoutMediator(binding.afterTabLayout, binding.afterViewpager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }

    // 사진 탭했을 때 다이얼로그 띄우기 (원형 인디케이터 적용)
    private fun showImageDialog(item: ScheduleItem) {
        if (item.imageList.isEmpty()) return

        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_after_image)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        dialog.window?.apply {
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.8f) // 0.0f(투명) ~ 1.0f(완전 검정) 사이의 실수값. (추천: 0.5f ~ 0.8f)
        }

        // 다이얼로그 뷰 바인딩
        val rankTv = dialog.findViewById<TextView>(R.id.dialog_rank_tv)
        val dateTv = dialog.findViewById<TextView>(R.id.dialog_date_tv)
        val addressTv = dialog.findViewById<TextView>(R.id.dialog_address_tv)
        val closeBtn = dialog.findViewById<ImageView>(R.id.dialog_close_btn)
        val imageVp = dialog.findViewById<ViewPager2>(R.id.dialog_image_vp)

        // ★ 숫자 텍스트뷰 대신 리니어 레이아웃을 가져옵니다.
        val indicatorLl = dialog.findViewById<LinearLayout>(R.id.dialog_indicator_ll)

        // 데이터 꽂기
        rankTv.text = item.rankLabel
        dateTv.text = "${item.time} 탐색"
        addressTv.text = item.address

        // 이미지 어댑터 세팅
        imageVp.adapter = DialogImageAdapter(item.imageList)

        // =========================================================
        // ★ 4x4 dp 원형 인디케이터 동적 생성 로직
        // =========================================================
        val dotCount = item.imageList.size
        val dots = arrayOfNulls<ImageView>(dotCount)

        // dp를 픽셀(px)로 변환하는 마법의 공식
        val dpToPx = { dp: Int -> (dp * resources.displayMetrics.density).toInt() }
        val sizePx = dpToPx(4) // 4dp 크기
        val marginPx = dpToPx(4) // 점들 사이의 간격 4dp

        // 사진 개수만큼 점을 만듭니다.
        for (i in 0 until dotCount) {
            dots[i] = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    setMargins(marginPx, 0, marginPx, 0)
                }

                // 코드로 동그라미 그리기
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    // 첫 번째 점만 brand_800, 나머지는 gray_700으로 초기화
                    val colorRes = if (i == 0) R.color.brand_800 else R.color.gray_700
                    setColor(ContextCompat.getColor(this@AfterExploreActivity, colorRes))
                }
            }
            indicatorLl.addView(dots[i])
        }

        // =========================================================
        // ★ 페이지 넘길 때마다 점 색상 변경 로직
        // =========================================================
        imageVp.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                for (i in 0 until dotCount) {
                    val drawable = dots[i]?.background as? GradientDrawable
                    val colorRes = if (i == position) R.color.brand_800 else R.color.gray_700
                    drawable?.setColor(ContextCompat.getColor(this@AfterExploreActivity, colorRes))
                }
            }
        })

        closeBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }}