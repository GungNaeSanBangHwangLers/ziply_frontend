package com.keder.zply

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

class AfterExploreActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAfterExploreBinding
    private val tabTitles = listOf("직주거리", "방향", "소음", "채광")

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
                // 1. [병렬 호출] 집 목록 조회 & 거리 분석(직장주소용)
                val houseDeferred = async { RetrofitClient.getInstance(this@AfterExploreActivity).getCardHouseList(cardId) }
                val addressDeferred = async { RetrofitClient.getInstance(this@AfterExploreActivity).getCardAddresses(cardId) }

                val houseRes = houseDeferred.await()
                val addressRes = addressDeferred.await()

                // 2. 직장 주소 설정 (Distance API 결과에서 추출)
                if (addressRes.isSuccessful && addressRes.body() != null) {
                    val address = addressRes.body()!!
                    if (address.isNotEmpty()) {
                        binding.afterMyAddressTv.text = address[0].address // ★ 직장 주소 반영
                    } else {
                        binding.afterMyAddressTv.text = "직장 정보 없음"
                    }
                }

                // 3. 랭크 기준 잡기 (House List API 결과 활용)
                if (houseRes.isSuccessful && houseRes.body() != null) {
                    val rawHouses = houseRes.body()!!

                    // 방문 시간순 정렬 -> 이것이 곧 랭크 순서!
                    val sortedHouses = rawHouses.sortedBy { it.visitTime }

                    // 리스트를 Map으로 변환 (HouseID -> ScheduleItem)
                    sortedHouses.forEachIndexed { index, house ->
                        val rankChar = ('A'.code + index).toChar().toString() // A, B, C...

                        houseMap[house.houseId] = ScheduleItem(
                            houseId = house.houseId,
                            address = house.address,
                            time = house.visitTime,
                            rankLabel = rankChar // ★ 여기서 A, B, C 고정!
                        )
                    }

                    // 상단 가로 리스트(카드) 설정
                    setupCardRecyclerView(houseMap.values.toList().sortedBy { it.rankLabel })

                    // ★ 중요: 랭크 기준표(Map)가 완성된 후에 탭을 붙입니다.
                    setupViewPager()
                } else {
                    showCustomToast("데이터를 불러오지 못했어요, 다시 시도해주세요")
                }

            } catch (e: Exception) {
                Log.e("AfterExplore", "초기화 실패", e)
            }finally {
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
        val adapter = AfterCardAdapter(items)
        binding.afterCardRv.adapter = adapter
        binding.afterCardRv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    }

    private fun setupViewPager() {
        val pagerAdapter = AfterViewPagerAdapter(this)
        binding.afterViewpager.adapter = pagerAdapter
        binding.afterViewpager.offscreenPageLimit = 4 // 모든 탭 미리 로드 (데이터 보존)

        TabLayoutMediator(binding.afterTabLayout, binding.afterViewpager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }
}