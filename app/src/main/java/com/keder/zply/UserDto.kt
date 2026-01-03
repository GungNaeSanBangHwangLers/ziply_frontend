package com.keder.zply

import com.google.gson.annotations.SerializedName

data class UserDto(
    @SerializedName("id")
    val id: Long,

    @SerializedName("email")
    val email: String,

    @SerializedName("name")
    val name: String,

    @SerializedName("status")
    val status: String, // 예: "ACTIVE"

    @SerializedName("createdAt")
    val createdAt: String, // 나중에 LocalDateTime 등으로 변환해서 쓰면 좋습니다

    @SerializedName("updatedAt")
    val updatedAt: String
)