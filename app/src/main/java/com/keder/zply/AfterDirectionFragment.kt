package com.keder.zply

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.PagerSnapHelper
import com.keder.zply.databinding.FragmentAfterDirectionBinding
import kotlinx.coroutines.launch

class AfterDirectionFragment : Fragment() {
    private var _binding: FragmentAfterDirectionBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAfterDirectionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        loadDirectionData()
    }

    private fun loadDirectionData() {
        val activity = requireActivity() as? AfterExploreActivity ?: return
        val cardId = activity.currentCardId

        Log.d("API_DEBUG", "[AfterDirection] 로드 시작. CardID: $cardId")

        lifecycleScope.launch {
            try {
                // API 호출
                val response = RetrofitClient.getInstance(requireContext()).getAnalysisDirection(cardId)
                Log.d("API_DEBUG", "[AfterDirection] 응답 코드: ${response.code()}")

                if (response.isSuccessful && response.body() != null) {
                    val analysisList = response.body()!!

                    // ★ [로그 추가] 리스트 내용을 상세하게 출력
                    Log.d("API_DEBUG", "[AfterDirection] 전체 데이터 개수: ${analysisList.size}")
                    analysisList.forEachIndexed { index, item ->
                        Log.d("API_DEBUG", "--------------------------------------")
                        Log.d("API_DEBUG", "[$index] 방향: ${item.directionType}")
                        Log.d("API_DEBUG", " - 특징: ${item.features}")
                        Log.d("API_DEBUG", " - 장점: ${item.pros}")
                        Log.d("API_DEBUG", " - 단점: ${item.cons}")
                        Log.d("API_DEBUG", " - 포함된 집 ID들: ${item.houseIds}")
                    }
                    Log.d("API_DEBUG", "--------------------------------------")

                    if (analysisList.isNotEmpty()) {
                        binding.afterDirectionRv.visibility = View.VISIBLE
                        binding.emptyStateTv.visibility = View.GONE

                        // API 응답 -> 어댑터 아이템 변환
                        val adapterItems = analysisList.map { item ->
                            val ranks = item.houseIds.map { id ->
                                activity.getRankLabel(id).firstOrNull() ?: '?'
                            }.sorted().toMutableList()

                            DirectionGroupItem(
                                direction = item.directionType,
                                ranks = ranks,
                                desc = item.features,
                                goodPoints = item.pros,
                                badPoints = item.cons
                            )
                        }

                        val adapter = AfterDirectionAdapter(adapterItems)
                        binding.afterDirectionRv.adapter = adapter
                        binding.afterDirectionRv.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)

                        binding.afterDirectionRv.onFlingListener = null
                        val snapHelper = PagerSnapHelper()
                        snapHelper.attachToRecyclerView(binding.afterDirectionRv)
                    } else {
                        Log.w("API_DEBUG", "[AfterDirection] 데이터 리스트가 비어있음")
                        showEmptyState()
                    }
                } else {
                    Log.e("API_DEBUG", "[AfterDirection] API 실패: ${response.errorBody()?.string()}")
                    showEmptyState()
                }
            } catch (e: Exception) {
                Log.e("API_DEBUG", "[AfterDirection] 에러 발생", e)
                showEmptyState()
            }
        }
    }

    private fun showEmptyState() {
        binding.afterDirectionRv.visibility = View.GONE
        binding.emptyStateTv.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}