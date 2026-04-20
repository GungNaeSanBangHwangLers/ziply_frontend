package com.keder.zply

import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.view.Window
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.keder.zply.databinding.ActivityIngExploreBinding
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class IngExploreActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIngExploreBinding
    private var currentScheduleList: MutableList<ScheduleItem> = mutableListOf()
    private var cardId: String = ""

    private var currentHouseIndex: Int = -1
    private lateinit var photoAdapter: IngImageAdapter

    private lateinit var sensorManager: SensorManager
    private var lightSensor: Sensor? = null
    private var lightSensorListener: SensorEventListener? = null

    private var currentPhotoPath: String = ""
    private var isNavigatingBack = false

    private val takePhotoLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
        if (isSuccess && currentPhotoPath.isNotEmpty()) {
            val file = File(currentPhotoPath)
            if (file.exists() && file.length() > 0) {
                uploadPhotoToServer(currentHouseIndex, file)
            } else {
                showCustomToast("사진 저장에 실패했습니다. 다시 촬영해주세요.")
            }
        } else {
            showCustomToast("사진 촬영이 취소되었습니다.")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (savedInstanceState != null) {
            currentPhotoPath = savedInstanceState.getString("SAVED_PHOTO_PATH", "") ?: ""
        }

        try {
            binding = ActivityIngExploreBinding.inflate(layoutInflater)
            setContentView(binding.root)

            cardId = intent.extras?.get("CARD_ID")?.toString() ?: ""

            if (cardId.isEmpty()) {
                showCustomToast("카드 정보를 찾을 수 없습니다.")
                finish()
                return
            }

            binding.ingBackIv.setOnClickListener { goBackToMain() }
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() { goBackToMain() }
            })

            sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
            lightSensor = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

            setupTabButtons()

            photoAdapter = IngImageAdapter(mutableListOf()) {}
            binding.photoRv.adapter = photoAdapter

            binding.step2Btn.setOnClickListener {
                if (currentHouseIndex in 0 until currentScheduleList.size) {
                    showDirectionInfoDialog(currentScheduleList[currentHouseIndex].houseId)
                }
            }

            binding.step3Btn.setOnClickListener {
                if (currentHouseIndex in 0 until currentScheduleList.size) {
                    if (currentScheduleList[currentHouseIndex].imageList.size >= 7) {
                        showCustomToast("사진은 최대 7장까지만 등록 가능해요.")
                    } else {
                        try {
                            val photoFile: File = createImageFile()
                            val photoURI: Uri = FileProvider.getUriForFile(
                                this,
                                "${packageName}.fileprovider",
                                photoFile
                            )
                            takePhotoLauncher.launch(photoURI)
                        } catch (ex: Exception) {
                            showCustomToast("카메라를 실행할 수 없습니다.")
                        }
                    }
                }
            }

            if (savedInstanceState == null) {
                try {
                    val existingInfoFragment = ExistingInfoFragment.newInstance(cardId)
                    supportFragmentManager.beginTransaction()
                        .replace(R.id.existing_info_container, existingInfoFragment, "EXISTING_INFO")
                        .commit()
                } catch (e: Exception) { }
            }

            loadDataSafe()

        } catch (e: Exception) {
            showCustomToast("화면을 불러오는 중 문제가 발생했습니다.")
        }
    }

    private fun goBackToMain() {
        if (isNavigatingBack) return
        isNavigatingBack = true

        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("JPEG_${timeStamp}_", ".jpg", storageDir).apply {
            currentPhotoPath = absolutePath
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString("SAVED_PHOTO_PATH", currentPhotoPath)
    }

    private fun setupTabButtons() {
        val selectedColor = ContextCompat.getColor(this, R.color.brand_600)
        val unselectedColor = Color.TRANSPARENT

        binding.btn1.setOnClickListener {
            binding.btn1.backgroundTintList = ColorStateList.valueOf(selectedColor)
            binding.btn2.backgroundTintList = ColorStateList.valueOf(unselectedColor)
            binding.measureScrollView.visibility = View.VISIBLE
            binding.existingInfoContainer.visibility = View.GONE
        }
        binding.btn2.setOnClickListener {
            binding.btn2.backgroundTintList = ColorStateList.valueOf(selectedColor)
            binding.btn1.backgroundTintList = ColorStateList.valueOf(unselectedColor)
            binding.measureScrollView.visibility = View.GONE
            binding.existingInfoContainer.visibility = View.VISIBLE
        }
    }

    private fun loadDataSafe() {
        lifecycleScope.launch {
            binding.loadingLayout.visibility = View.VISIBLE
            try {
                val service = RetrofitClient.getInstance(this@IngExploreActivity)
                val prefs = getSharedPreferences("ZplyMeasurementPrefs", Context.MODE_PRIVATE)
                val houses = try { service.getCardHouseList(cardId).body() ?: emptyList() } catch (e: Exception) { emptyList() }

                val detailList = mutableListOf<ScheduleItem>()
                for (house in houses.sortedBy { it.visitTime ?: "" }) {
                    var displayLight = -1f
                    var measuredRooms = 0
                    val serverImages = mutableListOf<String>()

                    try {
                        val detailRes = service.getHouseCardDetail(house.houseId).body()
                        if (detailRes != null) {
                            val cards = detailRes.measurementCards ?: emptyList()
                            measuredRooms = cards.count { it.isDirectionDone }
                            val validLights = cards.filter { it.isLightDone && it.lightLevel != null }
                            if (validLights.isNotEmpty()) displayLight = validLights.map { it.lightLevel!! }.average().toFloat()
                            if (!detailRes.imageUrls.isNullOrEmpty()) serverImages.addAll(detailRes.imageUrls)
                        }
                    } catch (e: Exception) {}

                    val savedRoomCount = prefs.getInt("room_${house.houseId}", -1)
                    val savedLux = prefs.getFloat("lux_${house.houseId}", -1f)
                    val savedPhotosStr = prefs.getString("photos_${house.houseId}", "") ?: ""
                    val localPhotos = if (savedPhotosStr.isNotEmpty()) savedPhotosStr.split(",") else emptyList()

                    if (savedRoomCount >= 0) measuredRooms = savedRoomCount
                    if (savedLux >= 0f) displayLight = savedLux

                    detailList.add(ScheduleItem(
                        houseId = house.houseId,
                        address = house.address ?: "",
                        time = house.visitTime?.replace("T", " ")?.take(16) ?: "",
                        rankLabel = house.label ?: "?",
                        measuredLightLux = displayLight,
                        measuredRoomCount = measuredRooms,
                        imageList = (serverImages + localPhotos).distinct().toMutableList()
                    ))
                }

                currentScheduleList = detailList.sortedBy { it.rankLabel }.toMutableList()

                // ★ 시작 주거지 세팅 로직
                val targetHouseId = intent.getLongExtra("HOUSE_ID", -1L)
                val showTab = intent.getStringExtra("SHOW_TAB")

                if (targetHouseId != -1L) {
                    // 특정 주거지를 눌러서 들어온 경우
                    currentHouseIndex = currentScheduleList.indexOfFirst { it.houseId == targetHouseId }
                    if (currentHouseIndex == -1) currentHouseIndex = 0
                } else {
                    // 상단 카드 클릭 등 일반적인 진입: 첫 번째 미측정 주거지 찾기
                    val unmeasuredIndex = currentScheduleList.indexOfFirst {
                        it.measuredLightLux < 0f || it.measuredRoomCount <= 0 || it.imageList.isEmpty()
                    }
                    if (unmeasuredIndex != -1) {
                        currentHouseIndex = unmeasuredIndex
                    } else {
                        // 모든 주거지가 측정 완료되었다면 자동으로 After 화면으로 이동
                        val intent = Intent(this@IngExploreActivity, AfterExploreActivity::class.java)
                        intent.putExtra("CARD_ID", cardId)
                        startActivity(intent)
                        finish()
                        return@launch
                    }
                }

                // 지정된 탭 활성화
                if (showTab == "INFO") {
                    binding.btn2.performClick()
                } else {
                    binding.btn1.performClick()
                }

                updateHouseUI()
                updateStepsUI(currentHouseIndex)

            } catch (e: Exception) {
                showCustomToast("데이터를 불러오는데 실패했습니다.")
                showErrorOverlay { loadDataSafe() }
            } finally {
                binding.loadingLayout.visibility = View.GONE
            }
        }
    }

    // ★ 단일 주거 정보 UI 업데이트 함수
    private fun updateHouseUI() {
        if (currentHouseIndex !in currentScheduleList.indices) return
        val item = currentScheduleList[currentHouseIndex]

        binding.vpRankTv.text = item.rankLabel
        binding.vpDateTv.text = "${item.time} 탐색"
        binding.vpAddressTv.text = item.address

        val rankChar = if (item.rankLabel.isNotEmpty()) item.rankLabel[0] else '?'
        val brand100 = ContextCompat.getColor(this, R.color.brand_100)
        val brand800 = ContextCompat.getColor(this, R.color.brand_800)
        val brand400 = ContextCompat.getColor(this, R.color.brand_400)
        val white = ContextCompat.getColor(this, R.color.white)
        val brand700 = ContextCompat.getColor(this, R.color.brand_700)
        val brand950 = ContextCompat.getColor(this, R.color.brand_950)
        val black = ContextCompat.getColor(this, R.color.black)
        val gray400 = ContextCompat.getColor(this, R.color.gray_400)
        val gray700 = ContextCompat.getColor(this, R.color.gray_700)
        val gray200 = ContextCompat.getColor(this, R.color.gray_200)

        when (rankChar) {
            'A' -> { binding.vpRankTv.backgroundTintList = ColorStateList.valueOf(brand100); binding.vpRankTv.setTextColor(brand800) }
            'B' -> { binding.vpRankTv.backgroundTintList = ColorStateList.valueOf(brand400); binding.vpRankTv.setTextColor(white) }
            'C' -> { binding.vpRankTv.backgroundTintList = ColorStateList.valueOf(brand700); binding.vpRankTv.setTextColor(white) }
            'D' -> { binding.vpRankTv.backgroundTintList = ColorStateList.valueOf(brand950); binding.vpRankTv.setTextColor(white) }
            'E' -> { binding.vpRankTv.backgroundTintList = ColorStateList.valueOf(white); binding.vpRankTv.setTextColor(black) }
            'F' -> { binding.vpRankTv.backgroundTintList = ColorStateList.valueOf(gray400); binding.vpRankTv.setTextColor(white) }
            'G' -> { binding.vpRankTv.backgroundTintList = ColorStateList.valueOf(gray700); binding.vpRankTv.setTextColor(white) }
            else -> { binding.vpRankTv.backgroundTintList = ColorStateList.valueOf(gray200); binding.vpRankTv.setTextColor(black) }
        }
    }

    private fun updateStepsUI(position: Int) {
        if (position < 0 || position >= currentScheduleList.size) return
        val item = currentScheduleList[position]

        if (item.measuredLightLux >= 0f) {
            setStepStyleSafe(binding.step1Num, binding.step1Title, binding.step1Desc, binding.step1Btn, true, "측정완료")
        } else {
            setStepStyleSafe(binding.step1Num, binding.step1Title, binding.step1Desc, binding.step1Btn, false, "측정중...")
            startAutoLightMeasurement(position)
        }

        if (item.measuredRoomCount > 0) {
            setStepStyleSafe(binding.step2Num, binding.step2Title, binding.step2Desc, binding.step2Btn, true, "측정완료")
        } else {
            setStepStyleSafe(binding.step2Num, binding.step2Title, binding.step2Desc, binding.step2Btn, false, "측정하기")
        }

        photoAdapter.updateImages(item.imageList)
        if (item.imageList.size >= 7) {
            setStepStyleSafe(binding.step3Num, binding.step3Title, binding.step3Desc, binding.step3Btn, true, "촬영완료")
        } else {
            setStepStyleSafe(binding.step3Num, binding.step3Title, binding.step3Desc, binding.step3Btn, false, "촬영하기")
        }
    }

    private fun setStepStyleSafe(numTv: TextView?, titleTv: TextView?, descTv: TextView?, btn: TextView?, isCompleted: Boolean, btnText: String) {
        if (numTv == null || titleTv == null || descTv == null || btn == null) return
        try {
            if (isCompleted) {
                numTv.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.gray_500))
                titleTv.setTextColor(ContextCompat.getColor(this, R.color.gray_500))
                descTv.setTextColor(ContextCompat.getColor(this, R.color.gray_500))
                btn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.gray_700))
                btn.setTextColor(ContextCompat.getColor(this, R.color.gray_500))
                btn.isEnabled = false
            } else {
                numTv.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.white))
                titleTv.setTextColor(ContextCompat.getColor(this, R.color.white))
                descTv.setTextColor(ContextCompat.getColor(this, R.color.white))
                btn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.brand_700))
                btn.setTextColor(ContextCompat.getColor(this, R.color.white))
                btn.isEnabled = true
            }
            btn.text = btnText
        } catch (e: Exception) {}
    }

    // ★ 현재 주거의 측정이 모두 끝났는지 확인하고, 다음으로 자동 이동하는 함수
    private fun checkAndMoveToNextHouse() {
        if (currentHouseIndex !in currentScheduleList.indices) return
        val item = currentScheduleList[currentHouseIndex]

        val isFullyMeasured = item.measuredLightLux >= 0f && item.measuredRoomCount > 0 && item.imageList.isNotEmpty()

        if (isFullyMeasured) {
            val nextIndex = currentScheduleList.indexOfFirst {
                it.measuredLightLux < 0f || it.measuredRoomCount <= 0 || it.imageList.isEmpty()
            }

            if (nextIndex != -1) {
                showCustomToast2("현재 집의 측정을 모두 완료하여 다음 집으로 이동합니다.")
                stopAutoLightMeasurement()
                currentHouseIndex = nextIndex
                updateHouseUI()
                updateStepsUI(currentHouseIndex)
            } else {
                showCustomToast2("모든 집의 측정을 완료했습니다. 결과 화면으로 이동합니다.")
                val intent = Intent(this, AfterExploreActivity::class.java)
                intent.putExtra("CARD_ID", cardId)
                startActivity(intent)
                finish()
            }
        }
    }

    private fun startAutoLightMeasurement(position: Int) {
        if (lightSensor == null) {
            binding.step1Btn.text = "센서 없음"
            return
        }
        lightSensorListener = object : SensorEventListener {
            override fun onSensorChanged(event: SensorEvent?) {
                if (event?.sensor?.type == Sensor.TYPE_LIGHT) {
                    val lux = event.values[0]
                    stopAutoLightMeasurement()
                    sendLightDataToServer(position, lux)
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }
        sensorManager.registerListener(lightSensorListener, lightSensor, SensorManager.SENSOR_DELAY_UI)
    }

    private fun stopAutoLightMeasurement() {
        lightSensorListener?.let {
            sensorManager.unregisterListener(it)
            lightSensorListener = null
        }
    }

    private fun sendLightDataToServer(position: Int, lux: Float) {
        val houseId = currentScheduleList[position].houseId
        lifecycleScope.launch {
            try {
                val service = RetrofitClient.getInstance(this@IngExploreActivity)
                val response = service.saveLight(houseId, LightRequest(1, listOf(lux.toDouble())))
                if (response.isSuccessful) {
                    val prefs = getSharedPreferences("ZplyMeasurementPrefs", Context.MODE_PRIVATE)
                    prefs.edit().putFloat("lux_${houseId}", lux).apply()
                    currentScheduleList[position].measuredLightLux = lux

                    if (currentHouseIndex == position) {
                        updateStepsUI(position)
                        checkAndMoveToNextHouse() // 자동 검사
                    }

                    val existingInfoFragment = supportFragmentManager.findFragmentByTag("EXISTING_INFO") as? ExistingInfoFragment
                    existingInfoFragment?.updateMeasurementLocal(houseId = houseId, lightLux = lux)

                    showCustomToast2("채광 측정을 완료했어요")
                } else {
                    handleLightFail(position)
                }
            } catch (e: Exception) {
                handleLightFail(position)
            }
        }
    }

    private fun handleLightFail(position: Int) {
        if (currentHouseIndex == position) {
            binding.step1Btn.text = "측정 실패"
            binding.step1Btn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(this, R.color.gray_600))
        }
    }

    private fun showDirectionInfoDialog(houseId: Long) {
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
                    currentScheduleList[currentHouseIndex].measuredRoomCount = roomCount
                    updateStepsUI(currentHouseIndex)
                    checkAndMoveToNextHouse() // 자동 검사

                    val existingInfoFragment = supportFragmentManager.findFragmentByTag("EXISTING_INFO") as? ExistingInfoFragment
                    existingInfoFragment?.updateMeasurementLocal(houseId = houseId, roomCount = roomCount)

                    showCustomToast2("방향 측정을 완료했어요")
                }
            }
            bottomSheet.show(supportFragmentManager, "IngBottomSheetFragment")
        }
        dialog.show()
    }

    private fun uploadPhotoToServer(position: Int, file: File) {
        if (!file.exists() || file.length() == 0L) {
            showCustomToast("사진 파일이 유효하지 않습니다.")
            return
        }

        binding.loadingLayout.visibility = View.VISIBLE
        val houseId = currentScheduleList[position].houseId

        lifecycleScope.launch {
            var tempCompressedFile: File? = null
            try {
                tempCompressedFile = getCompressedImageFile(file)
                val service = RetrofitClient.getInstance(this@IngExploreActivity)
                val requestFile = tempCompressedFile.asRequestBody("image/jpeg".toMediaTypeOrNull())
                val imagePart = MultipartBody.Part.createFormData("images", tempCompressedFile.name, requestFile)
                val response = service.uploadHouseImages(houseId, listOf(imagePart))

                if (response.isSuccessful) {
                    val absolutePath = file.absolutePath
                    val prefs = getSharedPreferences("ZplyMeasurementPrefs", Context.MODE_PRIVATE)
                    val existingPhotos = prefs.getString("photos_${houseId}", "") ?: ""
                    prefs.edit().putString("photos_${houseId}", if (existingPhotos.isEmpty()) absolutePath else "$existingPhotos,$absolutePath").apply()

                    currentScheduleList[position].imageList.add(absolutePath)

                    if (currentHouseIndex == position) {
                        updateStepsUI(position)
                        checkAndMoveToNextHouse() // 자동 검사
                    }

                    val existingInfoFragment = supportFragmentManager.findFragmentByTag("EXISTING_INFO") as? ExistingInfoFragment
                    existingInfoFragment?.updateMeasurementLocal(houseId = houseId, newImagePath = absolutePath)

                    showCustomToast2("사진이 추가되었습니다.")
                } else {
                    showCustomToast("사진 등록에 실패했어요.")
                }
            } catch (e: Exception) {
                showCustomToast("네트워크 오류가 발생했습니다.")
            } finally {
                tempCompressedFile?.delete()
                binding.loadingLayout.visibility = View.GONE
            }
        }
    }

    private fun getCompressedImageFile(file: File): File {
        val options = android.graphics.BitmapFactory.Options()
        options.inJustDecodeBounds = true
        android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)

        val maxSide = 1280
        var inSampleSize = 1
        if (options.outHeight > maxSide || options.outWidth > maxSide) {
            val halfHeight = options.outHeight / 2
            val halfWidth = options.outWidth / 2
            while (halfHeight / inSampleSize >= maxSide && halfWidth / inSampleSize >= maxSide) {
                inSampleSize *= 2
            }
        }

        options.inJustDecodeBounds = false
        options.inSampleSize = inSampleSize
        val bitmap = android.graphics.BitmapFactory.decodeFile(file.absolutePath, options)

        val compressedFile = File(cacheDir, "temp_up_${System.currentTimeMillis()}.jpg")
        val out = java.io.FileOutputStream(compressedFile)
        bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 80, out)
        out.flush()
        out.close()
        bitmap.recycle()

        return compressedFile
    }

    override fun onDestroy() {
        super.onDestroy()
        stopAutoLightMeasurement()
    }
}