package com.keder.zply

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.keder.zply.databinding.ActivityMainBinding
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class MainActivity : AppCompatActivity() {
    private  lateinit var binding : ActivityMainBinding
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 메인 화면 (카드 있을 때) 추가 버튼
        binding.mainPlusBtn.setOnClickListener {
            openAddressFragment()
        }

        // 빈 화면 (카드 없을 때) 추가 버튼
        binding.emptyMainPlusBtn.setOnClickListener {
            openAddressFragment()
        }
    }

    private fun openAddressFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.main_frm, CompanyAddressFragment())
            .addToBackStack(null)
            .commitAllowingStateLoss()
    }

    override fun onResume() {
        super.onResume()
        loadAndRefreshData()
    }

    private fun loadAndRefreshData() {
        // 1. 메인 저장소("MainStorage")에서 세션 리스트 불러오기
        val mainPref = getSharedPreferences("MainStorage", Context.MODE_PRIVATE)
        val jsonString = mainPref.getString("KEY_ALL_SESSIONS", null)

        if (jsonString.isNullOrEmpty()) {
            setViewState("EMPTY")
            return
        }

        val type = object : TypeToken<List<ExplorationSession>>() {}.type
        val savedSessions: List<ExplorationSession> = gson.fromJson(jsonString, type)

        if (savedSessions.isEmpty()) {
            setViewState("EMPTY")
            return
        }

        // 2. 데이터 가공
        val viewDataList = processSessionData(savedSessions)

        if (viewDataList.isEmpty()) setViewState("EMPTY")
        else {
            setupRecyclerView(viewDataList)
            setViewState("SUCCESS")
        }
    }

    private fun processSessionData(sessions: List<ExplorationSession>): List<MainCardData> {
        val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val today = LocalDate.now()
        val resultList = mutableListOf<MainCardData>()

        sessions.forEachIndexed { index, session ->
            val items = session.scheduleList
            if (items.isEmpty()) return@forEachIndexed

            val dates = items.mapNotNull {
                try {
                    LocalDate.parse(it.time.split(" ")[0], dateFormatter)
                } catch (e: Exception) { null }
            }

            if (dates.isEmpty()) return@forEachIndexed

            val minDate = dates.minOrNull()!!
            val maxDate = dates.maxOrNull()!!
            val finalEndDate = maxDate

            var status = ""
            if (today.isBefore(minDate)) {
                status = "탐색예정"
            } else if (!today.isAfter(finalEndDate)) {
                status = "탐색중"
            } else {
                val daysDiff = ChronoUnit.DAYS.between(finalEndDate, today)
                if (daysDiff <= 100) {
                    status = "탐색완료"
                } else {
                    return@forEachIndexed
                }
            }

            val displayDate = "${minDate.format(dateFormatter)}\n~ ${finalEndDate.format(dateFormatter)}"

            resultList.add(
                MainCardData(
                    status = status,
                    date = displayDate,
                    location = session.companyAddress,
                    count = items.size,
                    sessionIndex = index // [중요] 세션 인덱스 저장
                )
            )
        }

        // 날짜순 정렬 (필요시 사용)
        return resultList.sortedBy { it.date }
    }

    private fun setupRecyclerView(dataList: List<MainCardData>) {
        val adapter = MainCardRVAdapter(dataList) { cardData ->
            val destActivityClass = when (cardData.status) {
                "탐색예정" -> BeforeExploreActivity::class.java
                "탐색중" -> IngExploreActivity::class.java
                "탐색완료" -> AfterExploreActivity::class.java
                else -> BeforeExploreActivity::class.java
            }

            val intent = Intent(this, destActivityClass)
            intent.putExtra("SESSION_INDEX", cardData.sessionIndex) // 인덱스 전달
            startActivity(intent)
        }

        binding.mainExplorListRl.adapter = adapter
        binding.mainExplorListRl.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        binding.mainExplorListRl.onFlingListener = null
        val snapHelper = PagerSnapHelper()
        snapHelper.attachToRecyclerView(binding.mainExplorListRl)

        binding.mainCardIndicatorCi.attachToRecyclerView(binding.mainExplorListRl, snapHelper)
        adapter.registerAdapterDataObserver(binding.mainCardIndicatorCi.adapterDataObserver)
    }

    private fun setViewState(state: String){
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