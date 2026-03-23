package com.keder.zply

import android.content.Context
import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.keder.zply.databinding.FragmentExistingInfoBinding
import kotlinx.coroutines.launch

class ExistingInfoFragment : Fragment() {

    private var _binding: FragmentExistingInfoBinding? = null
    private val binding get() = _binding!!

    private var cardId: String = ""
    private var isDistanceTabSelected = true
    private var isDayMode = true
    private var originalScheduleList: List<ScheduleItem> = emptyList()

    private var isFirstTabInit = true

    private var transportMessage: String = ""
    private var bicycleMessage: String = ""

    private lateinit var cardAdapter: ExistingInfoCardAdapter
    private lateinit var lengthAdapter: LengthRankAdapter
    private lateinit var graphAdapter: GraphAdapter

    companion object {
        fun newInstance(cardId: String): ExistingInfoFragment {
            val fragment = ExistingInfoFragment()
            val args = Bundle().apply { putString("CARD_ID", cardId) }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExistingInfoBinding.inflate(inflater, container, false)
        cardId = arguments?.getString("CARD_ID") ?: ""
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (cardId.isEmpty()) return

        setupListeners()
        updateTabUI()
        loadAllDataSafe()
    }

    private fun setupListeners() {
        binding.tabDistance.setOnClickListener {
            if (!isDistanceTabSelected) { isDistanceTabSelected = true; updateTabUI() }
        }
        binding.tabNoise.setOnClickListener {
            if (isDistanceTabSelected) { isDistanceTabSelected = false; updateTabUI() }
        }

        binding.chipWalk.setOnClickListener { updateTransportUI(0) }
        binding.chipPublic.setOnClickListener { updateTransportUI(1) }
        binding.chipCar.setOnClickListener { updateTransportUI(2) }
        binding.chipBike.setOnClickListener { updateTransportUI(3) }

        val ctx = requireContext()
        val grayColor = ContextCompat.getColor(context, R.color.gray_900)
        val blueColor = ContextCompat.getColor(context, R.color.brand_700)

        binding.dayBtn.setOnClickListener {
            if (!isDayMode) {
                isDayMode = true
                binding.dayBtn.backgroundTintList = ColorStateList.valueOf(blueColor)
                binding.nightBtn.backgroundTintList = ColorStateList.valueOf(grayColor)
                if (::graphAdapter.isInitialized) graphAdapter.setMode(true)
                binding.graphDetailTv.visibility = View.GONE
            }
        }
        binding.nightBtn.setOnClickListener {
            if (isDayMode) {
                isDayMode = false
                binding.dayBtn.backgroundTintList = ColorStateList.valueOf(grayColor)
                binding.nightBtn.backgroundTintList = ColorStateList.valueOf(blueColor)
                if (::graphAdapter.isInitialized) graphAdapter.setMode(false)
                binding.graphDetailTv.visibility = View.GONE
            }
        }
    }

    private fun updateTabUI() {
        val ctx = context ?: return
        val selectedColor = ContextCompat.getColor(ctx, R.color.brand_700)
        val unselectedColor = ContextCompat.getColor(ctx, R.color.gray_500)
        val targetTab = if (isDistanceTabSelected) binding.tabDistance else binding.tabNoise

        if (isDistanceTabSelected) {
            binding.tabDistance.setTextColor(selectedColor)
            binding.tabNoise.setTextColor(unselectedColor)
            binding.layoutContentDistance.visibility = View.VISIBLE
            binding.layoutContentNoise.visibility = View.GONE
        } else {
            binding.tabDistance.setTextColor(unselectedColor)
            binding.tabNoise.setTextColor(selectedColor)
            binding.layoutContentDistance.visibility = View.GONE
            binding.layoutContentNoise.visibility = View.VISIBLE
        }

        targetTab.post {
            val targetX = targetTab.x
            val targetWidth = targetTab.width

            // =========================================================
            // ★ 핵심 수정: 너비가 0이면 포기하는 게 아니라, 다 그려질 때까지 "재귀 호출"로 끈질기게 기다립니다!
            // =========================================================
            if (targetWidth <= 0) {
                targetTab.post { updateTabUI() } // <--- 이 줄이 추가되었습니다!
                return@post
            }

            if (isFirstTabInit) {
                isFirstTabInit = false
                binding.tabIndicator.translationX = targetX

                val params = binding.tabIndicator.layoutParams
                params.width = targetWidth
                binding.tabIndicator.layoutParams = params
                return@post
            }

            // 두 번째 클릭부터는 정상적으로 애니메이션 작동
            val startX = binding.tabIndicator.translationX
            val startWidth = binding.tabIndicator.width

            val animator = android.animation.ValueAnimator.ofFloat(0f, 1f)
            animator.duration = 250
            animator.addUpdateListener { animation ->
                val fraction = animation.animatedFraction
                binding.tabIndicator.translationX = startX + (targetX - startX) * fraction
                val params = binding.tabIndicator.layoutParams
                params.width = (startWidth + (targetWidth - startWidth) * fraction).toInt()
                binding.tabIndicator.layoutParams = params
            }
            animator.start()
        }
    }
    private fun updateTransportUI(mode: Int) {
        if (originalScheduleList.isEmpty()) return

        val ctx = context ?: return
        val chips = listOf(binding.chipWalk, binding.chipPublic, binding.chipCar, binding.chipBike)
        val selectedColor = ContextCompat.getColor(ctx, R.color.white)
        val unselectedColor = ContextCompat.getColor(ctx, R.color.gray_500)
        val brandColor = ContextCompat.getColor(ctx, R.color.brand_600)
        val grayColor = ContextCompat.getColor(ctx, R.color.gray_800)

        chips.forEachIndexed { index, textView ->
            if (index == mode) {
                textView.backgroundTintList = ColorStateList.valueOf(brandColor)
                textView.setTextColor(selectedColor)
            } else {
                textView.backgroundTintList = ColorStateList.valueOf(grayColor)
                textView.setTextColor(unselectedColor)
            }
        }

        when (mode) {
            1 -> {
                binding.tvTransportInfo.visibility = if (transportMessage.isNotEmpty()) View.VISIBLE else View.GONE
                binding.tvTransportInfo.text = transportMessage
            }
            3 -> {
                binding.tvTransportInfo.visibility = if (bicycleMessage.isNotEmpty()) View.VISIBLE else View.GONE
                binding.tvTransportInfo.text = bicycleMessage
            }
            else -> binding.tvTransportInfo.visibility = View.GONE
        }

        val sortedList = originalScheduleList.sortedBy { item ->
            when (mode) {
                0 -> item.walkingTimeMin; 1 -> item.transitTimeMin; 2 -> item.carTimeMin; 3 -> item.bicycleTimeMin; else -> item.walkingTimeMin
            }
        }

        if (!::lengthAdapter.isInitialized) {
            lengthAdapter = LengthRankAdapter(sortedList)
            binding.lengthRankRv.adapter = lengthAdapter
        } else {
            lengthAdapter.updateList(sortedList)
        }
        lengthAdapter.setMode(mode)

        val brand700 = ContextCompat.getColor(ctx, R.color.brand_700)
        val shortestItem = sortedList.filter { it.walkingDistanceKm > 0.0 || it.walkingTimeMin > 0 }
            .minByOrNull { if (it.walkingDistanceKm > 0.0) it.walkingDistanceKm else it.walkingTimeMin.toDouble() }

        if (shortestItem != null) {
            val rank = shortestItem.rankLabel
            val text = "직주거리는 $rank 가 \n가장 짧아요"
            val spannable = SpannableString(text)
            val idx = text.indexOf(rank)
            if (idx != -1) spannable.setSpan(ForegroundColorSpan(brand700), idx, idx + rank.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            binding.lengthRankTv.text = spannable
        } else {
            binding.lengthRankTv.text = "경로를 찾을 수 없어요"
        }
    }

    private fun loadAllDataSafe() {
        val ctx = context ?: return
        lifecycleScope.launch {
            binding.loadingLayout.visibility = View.VISIBLE
            val service = RetrofitClient.getInstance(ctx)

            // ★ 추가됨: 스마트폰 로컬에 저장된 측정 기록을 불러오기 위한 객체
            val prefs = ctx.getSharedPreferences("ZplyMeasurementPrefs", Context.MODE_PRIVATE)

            try {
                val houses = try { service.getCardHouseList(cardId).body() ?: emptyList() } catch (e: Exception) { emptyList() }

                val addressRes = try { service.getCardAddresses(cardId).body() } catch (e: Exception) { null }
                var companyAddr = "직장 정보 없음"
                if (!addressRes.isNullOrEmpty()) {
                    companyAddr = addressRes[0].address ?: "직장 정보 없음"
                }
                binding.lengthRankDesTv.text = "[$companyAddr]부터 각 주거지까지의 거리예요."

                val distBody = try { service.getAnalysisDistance(cardId).body()?.firstOrNull() } catch (e: Exception) { null }
                transportMessage = distBody?.transportMessage ?: ""
                bicycleMessage = distBody?.bicycleMessage ?: ""
                val distanceMap = distBody?.results?.associateBy { it.houseId } ?: emptyMap()

                val lifeMap = try { service.getAnalysisLife(cardId).body()?.associateBy { it.houseId } ?: emptyMap() } catch (e: Exception) { emptyMap() }

                val sortedHouses = houses.sortedBy { it.visitTime ?: "" }
                val detailList = mutableListOf<ScheduleItem>()

                for (house in sortedHouses) {
                    var displayLight = -1f
                    var measuredRooms = 0
                    val serverImageUrls = mutableListOf<String>()

                    try {
                        val detailRes = service.getHouseCardDetail(house.houseId).body()
                        if (detailRes != null) {
                            val cards = detailRes.measurementCards ?: emptyList()
                            measuredRooms = cards.count { it.isDirectionDone }
                            val validLights = cards.filter { it.isLightDone && it.lightLevel != null }
                            if (validLights.isNotEmpty()) {
                                displayLight = validLights.map { it.lightLevel!! }.average().toFloat()
                            }
                            if (!detailRes.imageUrls.isNullOrEmpty()) {
                                serverImageUrls.addAll(detailRes.imageUrls)
                            }
                        }
                    } catch (e: Exception) {}

                    // ==========================================================
                    // ★ 핵심 로직: 백엔드에 데이터가 없어도 로컬 저장소에서 값을 꺼내서 덮어씌움
                    // ==========================================================
                    val savedRoomCount = prefs.getInt("room_${house.houseId}", -1)
                    val savedLux = prefs.getFloat("lux_${house.houseId}", -1f)
                    val savedPhotosStr = prefs.getString("photos_${house.houseId}", "") ?: ""
                    val localPhotos = if (savedPhotosStr.isNotEmpty()) savedPhotosStr.split(",") else emptyList()

                    if (savedRoomCount >= 0) measuredRooms = savedRoomCount
                    if (savedLux >= 0f) displayLight = savedLux

                    // 서버 사진 + 방금 찍은 로컬 사진 병합 (중복 제거)
                    val combinedImages = (serverImageUrls + localPhotos).distinct().toMutableList()

                    val dist = distanceMap[house.houseId]
                    val life = lifeMap[house.houseId]

                    detailList.add(ScheduleItem(
                        houseId = house.houseId,
                        address = house.address ?: "주소 없음",
                        time = house.visitTime ?: "",
                        rankLabel = house.label ?: "?",
                        measuredLightLux = displayLight, // ★ 로컬 채광값 적용 완료
                        measuredRoomCount = measuredRooms, // ★ 로컬 방 갯수 적용 완료
                        imageList = combinedImages, // ★ 로컬 사진 적용 완료
                        walkingTimeMin = dist?.walkingTimeMin ?: 0,
                        walkingDistanceKm = dist?.walkingDistanceKm ?: 0.0,
                        transitTimeMin = dist?.transitTimeMin ?: 0,
                        transitPayment = dist?.transitPaymentStr ?: "",
                        carTimeMin = dist?.carTimeMin ?: 0,
                        bicycleTimeMin = dist?.bicycleTimeMin ?: 0,
                        dayScore = life?.dayScore ?: 0,
                        nightScore = life?.nightScore ?: 0,
                        dayDesc = life?.message ?: "",
                        nightDesc = life?.message ?: ""
                    ))
                }

                originalScheduleList = detailList.sortedBy { it.rankLabel }
                setupRecyclerViews(originalScheduleList)
                updateSummaries(originalScheduleList)
                updateTransportUI(0)

            } catch (e: Exception) {
                Log.e("ExistingInfo", "렌더링 에러", e)
            } finally {
                binding.loadingLayout.visibility = View.GONE
            }
        }
    }

    private fun setupRecyclerViews(list: List<ScheduleItem>) {
        cardAdapter = ExistingInfoCardAdapter(list)
        binding.existingCardRv.adapter = cardAdapter

        graphAdapter = GraphAdapter(list) { desc ->
            binding.graphDetailTv.visibility = if (desc.isNotEmpty()) View.VISIBLE else View.GONE
            binding.graphDetailTv.text = desc
        }
        binding.graphRv.adapter = graphAdapter
        graphAdapter.setMode(isDayMode)
    }

    // ★ 수정됨: 소음 점수가 가장 '높은(max)' 것이 가장 조용한 집
    private fun updateSummaries(list: List<ScheduleItem>) {
        val bestDay = list.filter { it.dayScore > 0 }.maxByOrNull { it.dayScore }
        val bestNight = list.filter { it.nightScore > 0 }.maxByOrNull { it.nightScore }

        val dayRank = bestDay?.rankLabel ?: "-"
        val nightRank = bestNight?.rankLabel ?: "-"
        val ctx = context ?: return
        val brandColor = ContextCompat.getColor(ctx, R.color.brand_700)

        val noiseText = "낮에는 $dayRank, 밤에는 $nightRank 가 \n소음이 가장 낮아요."
        val noiseSpan = SpannableString(noiseText)
        val dayIdx = noiseText.indexOf(dayRank)
        if (dayIdx != -1) noiseSpan.setSpan(ForegroundColorSpan(brandColor), dayIdx, dayIdx + dayRank.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        val nightIdx = noiseText.lastIndexOf(nightRank)
        if (nightIdx != -1) noiseSpan.setSpan(ForegroundColorSpan(brandColor), nightIdx, nightIdx + nightRank.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)

        binding.noiseTv.text = noiseSpan
    }

    // ★ 추가됨: Ing 화면에서 측정 시 즉시 이 프래그먼트의 카드들을 업데이트하는 창구
    fun updateMeasurementLocal(houseId: Long, roomCount: Int = -1, lightLux: Float = -1f, newImagePath: String? = null) {
        val index = originalScheduleList.indexOfFirst { it.houseId == houseId }
        if (index != -1) {
            if (roomCount >= 0) originalScheduleList[index].measuredRoomCount = roomCount
            if (lightLux >= 0f) originalScheduleList[index].measuredLightLux = lightLux
            if (newImagePath != null) originalScheduleList[index].imageList.add(newImagePath) // ★ 사진 추가
            if (::cardAdapter.isInitialized) cardAdapter.notifyItemChanged(index)
        }
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}