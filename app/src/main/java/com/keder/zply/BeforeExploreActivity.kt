package com.keder.zply

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.keder.zply.databinding.ActivityBeforeExploreBinding

class BeforeExploreActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBeforeExploreBinding
    private val gson = Gson()
    private lateinit var graphAdapter: GraphAdapter
    private var isDayMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBeforeExploreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. 데이터 로드 (메인에서 받은 인덱스 or 방금 추가된 마지막 데이터)
        val sessionIndex = intent.getIntExtra("SESSION_INDEX", -1)

        // 데이터를 불러와서 화면을 세팅합니다.
        loadAndSetupSession(sessionIndex)

        // 뒤로가기
        binding.beforeBackIv.setOnClickListener {
            finish()
        }

        setupDayNightButtons()
    }

    // [핵심 변경] 저장된 모든 세션을 불러와서, 특정 인덱스의 데이터를 화면에 뿌려줍니다.
    private fun loadAndSetupSession(index: Int) {
        val sharedPref = getSharedPreferences("MainStorage", Context.MODE_PRIVATE)
        val jsonString = sharedPref.getString("KEY_ALL_SESSIONS", null)

        if (jsonString == null) {
            // 데이터가 아예 없는 경우 예외처리
            return
        }

        val type = object : TypeToken<List<ExplorationSession>>() {}.type
        val sessions: List<ExplorationSession> = gson.fromJson(jsonString, type)

        if (sessions.isEmpty()) return

        // index가 -1이면(방금 추가해서 넘어옴) 리스트의 마지막(최신) 데이터를 사용
        // index가 있으면 그 데이터를 사용
        val targetSession = if (index != -1 && index < sessions.size) {
            sessions[index]
        } else {
            sessions.last()
        }

        // --- 데이터가 준비되었으니 화면에 반영 ---

        // 1. 내 주소(회사) 설정
        binding.beforeMyAddressTv.text = targetSession.companyAddress

        // 2. 스케줄 리스트 가져오기 및 시간순 정렬
        val scheduleList = targetSession.scheduleList.sortedBy { it.time }.toMutableList()

        // 3. (임시) 목 데이터 점수 생성
        // -> 실제 앱에서는 저장할 때 점수가 이미 있어야 하지만, 요청하신 로직 유지를 위해 여기서 랜덤 부여
        scheduleList.forEach {
            if (it.dayScore == 0) { // 점수가 없을 때만 랜덤 생성 (덮어쓰기 방지)
                it.dayScore = (40..95).random()
                it.nightScore = (30..80).random()
                it.dayDesc = "이 점수는 버스 운행횟수(20회), 인근 도로 트래픽, 학교(2곳), 지상 지하철역(1곳), 상권 밀도를 함께 반영해 계산됐어요."
                it.nightDesc = "이 점수는 버스 운행횟수(11회), 인근 도로 트래픽, 학교(2곳), 지상 지하철역(1곳), 상권 밀도를 함께 반영해 계산됐어요."
            }
        }

        // 4. 상단 탐색 리스트 설정
        setupExploreList(scheduleList)

        // 5. 하단 직주거리 랭크 리스트 설정
        setupLengthRankList(scheduleList)

        // 6. "가장 짧은 곳" 텍스트 업데이트
        updateShortestRankText(scheduleList)

        // 7. 소음 요약 텍스트 업데이트
        updateNoiseSummaryText(scheduleList)

        // 8. 그래프 설정
        setupGraph(scheduleList)
    }

    private fun setupGraph(list : List<ScheduleItem>){
        // list.size 등 불필요한 변수 제거
        binding.beforeGraphRv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)

        graphAdapter = GraphAdapter(list) { description ->
            if (description.isEmpty()) {
                binding.graphDetailTv.visibility = View.GONE
            } else {
                binding.graphDetailTv.visibility = View.VISIBLE
                binding.graphDetailTv.text = description
            }
        }
        binding.beforeGraphRv.adapter = graphAdapter
    }

    private fun setupDayNightButtons() {
        val selectedBg = ContextCompat.getDrawable(this, R.drawable.blue_bg6)
        val unselectedBg = ContextCompat.getDrawable(this, R.drawable.gray_bg6)
        val whiteColor = ContextCompat.getColor(this, R.color.white)
        val grayColor = ContextCompat.getColor(this, R.color.gray_500)

        binding.beforeDayBtn.setOnClickListener {
            if (!isDayMode) {
                isDayMode = true
                binding.beforeDayBtn.background = selectedBg
                binding.beforeDayBtn.setTextColor(whiteColor)
                binding.beforeNightBtn.background = unselectedBg
                binding.beforeNightBtn.setTextColor(grayColor)

                // graphAdapter 초기화 전에 클릭될 수 있으므로 null 체크 혹은 lateinit 보장 필요
                // loadAndSetupSession이 먼저 호출되므로 괜찮음
                graphAdapter.setMode(true)
                binding.graphDetailTv.visibility = View.GONE
            }
        }

        binding.beforeNightBtn.setOnClickListener {
            if (isDayMode) {
                isDayMode = false
                binding.beforeNightBtn.background = selectedBg
                binding.beforeNightBtn.setTextColor(whiteColor)
                binding.beforeDayBtn.background = unselectedBg
                binding.beforeDayBtn.setTextColor(grayColor)

                graphAdapter.setMode(false)
                binding.graphDetailTv.visibility = View.GONE
            }
        }
    }

    private fun updateNoiseSummaryText(list: List<ScheduleItem>) {
        if (list.isEmpty()) return

        val minDayItem = list.minByOrNull { it.dayScore }
        val minDayRank = if (minDayItem != null) ('A'.code + list.indexOf(minDayItem)).toChar() else '?'

        val minNightItem = list.minByOrNull { it.nightScore }
        val minNightRank = if (minNightItem != null) ('A'.code + list.indexOf(minNightItem)).toChar() else '?'

        val text = "낮에는 $minDayRank, 밤에는 $minNightRank 가 \n소음이 가장 낮아요."
        val spannable = SpannableString(text)
        val blueColor = ContextCompat.getColor(this, R.color.brand_100)

        val dayIndex = text.indexOf(minDayRank.toString())
        if (dayIndex != -1) {
            spannable.setSpan(ForegroundColorSpan(blueColor), dayIndex, dayIndex + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        val nightIndex = text.lastIndexOf(minNightRank.toString())
        if (nightIndex != -1) {
            spannable.setSpan(ForegroundColorSpan(blueColor), nightIndex, nightIndex + 1, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        binding.beforeNoiseTv.text = spannable
    }

    // [삭제됨] setupMyAddress(), getScheduleList() -> loadAndSetupSession()으로 통합됨

    private fun setupExploreList(list: List<ScheduleItem>) {
        binding.beforeExploreListRv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.beforeExploreListRv.adapter = BeforeExploreAdapter(list)
    }

    private fun setupLengthRankList(list: List<ScheduleItem>) {
        binding.beforeLengthRankRv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.beforeLengthRankRv.adapter = LengthRankAdapter(list)
    }

    private fun updateShortestRankText(list: List<ScheduleItem>) {
        if (list.isNotEmpty()) {
            val shortestAddress = list[0].address // A랭크(0번)가 가장 짧다고 가정
            val fullText = "직주거리는 $shortestAddress 이(가) \n가장 짧아요"
            val spannable = SpannableString(fullText)
            val startIndex = fullText.indexOf(shortestAddress)

            if (startIndex != -1) {
                val endIndex = startIndex + shortestAddress.length
                val blueColor = ContextCompat.getColor(this, R.color.brand_500)

                spannable.setSpan(
                    ForegroundColorSpan(blueColor),
                    startIndex,
                    endIndex,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            binding.beforeLengthRankTv.text = spannable
        } else {
            binding.beforeLengthRankTv.text = "일정 정보가 없습니다."
        }
    }
}