package com.keder.zply

import android.content.Context
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.tabs.TabLayoutMediator
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.keder.zply.databinding.ActivityAfterExploreBinding

class AfterExploreActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAfterExploreBinding
    private val gson = Gson()

    private val tabTitles = listOf("직주거리", "방향", "소음", "채광")

    // [중요] 자식 프래그먼트들이 가져다 쓸 데이터
    var currentSession: ExplorationSession? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAfterExploreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.afterBackIv.setOnClickListener { finish() }

        val sessionIndex = intent.getIntExtra("SESSION_INDEX", -1)
        if (sessionIndex != -1) {
            loadSessionData(sessionIndex)
        }

        setupViewPager()
    }

    private fun loadSessionData(index: Int) {
        val sharedPref = getSharedPreferences("MainStorage", Context.MODE_PRIVATE)
        val jsonString = sharedPref.getString("KEY_ALL_SESSIONS", null)

        if (jsonString != null) {
            try {
                val type = object : TypeToken<List<ExplorationSession>>() {}.type
                // [수정] sessions가 null일 경우 빈 리스트 반환
                val sessions: List<ExplorationSession> = gson.fromJson(jsonString, type) ?: emptyList()

                if (index in sessions.indices) {
                    val session = sessions[index]
                    currentSession = session

                    binding.afterMyAddressTv.text = session.companyAddress ?: ""

                    // [수정] scheduleList가 null일 경우 빈 리스트로 대체하여 앱 꺼짐 방지
                    val safeList = session.scheduleList ?: emptyList()
                    val sortedList = safeList.sortedBy { it.time ?: "" }

                    setupCardRecyclerView(sortedList)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // 데이터 파싱 중 에러가 나도 앱이 꺼지지 않도록 처리
            }
        }
    }

    private fun setupCardRecyclerView(items: List<ScheduleItem>) {
        val adapter = AfterCardAdapter(items)
        binding.afterCardRv.adapter = adapter
        binding.afterCardRv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    }

    private fun setupViewPager() {
        val pagerAdapter = AfterViewPagerAdapter(this)
        binding.afterViewpager.adapter = pagerAdapter

        TabLayoutMediator(binding.afterTabLayout, binding.afterViewpager) { tab, position ->
            tab.text = tabTitles[position]
        }.attach()
    }
}