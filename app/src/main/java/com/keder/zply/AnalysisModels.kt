package com.keder.zply

import com.google.gson.annotations.SerializedName

// ==========================================
// 1. 요청 모델 (Request)
// ==========================================

data class GoogleLoginRequest(val idToken: String)

data class ReviewCardRequest(
    val basePointAddress: String,
    val houses: List<RequestHouse>
)

data class RequestHouse(
    val address: String,
    val visitDateTime: String // "yyyy-MM-dd HH:mm"
)

data class MeasurementDto(
    val round: Int,
    val direction: Double,
    val lightLevel: List<Double>
)

// ==========================================
// 2. 응답 모델 (Response)
// ==========================================

data class LoginResponse(val accessToken: String, val refreshToken: String)
data class UserNameResponse(val name: String)

data class ReviewCardResponse(
    val cardId: String,
    val title: String,
    val address: String,
    val startDate: String,
    val endDate: String,
    val status: String,
    val houseCount: Int
)

data class HouseListResponse(
    val houseId: Long,
    val address: String,
    val label: String,      // "A", "B"...
    val visitTime: String
)

data class MapInfoResponse(
    val id: Long,
    val label: String,
    val latitude: Double,
    val longitude: Double,
    val address: String
)

//data class DistanceResponse(
//    val results: List<DistanceResult>,
//    val transportMessage: String,
//
//    @SerializedName("bileMessage")
//    val bicycleMessage: String
//)



data class NoiseScoreResponse(
    val houseId: Long,
    val label: String,
    val dayScore: Int,
    val nightScore: Int,
    val message: String
)

data class DirectionAnalysisResponse(
    val directionType: String,
    val features: String,
    val pros: String,
    val cons: String,
    val houseIds: List<Long>
)

//data class HouseMeasurementResponse(
//    val round : Int,
//    val title : String,
//    val isDirectionDone : Boolean,
//    val isLightDone : Boolean,
//    val directionStatus : String,
//    val lightStatus : String,
//    val direction : Double,
//    val lightLevel : Double
//)

data class LightScoreResponse(
    val houseId: Long,
    val label: String,
    val score: Int
)

data class HouseDirectionGroupResponse(
    @SerializedName("houseAlias") val houseAlias: String = "",
    @SerializedName("houseId") val houseId: Long = 0L,
    @SerializedName("windows") val windows: List<DirectionWindowResponse> = emptyList()
)

data class DirectionWindowResponse(
    @SerializedName("windowLocation") val windowLocation: String = "", // "거실 정면", "방 1"
    @SerializedName("directionType") val directionType: String = "",   // "남향"
    @SerializedName("features") val features: String = "",             // 특징 설명
    @SerializedName("pros") val pros: String = "",                     // 장점
    @SerializedName("cons") val cons: String = ""                      // 단점
)

data class CardAddressResponse(
    val alias: String,
    val address: String
)

// ==========================================
// 3. 앱 내부 사용 모델 (UI Model) - 수정된 부분
// ==========================================

data class ScheduleItem (
    // ★ [기존 순서 복구] address와 time이 맨 앞에 있어야 AddScheduleBottomSheet 오류가 안 남
    val address : String,
    val time : String,

    var dayScore: Int = 0,
    var nightScore: Int = 0,
    var dayDesc: String = "",
    var nightDesc: String = "",

    // 이동수단별 시간 (기존 필드 + 추가 필드)
    var walkingTimeMin : Int = 0,
    var walkingDistanceKm : Double = 0.0,
    var transitTimeMin: Int = 0,      // 추가
    var transitPayment: String = "",  // 추가
    var carTimeMin: Int = 0,          // 추가
    var bicycleTimeMin: Int = 0,      // 추가

    // ID (기존 위치 유지)
    var houseId : Long = 0,

    var rankLabel : String = "",

    var measuredLightLux : Float = -1f, // -1이면 미측정

    // 방향 데이터 (방 갯수)
    var measuredRoomCount : Int = 0,    // 0이면 미측정

    // 사진 데이터
    var imageList: MutableList<String> = mutableListOf(),

    var measuredAzimuths : List<Int> = emptyList(),
    var measuredLight : Float = 0f,
    var isMeasured : Boolean = false,
    )

//data class HouseResponse(
//    val houseId: Long,
//    val label: String,         // API가 직접 "A", "B" 등을 내려줌
//    val address: String,
//    val visitTime: String,
//    val imageUrls: List<String>? // 이미지 URL 리스트
//)

// [신규 추가] 하우스 측정 카드 데이터 조회 응답 (GET /card)
data class HouseMeasurementResponse(
    val round: Int,
    val title: String,          // 예: "메인/거실"
    val isDirectionDone: Boolean,
    val isLightDone: Boolean,
    val directionStatus: String?,
    val lightStatus: String?,
    val direction: Int?,        // 방향 값
    val lightLevel: Int?,       // 채광 값
    val imageUrl: String?
)

