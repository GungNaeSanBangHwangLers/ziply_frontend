package com.keder.zply

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.keder.zply.databinding.FragmentExploreScheduleBinding

class ExploreScheduleFragment : Fragment() {

    private var _binding: FragmentExploreScheduleBinding? = null
    private val binding get() = _binding!!

    private val scheduleList = mutableListOf<ScheduleItem>()
    private lateinit var adapter: ScheduleAdapter
    private val gson = Gson()

    // [핵심] 앱이 실행된 동안 메모리에 살아있는 변수 (static)
    // 앱을 끄면(프로세스 종료) 이 변수도 사라지므로, 다시 켜면 false로 시작합니다.
    companion object {
        private var isAppSessionStarted = false
    }

    private val DRAFT_PREF = "schedule_draft_pref"
    private val KEY_DRAFT_LIST = "draft_schedule_list"
    private val KEY_COMPANY_ADDRESS = "draft_company_address"

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentExploreScheduleBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (!isAppSessionStarted) {
            requireContext().getSharedPreferences(DRAFT_PREF, Context.MODE_PRIVATE)
                .edit().remove(KEY_DRAFT_LIST).apply()
            isAppSessionStarted = true
        }
        initRecyclerView()
        loadDraftScheduleData() // 데이터 불러오기

        // 1. [+주거지 주소 추가] 버튼
        binding.scheduleAdditionBtn.setOnClickListener {
            if (scheduleList.size >= 7) {
                showCustomToast("최대 7개까지 등록할 수 있어요")
                return@setOnClickListener
            }

            val bottomSheet = AddScheduleBottomSheet()
            bottomSheet.onSaveCompleted = {
                loadDraftScheduleData() // 저장 후 리스트 갱신
            }
            bottomSheet.show(parentFragmentManager, "AddScheduleBottomSheet")
        }

        // 2. [다음으로] 버튼
        binding.addressNextBtnMb.setOnClickListener {
            if (scheduleList.isEmpty()) { // 1개 이상이면 됨 (조건 완화 가능)
                showCustomToast("1개 이상의 일정을 등록해주세요")
            } else {
                // ★★★ [핵심] 임시 데이터를 영구 세션으로 저장
                finalizeAndSaveSession()

                // 다음 화면으로 이동 (BeforeExploreActivity)
                val intent = Intent(requireContext(), BeforeExploreActivity::class.java)
                startActivity(intent)

                parentFragmentManager.beginTransaction().remove(this).commitAllowingStateLoss()
            }
        }

        binding.backBtnIv.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
    }

    private fun loadDraftScheduleData() {
        val sharedPref = requireContext().getSharedPreferences(DRAFT_PREF, Context.MODE_PRIVATE)
        val jsonString = sharedPref.getString(KEY_DRAFT_LIST, null)

        scheduleList.clear()
        if (jsonString != null) {
            val type = object : TypeToken<MutableList<ScheduleItem>>() {}.type
            val savedList: MutableList<ScheduleItem> = gson.fromJson(jsonString, type)
            scheduleList.addAll(savedList)
        }
        adapter.notifyDataSetChanged()
        updateUIState()
    }

    private fun initRecyclerView() {
        adapter = ScheduleAdapter(scheduleList) { position ->
            deleteItem(position)
        }
        binding.scheduleRecyclerV.layoutManager = LinearLayoutManager(requireContext())
        binding.scheduleRecyclerV.adapter = adapter
    }

    private fun saveListToDraft() {
        val sharedPref = requireContext().getSharedPreferences(DRAFT_PREF, Context.MODE_PRIVATE)
        val jsonString = gson.toJson(scheduleList)
        sharedPref.edit().putString(KEY_DRAFT_LIST, jsonString).apply()
    }

    private fun deleteItem(position: Int) {
        scheduleList.removeAt(position)
        saveListToDraft() // [수정]
        adapter.notifyItemRemoved(position)
        adapter.notifyItemRangeChanged(position, scheduleList.size)
        updateUIState()
    }

    private fun finalizeAndSaveSession() {
        val context = requireContext()
        val draftPref = context.getSharedPreferences(DRAFT_PREF, Context.MODE_PRIVATE)

        // 1. 임시 저장된 직장 주소 가져오기
        val companyAddress = draftPref.getString(KEY_COMPANY_ADDRESS, "") ?: ""

        // 2. 현재 리스트(scheduleList) 사용 (이미 로드되어 있음)
        if (companyAddress.isBlank() || scheduleList.isEmpty()) return

        // 3. 새로운 세션 객체 생성 (scheduleList 복사본 사용 추천)
        val newSession = ExplorationSession(companyAddress, ArrayList(scheduleList))

        // 4. 메인 저장소(MainStorage) 불러오기
        val mainPref = context.getSharedPreferences("MainStorage", Context.MODE_PRIVATE)
        val mainJson = mainPref.getString("KEY_ALL_SESSIONS", null)
        val sessionType = object : TypeToken<MutableList<ExplorationSession>>() {}.type

        val allSessions: MutableList<ExplorationSession> = if (mainJson != null) {
            gson.fromJson(mainJson, sessionType)
        } else {
            mutableListOf()
        }

        // 5. 리스트에 추가하고 저장
        allSessions.add(newSession)
        val newMainJson = gson.toJson(allSessions)
        mainPref.edit().putString("KEY_ALL_SESSIONS", newMainJson).apply()

        draftPref.edit().clear().apply()
        scheduleList.clear()
    }


    private fun updateUIState() {
        if (scheduleList.isEmpty()) {
            binding.scheduleRecyclerV.visibility = View.GONE
        } else {
            binding.scheduleRecyclerV.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}