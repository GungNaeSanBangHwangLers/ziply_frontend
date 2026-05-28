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
            if (scheduleList.size >= 7) {
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
        binding.loadingLayout.visibility = View.VISIBLE

        lifecycleScope.launch {
            try {
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

                val response = RetrofitClient.getInstance(requireContext()).createReviewCard(request)

                if (response.isSuccessful) {
                    draftPref.edit().clear().apply()
                    scheduleList.clear()

                    val intent = Intent(requireContext(), MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK
                    startActivity(intent)

                } else {
                    binding.loadingLayout.visibility = View.GONE
                    binding.addressNextBtnMb.isEnabled = true
                    if (response.code() == 400) {
                        showCustomToast("올바른 주소를 입력해주세요")
                    } else {
                        showCustomToast("등록에 실패했어요. 다시 시도해주세요")
                    }
                }
            } catch (e: Exception) {
                binding.loadingLayout.visibility = View.GONE
                binding.addressNextBtnMb.isEnabled = true
                showCustomToast("등록에 실패했어요. 다시 시도해주세요")
            }
        }
    }

    private fun convertToServerFormat(displayTime: String): String {
        return try {
            val inputFormat = SimpleDateFormat("yyyy. M. d. HH:mm", Locale.KOREA)
            val date = inputFormat.parse(displayTime)
            val safeDate = date ?: Date()
            val outputFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.KOREA)
            outputFormat.format(safeDate)
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