package com.keder.zply

import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.Path

interface AuthService {
    // 로그인
    @POST("api/v1/auth/google")
    suspend fun googleLogin(@Body request: GoogleLoginRequest) : Response<LoginResponse>

    // 유저 정보 조회
    @GET("api/v1/users/name")
    suspend fun getUserName(): Response<UserNameResponse>

    // 탐색 카드 전체 조회
    @GET("api/v1/review/card")
    suspend fun getReviewCards() : Response<List<ReviewCardResponse>>

    // 카드 생성 (성공 시 CardId String 반환)
    @POST("api/v1/review/card")
    suspend fun createReviewCard(@Body request: ReviewCardRequest) : Response<String>

    // 집 목록 조회
    @GET("api/v1/review/card/{cardId}/houses")
    suspend fun getCardHouseList(@Path("cardId") cardId: String): Response<List<HouseListResponse>>

    // 거리 정보 (직장 주소 포함)
    @GET("api/v1/analysis/distance/{searchCardId}")
    suspend fun getAnalysisDistance(@Path("searchCardId") searchCardId : String) : Response<DistanceResponse>

    // 소음 정보
    @GET("api/v1/analysis/score/{searchCardId}")
    suspend fun getAnalysisScore(@Path("searchCardId") searchCardId : String) : Response<List<NoiseScoreResponse>>

    // ★ 측정 데이터 전송 (List<MeasurementDto> 사용)
    @POST("api/v1/review/{houseId}/measure")
    suspend fun saveMeasurement(
        @Path("houseId") houseId: Long,
        @Body measurements: List<MeasurementDto>
    ): Response<Void>

    // 방향 분석
    @GET("api/v1/review/card/{searchCardId}")
    suspend fun getAnalysisDirection(@Path("searchCardId") cardId : String) : Response<List<DirectionAnalysisResponse>>

    // 집 상세 조회 (측정값/점수 확인)
    @GET("api/v1/review/{houseId}/card")
    suspend fun getHouseCardDetail(@Path("houseId") houseId : Long): Response<List<HouseMeasurementResponse>>

    // 채광 조회
    @GET("api/v1/review/score/{cardId}")
    suspend fun getLightScoreList(@Path("cardId") cardId: String): Response<List<LightScoreResponse>>

    // [신규] 특정 하우스 방향 정보 조회
    @GET("api/v1/review/house/{houseId}")
    suspend fun getHouseDirectionDetail(@Path("houseId") houseId: Long): Response<List<HouseDirectionDetailResponse>>

    @GET("api/v1/review/card/{searchCardId}/addresses")
    suspend fun getCardAddresses(@Path("searchCardId") cardId: String): Response<List<CardAddressResponse>>
}