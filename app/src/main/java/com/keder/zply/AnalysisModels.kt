package com.keder.zply

// ==========================================
// 1. 요청 모델 (보낼 때)
// ==========================================

data class GoogleLoginRequest(val idToken: String)

// 카드 생성 요청
data class ReviewCardRequest(
    val basePointAddress: String,
    val houses: List<RequestHouse>
)

data class RequestHouse(
    val address: String,
    val visitDateTime: String // "yyyy-MM-dd HH:mm" (T 없음)
)

// ★ [POST용] 측정값 저장 시: 서버가 배열([]) 형태를 원하므로 List<Double> 사용
// (400 Bad Request 해결)
data class MeasurementDto(
    val round: Int,
    val direction: Double,
    val lightLevel: List<Double>
)


// ==========================================
// 2. 응답 모델 (받을 때)
// ==========================================

data class LoginResponse(val accessToken: String, val refreshToken: String)
data class UserNameResponse(val name: String)

// ★ [수정 완료] 메인 카드 목록 조회
// API: GET /api/v1/review/card
data class ReviewCardResponse(
    val cardId: String,
    val title: String,
    val address: String,
    val startDate: String,
    val endDate: String,
    val status: String,

    // ★ [변경] basePointCount -> houseCount 로 이름 변경
    val houseCount: Int
)

// 집 목록 조회
data class HouseListResponse(
    val houseId: Long,
    val address: String,
    val visitTime: String
)

// 거리 분석 결과
data class DistanceResponse(val basePoints: List<BasePoint>)

data class BasePoint(
    val basePointId: Long,
    val basePointName: String, // 직장 주소
    val results: List<DistanceResult>
)

data class DistanceResult(
    val houseId: Long,
    val walkingTimeMin: Int,
    val walkingDistanceKm: Double
)

// 소음 점수 결과
data class NoiseScoreResponse(
    val houseId: Long,
    val dayScore: Int,
    val nightScore: Int,
    val avgScore: Double,
    val message: String
)

// 방향 분석 결과
data class DirectionAnalysisResponse(
    val directionType: String,
    val features: String,
    val pros: String,
    val cons: String,
    val houseIds: List<Long>
)

// ★ [GET용] 상세 조회 시: 서버가 점수(숫자)를 주므로 Double 사용
// (JSON 파싱 에러 해결)
data class HouseMeasurementResponse(
    val round : Int,
    val title : String,
    val isDirectionDone : Boolean,
    val isLightDone : Boolean,
    val directionStatus : String,
    val lightStatus : String,
    val direction : Double,
    val lightLevel : Double // 리스트 아님! 숫자임.
)

data class LightScoreResponse(
    val houseId: Long,
    val score: Int
)

// [신규] 하우스별 방향 상세 정보
// API: GET /api/v1/review/house/{houseId}
data class HouseDirectionDetailResponse(
    val directionType: String,
    val features: String,
    val pros: String,
    val cons: String,
    val houseIds: List<Long>
)

data class CardAddressResponse(
    val alias: String,
    val address: String
)
