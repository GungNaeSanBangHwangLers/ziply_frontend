package com.keder.zply

import com.google.gson.annotations.SerializedName

// 백엔드로 보낼 요청 데이터
data class GoogleLoginRequest(
    @SerializedName("idToken")
    val idToken: String
)

// 백엔드에서 받을 응답 데이터
data class LoginResponse(
    @SerializedName("accessToken")
    val accessToken: String,
    @SerializedName("refreshToken")
    val refreshToken: String
)