package com.keder.zply

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.keder.zply.databinding.FragmentExploreScheduleBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ExploreScheduleFragment : Fragment() {

    private var _binding: FragmentExploreScheduleBinding? = null
    private val binding get() = _binding!!

    private val scheduleList = mutableListOf<ScheduleItem>()
    private lateinit var adapter: ScheduleAdapter
    private val gson = Gson()

    private val DRAFT_PREF = "schedule_draft_pref"

    private val KEY_DRAFT_LIST = "draft_schedule_list"
    private val KEY_COMPANY_ADDRESS = "draft_company_address"

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentExploreScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        initRecyclerView()
        loadDraftScheduleData()

        binding.scheduleAdditionBtn.setOnClickListener {
            if (scheduleList.size > 7) {
                showCustomToast("최대 7개까지 등록할 수 있어요")
                return@setOnClickListener
            }
            val bottomSheet = AddScheduleBottomSheet()
            bottomSheet.onSaveCompleted = { loadDraftScheduleData() }
            bottomSheet.show(parentFragmentManager, "AddScheduleBottomSheet")
        }

        binding.addressNextBtnMb.setOnClickListener {
            if (scheduleList.size < 2) {
                showCustomToast("2개 이상의 일정을 등록해주세요")
            } else {
                sendDataToBackend()
            }
        }

        binding.backBtnIv.setOnClickListener { parentFragmentManager.popBackStack() }
    }

    private fun sendDataToBackend() {
        val context = requireContext()
        val draftPref = context.getSharedPreferences(DRAFT_PREF, Context.MODE_PRIVATE)
        val companyAddress = draftPref.getString(KEY_COMPANY_ADDRESS, "") ?: ""

        if (companyAddress.isBlank()) {
            showCustomToast("직장 주소 정보가 없습니다.")
            return
        }

        binding.addressNextBtnMb.isEnabled = false

        lifecycleScope.launch {
            try {
                // ★ [로그 추가] 전송 전 리스트 개수 확인
                Log.d("API_DEBUG", "전송할 일정 개수: ${scheduleList.size}개")
                scheduleList.forEach { Log.d("API_DEBUG", " - 주소: ${it.address}, 시간: ${it.time}") }

                // RequestHouse 생성 (List 변환)
                val requestHouses = scheduleList.map { item ->
                    RequestHouse(
                        address = item.address,
                        visitDateTime = convertToServerFormat(item.time)
                    )
                }

                val request = ReviewCardRequest(
                    basePointAddress = companyAddress,
                    houses = requestHouses
                )

                // ★ [로그 추가] 최종 요청 객체 확인
                Log.d("API_DEBUG", "최종 요청 객체 houses 개수: ${request.houses.size}")

                val response = RetrofitClient.getInstance(requireContext()).createReviewCard(request)

                if (response.isSuccessful && response.body() != null) {
                    val cardId = response.body()!!
                    Log.d("API_DEBUG", "카드 생성 성공 ID: $cardId")

                    draftPref.edit().clear().apply()
                    scheduleList.clear()

                    val intent = Intent(requireContext(), BeforeExploreActivity::class.java)
                    intent.putExtra("CARD_ID", cardId)
                    startActivity(intent)
                    requireActivity().finish()
                } else {
                    Log.e("API_DEBUG", "등록 실패: ${response.code()} / ${response.errorBody()?.string()}")
                    showCustomToast("등록에 실패했어요. 다시 시도해주세요")
                    binding.addressNextBtnMb.isEnabled = true
                }
            } catch (e: Exception) {
                Log.e("API_DEBUG", "네트워크 오류", e)
                showCustomToast("등록에 실패했어요. 다시 시도해주세요")
                binding.addressNextBtnMb.isEnabled = true
            }
        }
    }

    private fun convertToServerFormat(displayTime: String): String {
        return try {
            // 입력: "yyyy. M. d. HH:mm" (공백, 점 주의)
            val inputFormat = SimpleDateFormat("yyyy. M. d. HH:mm", Locale.KOREA)

            // ★ 수정: "yyyy-MM-dd HH:mm" (T 제거, 스크린샷에는 T가 없었음)
            // 만약 서버가 T를 원하면 "yyyy-MM-dd'T'HH:mm:ss" 로 변경해야 함
            // 하지만 사용자님 피드백에 따라 "yyyy-MM-dd HH:mm"으로 유지합니다.
            val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)

            val date = inputFormat.parse(displayTime) ?: Date()
            outputFormat.format(date)
        } catch (e: Exception) {
            val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
            outputFormat.format(Date())
        }
    }

    private fun loadDraftScheduleData() {
        val sharedPref = requireContext().getSharedPreferences(DRAFT_PREF, Context.MODE_PRIVATE)
        val jsonString = sharedPref.getString(KEY_DRAFT_LIST, null)
        scheduleList.clear()
        if (jsonString != null) {
            val type = object : TypeToken<MutableList<ScheduleItem>>() {}.type
            scheduleList.addAll(gson.fromJson(jsonString, type))
        }
        adapter.notifyDataSetChanged()
        updateUIState()
    }

    private fun initRecyclerView() {
        adapter = ScheduleAdapter(scheduleList) { position -> deleteItem(position) }
        binding.scheduleRecyclerV.layoutManager = LinearLayoutManager(requireContext())
        binding.scheduleRecyclerV.adapter = adapter
    }

    private fun deleteItem(position: Int) {
        if (position in scheduleList.indices) {
            scheduleList.removeAt(position)
            val sharedPref = requireContext().getSharedPreferences(DRAFT_PREF, Context.MODE_PRIVATE)
            sharedPref.edit().putString(KEY_DRAFT_LIST, gson.toJson(scheduleList)).apply()
            adapter.notifyItemRemoved(position)
            updateUIState()
        }
    }

    private fun updateUIState() {
        binding.scheduleRecyclerV.visibility = if (scheduleList.isEmpty()) View.GONE else View.VISIBLE
    }


    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}