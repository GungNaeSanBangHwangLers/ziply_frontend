package com.keder.zply

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.viewpager2.widget.ViewPager2
import com.keder.zply.databinding.FragmentExistingInfoBinding
import kotlinx.coroutines.launch

class ExistingInfoFragment : Fragment() {

    private var _binding: FragmentExistingInfoBinding? = null
    private val binding get() = _binding!!

    private var cardId: String = ""

    private var currentTabIdx = 0

    private var isDayMode = true
    private var originalScheduleList: List<ScheduleItem> = emptyList()

    private var isFirstTabInit = true

    private var transportMessage: String = ""
    private var bicycleMessage: String = ""

    private lateinit var cardAdapter: ExistingInfoCardAdapter
    private lateinit var lengthAdapter: LengthRankAdapter
    private lateinit var graphAdapter: GraphAdapter
    private lateinit var safetyGraphAdapter: GraphAdapter

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
        binding.tabDistance.setOnClickListener { if (currentTabIdx != 0) { currentTabIdx = 0; updateTabUI() } }
        binding.tabNoise.setOnClickListener { if (currentTabIdx != 1) { currentTabIdx = 1; updateTabUI() } }
        binding.tabSafety.setOnClickListener { if (currentTabIdx != 2) { currentTabIdx = 2; updateTabUI() } }

        binding.chipWalk.setOnClickListener { updateTransportUI(0) }
        binding.chipPublic.setOnClickListener { updateTransportUI(1) }
        binding.chipCar.setOnClickListener { updateTransportUI(2) }
        binding.chipBike.setOnClickListener { updateTransportUI(3) }

        val ctx = requireContext()
        val grayColor = ContextCompat.getColor(ctx, R.color.gray_900)
        val blueColor = ContextCompat.getColor(ctx, R.color.brand_700)

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

        // ★ 바인딩이 널인지 안전 확인
        val safeBinding = _binding ?: return

        safeBinding.tabDistance.setTextColor(if (currentTabIdx == 0) selectedColor else unselectedColor)
        safeBinding.tabNoise.setTextColor(if (currentTabIdx == 1) selectedColor else unselectedColor)
        safeBinding.tabSafety.setTextColor(if (currentTabIdx == 2) selectedColor else unselectedColor)

        safeBinding.layoutContentDistance.visibility = if (currentTabIdx == 0) View.VISIBLE else View.GONE
        safeBinding.layoutContentNoise.visibility = if (currentTabIdx == 1) View.VISIBLE else View.GONE
        safeBinding.layoutContentSafety.visibility = if (currentTabIdx == 2) View.VISIBLE else View.GONE

        val targetTab = when (currentTabIdx) {
            0 -> safeBinding.tabDistance
            1 -> safeBinding.tabNoise
            else -> safeBinding.tabSafety
        }

