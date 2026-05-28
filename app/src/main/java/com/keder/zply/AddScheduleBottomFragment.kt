package com.keder.zply

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Color
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.keder.zply.databinding.FragmentAddScheduleBottomBinding
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class AddScheduleBottomSheet : BottomSheetDialogFragment() {

    private var _binding: FragmentAddScheduleBottomBinding? = null
    private val binding get() = _binding!!

    // 임시 저장용
    private lateinit var draftPrefs: SharedPreferences
    private val DRAFT_PREF_NAME = "schedule_draft_pref"
    private val KEY_DRAFT_LIST = "draft_schedule_list"
    private var isSaveSuccess = false

    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null

    private var currentStep = 0

    private var currentCalendarDate = LocalDate.now()
    private var selectedDate: LocalDate? = null

    private lateinit var hourAdapter: TimeAdapter
    private lateinit var minuteAdapter: TimeAdapter
    private lateinit var ampmAdapter: TimeAdapter
    private val hourSnapHelper = LinearSnapHelper()
    private val minuteSnapHelper = LinearSnapHelper()
    private val ampmSnapHelper = LinearSnapHelper()

    private val hourList = (1..12).map { String.format("%02d", it) }
    private val minuteList = (0..55 step 5).map { String.format("%02d", it) }
    private val ampmList = listOf("오전", "오후")

    private val imm by lazy { requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager }

    var onSaveCompleted : (()->Unit)? = null

    private var editHouseId: Long = -1L
    private var isEditMode: Boolean = false

    companion object {
        fun newInstance(houseId: Long, address: String, time: String): AddScheduleBottomSheet {
            val fragment = AddScheduleBottomSheet()
            val args = Bundle().apply {
                putLong("HOUSE_ID", houseId)
                putString("ADDRESS", address)
                putString("TIME", time)
            }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddScheduleBottomBinding.inflate(inflater, container, false)
        draftPrefs = requireContext().getSharedPreferences(DRAFT_PREF_NAME, Context.MODE_PRIVATE)
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (currentStep > 0) {
                    moveStepTo(currentStep - 1)
                    return@setOnKeyListener true
                }
            }
            return@setOnKeyListener false
        }
        return dialog
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let { sheet ->
            bottomSheetBehavior = BottomSheetBehavior.from(sheet)
            bottomSheetBehavior?.state = BottomSheetBehavior.STATE_EXPANDED
            bottomSheetBehavior?.skipCollapsed = true
            val displayMetrics = Resources.getSystem().displayMetrics
            sheet.layoutParams.height = (displayMetrics.heightPixels * 0.85).toInt()
            sheet.requestLayout()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        editHouseId = arguments?.getLong("HOUSE_ID", -1L) ?: -1L
        isEditMode = editHouseId != -1L

        currentStep = 0
        binding.mainStepFlipper.displayedChild = 0

        setupCalendar()
        setupTimePicker()
        setupInteractions()
        setupKeyboardBehavior()

        if (isEditMode) {
            setupEditMode()
            binding.addressNextBtnMb.visibility = View.GONE
            binding.bottomBtnContainer.visibility = View.VISIBLE
            binding.deleteBtnMb.visibility = View.VISIBLE
        } else {
            restoreDraftData()
            binding.addressNextBtnMb.visibility = View.VISIBLE
            binding.bottomBtnContainer.visibility = View.GONE
        }

        updateUIForStep(0)
    }

    private fun setupEditMode() {
        val passedAddress = arguments?.getString("ADDRESS") ?: ""
        val passedTime = arguments?.getString("TIME") ?: ""

        binding.scheduleInputEt.setText(passedAddress)

        try {
            val parser = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")
            val dateTime = java.time.LocalDateTime.parse(passedTime, parser)
            selectedDate = dateTime.toLocalDate()
            currentCalendarDate = selectedDate!!

            val isPm = dateTime.hour >= 12
            val hr12 = if (dateTime.hour % 12 == 0) 12 else dateTime.hour % 12

            val ampmStr = if (isPm) "오후" else "오전"
            val hourStr = String.format("%02d", hr12)
            val minStr = String.format("%02d", (dateTime.minute / 5) * 5)

            scrollToValue(binding.ampmRecyclerView, ampmList, ampmStr, false)
            scrollToValue(binding.hourRecyclerView, hourList, hourStr, true)
            scrollToValue(binding.minuteRecyclerView, minuteList, minStr, true)

            updateCalendarGrid()
        } catch (e: Exception) { }
    }

    private fun scrollToValue(rv: RecyclerView, list: List<String>, target: String, isInfinite: Boolean) {
        val lm = rv.layoutManager as? LinearLayoutManager ?: return
        rv.post {
            val idx = list.indexOf(target)
            if (idx == -1) return@post
            val targetPos = if (isInfinite) (Int.MAX_VALUE / 2) - ((Int.MAX_VALUE / 2) % list.size) + idx else idx
            lm.scrollToPositionWithOffset(targetPos, 0)
        }
    }

    private fun updateUIForStep(step: Int) {
        currentStep = step
        binding.mainStepFlipper.displayedChild = step

        if (isEditMode) {
            binding.addressNextBtnMb.visibility = View.GONE
            binding.bottomBtnContainer.visibility = View.VISIBLE
        } else {
            binding.addressNextBtnMb.visibility = View.VISIBLE
            binding.bottomBtnContainer.visibility = View.GONE
        }

        when (step) {
            0 -> {
                binding.onFlowIv.setImageResource(R.drawable.ic_on_flow)
                binding.scheduleTitleTv.text = "탐색할 집의 \n주소를 입력해주세요"
                if (isEditMode) binding.addressNext2BtnMb.text = "수정하기"
                else binding.addressNextBtnMb.text = "다음으로"
            }
            1 -> {
                hideKeyboard()
                binding.onFlowIv.setImageResource(R.drawable.ic_on_flow2)
                binding.scheduleTitleTv.text = "탐색할 날짜와 시간을\n입력해주세요"
                if (isEditMode) binding.addressNext2BtnMb.text = "수정하기"
                else binding.addressNextBtnMb.text = "다음으로"
            }
            2 -> {
                binding.onFlowIv.setImageResource(R.drawable.ic_on_flow2)
                binding.scheduleTitleTv.text = "탐색할 날짜와 시간을\n입력해주세요"
                if (isEditMode) binding.addressNext2BtnMb.text = "수정하기"
                else binding.addressNextBtnMb.text = "입력하기"
                binding.hourRecyclerView.post { updateRealTimeDisplay() }
            }
        }
    }

    private fun moveStepTo(step: Int) {
        if (step > currentStep) {
            binding.mainStepFlipper.showNext()
        } else {
            binding.mainStepFlipper.showPrevious()
        }
        updateUIForStep(step)
    }

    private fun setupInteractions() {
        val onNextOrSaveClick = {
            when (currentStep) {
                0 -> if (binding.scheduleInputEt.text.isNotBlank()) moveStepTo(1)
                1 -> if (selectedDate != null) moveStepTo(2) else showCustomToast("날짜를 선택해 주세요")
                2 -> if (isEditMode) updateHouseOnServer() else validateAndSave()
            }
        }

        binding.addressNextBtnMb.setOnClickListener { onNextOrSaveClick() }
        binding.addressNext2BtnMb.setOnClickListener { onNextOrSaveClick() }

        binding.deleteBtnMb.setOnClickListener { deleteHouseOnServer() }

        binding.btnPrevMonth.setOnClickListener {
            currentCalendarDate = currentCalendarDate.minusMonths(1)
            updateCalendarGrid()
        }
        binding.btnNextMonth.setOnClickListener {
            currentCalendarDate = currentCalendarDate.plusMonths(1)
            updateCalendarGrid()
        }
    }

    private fun setupCalendar() {
        binding.calendarRecyclerView.layoutManager = GridLayoutManager(context, 7)
        updateCalendarGrid()
    }

    private fun updateCalendarGrid() {
        binding.tvYearMonth.text = currentCalendarDate.format(DateTimeFormatter.ofPattern("yyyy년 MM월"))

        val dayList = ArrayList<LocalDate?>() // 기존 어댑터 타입 호환을 위해 ? 유지
        val firstDayOfMonth = currentCalendarDate.withDayOfMonth(1)
        val lastDayOfMonth = currentCalendarDate.lengthOfMonth()
        val dayOfWeekValue = firstDayOfMonth.dayOfWeek.value
        val emptyCount = if (dayOfWeekValue == 7) 0 else dayOfWeekValue

        // ★ 1. 이전 달 날짜 채우기 (null 대신 실제 날짜 넣기)
        val prevMonth = currentCalendarDate.minusMonths(1)
        val prevMonthDays = prevMonth.lengthOfMonth()
        for (i in emptyCount - 1 downTo 0) {
            dayList.add(prevMonth.withDayOfMonth(prevMonthDays - i))
        }

        // ★ 2. 이번 달 날짜 채우기
        for (i in 1..lastDayOfMonth) {
            dayList.add(currentCalendarDate.withDayOfMonth(i))
        }

        // ★ 3. 다음 달 날짜 채우기 (달력 42칸 꽉 채우기)
        val nextMonth = currentCalendarDate.plusMonths(1)
        val remainingCount = 42 - dayList.size
        for (i in 1..remainingCount) {
            dayList.add(nextMonth.withDayOfMonth(i))
        }

        // 기존 어댑터 연결
        val adapter = DateAdapter(dayList, selectedDate) { clickedDate ->
            selectedDate = clickedDate
            // 클릭한 날짜가 다른 달이면 달력 페이지도 같이 넘어가게 처리
            if (clickedDate.monthValue != currentCalendarDate.monthValue) {
                currentCalendarDate = clickedDate.withDayOfMonth(1)
            }
            updateCalendarGrid()
        }
        binding.calendarRecyclerView.adapter = adapter
    }

    private fun setupTimePicker() {
        hourAdapter = TimeAdapter(hourList, isInfinite = true) { }
        minuteAdapter = TimeAdapter(minuteList, isInfinite = true) { }
        ampmAdapter = TimeAdapter(ampmList, isInfinite = false) { }

        fun setupVertical(rv: RecyclerView, adapter: RecyclerView.Adapter<*>, snapHelper: LinearSnapHelper, list: List<String>, initVal: String, isInfinite: Boolean) {
            rv.apply {
                this.adapter = adapter
                val lm = LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false)
                layoutManager = lm
                isNestedScrollingEnabled = false
                overScrollMode = View.OVER_SCROLL_NEVER
                snapHelper.attachToRecyclerView(null)
                snapHelper.attachToRecyclerView(this)

                post {
                    val targetPos: Int
                    if (isInfinite) {
                        val center = Int.MAX_VALUE / 2
                        val startOffset = center % list.size
                        val targetIndex = list.indexOf(initVal)
                        targetPos = center - startOffset + targetIndex
                    } else {
                        targetPos = list.indexOf(initVal)
                    }
                    lm.scrollToPositionWithOffset(targetPos, 0)
                    post {
                        updateItemColors(rv, snapHelper)
                        updateRealTimeDisplay()
                    }
                }

                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(r: RecyclerView, dx: Int, dy: Int) {
                        updateItemColors(rv, snapHelper)
                        updateRealTimeDisplay()
                    }
                })
            }
        }
        setupVertical(binding.hourRecyclerView, hourAdapter, hourSnapHelper, hourList, "10", true)
        setupVertical(binding.minuteRecyclerView, minuteAdapter, minuteSnapHelper, minuteList, "00", true)
        setupVertical(binding.ampmRecyclerView, ampmAdapter, ampmSnapHelper, ampmList, "오전", false)
    }

    private fun updateItemColors(rv: RecyclerView, snapHelper: LinearSnapHelper) {
        val lm = rv.layoutManager ?: return
        val centerView = snapHelper.findSnapView(lm)
        for (i in 0 until lm.childCount) {
            val child = lm.getChildAt(i) ?: continue
            val tv = child.findViewById<TextView>(R.id.time_tv)
            if (child == centerView) tv.setTextColor(Color.WHITE)
            else tv.setTextColor(Color.parseColor("#666666"))
        }
    }

    private fun getText(rv: RecyclerView, snap: LinearSnapHelper, list: List<String>, isInfinite: Boolean): String {
        val lm = rv.layoutManager ?: return ""
        val view = snap.findSnapView(lm) ?: return ""
        val pos = lm.getPosition(view)
        if (pos == RecyclerView.NO_POSITION) return ""
        val index = if (isInfinite) pos % list.size else pos
        return list.getOrElse(index) { "" }
    }

    private fun updateRealTimeDisplay() {
        if (selectedDate == null) return
        val dateStr = selectedDate!!.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일", Locale.KOREA))

        val ampm = getText(binding.ampmRecyclerView, ampmSnapHelper, ampmList, false)
        val hour = getText(binding.hourRecyclerView, hourSnapHelper, hourList, true)
        val minute = getText(binding.minuteRecyclerView, minuteSnapHelper, minuteList, true)

        if (ampm.isEmpty() || hour.isEmpty() || minute.isEmpty()) return

        var hourInt = hour.toIntOrNull() ?: 0
        if (ampm == "오후" && hourInt != 12) hourInt += 12
        if (ampm == "오전" && hourInt == 12) hourInt = 0

        val fullText = "$dateStr ${String.format("%02d", hourInt)}시 ${minute}분"
        binding.scheduleDateDisplayTv.text = fullText
    }

    private fun validateAndSave() {
        val address = binding.scheduleInputEt.text.toString()
        if (address.isBlank() || selectedDate == null) return

        val dateStr = selectedDate!!.format(DateTimeFormatter.ofPattern("yyyy. M. d.", Locale.KOREA))
        val ampm = getText(binding.ampmRecyclerView, ampmSnapHelper, ampmList, false)
        val hour = getText(binding.hourRecyclerView, hourSnapHelper, hourList, true)
        val minute = getText(binding.minuteRecyclerView, minuteSnapHelper, minuteList, true)

        var hourInt = hour.toIntOrNull() ?: 0
        if (ampm == "오후" && hourInt != 12) hourInt += 12
        if (ampm == "오전" && hourInt == 12) hourInt = 0

        val fullTimeStr = "$dateStr ${String.format("%02d", hourInt)}:${minute}"

        val listPrefs = requireContext().getSharedPreferences(DRAFT_PREF_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val jsonString = listPrefs.getString(KEY_DRAFT_LIST, null)
        val currentList: MutableList<ScheduleItem> = if (jsonString != null) {
            val type = object : TypeToken<MutableList<ScheduleItem>>() {}.type
            gson.fromJson(jsonString, type)
        } else {
            mutableListOf()
        }

        // ★ 2. 중복 시간 검증 및 토스트 메시지 처리
        if (currentList.any { it.time == fullTimeStr }) {
            showCustomToast("중복된 시간은 입력 불가합니다.")
            return
        }

        currentList.add(ScheduleItem(address, fullTimeStr))

        val newJsonString = gson.toJson(currentList)
        listPrefs.edit().putString(KEY_DRAFT_LIST, newJsonString).apply()

        isSaveSuccess = true
        draftPrefs.edit().remove("draft_address").apply()

        onSaveCompleted?.invoke()
        dismiss()
    }

    private fun updateHouseOnServer() {
        val address = binding.scheduleInputEt.text.toString()
        if (address.isBlank() || selectedDate == null) return

        binding.addressNext2BtnMb.isEnabled = false
        binding.deleteBtnMb.isEnabled = false

        val dateStr = selectedDate!!.format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.KOREA))
        val ampm = getText(binding.ampmRecyclerView, ampmSnapHelper, ampmList, false)
        val hour = getText(binding.hourRecyclerView, hourSnapHelper, hourList, true)
        val minute = getText(binding.minuteRecyclerView, minuteSnapHelper, minuteList, true)

        var hourInt = hour.toIntOrNull() ?: 0
        if (ampm == "오후" && hourInt != 12) hourInt += 12
        if (ampm == "오전" && hourInt == 12) hourInt = 0

        val serverTimeStr = "${dateStr}T${String.format("%02d", hourInt)}:${minute}:00"

        lifecycleScope.launch {
            try {
                val service = RetrofitClient.getInstance(requireContext())
                val request = UpdateHouseRequest(address, serverTimeStr)
                val response = service.updateHouse(editHouseId, request)

                if (response.isSuccessful) {
                    isSaveSuccess = true
                    showCustomToast2("일정이 수정되었습니다.")
                    onSaveCompleted?.invoke()
                    dismiss()
                } else {
                    showCustomToast("수정에 실패했어요. 다시 시도해주세요.")
                    binding.addressNext2BtnMb.isEnabled = true
                    binding.deleteBtnMb.isEnabled = true
                }
            } catch (e: Exception) {
                showCustomToast("네트워크 오류가 발생했습니다.")
                binding.addressNext2BtnMb.isEnabled = true
                binding.deleteBtnMb.isEnabled = true
            }
        }
    }

    private fun deleteHouseOnServer() {
        binding.addressNext2BtnMb.isEnabled = false
        binding.deleteBtnMb.isEnabled = false

        lifecycleScope.launch {
            try {
                val service = RetrofitClient.getInstance(requireContext())
                val response = service.deleteHouse(editHouseId)

                if (response.isSuccessful) {
                    isSaveSuccess = true
                    showCustomToast2("일정이 삭제되었습니다.")
                    onSaveCompleted?.invoke()
                    dismiss()
                } else {
                    showCustomToast("삭제에 실패했어요. 다시 시도해주세요.")
                    binding.addressNext2BtnMb.isEnabled = true
                    binding.deleteBtnMb.isEnabled = true
                }
            } catch (e: Exception) {
                showCustomToast("네트워크 오류가 발생했습니다.")
                binding.addressNext2BtnMb.isEnabled = true
                binding.deleteBtnMb.isEnabled = true
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupKeyboardBehavior() {
        binding.scheduleInputEt.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                if (binding.scheduleInputEt.text.isNotBlank()) {
                    moveStepTo(1)
                }
                hideKeyboard()
                v.clearFocus()
                return@setOnEditorActionListener true
            }
            false
        }
    }

    private fun hideKeyboard() {
        val view = view?.findFocus() ?: View(context)
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (!isSaveSuccess && !isEditMode) {
            draftPrefs.edit().putString("draft_address", binding.scheduleInputEt.text.toString()).apply()
        }
        super.onDismiss(dialog)
    }

    private fun restoreDraftData() {
        binding.scheduleInputEt.setText(draftPrefs.getString("draft_address", ""))
    }
}