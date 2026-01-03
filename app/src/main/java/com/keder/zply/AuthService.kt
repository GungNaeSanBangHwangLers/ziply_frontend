package com.keder.zply

import retrofit2.Call
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST

interface AuthService {
    // 로그인
    @POST("api/v1/auth/google")
    fun googleLogin(
        @Body request: GoogleLoginRequest
    ) : Call<LoginResponse>

    // 유저 정보 조회
    @GET("api/v1/users/me")
    fun getUserProfile(
        @Header("Authorization") accessToken: String
    ): Call<UserDto>
}