        // ★ post(비동기) 내부에서 파괴 여부 꼼꼼하게 체크
        targetTab.post {
            val currentBinding = _binding ?: return@post
            val tabLocation = IntArray(2)
            targetTab.getLocationInWindow(tabLocation)

            val parentLocation = IntArray(2)
            (currentBinding.tabIndicator.parent as View).getLocationInWindow(parentLocation)

            val targetX = (tabLocation[0] - parentLocation[0]).toFloat() - currentBinding.tabIndicator.left
            val targetWidth = targetTab.width

            if (targetWidth <= 0) {
                targetTab.post { updateTabUI() }
                return@post
            }

            if (isFirstTabInit) {
                isFirstTabInit = false
                currentBinding.tabIndicator.translationX = targetX
                val params = currentBinding.tabIndicator.layoutParams
                params.width = targetWidth
                currentBinding.tabIndicator.layoutParams = params
                return@post
            }

            val startX = currentBinding.tabIndicator.translationX
            val startWidth = currentBinding.tabIndicator.width

            val animator = android.animation.ValueAnimator.ofFloat(0f, 1f)
            animator.duration = 250
            animator.addUpdateListener { animation ->
                val activeBinding = _binding ?: return@addUpdateListener // 중간에 파괴되면 즉시 정지
                val fraction = animation.animatedFraction
                activeBinding.tabIndicator.translationX = startX + (targetX - startX) * fraction
                val params = activeBinding.tabIndicator.layoutParams
                params.width = (startWidth + (targetWidth - startWidth) * fraction).toInt()
                activeBinding.tabIndicator.layoutParams = params
            }
            animator.start()
        }
    }

    private fun updateTransportUI(mode: Int) {
        if (originalScheduleList.isEmpty()) return

        val ctx = context ?: return
        val safeBinding = _binding ?: return

        val chips = listOf(safeBinding.chipWalk, safeBinding.chipPublic, safeBinding.chipCar, safeBinding.chipBike)
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
                safeBinding.tvTransportInfo.visibility = if (transportMessage.isNotEmpty()) View.VISIBLE else View.GONE
                safeBinding.tvTransportInfo.text = transportMessage
            }
            3 -> {
                safeBinding.tvTransportInfo.visibility = if (bicycleMessage.isNotEmpty()) View.VISIBLE else View.GONE
                safeBinding.tvTransportInfo.text = bicycleMessage
            }
            else -> safeBinding.tvTransportInfo.visibility = View.GONE
        }

        val sortedList = originalScheduleList.sortedBy { item ->
            when (mode) {
                0 -> item.walkingTimeMin; 1 -> item.transitTimeMin; 2 -> item.carTimeMin; 3 -> item.bicycleTimeMin; else -> item.walkingTimeMin
            }
        }

        if (!::lengthAdapter.isInitialized) {
            lengthAdapter = LengthRankAdapter(sortedList)
            safeBinding.lengthRankRv.adapter = lengthAdapter
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
            safeBinding.lengthRankTv.text = spannable
        } else {
            safeBinding.lengthRankTv.text = "경로를 찾을 수 없어요"
        }
    }

    private fun loadAllDataSafe() {
        val ctx = context ?: return
        lifecycleScope.launch {
            _binding?.loadingLayout?.visibility = View.VISIBLE
            val service = RetrofitClient.getInstance(ctx)
            val prefs = ctx.getSharedPreferences("ZplyMeasurementPrefs", Context.MODE_PRIVATE)

            try {
                val houses = try { service.getCardHouseList(cardId).body() ?: emptyList() } catch (e: Exception) { emptyList() }

                val addressRes = try { service.getCardAddresses(cardId).body() } catch (e: Exception) { null }
                var companyAddr = "직장 정보 없음"
                if (!addressRes.isNullOrEmpty()) {
                    companyAddr = addressRes[0].address ?: "직장 정보 없음"
                }
                _binding?.lengthRankDesTv?.text = "[$companyAddr]부터 각 주거지까지의 거리예요."

                val distBody = try { service.getAnalysisDistance(cardId).body()?.firstOrNull() } catch (e: Exception) { null }
                transportMessage = distBody?.transportMessage ?: ""
                bicycleMessage = distBody?.bicycleMessage ?: ""
                val distanceMap = distBody?.results?.associateBy { it.houseId } ?: emptyMap()

                val lifeMap = try { service.getAnalysisLife(cardId).body()?.associateBy { it.houseId } ?: emptyMap() } catch (e: Exception) { emptyMap() }

                val safetyRes = try { service.getAnalysisSafety(cardId).body() } catch (e: Exception) { null }

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
                            if (validLights.isNotEmpty()) displayLight = validLights.map { it.lightLevel!! }.average().toFloat()
                            if (!detailRes.imageUrls.isNullOrEmpty()) serverImageUrls.addAll(detailRes.imageUrls)
                        }
                    } catch (e: Exception) {}

                    val savedRoomCount = prefs.getInt("room_${house.houseId}", -1)
                    val savedLux = prefs.getFloat("lux_${house.houseId}", -1f)
                    val savedPhotosStr = prefs.getString("photos_${house.houseId}", "") ?: ""
                    val localPhotos = if (savedPhotosStr.isNotEmpty()) savedPhotosStr.split(",") else emptyList()

                    if (savedRoomCount >= 0) measuredRooms = savedRoomCount
                    if (savedLux >= 0f) displayLight = savedLux

                    val combinedImages = (serverImageUrls + localPhotos).distinct().toMutableList()

                    val dist = distanceMap[house.houseId]
                    val life = lifeMap[house.houseId]

                    detailList.add(ScheduleItem(
                        houseId = house.houseId,
                        address = house.address ?: "주소 없음",
                        // ★ 서버에서 온 T 제거 및 포맷팅 (초 자르기)
                        time = house.visitTime?.replace("T", " ")?.take(16) ?: "",
                        rankLabel = house.label ?: "?",
                        measuredLightLux = displayLight,
                        measuredRoomCount = measuredRooms,
                        imageList = combinedImages,
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

                val safetyUiList = safetyRes?.map { safety ->
                    ScheduleItem(
                        houseId = safety.houseId,
                        address = "", time = "",
                        dayScore = safety.safetyScore,
                        nightScore = 0,
                        dayDesc = safety.message ?: "CCTV ${safety.cctvCount}대 · 가로등 ${safety.streetlightCount}개 · 치안시설 ${safety.policeCount}곳",
                        nightDesc = "",
                        rankLabel = originalScheduleList.find { it.houseId == safety.houseId }?.rankLabel ?: "?"
                    )
                }?.sortedBy { it.rankLabel } ?: emptyList()

                if (safetyUiList.isNotEmpty()) {
                    setupSafetyGraph(safetyUiList)
                    updateSafetySummaryText(safetyUiList)
                }

            } catch (e: Exception) {
                Log.e("ExistingInfo", "렌더링 에러", e)
            } finally {
                _binding?.loadingLayout?.visibility = View.GONE
            }
        }
    }

    private fun setupRecyclerViews(list: List<ScheduleItem>) {
        cardAdapter = ExistingInfoCardAdapter(list, onImageClick = { clickedItem, clickedIndex ->
            showImageDialog(clickedItem, clickedIndex)
        })
        _binding?.existingCardRv?.adapter = cardAdapter

        graphAdapter = GraphAdapter(list) { desc ->
            _binding?.graphDetailTv?.visibility = if (desc.isNotEmpty()) View.VISIBLE else View.GONE
            _binding?.graphDetailTv?.text = desc
        }
        _binding?.graphRv?.adapter = graphAdapter
        graphAdapter.setMode(isDayMode)
    }

    private fun showImageDialog(item: ScheduleItem, startIndex: Int) {
        if (item.imageList.isEmpty()) return

        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_after_image)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)

        dialog.window?.apply {
            addFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.8f)
        }

        val rankTv = dialog.findViewById<TextView>(R.id.dialog_rank_tv)
        val dateTv = dialog.findViewById<TextView>(R.id.dialog_date_tv)
        val addressTv = dialog.findViewById<TextView>(R.id.dialog_address_tv)
        val closeBtn = dialog.findViewById<ImageView>(R.id.dialog_close_btn)
        val imageVp = dialog.findViewById<ViewPager2>(R.id.dialog_image_vp)
        val indicatorLl = dialog.findViewById<LinearLayout>(R.id.dialog_indicator_ll)

        rankTv.text = item.rankLabel
        dateTv.text = "${item.time} 탐색"
        addressTv.text = item.address

        imageVp.adapter = DialogImageAdapter(item.imageList)
        imageVp.setCurrentItem(startIndex, false)

        val dotCount = item.imageList.size
        val dots = arrayOfNulls<ImageView>(dotCount)
        val dpToPx = { dp: Int -> (dp * resources.displayMetrics.density).toInt() }
        val sizePx = dpToPx(4)
        val marginPx = dpToPx(4)

        for (i in 0 until dotCount) {
            dots[i] = ImageView(requireContext()).apply {
                layoutParams = LinearLayout.LayoutParams(sizePx, sizePx).apply {
                    setMargins(marginPx, 0, marginPx, 0)
                }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    val colorRes = if (i == startIndex) R.color.brand_800 else R.color.gray_700
                    setColor(ContextCompat.getColor(requireContext(), colorRes))
                }
            }
            indicatorLl.addView(dots[i])
        }

        imageVp.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                for (i in 0 until dotCount) {
                    val drawable = dots[i]?.background as? GradientDrawable
                    val colorRes = if (i == position) R.color.brand_800 else R.color.gray_700
                    drawable?.setColor(ContextCompat.getColor(requireContext(), colorRes))
                }
            }
        })

        closeBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun setupSafetyGraph(list: List<ScheduleItem>) {
        _binding?.safetyGraphRv?.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        safetyGraphAdapter = GraphAdapter(list) { desc ->
            _binding?.safetyGraphDetailTv?.visibility = if (desc.isNotEmpty()) View.VISIBLE else View.GONE
            _binding?.safetyGraphDetailTv?.text = desc
        }
        _binding?.safetyGraphRv?.adapter = safetyGraphAdapter
        safetyGraphAdapter.setMode(true)
    }

    private fun updateSafetySummaryText(list: List<ScheduleItem>) {
        val maxItem = list.maxByOrNull { it.dayScore }
        val maxRank = maxItem?.rankLabel ?: "-"
        val text = "안전 점수는 $maxRank 가 \n가장 높아요."
        val spannable = SpannableString(text)
        val brandColor = ContextCompat.getColor(requireContext(), R.color.brand_600)

        val rankIndex = text.indexOf(maxRank)
        if (rankIndex != -1) {
            spannable.setSpan(ForegroundColorSpan(brandColor), rankIndex, rankIndex + maxRank.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        _binding?.safetyTv?.text = spannable
    }

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

        _binding?.noiseTv?.text = noiseSpan
    }

    fun updateMeasurementLocal(houseId: Long, roomCount: Int = -1, lightLux: Float = -1f, newImagePath: String? = null) {
        val index = originalScheduleList.indexOfFirst { it.houseId == houseId }

        if (index != -1) {
            val item = originalScheduleList[index]
            if (roomCount >= 0) item.measuredRoomCount = roomCount
            if (lightLux >= 0f) item.measuredLightLux = lightLux

            if (newImagePath != null) {
                if (!item.imageList.contains(newImagePath)) {
                    item.imageList.add(newImagePath)
                }
            }

            if (::cardAdapter.isInitialized) {
                activity?.runOnUiThread {
                    // UI 스레드에서 어댑터를 확실하게 새로고침
                    cardAdapter.notifyItemChanged(index)
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}