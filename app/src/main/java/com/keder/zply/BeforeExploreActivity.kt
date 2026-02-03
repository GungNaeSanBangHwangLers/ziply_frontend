package com.keder.zply

import android.content.Intent
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.View
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.keder.zply.databinding.ActivityBeforeExploreBinding
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class BeforeExploreActivity : AppCompatActivity() {

    private lateinit var binding: ActivityBeforeExploreBinding

    private lateinit var mainListAdapter: BeforeExploreAdapter
    private lateinit var lengthAdapter: LengthRankAdapter
    private lateinit var graphAdapter: GraphAdapter

    private var currentCardId: String = ""
    private var isDayMode = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBeforeExploreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentCardId = intent.getStringExtra("CARD_ID") ?: ""

        // [로그] 1. 액티비티 진입 및 ID 확인
        Log.d("API_DEBUG", "========== [BeforeExploreActivity] 시작 ==========")
        Log.d("API_DEBUG", "전달받은 CardID: '$currentCardId'")

        if (currentCardId.isEmpty()) {
            showCustomToast("전체 내용을 입력해주세요")
            Log.e("API_DEBUG", "오류: CardID가 없음 -> 종료")
            finish()
            return
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                goToMainActivity()
            }
        })

        setupListeners()
        loadAllData()
    }

    private fun goToMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun setupListeners() {
        binding.beforeBackIv.setOnClickListener { goToMainActivity() }

        val context = this
        val selectedBg = ContextCompat.getDrawable(context, R.drawable.blue_bg6)
        val unselectedBg = ContextCompat.getDrawable(context, R.drawable.gray_bg6)
        val whiteColor = ContextCompat.getColor(context, R.color.white)
        val grayColor = ContextCompat.getColor(context, R.color.gray_500)

        binding.beforeDayBtn.setOnClickListener {
            if (!isDayMode) {
                isDayMode = true
                binding.beforeDayBtn.background = selectedBg
                binding.beforeDayBtn.setTextColor(whiteColor)
                binding.beforeNightBtn.background = unselectedBg
                binding.beforeNightBtn.setTextColor(grayColor)
                if (::graphAdapter.isInitialized) graphAdapter.setMode(true)
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
                if (::graphAdapter.isInitialized) graphAdapter.setMode(false)
                binding.graphDetailTv.visibility = View.GONE
            }
        }
    }

    private fun loadAllData() {
        lifecycleScope.launch {
            binding.loadingLayout.visibility = View.VISIBLE
            var retryCount = 0
            val maxRetries = 5
            var isSuccess = false

            while (retryCount < maxRetries) {
                try {
                    Log.d("API_DEBUG", "--- 데이터 로딩 시도: ${retryCount + 1}/$maxRetries ---")
                    val service = RetrofitClient.getInstance(this@BeforeExploreActivity)

                    // API 호출 (4개)
                    val houseDeferred = async { service.getCardHouseList(currentCardId) }
                    val scoreDeferred = async { service.getAnalysisScore(currentCardId) }
                    val distanceDeferred = async { service.getAnalysisDistance(currentCardId) }
                    val addressDeferred = async {service.getCardAddresses(currentCardId)}

                    val houseRes = houseDeferred.await()
                    val scoreRes = scoreDeferred.await()
                    val distanceRes = distanceDeferred.await()
                    val addressRes = addressDeferred.await()

                    // 필수 데이터 확인 (집 목록, 거리 정보)
                    val isHouseReady = houseRes.isSuccessful && !houseRes.body().isNullOrEmpty()
                    var isDistanceReady = false
                    if (distanceRes.isSuccessful && distanceRes.body() != null) {
                        val body = distanceRes.body()!!
                        if (body.basePoints.isNotEmpty()) {
                            isDistanceReady = true
                        }
                    }

                    if (isHouseReady && isDistanceReady) {
                        Log.d("API_DEBUG", ">> 모든 필수 데이터 준비 완료! UI 렌더링 시작")

                        val houses = houseRes.body()!!
                        val distBody = distanceRes.body()!!

                        // --- 1. 직장 주소 처리 ---
                        var companyAddr = "직장 정보 없음"
                        if (addressRes.isSuccessful && addressRes.body() != null) {
                            val addrList = addressRes.body()!!
                            if (addrList.isNotEmpty()) {
                                companyAddr = addrList[0].address
                            }
                        }

                        // ★ [수정] 여기가 빠져 있었습니다! UI에 반영
                        binding.beforeMyAddressTv.text = companyAddr
                        Log.d("API_DEBUG", "직장 주소 UI 반영 완료: $companyAddr")

                        // --- 2. 거리 데이터 매핑 ---
                        val distanceMap = mutableMapOf<Long, Pair<Int, Double>>()
                        distBody.basePoints[0].results.forEach {
                            distanceMap[it.houseId] = Pair(it.walkingTimeMin, it.walkingDistanceKm)
                        }

                        // --- 3. 점수 데이터 매핑 ---
                        val scoreMap = if (scoreRes.isSuccessful && scoreRes.body() != null) {
                            scoreRes.body()!!.associateBy { it.houseId }
                        } else {
                            emptyMap()
                        }

                        // --- 4. 리스트 합치기 및 어댑터 연결 ---
                        val sortedHouses = houses.sortedBy { it.visitTime }
                        val uiList = sortedHouses.mapIndexed { index, house ->
                            val dist = distanceMap[house.houseId]
                            val score = scoreMap[house.houseId]

                            ScheduleItem(
                                houseId = house.houseId,
                                address = house.address,
                                time = house.visitTime,
                                rankLabel = ('A'.code + index).toChar().toString(),
                                dayScore = score?.dayScore ?: 0,
                                nightScore = score?.nightScore ?: 0,
                                dayDesc = score?.message ?: "",
                                nightDesc = score?.message ?: "",
                                walkingTimeMin = dist?.first ?: 0,
                                walkingDistanceKm = dist?.second ?: 0.0
                            )
                        }

                        setupRecyclerViews(uiList)
                        updateSummaries(uiList)

                        isSuccess = true
                        break
                    } else {
                        Log.w("API_DEBUG", "데이터 불완전 -> 0.5초 대기 후 재시도")
                    }

                } catch (e: Exception) {
                    Log.e("API_DEBUG", "에러 발생: ${e.message}", e)
                }

                retryCount++
                delay(500)
            }
            binding.loadingLayout.visibility = View.GONE

            if (!isSuccess) {
                // 실패 처리 (토스트 등)
            }
        }
    }
    private fun setupRecyclerViews(list: List<ScheduleItem>) {
        mainListAdapter = BeforeExploreAdapter(list)
        binding.beforeExploreListRv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.beforeExploreListRv.adapter = mainListAdapter

        val sortedByDist = list.sortedBy { it.walkingTimeMin }
        lengthAdapter = LengthRankAdapter(sortedByDist)
        binding.beforeLengthRankRv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.beforeLengthRankRv.adapter = lengthAdapter

        graphAdapter = GraphAdapter(list) { desc ->
            if (desc.isNotEmpty()) {
                binding.graphDetailTv.visibility = View.VISIBLE
                binding.graphDetailTv.text = desc
            } else {
                binding.graphDetailTv.visibility = View.GONE
            }
        }
        binding.beforeGraphRv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.beforeGraphRv.adapter = graphAdapter
        graphAdapter.setMode(isDayMode)
    }

    private fun updateSummaries(list: List<ScheduleItem>) {
        val brandColor = ContextCompat.getColor(this, R.color.brand_700)

        // 거리 요약
        val bestDistItem = list.filter { it.walkingTimeMin > 0 }.minByOrNull { it.walkingTimeMin }
        if (bestDistItem != null) {
            val rank = bestDistItem.rankLabel
            val text = "직주거리는 $rank 가 \n가장 짧아요"
            val spannable = SpannableString(text)
            val idx = text.indexOf(rank)
            if (idx != -1) spannable.setSpan(ForegroundColorSpan(brandColor), idx, idx + rank.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            binding.beforeLengthRankTv.text = spannable
        } else {
            binding.beforeLengthRankTv.text = "거리 정보가 없습니다."
        }

        // 소음 요약
        val bestDay = list.filter { it.dayScore > 0 }.minByOrNull { it.dayScore }
        val bestNight = list.filter { it.nightScore > 0 }.minByOrNull { it.nightScore }

        val dayRank = bestDay?.rankLabel ?: "-"
        val nightRank = bestNight?.rankLabel ?: "-"
        val noiseText = "낮에는 $dayRank, 밤에는 $nightRank 가 \n소음이 가장 낮아요."
        val noiseSpan = SpannableString(noiseText)
        val dayIdx = noiseText.indexOf(dayRank)
        if (dayIdx != -1) noiseSpan.setSpan(ForegroundColorSpan(brandColor), dayIdx, dayIdx + dayRank.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        val nightIdx = noiseText.lastIndexOf(nightRank)
        if (nightIdx != -1) noiseSpan.setSpan(ForegroundColorSpan(brandColor), nightIdx, nightIdx + nightRank.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        binding.beforeNoiseTv.text = noiseSpan
    }
}