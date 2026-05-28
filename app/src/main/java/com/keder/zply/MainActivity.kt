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
    private var targetUnmeasuredHouseId: Long = -1L // ★ 미측정된 집 번호를 저장할 변수 추가

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
                    // ★ 찾아둔 미측정 집 번호가 있으면 같이 넘겨줌 (A가 안끝났으면 A, A가 끝났으면 B)
                    if (targetUnmeasuredHouseId != -1L) {
                        intent.putExtra("HOUSE_ID", targetUnmeasuredHouseId)
                    }
                    intent.putExtra("SHOW_TAB", "MEASURE")
                    startActivity(intent)
                }
            } else {
                openAddressFragment()
            }
        }

        binding.emptyCardAddBtn.setOnClickListener { openAddressFragment() }
        binding.errorMainReloadBt.setOnClickListener { fetchMainData() }
        binding.btnResetBottom.setOnClickListener { resetData() }
    }

    private fun initRecyclerViews() {
        mainAdapter = MainCardRVAdapter(items = emptyList()) { item, status ->
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
        }
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

    private fun resetData() {
        lifecycleScope.launch {
            setViewState("LOADING")
            try {
                val service = RetrofitClient.getInstance(this@MainActivity)
                val userMeResponse = service.getUserMe()

                if (userMeResponse.isSuccessful && userMeResponse.body() != null) {
                    val currentUserId = userMeResponse.body()!!.id
                    val resetResponse = service.resetData(currentUserId)

                    if (resetResponse.isSuccessful && resetResponse.body() != null) {
                        showCustomToast2("데이터가 초기화되었습니다.")
                        fetchMainData()
                    } else {
                        showCustomToast("초기화에 실패했어요. 다시 시도해주세요.")
                        setViewState("SUCCESS")
                    }
                } else {
                    showCustomToast("유저 정보를 확인할 수 없어 초기화에 실패했습니다.")
                    setViewState("SUCCESS")
                }

            } catch (e: Exception) {
                setViewState("ERROR")
                showErrorOverlay { resetData() }
            }
        }
    }

    private fun fetchMainData() {
        lifecycleScope.launch {
            setViewState("LOADING")
            try {
                val service = RetrofitClient.getInstance(this@MainActivity)
                val (nameResponse, cardsResponse) = coroutineScope {
                    val nd = async { service.getUserName() }
                    val cd = async { service.getReviewCards() }
                    nd.await() to cd.await()
                }

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
                    val userName = nameResponse.body()!!.name
                    val prefix = "안녕하세요 "
                    val fullText = "$prefix${userName}님!"

                    val spannable = android.text.SpannableString(fullText)
                    val startIndex = prefix.length
                    if (startIndex != -1) {
                        spannable.setSpan(
                            android.text.style.ForegroundColorSpan(androidx.core.content.ContextCompat.getColor(this@MainActivity, R.color.brand_500)),
                            startIndex,
                            startIndex + userName.length,
                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    binding.mainHiTv.text = spannable
                }

                if (cardsResponse.isSuccessful && cardsResponse.body() != null) {
                    val rawCards = cardsResponse.body()!!
                    val uniqueCards = rawCards.distinctBy { it.cardId }

                    if (uniqueCards.isEmpty()) {
                        showEmptyStateAll()
                        setViewState("SUCCESS")
                        return@launch
                    }

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

                    try {
                        val checklistRes = service.getChecklistDetails(currentCardId)

                        if (checklistRes.isSuccessful && checklistRes.body() != null) {
                            val body = checklistRes.body()!!
                            val hasValidData = body.any { it.houses != null && it.houses.isNotEmpty() }

                            if (hasValidData) {
                                binding.mainChecklistRv.visibility = View.VISIBLE
                                binding.tvEmptyChecklist.visibility = View.GONE
                                binding.btnResetBottom.visibility = View.VISIBLE

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

                                // ★ 핵심 1: '오늘의 할 일' 버튼을 눌렀을 때 넘어갈 주거지(가장 첫 번째 미측정 주거지) 미리 찾아두기
                                targetUnmeasuredHouseId = -1L
                                for (group in sortedChecklist) {
                                    val unmeasuredHouse = group.houses?.find { !it.isMeasurementCompleted }
                                    if (unmeasuredHouse != null) {
                                        targetUnmeasuredHouseId = unmeasuredHouse.id
                                        break // 찾았으면 반복문 즉시 탈출
                                    }
                                }

                            } else {
                                showEmptyChecklistState()
                            }
                        } else {
                            showEmptyChecklistState()
                        }
                    } catch (e: Exception) {
                        showEmptyChecklistState()
                    }

                    setViewState("SUCCESS")
                } else {
                    setViewState("ERROR")
                    showErrorOverlay { fetchMainData() }
                }
            } catch (e: Exception) {
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
        binding.btnResetBottom.visibility = View.GONE
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
                    val rawStatus = card.status?.toString() ?: ""
                    val safeStatus = if (rawStatus.contains("예정") || rawStatus.contains("중") || rawStatus.contains("BEFORE") || rawStatus.contains("ING")) {
                        "ING"
                    } else if (rawStatus.contains("종료") || rawStatus.contains("완료") || rawStatus.contains("AFTER")) {
                        "AFTER"
                    } else {
                        "ING"
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