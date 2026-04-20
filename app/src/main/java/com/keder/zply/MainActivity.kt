package com.keder.zply

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.keder.zply.databinding.ActivityMainBinding
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var mainAdapter: MainCardRVAdapter
    private lateinit var checklistAdapter: ChecklistGroupAdapter

    private var currentCardId: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initRecyclerViews()
        setupListeners()
    }

    private fun setupListeners() {
        binding.mainPlusBtn.setOnClickListener {
            if (binding.mainPlusBtn.text.toString() == "오늘 할 일 바로가기") {
                if (currentCardId.isNotEmpty()) {
                    val intent = Intent(this, IngExploreActivity::class.java)
                    intent.putExtra("CARD_ID", currentCardId)
                    intent.putExtra("SHOW_TAB", "MEASURE")
                    startActivity(intent)
                }
            } else {
                openAddressFragment()
            }
        }

        binding.emptyCardAddBtn.setOnClickListener { openAddressFragment() }
        binding.errorMainReloadBt.setOnClickListener { fetchMainData() }
    }

    private fun initRecyclerViews() {
        mainAdapter = MainCardRVAdapter(items = emptyList(), onItemClick =  { item, status ->
            if (item.cardId == "DUMMY_PLUS_CARD") {
                openAddressFragment()
                return@MainCardRVAdapter
            }

            val intent = if (status == ExploreStatus.AFTER) {
                Intent(this, AfterExploreActivity::class.java)
            } else {
                Intent(this, IngExploreActivity::class.java).apply {
                    putExtra("SHOW_TAB", "INFO")
                }
            }
            intent.putExtra("CARD_ID", item.cardId)
            startActivity(intent)
        }, onDeleteResetClick = {resetData()})
        binding.mainExploreListRv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.mainExploreListRv.adapter = mainAdapter

        checklistAdapter = ChecklistGroupAdapter(emptyList()) { house ->
            if (currentCardId.isNotEmpty()) {
                val intent = Intent(this, IngExploreActivity::class.java)
                intent.putExtra("CARD_ID", currentCardId)
                intent.putExtra("HOUSE_ID", house.id)
                intent.putExtra("SHOW_TAB", "MEASURE")
                startActivity(intent)
            }
        }
        binding.mainChecklistRv.layoutManager = LinearLayoutManager(this)
        binding.mainChecklistRv.adapter = checklistAdapter
    }

    override fun onResume() {
        super.onResume()
        fetchMainData()
    }

    // ★ 수정된 초기화 API 호출 함수 (내 정보 조회 -> 초기화 순차 실행)
    private fun resetData() {
        lifecycleScope.launch {
            setViewState("LOADING")
            try {
                val service = RetrofitClient.getInstance(this@MainActivity)

                // ★ 1. 내 정보 조회 API 먼저 호출하여 유저 ID 획득!
                val userMeResponse = service.getUserMe()

                if (userMeResponse.isSuccessful && userMeResponse.body() != null) {
                    val currentUserId = userMeResponse.body()!!.id
                    Log.d("API_RESET", "획득한 유저 ID: $currentUserId")

                    // ★ 2. 방금 알아낸 ID를 헤더 파라미터로 넣어서 초기화 API 호출!
                    val resetResponse = service.resetData(currentUserId)

                    if (resetResponse.isSuccessful && resetResponse.body() != null) {
                        val deletedStats = resetResponse.body()!!.deletedData
                        Log.d("API_RESET", "초기화 성공: 카드 ${deletedStats.searchCards}개 삭제됨")

                        showCustomToast2("데이터가 초기화되었습니다.")

                        // 초기화 성공 시 메인 데이터를 처음부터 다시 불러와 화면 갱신
                        fetchMainData()
                    } else {
                        Log.e("API_RESET", "초기화 실패 코드: ${resetResponse.code()}")
                        showCustomToast("초기화에 실패했어요. 다시 시도해주세요.")
                        setViewState("SUCCESS") // 로딩 바 숨기기
                    }

                } else {
                    Log.e("API_RESET", "유저 정보 획득 실패 코드: ${userMeResponse.code()}")
                    showCustomToast("유저 정보를 확인할 수 없어 초기화에 실패했습니다.")
                    setViewState("SUCCESS") // 로딩 바 숨기기
                }

            } catch (e: Exception) {
                Log.e("API_RESET", "초기화 에러 발생", e)
                setViewState("ERROR")
                showErrorOverlay { resetData() } // 에러 오버레이 표시
            }
        }
    }
    private fun fetchMainData() {
        lifecycleScope.launch {
            setViewState("LOADING")
            try {
                val service = RetrofitClient.getInstance(this@MainActivity)
                val nameDeferred = async { service.getUserName() }
                val cardsDeferred = async { service.getReviewCards() }

                val nameResponse = nameDeferred.await()
                val cardsResponse = cardsDeferred.await()

                if (nameResponse.code() == 401 || cardsResponse.code() == 401) {
                    val tokenManager = TokenManager(this@MainActivity)
                    tokenManager.clearTokens()
                    val intent = Intent(this@MainActivity, LoginActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                    return@launch
                }

                if (nameResponse.isSuccessful && nameResponse.body() != null) {
                    binding.mainHiTv.text = "안녕하세요 ${nameResponse.body()!!.name}님!"
                }

                if (cardsResponse.isSuccessful && cardsResponse.body() != null) {
                    val rawCards = cardsResponse.body()!!

                    val uniqueCards = rawCards.distinctBy { it.cardId }

                    if (uniqueCards.isEmpty()) {
                        showEmptyStateAll()
                        setViewState("SUCCESS")
                        return@launch
                    }

                    // 1. 전체 탐색 개수 계산
                    val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())
                    var totalTodayCount = 0
                    uniqueCards.forEach { card ->
                        try {
                            val startStr = card.startDate.take(10)
                            val endStr = (card.endDate ?: card.startDate).take(10)
                            if (todayStr in startStr..endStr) {
                                totalTodayCount += card.houseCount
                            }
                        } catch (e: Exception) {}
                    }
                    binding.mainExploreCountTv.text = "오늘 탐색 예정 주거가\n${totalTodayCount}개 있어요"

                    // 2. 카드 상태별 분기 (서버의 한글/영어 상태값을 내부 상태로 통일)
                    val mappedCards = getMappedCardDataWithAddress(uniqueCards)
                    val ingCards = mappedCards.filter { it.status == "ING" }
                    val afterCards = mappedCards.filter { it.status == "AFTER" }
                    val finalDisplayList = mutableListOf<MainCardData>()

                    if (ingCards.isNotEmpty()) {
                        finalDisplayList.addAll(ingCards.sortedWith(compareBy<MainCardData> { getStatusPriorityFromDate(it.date) }.thenBy { it.date }))
                        finalDisplayList.add(MainCardData(status = "PLUS_BTN", date = "", location = "", count = 0, cardId = "DUMMY_PLUS_CARD"))

                        binding.mainExploreListRv.visibility = View.VISIBLE
                        binding.layoutEmptyCard.visibility = View.GONE
                        binding.bottomBtnContainer.visibility = View.VISIBLE
                        binding.mainPlusBtn.text = "오늘 할 일 바로가기"

                        currentCardId = finalDisplayList[0].cardId

                    } else if (afterCards.isNotEmpty()) {
                        finalDisplayList.addAll(afterCards.sortedWith(compareBy<MainCardData> { getStatusPriorityFromDate(it.date) }.thenBy { it.date }))

                        binding.mainExploreListRv.visibility = View.VISIBLE
                        binding.layoutEmptyCard.visibility = View.VISIBLE
                        binding.bottomBtnContainer.visibility = View.VISIBLE
                        binding.mainPlusBtn.text = "주거탐색 추가하기"

                        currentCardId = finalDisplayList[0].cardId
                    } else {
                        showEmptyStateAll()
                        setViewState("SUCCESS")
                        return@launch
                    }

                    mainAdapter.updateList(finalDisplayList)

                    // 3. 체크리스트 데이터 연동
                    try {
                        val checklistRes = service.getChecklistDetails(currentCardId)

                        if (checklistRes.isSuccessful && checklistRes.body() != null) {
                            val body = checklistRes.body()!!

                            val hasValidData = body.any { it.houses != null && it.houses.isNotEmpty() }

                            if (hasValidData) {
                                binding.mainChecklistRv.visibility = View.VISIBLE
                                binding.tvEmptyChecklist.visibility = View.GONE

                                val sortedChecklist = body.map { group ->
                                    val safeHouses = group.houses ?: emptyList()
                                    val sortedHouses = safeHouses.sortedWith(
                                        compareBy<ChecklistHouseResponse> { it.isMeasurementCompleted }
                                            .thenBy { it.visitDateTime ?: "99:99" }
                                    )
                                    group.copy(houses = sortedHouses)
                                }.sortedWith(
                                    compareBy<ChecklistGroupResponse> { it.isAllCompleted }
                                        .thenBy { it.date ?: "" }
                                )
                                checklistAdapter.updateData(sortedChecklist)
                            } else {
                                showEmptyChecklistState()
                            }
                        } else {
                            showEmptyChecklistState()
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "Checklist Error", e)
                        showEmptyChecklistState()
                    }

                    setViewState("SUCCESS")
                } else {
                    setViewState("ERROR")
                    showErrorOverlay { fetchMainData() }
                }
            } catch (e: Exception) {
                Log.e("MainActivity", "Total Fetch Error", e)
                setViewState("ERROR")
                showErrorOverlay { fetchMainData() }
            }
        }
    }

    private fun showEmptyStateAll() {
        binding.mainExploreListRv.visibility = View.GONE
        binding.layoutEmptyCard.visibility = View.VISIBLE
        binding.bottomBtnContainer.visibility = View.GONE
        binding.mainExploreCountTv.text = "오늘 탐색 예정 주거가\n0개 있어요"
        showEmptyChecklistState()
    }

    private fun getStatusPriorityFromDate(dateString: String): Int {
        return try {
            val parts = dateString.split("~").map { it.trim() }
            if (parts.isEmpty()) return 2

            val startDateStr = parts[0]
            val endDateStr = if (parts.size > 1) parts[1] else startDateStr

            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
            val todayFormat = SimpleDateFormat("yyyyMMdd", Locale.KOREA)

            val start = sdf.parse(startDateStr) ?: return 2
            val end = sdf.parse(endDateStr) ?: start
            val today = Date()

            val startInt = todayFormat.format(start).toInt()
            val endInt = todayFormat.format(end).toInt()
            val todayInt = todayFormat.format(today).toInt()

            when {
                todayInt < startInt -> 2
                todayInt > endInt -> 3
                else -> 1
            }
        } catch (e: Exception) {
            2
        }
    }

    private fun showEmptyChecklistState() {
        binding.mainChecklistRv.visibility = View.GONE
        binding.tvEmptyChecklist.visibility = View.VISIBLE
    }

    private suspend fun getMappedCardDataWithAddress(cards: List<ReviewCardResponse>): List<MainCardData> {
        val service = RetrofitClient.getInstance(this@MainActivity)
        return coroutineScope {
            cards.map { card ->
                async {
                    val addressRes = try { service.getCardAddresses(card.cardId).body() } catch (e: Exception) { null }
                    val fetchedAddress = if (!addressRes.isNullOrEmpty()) addressRes[0].address ?: "주소 없음" else "주소 없음"

                    var minDateStr = card.startDate
                    var maxDateStr = card.endDate ?: card.startDate

                    try {
                        val houses = service.getCardHouseList(card.cardId).body()
                        if (!houses.isNullOrEmpty()) {
                            val dates = houses.mapNotNull { it.visitTime?.take(10) }.filter { it.isNotBlank() }.sorted()
                            if (dates.isNotEmpty()) {
                                minDateStr = dates.first()
                                maxDateStr = dates.last()
                            }
                        }
                    } catch (e: Exception) { }

                    val displayDate = if (minDateStr == maxDateStr) minDateStr else "$minDateStr ~ $maxDateStr"

                    // ★ 핵심 변경 포인트: 백엔드에서 날아오는 상태값을 안전하게 앱 내부 상태(ING/AFTER)로 매핑합니다.
                    val rawStatus = card.status?.toString() ?: ""
                    val safeStatus = if (rawStatus.contains("예정") || rawStatus.contains("중") || rawStatus.contains("BEFORE") || rawStatus.contains("ING")) {
                        "ING" // 탐색 예정, 탐색 중 -> 모두 앱 내부에서는 탐색 중(ING) 취급
                    } else if (rawStatus.contains("종료") || rawStatus.contains("완료") || rawStatus.contains("AFTER")) {
                        "AFTER" // 탐색 종료 -> 앱 내부에서는 탐색 후(AFTER) 취급
                    } else {
                        "ING" // 혹시 모를 알 수 없는 상태값이 오면 기본적으로 ING로 띄워줌
                    }

                    MainCardData(
                        status = safeStatus,
                        date = displayDate,
                        location = fetchedAddress,
                        count = card.houseCount,
                        cardId = card.cardId
                    )
                }
            }.awaitAll()
        }
    }

    private fun openAddressFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_frm, CompanyAddressFragment())
            .addToBackStack(null)
            .commitAllowingStateLoss()
    }

    private fun setViewState(state: String) {
        binding.errorMainLl.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        when (state) {
            "LOADING" -> binding.progressBar.visibility = View.VISIBLE
            "SUCCESS" -> { }
            "ERROR" -> binding.errorMainLl.visibility = View.VISIBLE
        }
    }
}