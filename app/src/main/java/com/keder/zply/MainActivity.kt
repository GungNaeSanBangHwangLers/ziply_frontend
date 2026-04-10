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
        binding.mainPlusBtn.setOnClickListener { openAddressFragment() }
        binding.emptyCardAddBtn.setOnClickListener { openAddressFragment() }
        binding.errorMainReloadBt.setOnClickListener { fetchMainData() }
    }

    private fun initRecyclerViews() {
        mainAdapter = MainCardRVAdapter(emptyList()) { item, status ->
            val intent = when (status) {
                ExploreStatus.BEFORE -> Intent(this, BeforeExploreActivity::class.java)
                ExploreStatus.ING -> Intent(this, IngExploreActivity::class.java)
                ExploreStatus.AFTER -> Intent(this, AfterExploreActivity::class.java)
            }
            intent.putExtra("CARD_ID", item.cardId.toString())
            startActivity(intent)
        }
        binding.mainExploreListRv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.mainExploreListRv.adapter = mainAdapter

        checklistAdapter = ChecklistGroupAdapter(emptyList()) { house ->
            if (currentCardId.isNotEmpty()) {
                val intent = Intent(this, IngExploreActivity::class.java)
                intent.putExtra("CARD_ID", currentCardId)
                intent.putExtra("HOUSE_ID", house.id)
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
                    Log.d("Auth", "토큰 만료 감지 (401 에러). 로그인 화면으로 이동합니다.")
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
                    val cards = cardsResponse.body()!!
                    val uniqueCards = cards.distinctBy { it.cardId }

                    if (uniqueCards.isEmpty()) {
                        binding.mainExploreListRv.visibility = View.GONE
                        binding.layoutEmptyCard.visibility = View.VISIBLE
                        binding.bottomBtnContainer.visibility = View.GONE
                        binding.mainExploreCountTv.text = "오늘 탐색 예정 주거가\n0개 있어요"
                    } else {
                        binding.mainExploreListRv.visibility = View.VISIBLE
                        binding.layoutEmptyCard.visibility = View.GONE
                        binding.bottomBtnContainer.visibility = View.VISIBLE

                        val mappedCardsWithAddress = getMappedCardDataWithAddress(uniqueCards)

                        val finalSortedList = mappedCardsWithAddress.sortedWith(
                            compareBy<MainCardData> { getStatusPriorityFromDate(it.date) }
                                .thenBy { it.date }
                        )
                        mainAdapter.updateList(finalSortedList)

                        val targetId = if (finalSortedList.isNotEmpty()) finalSortedList[0].cardId else uniqueCards[0].cardId
                        currentCardId = targetId

                        try {
                            val checklistRes = service.getChecklistDetails(targetId)
                            if (checklistRes.isSuccessful && !checklistRes.body().isNullOrEmpty()) {
                                val body = checklistRes.body()!!

                                // ★ [원복됨] 체크리스트 기준으로 오늘 개수 세기
                                val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA).format(Date())
                                val todayHouseCount = body.find { it.date == todayStr }?.houses?.size ?: 0
                                binding.mainExploreCountTv.text = "오늘 탐색 예정 주거가\n${todayHouseCount}개 있어요"

                                binding.mainChecklistRv.visibility = View.VISIBLE
                                binding.tvEmptyChecklist.visibility = View.GONE

                                val sortedChecklist = body.map { group ->
                                    val sortedHouses = group.houses.sortedWith(
                                        compareBy<ChecklistHouseResponse> { it.isMeasurementCompleted }
                                            .thenBy { it.visitDateTime ?: "99:99" }
                                    )
                                    group.copy(houses = sortedHouses)
                                }.sortedWith(
                                    compareBy<ChecklistGroupResponse> { it.isAllCompleted }
                                        .thenBy { it.date }
                                )
                                checklistAdapter.updateData(sortedChecklist)
                            } else {
                                binding.mainExploreCountTv.text = "오늘 탐색 예정 주거가\n0개 있어요"
                                showEmptyChecklistState()
                            }
                        } catch (e: Exception) {
                            Log.e("CHECKLIST_DEBUG", "체크리스트 호출 실패", e)
                            binding.mainExploreCountTv.text = "오늘 탐색 예정 주거가\n0개 있어요"
                            showEmptyChecklistState()
                        }
                    }
                    setViewState("SUCCESS")
                } else {
                    setViewState("ERROR")
                }
            } catch (e: Exception) {
                setViewState("ERROR")
            }
        }
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
                    } catch (e: Exception) {
                        Log.e("MainActivity", "집 목록 로딩 실패", e)
                    }

                    val displayDate = if (minDateStr == maxDateStr) minDateStr else "$minDateStr ~ $maxDateStr"

                    MainCardData(status = card.status, date = displayDate, location = fetchedAddress, count = card.houseCount, cardId = card.cardId)
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