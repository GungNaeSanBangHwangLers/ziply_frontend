package com.keder.zply

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.keder.zply.databinding.FragmentIngBottomsheetBinding
import kotlinx.coroutines.launch

data class Measurement(val azimuth: Int, val light: Float)

class IngBottomSheetFragment : BottomSheetDialogFragment(), SensorEventListener {
    private var _binding: FragmentIngBottomsheetBinding? = null
    private val binding get() = _binding!!

    private var targetHouseId: Long = -1L
    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var magnetometer: Sensor? = null
    private var lightSensor: Sensor? = null

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var currentAzimuth: Int = 0
    private var currentLight: Float = 0f
    private var lastUiAzimuth: Int = -1
    private val ALPHA = 0.05f

    private val measuredList = mutableListOf<Measurement>()
    private lateinit var adapter: DirectionAdapter

    var onMeasurementSaved: ((Float) -> Unit)? = null

    companion object {
        fun newInstance(houseId: Long): IngBottomSheetFragment {
            val fragment = IngBottomSheetFragment()
            val args = Bundle()
            args.putLong("HOUSE_ID", houseId)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        targetHouseId = arguments?.getLong("HOUSE_ID", -1L) ?: -1L
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentIngBottomsheetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        sensorManager = requireContext().getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        setupRecyclerView()
        setupButtons()
        updateOkButtonState()
    }

    private fun completeMeasurement() {
        if (measuredList.isEmpty()) {
            showCustomToast("측정된 값이 없습니다.")
            return
        }

        // ★ [POST 수정 완료] lightLevel을 List로 포장
        val dtoList = measuredList.mapIndexed { index, item ->
            MeasurementDto(
                round = index + 1,
                direction = item.azimuth.toDouble(),
                lightLevel = listOf(item.light.toDouble())
            )
        }

        sendMeasurementToBackend(dtoList)
    }

    private fun sendMeasurementToBackend(measurements: List<MeasurementDto>) {
        binding.tmpDirectionOkMb.isEnabled = false

        lifecycleScope.launch {
            try {
                val response = RetrofitClient.getInstance(requireContext()).saveMeasurement(targetHouseId, measurements)

                if (response.isSuccessful) {
                    showCustomToast2("측정이 완료되었습니다.")
                    val lastRawLight = measuredList.lastOrNull()?.light ?: 0f
                    onMeasurementSaved?.invoke(lastRawLight)
                    dismiss()
                } else {
                    showCustomToast("등록에 실패했어요. 다시 시도해주세요")
                    binding.tmpDirectionOkMb.isEnabled = true
                }
            } catch (e: Exception) {
                showCustomToast("등록에 실패했어요. 다시 시도해주세요")
                binding.tmpDirectionOkMb.isEnabled = true
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = DirectionAdapter(measuredList) { position -> deleteItem(position) }
        binding.tmpRv.adapter = adapter
        binding.tmpRv.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupButtons() {
        val addAction = {
            measuredList.add(Measurement(currentAzimuth, currentLight))
            adapter.notifyItemInserted(measuredList.size - 1)
            updateOkButtonState()
            updateInputButtonState()
        }
        binding.tmpDirectionInputMb.setOnClickListener { addAction() }
        binding.tmpDirectionInputPlusMb.setOnClickListener { addAction() }
        binding.tmpDirectionOkMb.setOnClickListener { completeMeasurement() }
    }

    private fun deleteItem(position: Int) {
        if (position in measuredList.indices) {
            measuredList.removeAt(position)
            adapter.notifyItemRemoved(position)
            updateOkButtonState()
            updateInputButtonState()
        }
    }

    private fun updateInputButtonState() {
        val hasData = measuredList.isNotEmpty()
        binding.tmpDirectionInputMb.visibility = if (hasData) View.GONE else View.VISIBLE
        binding.tmpDirectionInputPlusMb.visibility = if (hasData) View.VISIBLE else View.GONE
    }

    private fun updateOkButtonState() {
        if (measuredList.isNotEmpty()) {
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
        lightSensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI) }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                lowPass(event.values, accelerometerReading)
                updateAzimuth()
            }
            Sensor.TYPE_MAGNETIC_FIELD -> {
                lowPass(event.values, magnetometerReading)
                updateAzimuth()
            }
            Sensor.TYPE_LIGHT -> {
                currentLight = event.values[0]
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    private fun lowPass(input: FloatArray, output: FloatArray) {
        for (i in input.indices) output[i] += ALPHA * (input[i] - output[i])
    }

    private fun updateAzimuth() {
        SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        var azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toInt()
        if (azimuth < 0) azimuth += 360
        currentAzimuth = azimuth

        if (Math.abs(currentAzimuth - lastUiAzimuth) > 1) {
            binding.tmpDirectionTv.text = "${currentAzimuth}°"
            lastUiAzimuth = currentAzimuth
        }
    }

    override fun onStart() {
        super.onStart()
        val bottomSheet = (dialog as? BottomSheetDialog)?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val height = (Resources.getSystem().displayMetrics.heightPixels * 0.82).toInt()
            it.layoutParams.height = height
            it.requestLayout()
            BottomSheetBehavior.from(it).state = BottomSheetBehavior.STATE_EXPANDED
            BottomSheetBehavior.from(it).skipCollapsed = true
            BottomSheetBehavior.from(it).isDraggable = false
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}