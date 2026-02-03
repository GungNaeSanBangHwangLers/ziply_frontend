package com.keder.zply

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.keder.zply.databinding.ActivityIngExploreBinding
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch

class IngExploreActivity : AppCompatActivity() {

    private lateinit var binding: ActivityIngExploreBinding
    private var currentScheduleList: MutableList<ScheduleItem> = mutableListOf() // [수정] Mutable로 변경
    private var cardId: String? = null
    private val PREF_MEASURE = "measurement_local_storage"
    private lateinit var adapter: IngCardAdapter // [수정] 어댑터 전역 변수 (부분 갱신용)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityIngExploreBinding.inflate(layoutInflater)
        setContentView(binding.root)

        cardId = intent.getStringExtra("CARD_ID")
        if (cardId.isNullOrEmpty()) {
            finish()
            return
        }

        binding.ingBackIv.setOnClickListener { finish() }
        loadData()
    }

    private fun loadData() {
        val targetCardId = cardId ?: return
        lifecycleScope.launch {
            binding.loadingLayout.visibility = View.VISIBLE
            try {
                val service = RetrofitClient.getInstance(this@IngExploreActivity)

                // 1. 기본 정보 호출
                val houseDeferred = async { service.getCardHouseList(targetCardId) }
                val addressDeferred = async { service.getCardAddresses(targetCardId)}

                val houseRes = houseDeferred.await()
                val addressRes = addressDeferred.await()

                if (houseRes.isSuccessful && houseRes.body() != null) {
                    val rawHouses = houseRes.body()!!
                    val sortedHouses = rawHouses.sortedBy { it.visitTime }

                    // 주소 설정
                    var companyAddress = "직장 정보 없음"
                    if (addressRes.isSuccessful && addressRes.body() != null) {
                        val addrList = addressRes.body()!!
                        if (addrList.isNotEmpty()) {
                            companyAddress = addrList[0].address
                        }
                    }
                    binding.ingMyAddressTv.text = companyAddress

                    val sharedPref = getSharedPreferences(PREF_MEASURE, Context.MODE_PRIVATE)

                    // 2. 각 하우스별 상세 데이터 처리
                    val detailDeferredList = sortedHouses.mapIndexed { index, house ->
                        async {
                            val rankChar = ('A'.code + index).toChar().toString()

                            // ★ [중요] 변수를 반드시 async 블록 안에서 초기화해야 데이터가 섞이지 않음
                            var displayLight = 0f
                            var measuredAzimuths: List<Int> = emptyList()

                            try {
                                val detailRes = service.getHouseCardDetail(house.houseId)

                                if (detailRes.isSuccessful && detailRes.body() != null) {
                                    val details = detailRes.body()!!

                                    // ★ [수정 1] 방향: 리스트에 있는 모든 유효한 방향 값을 가져옴
                                    measuredAzimuths = details
                                        .mapNotNull { it.direction }
                                        .filter { it > 0.0 }
                                        .map { it.toInt() }
                                        .distinct() // 중복 제거 (필요 없으면 제거 가능)

                                    // ★ [수정 2] 채광: 0보다 큰 값들의 '평균' 계산
                                    val validLights = details
                                        .mapNotNull { it.lightLevel }
                                        .filter { it > 0 }

                                    if (validLights.isNotEmpty()) {
                                        displayLight = validLights.average().toFloat()
                                    }
                                }

                                // [로컬 데이터 우선 확인] (사용자가 방금 측정한 값 즉시 반영용)
                                val localRawLight = sharedPref.getFloat("raw_light_${house.houseId}", -1f)
                                if (localRawLight != -1f) {
                                    // 로컬 값이 있으면 평균 대신 최신 측정값 사용 (선택 사항)
                                    // 평균에 포함시키고 싶다면 위 로직 수정 필요. 현재는 즉각 피드백 위해 덮어쓰기
                                    displayLight = localRawLight
                                }

                                ScheduleItem(
                                    houseId = house.houseId,
                                    address = house.address,
                                    time = house.visitTime,
                                    rankLabel = rankChar,
                                    measuredLight = displayLight,
                                    measuredAzimuths = measuredAzimuths // 전체 리스트 전달
                                )
                            } catch (e: Exception) {
                                // 에러 발생 시 기본값
                                val localRawLight = sharedPref.getFloat("raw_light_${house.houseId}", 0f)
                                ScheduleItem(
                                    houseId = house.houseId,
                                    address = house.address,
                                    time = house.visitTime,
                                    rankLabel = rankChar,
                                    measuredLight = localRawLight
                                )
                            }
                        }
                    }

                    // 결과를 MutableList로 변환하여 저장
                    currentScheduleList = detailDeferredList.awaitAll().toMutableList()
                    setupRecyclerView(currentScheduleList)
                }
            } catch (e: Exception) {
                Log.e("IngExplore", "Network Error", e)
            } finally {
                binding.loadingLayout.visibility = View.GONE
            }
        }
    }

    private fun setupRecyclerView(items: List<ScheduleItem>) {
        adapter = IngCardAdapter(
            items,
            onMeasureClick = { itemPosition ->
                showWarningDialog(itemPosition)
            },
            onDirectionInfoClick = { houseId ->
                // ★ 방향 정보 클릭 시 호출
                showDirectionInfoDialog(houseId)
            }
        )

        val params = binding.ingCardRv.layoutParams
        if (items.size == 1) {
            params.width = ViewGroup.LayoutParams.WRAP_CONTENT
        } else {
            params.width = ViewGroup.LayoutParams.MATCH_PARENT
        }
        binding.ingCardRv.layoutParams = params

        binding.ingCardRv.adapter = adapter
        binding.ingCardRv.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
    }

    // ★ [수정] 로그 추가된 방향 정보 팝업 로직
    private fun showDirectionInfoDialog(houseId: Long) {
        Log.d("API_DEBUG", "[DirectionInfo] 요청 시작 - HouseId: $houseId")

        lifecycleScope.launch {
            try {
                val service = RetrofitClient.getInstance(this@IngExploreActivity)
                val response = service.getHouseDirectionDetail(houseId)

                Log.d("API_DEBUG", "[DirectionInfo] 응답 코드: ${response.code()}")

                if (response.isSuccessful && response.body() != null) {
                    val list = response.body()!!
                    Log.d("API_DEBUG", "[DirectionInfo] 데이터 개수: ${list.size}")

                    if (list.isNotEmpty()) {
                        val info = list[0]
                        // ★ [로그] 받아온 데이터 상세 출력
                        Log.d("API_DEBUG", "--------------------------------------")
                        Log.d("API_DEBUG", "방향: ${info.directionType}")
                        Log.d("API_DEBUG", "특징: ${info.features}")
                        Log.d("API_DEBUG", "장점: ${info.pros}")
                        Log.d("API_DEBUG", "단점: ${info.cons}")
                        Log.d("API_DEBUG", "--------------------------------------")

                        showDirectionPopupInternal(info.directionType, info.features, info.pros, info.cons)
                    } else {
                        Log.w("API_DEBUG", "[DirectionInfo] 데이터 리스트가 비어있음")
                        //showCustomToast("방향 정보가 없습니다.")
                    }
                } else {
                    Log.e("API_DEBUG", "[DirectionInfo] API 실패: ${response.errorBody()?.string()}")
                    showCustomToast("정보를 불러오지 못했어요. 다시 시도해주세요")
                }
            } catch (e: Exception) {
                Log.e("API_DEBUG", "[DirectionInfo] 네트워크 에러", e)
                showCustomToast("정보를 불러오지 못했어요. 다시 시도해주세요")
            }
        }
    }

    private fun showDirectionPopupInternal(title: String, features: String, pros: String, cons: String) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.dialog_house_direction)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialog.window?.setLayout(
            (resources.displayMetrics.widthPixels * 0.9).toInt(),
            ViewGroup.LayoutParams.WRAP_CONTENT
        )

        val titleTv = dialog.findViewById<TextView>(R.id.dialog_direction_title_tv)
        val featureTv = dialog.findViewById<TextView>(R.id.dialog_feature_tv)
        val prosTv = dialog.findViewById<TextView>(R.id.dialog_pros_tv)
        val consTv = dialog.findViewById<TextView>(R.id.dialog_cons_tv)
        val okBtn = dialog.findViewById<Button>(R.id.dialog_ok_btn)

        titleTv.text = title
        featureTv.text = features
        prosTv.text = pros
        consTv.text = cons

        okBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    private fun showWarningDialog(itemPosition: Int) {
        val dialog = Dialog(this)
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
        dialog.setContentView(R.layout.ing_alert)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialog.findViewById<Button>(R.id.alert_cancel_btn).setOnClickListener { dialog.dismiss() }
        dialog.findViewById<Button>(R.id.alert_ok_btn).setOnClickListener {
            dialog.dismiss()
            showBottomSheet(itemPosition)
        }
        dialog.show()
    }

    private fun showBottomSheet(itemPosition: Int) {
        if (itemPosition !in currentScheduleList.indices) return
        val targetHouseId = currentScheduleList[itemPosition].houseId
        val bottomSheet = IngBottomSheetFragment.newInstance(targetHouseId)

        bottomSheet.onMeasurementSaved = { rawLight ->
            // 1. 로컬 저장
            saveRawLightLocally(targetHouseId, rawLight)

            // ★ [수정 3] 즉시 UI 반영 (서버 로드 기다리지 않고 클라이언트 선반영)
            val oldItem = currentScheduleList[itemPosition]
            val newItem = oldItem.copy(
                measuredLight = rawLight,
                isMeasured = true
            )
            currentScheduleList[itemPosition] = newItem

            // "이 아이템만 바뀌었어"라고 알림 -> 깜빡임 없이 즉시 반영
            adapter.notifyItemChanged(itemPosition)

            // 2. 백그라운드에서 서버 데이터 최신화
            loadData()
        }

        bottomSheet.show(supportFragmentManager, "IngBottomSheetFragment")
    }

    private fun saveRawLightLocally(houseId: Long, rawLight: Float) {
        val sharedPref = getSharedPreferences(PREF_MEASURE, Context.MODE_PRIVATE)
        sharedPref.edit()
            .putFloat("raw_light_$houseId", rawLight)
            .apply()
    }

}