// [신규 추가] 측정 데이터 전송용 요청 객체 (POST, PATCH /measure 내부 requests 배열용)
data class MeasureRequestDto(
    val round: Int,
    val title: String,
    val direction: Int,
    val lightLevel: Int
)

//data class LifeResponse(
//    val houseId: Long,
//    val label: String?,
//    val dayScore: Int?,
//    val nightScore: Int?,
//    val schoolCount: Int?,
//    val subwayCount: Int?,
//    val message: String?
//)
//
//// 2. 대중교통 수단이 없으면 transitPaymentStr 등이 null일 수 있으므로 String? 로 변경
//data class DistanceResult(
//    val houseId: Long,
//    val label: String,
//    val walkingTimeMin: Int,
//    val walkingDistanceKm: Double,
//    val transitTimeMin: Int,
//    val transitPaymentStr: String?, // Null 올 수 있음
//    val transitDepth: Int,
//
//    @SerializedName("bikeTimeMin")
//    val bicycleTimeMin: Int,
//
//    val carTimeMin: Int = 0
//)

//// 3. 방 이름이나 상태값도 안전하게 Nullable 처리
//data class MeasurementCard(
//    val round: Int?,
//    val title: String?,
//    val isDirectionDone: Boolean,
//    val isLightDone: Boolean,
//    val directionStatus: String?,
//    val lightStatus: String?,
//    val direction: Int?,
//    val lightLevel: Int?
//)


// 방향 전송용
data class DirectionRequest(
    val round: Int,
    val direction: Double,         // 방향(각도)
    val windowLocation: String     // 새로 추가됨! 예: "방 1"
)

data class LightRequest(
    val round: Int,
    val lightLevels: List<Double>  // 새로 추가됨! 배열로 전달
)

// 하우스 측정 카드 상세 조회용 (GET /card)
//data class HouseCardDetailResponse(
//    val houseId: Long,
//    val imageUrls: List<String>?,
//    val measurementCards: List<MeasurementCard>?
//)

data class AnalysisDirectionResponse(
    val directionType: String, // 예: "남향"
    val features: String,      // 예: "하루 종일 햇빛이 고르게 들어와요."
    val pros: String,          // 좋은 점
    val cons: String,          // 불편한 점
    val houseIds: List<Long>   // 이 방향에 해당하는 집 ID 목록
)

// 1. 집 목록 조회
data class HouseResponse(
    val houseId: Long = 0L,
    val label: String? = "",
    val address: String? = "",
    val visitTime: String? = "",
    val imageUrls: List<String>? = emptyList()
)

// 2. 직주거리
data class DistanceResponse(
    val results: List<DistanceResult>? = emptyList(),
    val transportMessage: String? = "",
    @SerializedName("bileMessage") val bicycleMessage: String? = ""
)

data class DistanceResult(
    val houseId: Long = 0L,
    val label: String? = "",
    val walkingTimeMin: Int = 0,
    val walkingDistanceKm: Double = 0.0,
    val transitTimeMin: Int = 0,
    val transitPaymentStr: String? = "",
    val transitDepth: Int = 0,
    @SerializedName("bikeTimeMin") val bicycleTimeMin: Int = 0,
    val carTimeMin: Int = 0
)

// 3. 소음/생활
data class LifeResponse(
    val houseId: Long = 0L,
    val label: String? = "",
    val dayScore: Int = 0,
    val nightScore: Int = 0,
    val schoolCount: Int = 0,
    val subwayCount: Int = 0,
    val message: String? = ""
)

// 4. 하우스 측정 카드 상세
data class HouseCardDetailResponse(
    val houseId: Long = 0L,
    val imageUrls: List<String>? = emptyList(),
    val measurementCards: List<MeasurementCard>? = emptyList()
)

data class MeasurementCard(
    val round: Int = 0,
    val title: String? = "",
    val isDirectionDone: Boolean = false,
    val isLightDone: Boolean = false,
    val directionStatus: String? = "",
    val lightStatus: String? = "",
    // ★ 수정됨: Int? -> Double? 로 변경하여 소수점 에러 방지!
    val direction: Double? = 0.0,
    val lightLevel: Double? = 0.0
)

data class SafetyResponse(
    val houseId: Long,
    val label: String?,
    val safetyScore: Int,
    val policeCount: Int,
    val streetlightCount: Int,
    val cctvCount: Int,
    val message: String?
)

data class ChecklistGroupResponse(
    val date: String,
    val isAllCompleted: Boolean,
    val houses: List<ChecklistHouseResponse>
)

data class ChecklistHouseResponse(
    val id: Long, // 스웨거 기준 id
    val visitDateTime: String?, // ★ 핵심: time이 아니라 visitDateTime 입니다!
    val address: String?,
    val isMeasurementCompleted: Boolean
)

data class UpdateHouseRequest(
    val address: String,
    val visitDateTime: String
)