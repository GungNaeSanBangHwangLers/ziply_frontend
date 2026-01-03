package com.keder.zply

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Resources
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.icu.util.Output
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.keder.zply.databinding.FragmentIngBottomsheetBinding

data class Measurement(val azimuth : Int, val light : Float)

class IngBottomSheetFragment : BottomSheetDialogFragment(), SensorEventListener{
    private var _binding : FragmentIngBottomsheetBinding? = null
    private val binding get() = _binding!!

    private var sessionIndex : Int = -1
    private var itemIndex : Int = -1

    //센서 관련
    private lateinit var sensorManager: SensorManager
    private var accelerometer : Sensor? = null
    private var magnetometer : Sensor? = null
    private var lightSensor : Sensor? = null

    private val accelerometerReading = FloatArray(3)
    private val magnetometerReading = FloatArray(3)
    private val rotationMatrix = FloatArray(9)
    private val orientationAngles = FloatArray(3)

    private var currentAzimuth : Int =0
    private var currentLight : Float = 0f

    private var ALPHA = 0.05f

    private val measuredList = mutableListOf<Measurement>()
    private lateinit var adapter : DirectionAdapter

    var onMeasurementSaved : (()->Unit)? = null

    companion object{
        fun newInstance(sessionIndex : Int, itemIndex : Int) : IngBottomSheetFragment{
            val fragment = IngBottomSheetFragment()
            val args = Bundle()
            args.putInt("SESSION_IDX", sessionIndex)
            args.putInt("ITEM_IDX", itemIndex)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        sessionIndex = arguments?.getInt("SESSION_IDX", -1) ?: -1
        itemIndex = arguments?.getInt("ITEM_IDX", -1) ?: -1
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
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

    override fun onResume() {
        super.onResume()
        accelerometer?.also{
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        magnetometer?.also{
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        lightSensor?.also{
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    override fun onPause() {
        super.onPause()
        sensorManager.unregisterListener(this)
    }

    private fun setupRecyclerView(){
        adapter = DirectionAdapter(measuredList){
            position -> deleteItem(position)
        }
        binding.tmpRv.adapter = adapter
        binding.tmpRv.layoutManager = LinearLayoutManager(requireContext())
    }

    private fun setupButtons(){
        binding.tmpDirectionInputMb.setOnClickListener {
            addMeasurement()
        }

        binding.tmpDirectionInputPlusMb.setOnClickListener {
            addMeasurement()
        }

        binding.tmpDirectionOkMb.setOnClickListener {
            completeMeasurement()
        }
    }

    private fun addMeasurement(){
        val data = Measurement(currentAzimuth, currentLight)
        measuredList.add(data)
        adapter.notifyItemInserted(measuredList.size-1)
        updateInputButtonState()
        updateOkButtonState()
    }

    private fun deleteItem(position: Int){
        if(position in measuredList.indices){
            measuredList.removeAt(position)
            adapter.notifyItemRemoved(position)
            adapter.notifyItemRangeChanged(position, measuredList.size)
            updateInputButtonState()
            updateOkButtonState()
        }
    }

    private fun updateInputButtonState(){
        if(measuredList.isEmpty()){
            binding.tmpDirectionInputMb.visibility = View.VISIBLE
            binding.tmpDirectionInputPlusMb.visibility = View.GONE
        }else{
            binding.tmpDirectionInputMb.visibility = View.GONE
            binding.tmpDirectionInputPlusMb.visibility = View.VISIBLE
        }
    }

    private fun updateOkButtonState(){
        val context = requireContext()
        if(measuredList.isNotEmpty()){
            binding.tmpDirectionOkMb.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.brand_700))
            binding.tmpDirectionOkMb.isEnabled = true
        }else{
            binding.tmpDirectionOkMb.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.gray_700))
        }
    }

    private fun completeMeasurement(){
        if(measuredList.isEmpty()){
            showCustomToast("측정된 값이 없습니다.")
            return
        }
        val azimuthList = measuredList.map{it.azimuth}
        val avgLight = measuredList.map{it.light}.average().toFloat()
        saveToStorage(azimuthList, avgLight)
    }

    private fun saveToStorage(azimuth: List<Int>, avgLight : Float){
        if(sessionIndex == -1 || itemIndex == -1) return
        val context = requireContext()
        val pref = context.getSharedPreferences("MainStorage", Context.MODE_PRIVATE)
        val gson = Gson()
        val json = pref.getString("KEY_ALL_SESSIONS", null)

        if(json != null){
            val type = object : TypeToken<MutableList<ExplorationSession>>() {}.type
            val sessions : MutableList<ExplorationSession> = gson.fromJson(json, type)
            if(sessionIndex < sessions.size){
                val session = sessions[sessionIndex]
                val sortedList = session.scheduleList.sortedBy{it.time}
                if(itemIndex < sortedList.size){
                    val item = sortedList[itemIndex]

                    item.measuredAzimuths = azimuth
                    item.measuredLight = avgLight
                    item.isMeasured = true

                    val newJson = gson.toJson(sessions)
                    pref.edit().putString("KEY_ALL_SESSIONS", newJson).apply()
                    showCustomToast2("측정이 완료되었습니다.")
                    onMeasurementSaved?.invoke()
                    dismiss()
                }
            }
        }


    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    private fun lowPass(input: FloatArray, output: FloatArray){
        for(i in input.indices){
            output[i] = output[i] + ALPHA*(input[i] - output[i])
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if(event == null) return
        when(event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
//                System.arraycopy(
//                    event.values,
//                    0,
//                    accelerometerReading,
//                    0,
//                    accelerometerReading.size
//                )
                lowPass(event.values, accelerometerReading)
                updateAzimuth()
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                //System.arraycopy(event.values, 0, magnetometerReading, 0, magnetometerReading.size)
                lowPass(event.values, magnetometerReading)
                updateAzimuth()
            }
            Sensor.TYPE_LIGHT->{
                currentLight = event.values[0]
            }
        }
    }

    private var lastUiAzimuth : Int = -1

    private fun updateAzimuth(){
        SensorManager.getRotationMatrix(rotationMatrix, null, accelerometerReading, magnetometerReading)
        SensorManager.getOrientation(rotationMatrix, orientationAngles)
        var azimuth = Math.toDegrees(orientationAngles[0].toDouble()).toInt()
        if(azimuth < 0){
            azimuth += 360
        }
        currentAzimuth = azimuth

        if(Math.abs(currentAzimuth - lastUiAzimuth) > 1 ){
            binding.tmpDirectionTv.text = "${currentAzimuth}°"
            lastUiAzimuth = currentAzimuth
        }
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let { sheet ->
            // 2. 높이를 화면의 80%로 설정
            val displayMetrics = Resources.getSystem().displayMetrics
            val screenHeight = displayMetrics.heightPixels
            val targetHeight = (screenHeight * 0.82).toInt() // 80% 높이

            sheet.layoutParams.height = targetHeight
            sheet.requestLayout()

            // 3. 상태를 '펼쳐짐(EXPANDED)'으로 고정하고, 드래그로 숨기지 못하게 설정 (선택사항)
            val behavior = BottomSheetBehavior.from(sheet)
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true // 접히는 상태 건너뛰기
            behavior.isDraggable = false // (필요하면) 아래로 드래그해서 닫기 막기
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}