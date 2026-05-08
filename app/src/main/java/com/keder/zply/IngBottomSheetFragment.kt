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
        // 1. 데이터가 하나라도 들어있는 방만 필터링합니다.
        val validRooms = roomDataMap.filter { it.value.isNotEmpty() }

        if (validRooms.isEmpty()) {
            showCustomToast("내용을 입력해주세요")
            return
        }

        // 2. 만약 만들어놓은 탭 개수보다, 실제로 데이터가 들어간 탭(방) 개수가 적다면 경고 메시지 출력
        if (validRooms.size < roomDataMap.size) {
            showCustomToast("측정값을 입력하지 않은 방은 저장에서 제외됐어요.")
        }

        val requestDataList = mutableListOf<DirectionRequest>()
        var roundCount = 1

        // 3. 필터링된 "유효한 방" 데이터만 뭉쳐서 Request 리스트를 만듭니다.
        validRooms.forEach { (roomIndex, measurements) ->
            val avgAzimuth = measurements.map { it.azimuth }.average()
            val roomName = roomNames[roomIndex]

            requestDataList.add(
                DirectionRequest(
                    round = roundCount,
                    direction = avgAzimuth,
                    windowLocation = roomName
                )
            )
            roundCount++
        }

        // 4. 서버 전송 시작!
        sendMeasurementToBackend(requestDataList)
    }

    private fun sendMeasurementToBackend(measureList: List<DirectionRequest>) {
        binding.tmpDirectionOkMb.isEnabled = false

        lifecycleScope.launch {
            try {
                val service = RetrofitClient.getInstance(requireContext())

                // ★ 스웨거 명세대로 각 방의 데이터를 1개씩(단일 객체) 개별 전송합니다!
                val requests = measureList.map { request ->
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
                    showCustomToast("일부 데이터 등록에 실패했어요.")
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
        _binding?.tmpDirectionTv?.text = "${currentAzimuth}°"
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun lowPass(input: FloatArray, output: FloatArray) {
        for (i in input.indices) output[i] += 0.05f * (input[i] - output[i])
    }
}