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
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
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

    // ★ 단계 관리 (0:주소, 1:날짜, 2:시간)
    private var currentStep = 0

    // Date (Calendar) Logic
    private var currentCalendarDate = LocalDate.now() // 현재 보고 있는 달력의 월
    private var selectedDate: LocalDate? = null       // 사용자가 선택한 날짜

    // Time
    private lateinit var hourAdapter: TimeAdapter
    private lateinit var minuteAdapter: TimeAdapter
    private lateinit var ampmAdapter: TimeAdapter
    private val hourSnapHelper = LinearSnapHelper()
    private val minuteSnapHelper = LinearSnapHelper()
    private val ampmSnapHelper = LinearSnapHelper()

    // Data
    private val hourList = (1..12).map { String.format("%02d", it) }
    private val minuteList = (0..55 step 5).map { String.format("%02d", it) }
    private val ampmList = listOf("오전", "오후")

    private val imm by lazy { requireContext().getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager }

    var onSaveCompleted : (()->Unit)? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAddScheduleBottomBinding.inflate(inflater, container, false)
        draftPrefs = requireContext().getSharedPreferences(DRAFT_PREF_NAME, Context.MODE_PRIVATE)
        return binding.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_PAN)

        // 뒤로가기 키 처리 (단계별 뒤로가기)
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
            sheet.layoutParams.height = (displayMetrics.heightPixels * 0.85).toInt() // 높이 살짝 여유있게
            sheet.requestLayout()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 초기화
        currentStep = 0
        binding.mainStepFlipper.displayedChild = 0
        updateUIForStep(0)

        setupCalendar() // Grid Calendar 설정
        setupTimePicker() // 기존 TimePicker 설정
        setupInteractions()
        setupKeyboardBehavior()
        restoreDraftData()
    }

    // ★ 단계별 UI 업데이트 (텍스트, 이미지, 버튼 등)
    private fun updateUIForStep(step: Int) {
        currentStep = step
        binding.mainStepFlipper.displayedChild = step

        when (step) {
            0 -> { // 주소
                binding.onFlowIv.setImageResource(R.drawable.ic_on_flow)
                binding.scheduleTitleTv.text = "탐색할 집의 \n주소를 입력해주세요"
                binding.addressNextBtnMb.text = "다음으로"
            }
            1 -> { // 날짜
                hideKeyboard()
                binding.onFlowIv.setImageResource(R.drawable.ic_on_flow2)
                binding.scheduleTitleTv.text = "탐색할 날짜와 시간을\n입력해주세요"
                binding.addressNextBtnMb.text = "다음으로"
            }
            2 -> { // 시간
                binding.onFlowIv.setImageResource(R.drawable.ic_on_flow2)
                binding.scheduleTitleTv.text = "탐색할 날짜와 시간을\n입력해주세요"
                binding.addressNextBtnMb.text = "입력하기"

                // ★ [추가] 시간 화면 진입 시 텍스트 즉시 갱신
                binding.hourRecyclerView.post { updateRealTimeDisplay() }
            }
        }
    }

    // [수정] 타임 피커 설정 및 실시간 리스너 연결
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
                        // ★ [추가] 초기 로드 시 텍스트 갱신
                        updateRealTimeDisplay()
                    }
                }

                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(r: RecyclerView, dx: Int, dy: Int) {
                        updateItemColors(rv, snapHelper)
                        // ★ [추가] 스크롤 할 때마다 실시간 텍스트 갱신
                        updateRealTimeDisplay()
                    }
                })
            }
        }
        setupVertical(binding.hourRecyclerView, hourAdapter, hourSnapHelper, hourList, "10", true)
        setupVertical(binding.minuteRecyclerView, minuteAdapter, minuteSnapHelper, minuteList, "00", true)
        setupVertical(binding.ampmRecyclerView, ampmAdapter, ampmSnapHelper, ampmList, "오전", false)
    }

    // ★ [신규 함수] 현재 선택된 값을 읽어서 상단 텍스트뷰에 "YYYY년 MM월 DD일 HH시 MM분" 형식으로 표시
    private fun updateRealTimeDisplay() {
        if (selectedDate == null) return

        // 1. 날짜 포맷 (YYYY년 MM월 DD일)
        val dateStr = selectedDate!!.format(DateTimeFormatter.ofPattern("yyyy년 MM월 dd일", Locale.KOREA))

        // 2. 시간 피커 값 가져오기
        val ampm = getText(binding.ampmRecyclerView, ampmSnapHelper, ampmList, false)
        val hour = getText(binding.hourRecyclerView, hourSnapHelper, hourList, true)
        val minute = getText(binding.minuteRecyclerView, minuteSnapHelper, minuteList, true)

        // 값이 아직 로드되지 않았으면 리턴
        if (ampm.isEmpty() || hour.isEmpty() || minute.isEmpty()) return

        // 3. 24시간제 변환 ("HH시")
        var hourInt = hour.toIntOrNull() ?: 0
        if (ampm == "오후" && hourInt != 12) hourInt += 12
        if (ampm == "오전" && hourInt == 12) hourInt = 0

        // 4. 최종 텍스트 설정
        val fullText = "$dateStr ${String.format("%02d", hourInt)}시 ${minute}분"
        binding.scheduleDateDisplayTv.text = fullText
    }

    private fun moveStepTo(step: Int) {
        // 애니메이션 효과를 위해 단계에 따라 Flipper 제어
        if (step > currentStep) {
            binding.mainStepFlipper.showNext()
        } else {
            binding.mainStepFlipper.showPrevious()
        }
        updateUIForStep(step)
    }

    private fun setupInteractions() {
        // 버튼 클릭 하나로 단계별 분기 처리
        binding.addressNextBtnMb.setOnClickListener {
            when (currentStep) {
                0 -> { // 주소 -> 날짜
                    if (binding.scheduleInputEt.text.isBlank()) {
                        // Toast or Error message
                        return@setOnClickListener
                    }
                    moveStepTo(1)
                }
                1 -> { // 날짜 -> 시간
                    if (selectedDate == null) {
                        // 날짜 선택 안됨
                        return@setOnClickListener
                    }
                    moveStepTo(2)
                }
                2 -> { // 시간 -> 완료
                    validateAndSave()
                }
            }
        }

        // 달력 월 이동 버튼
        binding.btnPrevMonth.setOnClickListener {
            currentCalendarDate = currentCalendarDate.minusMonths(1)
            updateCalendarGrid()
        }
        binding.btnNextMonth.setOnClickListener {
            currentCalendarDate = currentCalendarDate.plusMonths(1)
            updateCalendarGrid()
        }
    }

    // ★ Grid Calendar 설정 및 로직
    private fun setupCalendar() {
        // XML에 정의된 calendarRecyclerView 사용
        binding.calendarRecyclerView.layoutManager = GridLayoutManager(context, 7) // 7열 (일~토)
        updateCalendarGrid()
    }

    private fun updateCalendarGrid() {
        binding.tvYearMonth.text = currentCalendarDate.format(DateTimeFormatter.ofPattern("yyyy년 MM월"))

        val dayList = ArrayList<LocalDate?>()
        val firstDayOfMonth = currentCalendarDate.withDayOfMonth(1)
        val lastDayOfMonth = currentCalendarDate.lengthOfMonth()

        // 1일의 요일 (Java Time: 월=1...일=7)
        // 우리가 원하는 Grid: 일(0), 월(1)... 토(6)
        // 1일이 일요일(7)이면 앞에 0칸 공백, 월요일(1)이면 앞에 1칸 공백
        val dayOfWeekValue = firstDayOfMonth.dayOfWeek.value
        val emptyCount = if (dayOfWeekValue == 7) 0 else dayOfWeekValue

        // 앞쪽 빈칸 추가
        for (i in 0 until emptyCount) {
            dayList.add(null)
        }
        // 날짜 추가
        for (i in 1..lastDayOfMonth) {
            dayList.add(currentCalendarDate.withDayOfMonth(i))
        }

        // Adapter 연결
        val adapter = DateAdapter(dayList, selectedDate) { clickedDate ->
            selectedDate = clickedDate
            // 선택된 날짜 갱신을 위해 UI 리로드 (배경색 변경)
            updateCalendarGrid()
        }
        binding.calendarRecyclerView.adapter = adapter
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

    // ★ 저장 로직 (최종 단계에서 호출)
    private fun validateAndSave() {
        val address = binding.scheduleInputEt.text.toString()

        // 날짜/시간 필수값 체크
        if (address.isBlank() || selectedDate == null) {
            // Error handling
            return
        }

        // 날짜 포맷
        val dateStr = selectedDate!!.format(DateTimeFormatter.ofPattern("yyyy. M. d.", Locale.KOREA))

        // 시간 가져오기
        val ampm = getText(binding.ampmRecyclerView, ampmSnapHelper, ampmList, false)
        val hour = getText(binding.hourRecyclerView, hourSnapHelper, hourList, true)
        val minute = getText(binding.minuteRecyclerView, minuteSnapHelper, minuteList, true)

        var hourInt = hour.toIntOrNull() ?: 0
        if (ampm == "오후" && hourInt != 12) hourInt += 12
        if (ampm == "오전" && hourInt == 12) hourInt = 0

        val fullTimeStr = "$dateStr ${String.format("%02d", hourInt)}:${minute}"

        // SharedPreferences 저장 (기존 로직 유지)
        val listPrefs = requireContext().getSharedPreferences(DRAFT_PREF_NAME, Context.MODE_PRIVATE)
        val gson = Gson()
        val jsonString = listPrefs.getString(KEY_DRAFT_LIST, null)
        val currentList: MutableList<ScheduleItem> = if (jsonString != null) {
            val type = object : TypeToken<MutableList<ScheduleItem>>() {}.type
            gson.fromJson(jsonString, type)
        } else {
            mutableListOf()
        }
        currentList.add(ScheduleItem(address, fullTimeStr))

        val newJsonString = gson.toJson(currentList)
        listPrefs.edit().putString(KEY_DRAFT_LIST, newJsonString).apply()

        isSaveSuccess = true
        draftPrefs.edit().remove("draft_address").apply()

        onSaveCompleted?.invoke()
        dismiss()
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupKeyboardBehavior() {
        binding.scheduleInputEt.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                // 주소 입력에서 엔터 누르면 다음 단계로 진행하는 UX 추가
                if (binding.scheduleInputEt.text.isNotBlank()) {
                    moveStepTo(1)
                }
                hideKeyboard()
                v.clearFocus()
                return@setOnEditorActionListener true
            }
            false
        }
        // ... (Touch listener logic)
    }

    private fun hideKeyboard() {
        val view = view?.findFocus() ?: View(context)
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun onDismiss(dialog: DialogInterface) {
        if (!isSaveSuccess) {
            draftPrefs.edit().putString("draft_address", binding.scheduleInputEt.text.toString()).apply()
        }
        super.onDismiss(dialog)
    }

    private fun restoreDraftData() {
        binding.scheduleInputEt.setText(draftPrefs.getString("draft_address", ""))
    }

    private fun Int.dpToPx(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()
}