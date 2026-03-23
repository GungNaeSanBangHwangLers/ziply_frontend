package com.keder.zply

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
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
        loadData()
    }

    private fun loadData() {
        val activity = requireActivity() as? AfterExploreActivity ?: return
        val cardId = activity.currentCardId

        if (cardId.isEmpty()) return

        lifecycleScope.launch {
            try {
                val service = RetrofitClient.getInstance(requireContext())

                // ★ 서버가 예쁘게 그룹화해준 새로운 API 1번만 딱 호출!
                val response = service.getCardDirectionGroups(cardId)

                if (response.isSuccessful && response.body() != null) {
                    val houseDirectionList = response.body()!!

                    // 측정된 창문(방)이 하나라도 있는 집만 필터링
                    val validHouses = houseDirectionList.filter { it.windows.isNotEmpty() }

                    if (validHouses.isEmpty()) {
                        showEmptyState()
                    } else {
                        setupCards(validHouses)
                    }
                } else {
                    Log.e("API_AFTER_DIR", "❌ 통신 실패: ${response.code()}")
                    showEmptyState()
                }

            } catch (e: Exception) {
                Log.e("API_AFTER_DIR", "❌ 예외 발생: ${e.message}", e)
                showEmptyState()
            }
        }
    }

    private fun setupCards(validHouses: List<HouseDirectionGroupResponse>) {
        binding.afterDirectionTitleTv.visibility = View.VISIBLE
        binding.afterDirectionDesTv.visibility = View.VISIBLE
        binding.directionCardsRv.visibility = View.VISIBLE
        binding.emptyStateTv.visibility = View.GONE

        // ★ 새 DTO 리스트를 어댑터로 바로 넘김
        val adapter = AfterDirectionCardAdapter(validHouses)
        binding.directionCardsRv.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.directionCardsRv.adapter = adapter

        val favoriteViewModel = androidx.lifecycle.ViewModelProvider(requireActivity())[FavoriteViewModel::class.java]
        favoriteViewModel.favoriteSet.observe(viewLifecycleOwner) { favorites ->
            adapter.updateFavorites(favorites)
        }
    }

    private fun showEmptyState() {
        binding.afterDirectionTitleTv.visibility = View.GONE
        binding.afterDirectionDesTv.visibility = View.GONE
        binding.directionCardsRv.visibility = View.GONE

        binding.emptyStateTv.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}