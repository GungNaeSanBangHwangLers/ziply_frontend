package com.keder.zply

import android.content.res.ColorStateList
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.keder.zply.databinding.FragmentAfterLengthBinding
import kotlinx.coroutines.launch

class AfterLengthFragment : Fragment() {
    private var _binding: FragmentAfterLengthBinding? = null
    private val binding get() = _binding!!

    private var originalScheduleList: List<ScheduleItem> = emptyList()
    private var transportMessage: String = ""
    private var bicycleMessage: String = ""
    private lateinit var adapter: LengthRankAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAfterLengthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnRetry.setOnClickListener { loadDistanceData() }
        setupChipListeners()
        loadDistanceData()
    }

    private fun setupChipListeners() {
        binding.chipWalk.setOnClickListener { updateTransportUI(0) }
        binding.chipPublic.setOnClickListener { updateTransportUI(1) }
        binding.chipCar.setOnClickListener { updateTransportUI(2) }
        binding.chipBike.setOnClickListener { updateTransportUI(3) }
    }

    private fun loadDistanceData() {
        val activity = requireActivity() as? AfterExploreActivity ?: return
        val cardId = activity.currentCardId
        if (cardId.isEmpty()) return

        binding.errorLayout.visibility = View.GONE

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getInstance(requireContext()).getAnalysisDistance(cardId)
                if (response.isSuccessful && response.body() != null) {
                    val distBody = response.body()?.firstOrNull()
                    val results = distBody?.results ?: emptyList()
                    transportMessage = distBody?.transportMessage ?: ""
                    bicycleMessage = distBody?.bicycleMessage ?: ""

                    if (results.isNotEmpty()) {
                        originalScheduleList = results.map { res ->
                            ScheduleItem(
                                houseId = res.houseId, address = "", time = "",
                                rankLabel = activity.getRankLabel(res.houseId),
                                walkingTimeMin = res.walkingTimeMin, walkingDistanceKm = res.walkingDistanceKm,
                                transitTimeMin = res.transitTimeMin, transitPayment = res.transitPaymentStr ?: "",
                                carTimeMin = res.carTimeMin, bicycleTimeMin = res.bicycleTimeMin
                            )
                        }

                        adapter = LengthRankAdapter(originalScheduleList)
                        binding.afterLengthRankRv.adapter = adapter
                        binding.afterLengthRankRv.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

                        val favoriteViewModel = androidx.lifecycle.ViewModelProvider(requireActivity())[FavoriteViewModel::class.java]
                        favoriteViewModel.favoriteSet.observe(viewLifecycleOwner) { favorites ->
                            adapter.updateFavorites(favorites)
                        }

                        updateTransportUI(0)
                    } else {
                        binding.errorLayout.visibility = View.VISIBLE
                    }
                } else {
                    binding.errorLayout.visibility = View.VISIBLE
                }
            } catch (e: Exception) {
                binding.errorLayout.visibility = View.VISIBLE
            }
        }
    }

    private fun updateTransportUI(mode: Int) {
        if (originalScheduleList.isEmpty()) return

        val chips = listOf(binding.chipWalk, binding.chipPublic, binding.chipCar, binding.chipBike)
        val selectedTextColor = ContextCompat.getColor(requireContext(), R.color.white)
        val unselectedTextColor = ContextCompat.getColor(requireContext(), R.color.gray_500)
        val brandColor = ContextCompat.getColor(requireContext(), R.color.brand_600)
        val grayColor = ContextCompat.getColor(requireContext(), R.color.gray_800)

        chips.forEachIndexed { index, textView ->
            if (index == mode) {
                textView.backgroundTintList = ColorStateList.valueOf(brandColor)
                textView.setTextColor(selectedTextColor)
            } else {
                textView.backgroundTintList = ColorStateList.valueOf(grayColor)
                textView.setTextColor(unselectedTextColor)
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

        // ★ 3. 데이터가 없는 구간을 최단거리로, 이후 도보 시간으로 우선순위 정렬
        val sortedList = originalScheduleList.sortedWith(Comparator { a, b ->
            val timeA = when (mode) { 0 -> a.walkingTimeMin; 1 -> a.transitTimeMin; 2 -> a.carTimeMin; 3 -> a.bicycleTimeMin; else -> a.walkingTimeMin }
            val timeB = when (mode) { 0 -> b.walkingTimeMin; 1 -> b.transitTimeMin; 2 -> b.carTimeMin; 3 -> b.bicycleTimeMin; else -> b.walkingTimeMin }

            if (mode != 0) {
                val aMissing = timeA <= 0
                val bMissing = timeB <= 0

                if (aMissing && bMissing) {
                    a.walkingTimeMin.compareTo(b.walkingTimeMin)
                } else if (aMissing) {
                    -1
                } else if (bMissing) {
                    1
                } else {
                    timeA.compareTo(timeB)
                }
            } else {
                timeA.compareTo(timeB)
            }
        })

        adapter.updateList(sortedList)
        adapter.setMode(mode)

        val brand700 = ContextCompat.getColor(requireContext(), R.color.brand_700)
        // 완벽하게 정렬된 리스트의 첫 번째 아이템이 1순위 (최단거리)
        val shortestItem = sortedList.firstOrNull()

        if (shortestItem != null) {
            val rank = shortestItem.rankLabel
            val text = "직주거리는 $rank 가 \n가장 짧아요"
            val spannable = SpannableString(text)
            val idx = text.indexOf(rank)
            if (idx != -1) spannable.setSpan(ForegroundColorSpan(brand700), idx, idx + rank.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
            binding.afterLengthRankTv.text = spannable
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}