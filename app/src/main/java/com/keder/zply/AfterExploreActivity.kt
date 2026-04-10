package com.keder.zply

import android.app.Dialog
import android.content.Context
import android.content.Intent
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
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.viewpager2.widget.ViewPager2

class AfterExploreActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAfterExploreBinding
    private val tabTitles = listOf("직주거리", "방향", "소음", "채광", "안전")

    private val houseMap = mutableMapOf<Long, ScheduleItem>()
    val houseList: List<ScheduleItem> get() = houseMap.values.toList().sortedBy { it.rankLabel }
    var currentCardId: String = ""
    private var isNavigatingBack = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAfterExploreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.afterBackIv.setOnClickListener {
            goBackToMain()
        }

        // 2. 휴대폰 기기 자체의 뒤로가기 제어 추가
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

        loadInitialData(cardId)
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

                        val savedPhotosStr = prefs.getString("photos_${house.houseId}", "") ?: ""
                        val localPhotos = if (savedPhotosStr.isNotEmpty()) savedPhotosStr.split(",") else emptyList()
                        val serverImages = house.imageUrls ?: emptyList()

                        val combinedImages = (serverImages + localPhotos).distinct().toMutableList()

                        houseMap[house.houseId] = ScheduleItem(
                            houseId = house.houseId,
                            address = house.address ?: "주소 없음",
                            time = house.visitTime?.replace("T", " ")?.take(16) ?: "",
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
        // ★ 1. 기존의 FavoriteViewModel 복구
        val favoriteViewModel = ViewModelProvider(this)[FavoriteViewModel::class.java]
        val favPrefs = getSharedPreferences("ZplyFavorites", Context.MODE_PRIVATE)

        // ★ 2. 최초 실행 시, 기기 저장소(SharedPreferences)의 데이터를 ViewModel에 채워넣기
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
                // 어댑터 클릭 시 기존처럼 ViewModel을 조작
                favoriteViewModel.toggleFavorite(houseId)
            },
            onImageClick = { clickedItem, clickedIndex ->
                showImageDialog(clickedItem, clickedIndex)
            }
        )
        binding.afterCardRv.adapter = adapter
        binding.afterCardRv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        // ★ 3. ViewModel의 LiveData를 관찰하여 UI 갱신 + 변경사항을 기기 저장소에 백업
        favoriteViewModel.favoriteSet.observe(this) { favorites ->
            adapter.updateFavorites(favorites)
            favPrefs.edit().putStringSet("fav_houses", favorites.map { it.toString() }.toSet()).apply()
        }
    }

    private fun setupViewPager() {
        val pagerAdapter = AfterViewPagerAdapter(this)
        binding.afterViewpager.adapter = pagerAdapter
        binding.afterViewpager.offscreenPageLimit = 4

        TabLayoutMediator(binding.afterTabLayout, binding.afterViewpager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
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
    }
}