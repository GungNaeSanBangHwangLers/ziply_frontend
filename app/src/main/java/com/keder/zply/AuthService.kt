package com.keder.zply

import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Multipart
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Part
import retrofit2.http.Path

interface AuthService {
    // 로그인 (기존 유지)
    @POST("api/v1/auth/google")
    suspend fun googleLogin(@Body request: GoogleLoginRequest) : Response<LoginResponse>

    // 유저 정보 조회 (기존 유지)
    @GET("api/v1/users/name")
    suspend fun getUserName(): Response<UserNameResponse>

    // 탐색 카드 전체 조회 (기존 유지)
    @GET("api/v1/review/card")
    suspend fun getReviewCards() : Response<List<ReviewCardResponse>>

    // 카드 생성 (기존 유지)
    @POST("api/v1/review/card")
    suspend fun createReviewCard(@Body request: ReviewCardRequest) : Response<String>

    // [변경] 집 목록 조회 (Response Model에 label 추가됨)
    // Path param 이름을 명세서에 맞춰 searchCardId로 변경 (기존 cardId여도 동작은 함)
    @GET("api/v1/review/card/{searchCardId}/houses")
    suspend fun getCardHouseList(@Path("searchCardId") searchCardId: String): Response<List<HouseResponse>>

    // [신규] 지도 정보 조회 (명세서 1번 사진)
    @GET("api/v1/review/card/map/{searchCardId}")
    suspend fun getCardMapInfo(@Path("searchCardId") searchCardId: String): Response<List<MapInfoResponse>>

    // [변경] 거리 정보 (직장 주소 포함 -> 구조 변경됨)
    // 리스트가 아니라 객체(DistanceResponse) 안에 리스트가 있는 형태로 변경됨
    @GET("api/v1/analysis/distance/{searchCardId}")
    suspend fun getAnalysisDistance(@Path("searchCardId") searchCardId : String) : Response<List<DistanceResponse>>
    // [변경] 소음 정보 (Response Model 변경: avgScore 삭제, label 추가)
    @GET("api/v1/analysis/score/{searchCardId}")
    suspend fun getAnalysisScore(@Path("searchCardId") searchCardId : String) : Response<List<NoiseScoreResponse>>

    @GET("api/v1/analysis/life/{searchCardId}")
    suspend fun getAnalysisLife(@Path("searchCardId") searchCardId: String): Response<List<LifeResponse>>

    // 측정 데이터 전송 (기존 유지)
    @POST("api/v1/review/{houseId}/measure")
    suspend fun saveMeasurement(
        @Path("houseId") houseId: Long,
        @Body measurements: List<MeasurementDto>
    ): Response<Void>

//    // 방향 분석 (기존 유지 - 사진에 없으므로)
//    @GET("api/v1/review/card/{searchCardId}")
//    suspend fun getAnalysisDirection(@Path("searchCardId") cardId : String) : Response<List<DirectionAnalysisResponse>>

    // 방향별 특징, 장/단점 정보 조회
    @GET("api/v1/analysis/direction/{searchCardId}")
    suspend fun getAnalysisDirection(
        @Path("searchCardId") searchCardId: String
    ): Response<List<AnalysisDirectionResponse>>

    // 집 상세 조회 (기존 유지)
//    @GET("api/v1/review/{houseId}/card")
//    suspend fun getHouseCardDetail(@Path("houseId") houseId : Long): Response<List<HouseMeasurementResponse>>

    // [변경] 채광 조회 (Response Model에 label 추가됨)
    @GET("api/v1/review/score/{cardId}")
    suspend fun getLightScoreList(@Path("cardId") cardId: String): Response<List<LightScoreResponse>>

    // 카드 주소 목록 (기존 유지)
    @GET("api/v1/review/card/{searchCardId}/addresses")
    suspend fun getCardAddresses(@Path("searchCardId") cardId: String): Response<List<CardAddressResponse>>


    // 3. 하우스 측정 데이터 저장 (POST - 사진 2)
    // multipart/form-data 처리를 위해 @Multipart 어노테이션 사용
    @Multipart
    @POST("api/v1/review/{houseId}/measure")
    suspend fun saveMeasurement(
        @Path("houseId") houseId: Long,
        @Part("requests") requests: RequestBody,     // JSON 문자열로 변환하여 전송
        @Part images: List<MultipartBody.Part>?      // 파일 리스트
    ): Response<Unit>

    // 4. 하우스 측정 데이터 업데이트 (PATCH - 사진 3)
    @Multipart
    @PATCH("api/v1/review/{houseId}/measure")
    suspend fun updateMeasurement(
        @Path("houseId") houseId: Long,
        @Part("requests") requests: RequestBody,
        @Part images: List<MultipartBody.Part>?
    ): Response<Unit>

    // 1. 방향 데이터 저장
    @POST("api/v1/review/{houseId}/measure/direction")
    suspend fun saveDirectionMeasurement(
        @Path("houseId") houseId: Long,
        @Body request: DirectionRequest
    ): Response<Unit>

    // 2. 채광 데이터 저장
    @POST("api/v1/review/{houseId}/measure/light")
    suspend fun saveLightMeasurement(
        @Path("houseId") houseId: Long,
        @Body request: LightRequest
    ): Response<Unit>

    // 3. 이미지 업로드
    @Multipart
    @POST("api/v1/review/{houseId}/images")
    suspend fun uploadHouseImages(
        @Path("houseId") houseId: Long,
        @Part images: List<MultipartBody.Part>
    ): Response<Unit>

    // 4. 하우스 측정 카드 데이터 상세 조회
    @GET("api/v1/review/{houseId}/card")
    suspend fun getHouseCardDetail(
        @Path("houseId") houseId: Long
    ): Response<HouseCardDetailResponse>

    @GET("api/v1/review/card/{searchCardId}/houses")
    suspend fun getCardHouseListRaw(@Path("searchCardId") searchCardId: String): Response<ResponseBody>

    @GET("/api/v1/review/card/{searchCardId}")
    suspend fun getCardDirectionGroups(
        @Path("searchCardId") searchCardId: String
    ): Response<List<HouseDirectionGroupResponse>>

    @POST("/api/v1/review/{houseId}/measure/direction")
    suspend fun saveDirection(
        @Path("houseId") houseId: Long,
        @Body request: DirectionRequest
    ): Response<Unit> // 반환값이 없는 경우 Unit

    @POST("/api/v1/review/{houseId}/measure/light")
    suspend fun saveLight(
        @Path("houseId") houseId: Long,
        @Body request: LightRequest
    ): Response<Unit>

    @GET("/api/v1/analysis/safety/{searchCardId}")
    suspend fun getAnalysisSafety(@Path("searchCardId") searchCardId: String): retrofit2.Response<List<SafetyResponse>>
}