package com.keder.zply

import android.content.Context
import android.content.res.ColorStateList
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.keder.zply.databinding.FragmentIngBottomsheetBinding
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

data class Measurement(val azimuth: Int, val light: Float)

class IngBottomSheetFragment : BottomSheetDialogFragment(), SensorEventListener {
    private var _binding: FragmentIngBottomsheetBinding? = null
    private val binding get() = _binding!!

    private var targetHouseId: Long = -1L

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)
    private var currentAzimuth: Int = 0

    private val roomDataMap = mutableMapOf<Int, MutableList<Measurement>>()
    private val roomNames = mutableListOf("거실 정면") // ★ 기본 이름을 거실 정면으로 수정 (옵션)
    private var currentRoomIndex = 0

    private lateinit var roomTabAdapter: RoomTabAdapter
    private lateinit var directionAdapter: DirectionAdapter

    var onComplete: ((Int) -> Unit)? = null

    companion object {
        fun newInstance(houseId: Long): IngBottomSheetFragment {
            val fragment = IngBottomSheetFragment()
            val args = Bundle().apply { putLong("HOUSE_ID", houseId) }
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentIngBottomsheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        targetHouseId = arguments?.getLong("HOUSE_ID", -1L) ?: -1L

        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        roomDataMap[0] = mutableListOf()

        setupRoomTabs()
        setupDirectionList()
        setupButtons()
    }

    private fun setupRoomTabs() {
        roomTabAdapter = RoomTabAdapter(roomNames) { position ->
            currentRoomIndex = position
            updateDirectionList()
            updateInputButtonState()
        }
        binding.ingRoomsRv.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        binding.ingRoomsRv.adapter = roomTabAdapter

        binding.ingAddRoom.setOnClickListener {
            val newRoomIndex = roomNames.size
            roomNames.add("방 $newRoomIndex") // ★ 탭 이름 추가 (방 1, 방 2...)
            roomDataMap[newRoomIndex] = mutableListOf()
            roomTabAdapter.notifyItemInserted(newRoomIndex)
            binding.ingRoomsRv.scrollToPosition(newRoomIndex)
        }
    }

    private fun setupDirectionList() {
        binding.tmpRv.layoutManager = LinearLayoutManager(requireContext())
        updateDirectionList()
    }

    private fun updateDirectionList() {
        val currentList = roomDataMap[currentRoomIndex] ?: mutableListOf()
        directionAdapter = DirectionAdapter(currentList) { position ->
            roomDataMap[currentRoomIndex]?.removeAt(position)
            updateDirectionList()
            updateInputButtonState()
        }
        binding.tmpRv.adapter = directionAdapter
    }

    private fun setupButtons() {
        val addMeasurement = {
            val list = roomDataMap[currentRoomIndex] ?: mutableListOf()
            list.add(Measurement(azimuth = currentAzimuth, light = 0f))
            roomDataMap[currentRoomIndex] = list

            updateDirectionList()
            updateInputButtonState()
        }

        binding.tmpDirectionInputMb.setOnClickListener { addMeasurement() }
        binding.tmpDirectionInputPlusMb.setOnClickListener { addMeasurement() }
        binding.tmpDirectionOkMb.setOnClickListener { completeMeasurement() }
    }

    private fun completeMeasurement() {
        if (roomDataMap.isEmpty() || roomDataMap.values.all { it.isEmpty() }) {
            showCustomToast("내용을 입력해주세요")
            return
        }

        val requestDataList = mutableListOf<DirectionRequest>()
        var roundCount = 1

        // ★ 각 방(탭)별로 돌면서 데이터를 뭉칩니다.
        roomDataMap.forEach { (roomIndex, measurements) ->
            if (measurements.isNotEmpty()) {
                val avgAzimuth = measurements.map { it.azimuth }.average() // Double로 처리
                val roomName = roomNames[roomIndex] // 현재 탭의 방 이름 가져오기

                // ★ [수정됨] 새 방향 DTO 규격 (windowLocation 추가, 방향은 Double)
                requestDataList.add(
                    DirectionRequest(
                        round = roundCount,
                        direction = avgAzimuth,
                        windowLocation = roomName // "거실 정면", "방 1" 등
                    )
                )
                roundCount++
            }
        }

        sendMeasurementToBackend(requestDataList)
    }

    private fun sendMeasurementToBackend(measureList: List<DirectionRequest>) {
        binding.tmpDirectionOkMb.isEnabled = false

        lifecycleScope.launch {
            try {
                val service = RetrofitClient.getInstance(requireContext())
                val requests = measureList.map { request ->
                    // ★ 함수명 주의: saveDirection (ApiService에 정의한 이름)
                    async { service.saveDirection(targetHouseId, request) }
                }
                val responses = requests.awaitAll()
                var isAllSuccess = true

                responses.forEachIndexed { index, response ->
                    if (!response.isSuccessful) {
                        isAllSuccess = false
                        Log.e("API_DIRECTION", "❌ $index 번째 방 전송 실패. 코드: ${response.code()}")
                    }
                }

                if (isAllSuccess) {
                    val prefs = requireContext().getSharedPreferences("ZplyMeasurementPrefs", Context.MODE_PRIVATE)
                    prefs.edit().putInt("room_${targetHouseId}", measureList.size).apply()

                    val activity = activity as? IngExploreActivity
                    activity?.showCustomToast2("측정을 완료했어요") ?: showCustomToast2("측정을 완료했어요")

                    onComplete?.invoke(measureList.size)
                    dismiss()
                } else {
                    showCustomToast("측정 값을 입력하지 않은 방은 제외 됐어요.")
                    binding.tmpDirectionOkMb.isEnabled = true
                }
            } catch (e: Exception) {
                Log.e("API_DIRECTION", "❌ 방향 전송 에러", e)
                showCustomToast("등록에 실패했어요. 다시 시도해주세요")
                binding.tmpDirectionOkMb.isEnabled = true
            }
        }
    }
    private fun updateInputButtonState() {
        val hasData = roomDataMap[currentRoomIndex]?.isNotEmpty() == true
        binding.tmpDirectionInputMb.visibility = if (hasData) View.GONE else View.VISIBLE
        binding.tmpDirectionInputPlusMb.visibility = if (hasData) View.VISIBLE else View.GONE

        val anyDataExists = roomDataMap.values.any { it.isNotEmpty() }
        if (anyDataExists) {
            binding.tmpDirectionOkMb.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.brand_700))
            binding.tmpDirectionOkMb.isEnabled = true
        } else {
            binding.tmpDirectionOkMb.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(requireContext(), R.color.gray_700))
        }
    }

    override fun onResume() {
        super.onResume()
        accelerometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
        magnetometer?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> lowPass(event.values, accelerometerReading)
            Sensor.TYPE_MAGNETIC_FIELD -> lowPass(event.values, magnetometerReading)
        }
        SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        var azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toInt()
        if (azimuth < 0) azimuth += 360
        currentAzimuth = azimuth
        binding.tmpDirectionTv.text = "${currentAzimuth}°"
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun lowPass(input: FloatArray, output: FloatArray) {
        for (i in input.indices) output[i] += 0.05f * (input[i] - output[i])
    }
}