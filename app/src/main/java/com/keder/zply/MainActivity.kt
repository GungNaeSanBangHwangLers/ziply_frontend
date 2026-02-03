package com.keder.zply

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import com.keder.zply.databinding.ActivityMainBinding
import kotlinx.coroutines.async
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var mainAdapter: MainCardRVAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initRecyclerView()

        binding.mainPlusBtn.setOnClickListener { openAddressFragment() }
        binding.emptyMainPlusBtn.setOnClickListener { openAddressFragment() }
    }

    private fun initRecyclerView() {
        mainAdapter = MainCardRVAdapter(emptyList()) { item, status ->
            // 클릭 시 상태에 따라 이동
            val intent = when (status) {
                ExploreStatus.BEFORE -> Intent(this, BeforeExploreActivity::class.java)
                ExploreStatus.ING -> Intent(this, IngExploreActivity::class.java)
                ExploreStatus.AFTER -> Intent(this, AfterExploreActivity::class.java)
            }
            intent.putExtra("CARD_ID", item.cardId)
            startActivity(intent)
        }

        binding.mainExplorListRl.apply {
            layoutManager = LinearLayoutManager(this@MainActivity, LinearLayoutManager.HORIZONTAL, false)
            adapter = mainAdapter
            onFlingListener = null
            val snapHelper = PagerSnapHelper()
            snapHelper.attachToRecyclerView(this)
            binding.mainCardIndicatorCi.attachToRecyclerView(this, snapHelper)
            try {
                mainAdapter.registerAdapterDataObserver(binding.mainCardIndicatorCi.adapterDataObserver)
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    override fun onResume() {
        super.onResume()
        fetchMainData()
    }

    private fun fetchMainData() {
        lifecycleScope.launch {
            try {
                val service = RetrofitClient.getInstance(this@MainActivity)
                val nameDeferred = async { service.getUserName() }
                val cardsDeferred = async { service.getReviewCards() }

                val nameResponse = nameDeferred.await()
                val cardsResponse = cardsDeferred.await()

                if (nameResponse.isSuccessful && nameResponse.body() != null) {
                    updateUserNameUI(nameResponse.body()!!.name)
                }

                if (cardsResponse.isSuccessful && cardsResponse.body() != null) {
                    val cards = cardsResponse.body()!!
                    val uniqueCards = cards.distinctBy { it.cardId } // 중복 제거

                    if (uniqueCards.isEmpty()) {
                        setViewState("EMPTY")
                    } else {
                        val viewDataList = mapResponseToCardData(uniqueCards)
                        val sortedList = viewDataList.sortedBy { card ->
                            when (card.status) {
                                "ING" -> 0    // 1순위: 탐색 중
                                "BEFORE" -> 1 // 2순위: 탐색 전
                                "AFTER" -> 2  // 3순위: 탐색 후
                                else -> 3     // 기타
                            }
                        }
                        mainAdapter.updateList(sortedList)
                        setViewState("SUCCESS")
                    }
                } else {
                    if (mainAdapter.itemCount == 0) setViewState("ERROR")
                }
            } catch (e: Exception) {
                Log.e("Main", "Error", e)
                if (mainAdapter.itemCount == 0) setViewState("ERROR")
            }
        }
    }

    private fun updateUserNameUI(name: String) {
        val text = "안녕하세요 ${name}님"
        binding.mainHiTv.text = text
        binding.emptyMainHiTv.text = text
    }

    private fun mapResponseToCardData(cards: List<ReviewCardResponse>): List<MainCardData> {
        return cards.map { card ->
            // ★ [수정 1] 날짜 포맷팅 (null 방지)
            val start = card.startDate
            val end = if (card.endDate.isNullOrEmpty() || card.endDate == "null") start else card.endDate

            MainCardData(
                status = card.status, // 서버 상태값 (참고용 - 실제 표시는 Adapter에서 계산)
                date = "$start \n~ $end", // 화면 표시용
                location = card.title,
                count = card.houseCount, // [수정] houseCount 사용
                cardId = card.cardId
            )
        }
    }

    private fun openAddressFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_frm, CompanyAddressFragment())
            .addToBackStack(null)
            .commitAllowingStateLoss()
    }

    private fun setViewState(state: String) {
        binding.viewMainCv.visibility = View.GONE
        binding.emptyMainLl.visibility = View.GONE
        binding.errorMainLl.visibility = View.GONE
        binding.progressBar.visibility = View.GONE
        when (state) {
            "LOADING" -> binding.progressBar.visibility = View.VISIBLE
            "SUCCESS" -> binding.viewMainCv.visibility = View.VISIBLE
            "EMPTY" -> binding.emptyMainLl.visibility = View.VISIBLE
            "ERROR" -> binding.errorMainLl.visibility = View.VISIBLE
        }
    }
}