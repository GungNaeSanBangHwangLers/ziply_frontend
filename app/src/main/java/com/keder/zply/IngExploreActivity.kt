package com.keder.zply

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.keder.zply.databinding.ActivityIngExploreBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.FileOutputStream

class IngExploreActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIngExploreBinding
    private var currentScheduleList: MutableList<ScheduleItem> = mutableListOf()
    private lateinit var adapter: IngCardAdapter
    private var cardId: String = ""

    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null

    private var currentPhotoPosition: Int = -1
    private var tempImageUri: Uri? = null
    private var currentPhotoPath: String = ""

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess: Boolean ->
        if (isSuccess && currentPhotoPosition != -1 && currentPhotoPath.isNotEmpty()) {
            Log.d("API_PHOTO", "📸 카메라 촬영 성공! 실제 파일 경로: $currentPhotoPath")
            val file = File(currentPhotoPath)
            uploadPhotoToServer(currentPhotoPosition, file)
        } else {
            Log.e("API_PHOTO", "❌ 카메라 촬영 취소 또는 실패. isSuccess: $isSuccess")
            showCustomToast("사진 촬영이 취소되었습니다.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIngExploreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cardId = intent.getStringExtra("CARD_ID") ?: ""
        if (cardId.isEmpty()) { finish(); return }

        binding.ingBackIv.setOnClickListener { finish() }
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

        if (savedInstanceState == null) {
            val existingInfoFragment = ExistingInfoFragment.newInstance(cardId)
            supportFragmentManager.beginTransaction()
                .replace(R.id.existing_info_container, existingInfoFragment)
                .commit()
        }

        setupTabButtons()
        loadDataSafe()
    }

    private fun setupTabButtons() {
        val btnMeasure = binding.btn1
        val btnInfo = binding.btn2
        val selectedColor = ContextCompat.getColor(this, R.color.brand_600)
        val unselectedColor = Color.TRANSPARENT

        btnMeasure.setOnClickListener {
            btnMeasure.backgroundTintList = ColorStateList.valueOf(selectedColor)
            btnInfo.backgroundTintList = ColorStateList.valueOf(unselectedColor)
            binding.ingCardRv.visibility = View.VISIBLE
            binding.existingInfoContainer.visibility = View.GONE
        }
        btnInfo.setOnClickListener {
            btnInfo.backgroundTintList = ColorStateList.valueOf(selectedColor)
            btnMeasure.backgroundTintList = ColorStateList.valueOf(unselectedColor)
            binding.ingCardRv.visibility = View.GONE
            binding.existingInfoContainer.visibility = View.VISIBLE
        }
    }

    private fun loadDataSafe() {
        lifecycleScope.launch {
            binding.loadingLayout.visibility = View.VISIBLE
            val service = RetrofitClient.getInstance(this@IngExploreActivity)
            val prefs = getSharedPreferences("ZplyMeasurementPrefs", Context.MODE_PRIVATE)

            try {
                val houses = try { service.getCardHouseList(cardId).body() ?: emptyList() } catch (e: Exception) { emptyList() }

                val addressRes = try { service.getCardAddresses(cardId).body() } catch (e: Exception) { null }
                if (!addressRes.isNullOrEmpty()) {
                    binding.ingMyAddressTv.text = addressRes[0].address ?: "주소 없음"
                }

                val sortedHouses = houses.sortedBy { it.visitTime ?: "" }
                val detailList = mutableListOf<ScheduleItem>()

                for (house in sortedHouses) {
                    var displayLight = -1f
                    var measuredRooms = 0
                    val serverImageUrls = mutableListOf<String>()

                    try {
                        val detailRes = service.getHouseCardDetail(house.houseId).body()
                        if (detailRes != null) {
                            val cards = detailRes.measurementCards ?: emptyList()
                            measuredRooms = cards.count { it.isDirectionDone }
                            val validLights = cards.filter { it.isLightDone && it.lightLevel != null }
                            if (validLights.isNotEmpty()) {
                                displayLight = validLights.map { it.lightLevel!! }.average().toFloat()
                            }
                            if (!detailRes.imageUrls.isNullOrEmpty()) {
                                serverImageUrls.addAll(detailRes.imageUrls)
                            }
                        }
                    } catch (e: Exception) {}

                    val savedRoomCount = prefs.getInt("room_${house.houseId}", -1)
                    val savedLux = prefs.getFloat("lux_${house.houseId}", -1f)
                    val savedPhotosStr = prefs.getString("photos_${house.houseId}", "") ?: ""
                    val localPhotos = if (savedPhotosStr.isNotEmpty()) savedPhotosStr.split(",") else emptyList()

                    if (savedRoomCount >= 0) measuredRooms = savedRoomCount
                    if (savedLux >= 0f) displayLight = savedLux

                    val combinedImages = (serverImageUrls + localPhotos).distinct().toMutableList()

                    detailList.add(ScheduleItem(
                        houseId = house.houseId,
                        address = house.address ?: "",
                        time = house.visitTime ?: "",
                        rankLabel = house.label ?: "?",
                        measuredLightLux = displayLight,
                        measuredRoomCount = measuredRooms,
                        imageList = combinedImages
                    ))
                }

                currentScheduleList = detailList.sortedBy { it.rankLabel }.toMutableList()
                setupRecyclerView()

            } catch (e: Exception) {
                Log.e("IngExplore", "화면 로드 실패", e)
            } finally {
                binding.loadingLayout.visibility = View.GONE
            }
        }
    }

    private fun setupRecyclerView() {
        adapter = IngCardAdapter(
            items = currentScheduleList,
            onLightClick = { position, item -> showLightAlertDialog(position) },
            onDirectionClick = { position, item -> showDirectionInfoDialog(position, item.houseId) },
            onPhotoClick = { position, item ->
                currentPhotoPosition = position

                val tempFile = File(cacheDir, "zply_photo_${System.currentTimeMillis()}.jpg")
                currentPhotoPath = tempFile.absolutePath

                val uri = androidx.core.content.FileProvider.getUriForFile(
                    this@IngExploreActivity,
                    "${packageName}.fileprovider",
                    tempFile
                )

                tempImageUri = uri
                takePhotoLauncher.launch(uri)
            }
        )
        binding.ingCardRv.adapter = adapter
        binding.ingCardRv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    }


    private fun showLightAlertDialog(position: Int) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.ing_alert)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.findViewById<Button>(R.id.alert_cancel_btn).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<Button>(R.id.alert_ok_btn).setOnClickListener {
            dialog.dismiss()
            measureLightRealtime(position)
        }
        dialog.show()
    }

    private fun measureLightRealtime(position: Int) {
        if (lightSensor == null) {
            showCustomToast("조도 센서를 지원하지 않는 기기입니다.")
            return
        }
        binding.loadingLayout.visibility = View.VISIBLE

        val listener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
                    val lux = event.values[0]
                    sensorManager.unregisterListener(this)
                    sendLightDataToServer(position, lux)
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(listener, lightSensor, SensorManager.SENSOR_DELAY_UI)

        lifecycleScope.launch {
            delay(2000)
            sensorManager.unregisterListener(listener)
            if (binding.loadingLayout.visibility == View.VISIBLE) {
                binding.loadingLayout.visibility = View.GONE
                showCustomToast("측정에 실패했습니다.")
            }
        }
    }

    private fun sendLightDataToServer(position: Int, lux: Float) {
        val houseId = currentScheduleList[position].houseId
        lifecycleScope.launch {
            try {
                val service = RetrofitClient.getInstance(this@IngExploreActivity)

                // ★ 수정됨: 새로 바뀐 채광 API DTO 규격에 맞춰 전송! (Double 타입의 List로 보냄)
                val request = LightRequest(
                    round = 1,
                    lightLevels = listOf(lux.toDouble())
                )

                val response = service.saveLight(houseId, request) // ★ 함수명 주의: saveLight

                if (response.isSuccessful) {
                    val prefs = getSharedPreferences("ZplyMeasurementPrefs", Context.MODE_PRIVATE)
                    prefs.edit().putFloat("lux_${houseId}", lux).apply()

                    currentScheduleList[position].measuredLightLux = lux
                    adapter.notifyItemChanged(position)

                    val fragment = supportFragmentManager.findFragmentById(R.id.existing_info_container) as? ExistingInfoFragment
                    fragment?.updateMeasurementLocal(houseId, lightLux = lux)

                    showCustomToast2("측정을 완료했어요")
                } else {
                    Log.e("API_LIGHT", "❌ 채광 전송 실패. 코드: ${response.code()}")
                    showCustomToast("등록에 실패했어요.")
                }
            } catch (e: Exception) {
                Log.e("API_LIGHT", "❌ 채광 전송 에러", e)
                showCustomToast("등록에 실패했어요.")
            }
            finally { binding.loadingLayout.visibility = View.GONE }
        }
    }

    private fun uploadPhotoToServer(position: Int, file: File) {
        binding.loadingLayout.visibility = View.VISIBLE
        val houseId = currentScheduleList[position].houseId

        Log.d("API_PHOTO", "📸 [사진 업로드 시도] HouseID: $houseId, 파일 경로: ${file.absolutePath}")

        lifecycleScope.launch {
            try {
                val service = RetrofitClient.getInstance(this@IngExploreActivity)
                val requestFile = file.asRequestBody("image/jpeg".toMediaTypeOrNull())
                val imagePart = MultipartBody.Part.createFormData("images", file.name, requestFile)

                val response = service.uploadHouseImages(houseId, listOf(imagePart))

                if (response.isSuccessful) {
                    Log.d("API_PHOTO", "✅ [사진 업로드 성공] HTTP 200")
                    val absolutePath = file.absolutePath

                    val prefs = getSharedPreferences("ZplyMeasurementPrefs", Context.MODE_PRIVATE)
                    val existingPhotos = prefs.getString("photos_${houseId}", "") ?: ""
                    val newPhotos = if (existingPhotos.isEmpty()) absolutePath else "$existingPhotos,$absolutePath"
                    prefs.edit().putString("photos_${houseId}", newPhotos).apply()

                    currentScheduleList[position].imageList.add(absolutePath)
                    adapter.notifyItemChanged(position)

                    val fragment = supportFragmentManager.findFragmentById(R.id.existing_info_container) as? ExistingInfoFragment
                    fragment?.updateMeasurementLocal(houseId, newImagePath = absolutePath)

                    showCustomToast2("사진이 추가되었습니다.")
                } else {
                    Log.e("API_PHOTO", "❌ [사진 업로드 실패] Code: ${response.code()}, Error: ${response.errorBody()?.string()}")
                    showCustomToast("사진 등록에 실패했어요.")
                }
            } catch (e: Exception) {
                Log.e("API_PHOTO", "❌ [사진 업로드 예외 발생]: ${e.message}", e)
                showCustomToast("네트워크 오류가 발생했습니다.")
            } finally {
                binding.loadingLayout.visibility = View.GONE
            }
        }
    }

    private fun showDirectionInfoDialog(position: Int, houseId: Long) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.ing_info_dialog)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.findViewById<Button>(R.id.alert_cancel_btn).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<Button>(R.id.alert_ok_btn).setOnClickListener {
            dialog.dismiss()
            val bottomSheet = IngBottomSheetFragment.newInstance(houseId)
            bottomSheet.onComplete = { roomCount ->
                if (roomCount > 0) {
                    currentScheduleList[position].measuredRoomCount = roomCount
                    adapter.notifyItemChanged(position)

                    val fragment = supportFragmentManager.findFragmentById(R.id.existing_info_container) as? ExistingInfoFragment
                    fragment?.updateMeasurementLocal(houseId, roomCount = roomCount)
                }
            }
            bottomSheet.show(supportFragmentManager, "IngBottomSheetFragment")
        }
        dialog.show()
    }
    private fun saveBitmapToTempFile(bitmap: Bitmap): String {
        val file = File(cacheDir, "photo_${System.currentTimeMillis()}.jpg")
        FileOutputStream(file).use { out -> bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out) }
        return Uri.fromFile(file).toString()
    }
}