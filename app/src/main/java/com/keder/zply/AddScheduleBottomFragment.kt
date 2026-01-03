package com.keder.zply

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.content.DialogInterface
import android.content.SharedPreferences
import android.content.res.Resources
import android.graphics.Color
import android.graphics.Rect
import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.TextView
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
    private var isSaveSuccess = false // 저장 완료 여부 체크

    private var bottomSheetBehavior: BottomSheetBehavior<View>? = null

    // Date
    private val startDate = LocalDate.now().minusYears(1)
    private val endDate = LocalDate.now().plusYears(1)
    private lateinit var dateAdapter: DateAdapter
    private lateinit var dateSnapHelper: LinearSnapHelper
    private val dateItemWidthDp = 38

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
        dialog.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                if (binding.dateTimeFlipper.visibility == View.VISIBLE && binding.dateTimeFlipper.displayedChild == 1) {
                    showDatePickerScreen()
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
            sheet.layoutParams.height = (displayMetrics.heightPixels * 0.81).toInt()
            sheet.requestLayout()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.dateTimeFlipper.visibility = View.GONE
        binding.scheduleDateDisplayTv.text = "YYYY-MM-DD"
        setupDatePicker()
        setupTimePicker()
        setupInteractions()
        setupKeyboardBehavior()
        restoreDraftData()
    }

    private fun setupDatePicker() {
        dateAdapter = DateAdapter(startDate, endDate) { onDateItemClicked(it) }
        binding.dateRecyclerView.apply {
            adapter = dateAdapter
            layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
            if (itemDecorationCount > 0) removeItemDecorationAt(0)
            addItemDecoration(SymmetricalSpaceItemDecoration(22.dpToPx())) // 간격 데코레이션

            dateSnapHelper = LinearSnapHelper()
            dateSnapHelper.attachToRecyclerView(this)

            post {
                if (width > 0) {
                    val padding = (width - dateItemWidthDp.dpToPx()) / 2
                    setPadding(padding, 0, padding, 0)
                    clipToPadding = false
                }
            }
            addOnScrollListener(object : RecyclerView.OnScrollListener() {
                override fun onScrolled(rv: RecyclerView, dx: Int, dy: Int) { updateDisplay() }
            })
        }
        binding.btnPrevMonth.setOnClickListener { moveDateByMonth(-1) }
        binding.btnNextMonth.setOnClickListener { moveDateByMonth(1) }
    }

    private fun scrollToToday() {
        val rv = binding.dateRecyclerView

        rv.post(object : Runnable {
            override fun run() {
                // 1. 너비가 0이면 아직 준비 안 된 것 -> 10ms 뒤에 다시 시도
                if (rv.width <= 0 || rv.layoutManager == null) {
                    rv.postDelayed(this, 10)
                    return
                }

                // 2. 너비가 확인되면 계산 시작
                val totalWidth = rv.width.toFloat()

                // XML상 회색 박스가 38dp이므로, 아이템 크기도 38dp라고 가정
                val itemWidth = 38.dpToPx().toFloat()

                // [수정] decorationLeft를 뺄셈에서 제거합니다.
                // 공식: (화면중앙) - (아이템절반) = 아이템의 시작(Left) 좌표
                val offset = ((totalWidth / 2) - (itemWidth / 2)).toInt()

                val today = LocalDate.now()
                val todayPos = dateAdapter.getPositionOfDate(today)
                val targetPos = if (todayPos != -1) todayPos else 0

                // 3. 레이아웃 매니저를 통해 이동 (offset 적용)
                (rv.layoutManager as LinearLayoutManager)
                    .scrollToPositionWithOffset(targetPos, offset)

                // 4. 이동 후 텍스트 업데이트
                rv.post { updateDisplay() }
            }
        })
    }

    // [추가] 키보드 내리기 & 엔터 처리 로직
    @SuppressLint("ClickableViewAccessibility")
    private fun setupKeyboardBehavior() {
        // 1. 엔터(완료) 키 누르면 키보드 내리기
        binding.scheduleInputEt.setOnEditorActionListener { v, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)) {
                hideKeyboard()
                v.clearFocus()
                return@setOnEditorActionListener true
            }
            false
        }

        // 2. 바텀시트 배경 터치 시 키보드 내리기
        // root 뷰(ConstraintLayout)가 터치 이벤트를 받으려면 xml에 clickable=true가 있어야 함 (이미 되어 있음)
        binding.root.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                // 현재 포커스가 EditText에 있다면 키보드 숨김
                if (binding.scheduleInputEt.hasFocus()) {
                    hideKeyboard()
                    binding.scheduleInputEt.clearFocus()
                }
            }
            // false를 반환해야 다른 클릭 이벤트(버튼 등)를 방해하지 않음.
            // 다만 배경을 터치한 경우라면 보통 true로 소비하거나 false로 둬도 상관없으나,
            // 여기서는 터치 감지만 하고 이벤트 흐름은 유지합니다.
            false
        }
    }

    // [추가] 키보드 숨김 함수
    private fun hideKeyboard() {
        val view = view?.findFocus() ?: View(context)
        imm.hideSoftInputFromWindow(view.windowToken, 0)
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
                addOnScrollListener(object : RecyclerView.OnScrollListener() {
                    override fun onScrolled(r: RecyclerView, dx: Int, dy: Int) {
                        updateDisplay()
                        updateItemColors(rv, snapHelper)
                    }
                })
                val targetPos: Int
                if (isInfinite) {
                    val center = Int.MAX_VALUE / 2
                    val startOffset = center % list.size
                    val targetIndex = list.indexOf(initVal)
                    targetPos = center - startOffset + targetIndex
                } else {
                    targetPos = list.indexOf(initVal)
                }
                post {
                    lm.scrollToPositionWithOffset(targetPos, 0)
                    post { updateItemColors(rv, snapHelper) }
                }
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

    private fun updateDisplay() {
        if (binding.dateTimeFlipper.visibility != View.VISIBLE) return

        val dateLm = binding.dateRecyclerView.layoutManager as LinearLayoutManager
        val dateView = dateSnapHelper.findSnapView(dateLm)
        val datePos = if (dateView != null) dateLm.getPosition(dateView) else -1
        var dateText = "YYYY-MM-DD"
        if (datePos != RecyclerView.NO_POSITION) {
            val centerDate = dateAdapter.getDateAt(datePos)
            dateText = centerDate.toString()
            binding.tvYearMonth.text = centerDate.format(DateTimeFormatter.ofPattern("yyyy년 MM월", Locale.KOREA))
        }

        // [수정 1] 화면에 표시되는 텍스트를 24시간제(15시)로 변환
        var timeText = ""
        if (binding.dateTimeFlipper.displayedChild == 1) {
            val hourStr = getText(binding.hourRecyclerView, hourSnapHelper, hourList, true)
            val minuteStr = getText(binding.minuteRecyclerView, minuteSnapHelper, minuteList, true)
            val ampmStr = getText(binding.ampmRecyclerView, ampmSnapHelper, ampmList, false)

            if (hourStr.isNotEmpty()) {
                // 12시간제 -> 24시간제 변환 로직
                var hourInt = hourStr.toInt()
                if (ampmStr == "오후" && hourInt != 12) hourInt += 12
                if (ampmStr == "오전" && hourInt == 12) hourInt = 0

                // "오전/오후" 텍스트 제거하고 24시간 포맷으로 표시
                timeText = " ${String.format("%02d", hourInt)}시 ${minuteStr}분"
            }
        }
        binding.scheduleDateDisplayTv.text = "$dateText$timeText"
    }

    private fun getText(rv: RecyclerView, snap: LinearSnapHelper, list: List<String>, isInfinite: Boolean): String {
        val lm = rv.layoutManager ?: return ""
        val view = snap.findSnapView(lm) ?: return ""
        val pos = lm.getPosition(view)
        if (pos == RecyclerView.NO_POSITION) return ""
        val index = if (isInfinite) pos % list.size else pos
        return list.getOrElse(index) { "" }
    }

    private fun setupInteractions() {
        binding.scheduleDateDisplayTv.setOnClickListener {
            // [수정] 날짜 입력창을 열 때 오늘 날짜로 이동!
            if (binding.dateTimeFlipper.visibility != View.VISIBLE) {
                binding.dateTimeFlipper.visibility = View.VISIBLE

                // 뷰가 그려질 시간을 주기 위해 post 사용
                binding.dateRecyclerView.post {
                    scrollToToday()
                    // 스크롤 후 화면 업데이트
                    binding.dateRecyclerView.post { updateDisplay() }
                }
            }
        }
        binding.timePickerContainer.setOnClickListener { showDatePickerScreen() }
        binding.root.findViewById<View>(R.id.picker_inner_frame)?.setOnClickListener { }
        binding.addressNextBtnMb.setOnClickListener { validateAndSave() }
    }

    private fun showDatePickerScreen() {
        if (binding.dateTimeFlipper.displayedChild == 1) {
            binding.dateTimeFlipper.showPrevious()
            binding.scheduleDateTv.text = "탐색할 날짜와 시간을\n입력해주세요"
        }
    }

    private fun onDateItemClicked(clickedDate: LocalDate) {
        val lm = binding.dateRecyclerView.layoutManager as LinearLayoutManager
        val centerView = dateSnapHelper.findSnapView(lm)
        val currentPos = if (centerView != null) lm.getPosition(centerView) else -1
        val clickedPos = dateAdapter.getPositionOfDate(clickedDate)

        if (currentPos == clickedPos) {
            if (binding.dateTimeFlipper.displayedChild == 0) {
                binding.dateTimeFlipper.showNext()
                binding.scheduleDateTv.text = "탐색할 날짜와 시간을\n입력해주세요"
                binding.root.post { updateDisplay() }
            }
        } else {
            smoothScrollToPosition(clickedPos)
        }
    }

    private fun smoothScrollToPosition(position: Int) {
        val lm = binding.dateRecyclerView.layoutManager as LinearLayoutManager
        val scroller = object : androidx.recyclerview.widget.LinearSmoothScroller(context) {
            override fun getHorizontalSnapPreference(): Int = SNAP_TO_ANY
        }
        scroller.targetPosition = position
        lm.startSmoothScroll(scroller)
    }

    // [수정 2] 저장되는 텍스트도 24시간제(15시)로 변환
    private fun validateAndSave() {
        val address = binding.scheduleInputEt.text.toString()
        val dateLm = binding.dateRecyclerView.layoutManager as LinearLayoutManager
        val dateView = dateSnapHelper.findSnapView(dateLm)

        // [수정 포인트 1] 단순히 visibility만 체크하는 것이 아니라, 현재 보여지는 화면이 '시간 선택(index 1)'인지 확인해야 합니다.
        val isTimePickerVisible = binding.dateTimeFlipper.visibility == View.VISIBLE &&
                binding.dateTimeFlipper.displayedChild == 1

        // [수정 포인트 2] 조건문에 isTimePickerVisible을 추가하여 시간 선택 화면일 때만 저장하도록 합니다.
        if (address.isBlank() || dateView == null || !isTimePickerVisible) {
            showCustomToast("전체 내용을 입력해주세요")
            return
        }

        val datePos = dateLm.getPosition(dateView)
        val selectedDate = dateAdapter.getDateAt(datePos)
        val dateStr = selectedDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.KOREA))

        val ampm = getText(binding.ampmRecyclerView, ampmSnapHelper, ampmList, false)
        val hour = getText(binding.hourRecyclerView, hourSnapHelper, hourList, true)
        val minute = getText(binding.minuteRecyclerView, minuteSnapHelper, minuteList, true)

        // [수정 포인트 3] 빈 문자열("")이 올 경우 앱이 꺼지는 것을 방지하기 위해 toIntOrNull() 사용
        // 위에서 isTimePickerVisible 체크를 했으므로 안전하지만, 이중 방지 차원입니다.
        var hourInt = hour.toIntOrNull() ?: 0

        // 12시간제 -> 24시간제 변환
        if (ampm == "오후" && hourInt != 12) hourInt += 12
        if (ampm == "오전" && hourInt == 12) hourInt = 0

        // "오전/오후" 제거 및 24시간 포맷 적용
        val fullTimeStr = "$dateStr ${String.format("%02d", hourInt)}시 ${minute}분"

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
    override fun onDismiss(dialog: DialogInterface) {
        if (!isSaveSuccess) {
            draftPrefs.edit().putString("draft_address", binding.scheduleInputEt.text.toString()).apply()
        }
        super.onDismiss(dialog)
    }

    private fun restoreDraftData() {
        binding.scheduleInputEt.setText(draftPrefs.getString("draft_address", ""))
    }

    private fun moveDateByMonth(months: Long) {
        val lm = binding.dateRecyclerView.layoutManager as LinearLayoutManager
        val view = dateSnapHelper.findSnapView(lm) ?: return
        val pos = lm.getPosition(view)
        val target = dateAdapter.getDateAt(pos).plusMonths(months).withDayOfMonth(1)
        val finalDate = if (target.isBefore(startDate)) startDate else if (target.isAfter(endDate)) endDate else target
        smoothScrollToPosition(dateAdapter.getPositionOfDate(finalDate))
    }

    private fun Int.dpToPx(): Int = (this * Resources.getSystem().displayMetrics.density).toInt()

    inner class SymmetricalSpaceItemDecoration(private val spacePx: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            outRect.left = spacePx / 2
            outRect.right = spacePx / 2
        }
    }
}