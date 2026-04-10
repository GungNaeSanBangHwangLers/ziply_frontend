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
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.keder.zply.databinding.ActivityIngExploreBinding
import com.keder.zply.databinding.ItemIngHouseVpBinding
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

            // 1. 화면 내 뒤로 가기 화살표 클릭
            binding.ingBackIv.setOnClickListener {
                goBackToMain()
            }

            // 2. 휴대폰 자체 뒤로 가기 버튼 클릭
            onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    goBackToMain()
                }
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
                            Log.e("ZplyError", "카메라 실행 오류", ex)
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
                } catch (e: Exception) {
                    Log.e("z_error", "Fragment 에러", e)
                }
            }

            loadDataSafe()

        } catch (e: Exception) {
            Log.e("z_error", "onCreate 에러", e)
            showCustomToast("화면을 불러오는 중 문제가 발생했습니다.")
        }
    }

    // ==============================================================
    // ★ [안전장치 적용] 한 번 실행되면 두 번 다시 실행되지 않는 철벽 탈출 함수
    // ==============================================================
    private fun goBackToMain() {
        if (isNavigatingBack) return // 이미 뒤로 가기를 누른 상태면 무시!
        isNavigatingBack = true      // 자물쇠 채우기

        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        startActivity(intent)
        finish()
    }

    @Throws(IOException::class)
    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.KOREA).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
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
                setupViewPager()

            } catch (e: Exception) {
                showCustomToast("데이터를 불러오는데 실패했습니다.")
            } finally {
                binding.loadingLayout.visibility = View.GONE
            }
        }
    }

    private fun setupViewPager() {
        try {
            val vpAdapter = HousePagerAdapter(currentScheduleList)
            binding.houseVp.adapter = vpAdapter
            if (currentScheduleList.isNotEmpty()) {
                binding.houseVp.offscreenPageLimit = 3
            }

            binding.houseVp.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    try {
                        stopAutoLightMeasurement()
                        currentHouseIndex = position
                        updateStepsUI(position)
                    } catch (e: Exception) {}
                }
            })
        } catch (e: Exception) {}
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
            val context = this
            if (isCompleted) {
                numTv.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.gray_500))
                titleTv.setTextColor(ContextCompat.getColor(context, R.color.gray_500))
                descTv.setTextColor(ContextCompat.getColor(context, R.color.gray_500))
                btn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.gray_700))
                btn.setTextColor(ContextCompat.getColor(context, R.color.gray_500))
                btn.isEnabled = false
            } else {
                numTv.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.white))
                titleTv.setTextColor(ContextCompat.getColor(context, R.color.white))
                descTv.setTextColor(ContextCompat.getColor(context, R.color.white))
                btn.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.brand_700))
                btn.setTextColor(ContextCompat.getColor(context, R.color.white))
                btn.isEnabled = true
            }
            btn.text = btnText
        } catch (e: Exception) {}
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
                    if (currentHouseIndex == position) updateStepsUI(position)

                    val existingInfoFragment = supportFragmentManager.findFragmentById(R.id.existing_info_container) as? ExistingInfoFragment
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

                    val existingInfoFragment = supportFragmentManager.findFragmentById(R.id.existing_info_container) as? ExistingInfoFragment
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
                    if (currentHouseIndex == position) updateStepsUI(position)

                    val existingInfoFragment = supportFragmentManager.findFragmentByTag("EXISTING_INFO") as? ExistingInfoFragment
                        ?: supportFragmentManager.findFragmentById(R.id.existing_info_container) as? ExistingInfoFragment

                    existingInfoFragment?.updateMeasurementLocal(houseId = houseId, newImagePath = absolutePath)

                    showCustomToast2("사진이 추가되었습니다.")
                } else {
                    showCustomToast("사진 등록에 실패했어요. (에러: ${response.code()})")
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

class HousePagerAdapter(private val items: List<ScheduleItem>) : RecyclerView.Adapter<HousePagerAdapter.ViewHolder>() {
    inner class ViewHolder(val binding: ItemIngHouseVpBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ScheduleItem) {
            binding.vpRankTv.text = item.rankLabel
            binding.vpDateTv.text = "${item.time} 탐색"
            binding.vpAddressTv.text = item.address

            val context = binding.root.context
            val brand100 = ContextCompat.getColor(context, R.color.brand_100)
            val brand800 = ContextCompat.getColor(context, R.color.brand_800)
            val brand400 = ContextCompat.getColor(context, R.color.brand_400)
            val white = ContextCompat.getColor(context, R.color.white)
            val brand700 = ContextCompat.getColor(context, R.color.brand_700)
            val brand950 = ContextCompat.getColor(context, R.color.brand_950)
            val black = ContextCompat.getColor(context, R.color.black)
            val gray400 = ContextCompat.getColor(context, R.color.gray_400)
            val gray700 = ContextCompat.getColor(context, R.color.gray_700)
            val gray200 = ContextCompat.getColor(context, R.color.gray_200)

            val rankChar = if (item.rankLabel.isNotEmpty()) item.rankLabel[0] else '?'
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
    }
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        return ViewHolder(ItemIngHouseVpBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }
    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(items[position])
    override fun getItemCount(): Int = items.size
}