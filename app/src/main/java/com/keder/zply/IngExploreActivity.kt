package com.keder.zply

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Window
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.keder.zply.databinding.ActivityIngExploreBinding

class IngExploreActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIngExploreBinding
    private val gson = Gson()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIngExploreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 1. 뒤로가기 버튼
        binding.ingBackIv.setOnClickListener {
            finish()
        }

        // 2. 데이터 로드 및 UI 설정
        val sessionIndex = intent.getIntExtra("SESSION_INDEX", -1)
        if (sessionIndex != -1) {
            loadSessionData(sessionIndex)
        }
    }

    private fun loadSessionData(index: Int) {
        val sharedPref = getSharedPreferences("MainStorage", Context.MODE_PRIVATE)
        val jsonString = sharedPref.getString("KEY_ALL_SESSIONS", null)

        if (jsonString != null) {
            val type = object : TypeToken<List<ExplorationSession>>() {}.type
            val sessions: List<ExplorationSession> = gson.fromJson(jsonString, type)

            if (index in sessions.indices) {
                val session = sessions[index]

                // (1) 직장 주소 설정
                binding.ingMyAddressTv.text = session.companyAddress

                // (2) 리스트 날짜순 정렬 (탐색 예정 시간 기준)
                val sortedList = session.scheduleList.sortedBy { it.time }

                // (3) 리사이클러뷰 설정
                setupRecyclerView(sortedList)
            }
        }
    }

    private fun setupRecyclerView(items: List<ScheduleItem>) {
        val adapter = IngCardAdapter(items){itemPosition -> showWarningDialog(itemPosition)}
        binding.ingCardRv.adapter = adapter
        // 가로 스크롤 설정
        binding.ingCardRv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    }

    private fun showWarningDialog(itemPosition : Int){
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView((R.layout.ing_alert))

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val cancelBtn = dialog.findViewById<Button>(R.id.alert_cancel_btn)
        val okBtn = dialog.findViewById<Button>(R.id.alert_ok_btn)

        cancelBtn.setOnClickListener {
            dialog.dismiss()
        }

        okBtn.setOnClickListener {
            dialog.dismiss()
            showBottomSheet(itemPosition)
        }
        dialog.show()
    }
    private fun showBottomSheet(itemPosition: Int){
        val sessionIdx = intent.getIntExtra("SESSION_INDEX", -1)
        val bottomSheet = IngBottomSheetFragment.newInstance(sessionIdx, itemPosition)
        bottomSheet.onMeasurementSaved = {
            if(sessionIdx != -1){
                loadSessionData(sessionIdx)
            }
        }
        bottomSheet.show(supportFragmentManager, "IngBottomSheetFragment")
    }
}