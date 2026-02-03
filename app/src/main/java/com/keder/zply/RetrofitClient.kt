package com.keder.zply

import android.content.Context
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

// 1. 토큰을 헤더에 넣는 인터셉터 (Interceptor)
class AuthInterceptor(private val context: Context) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        // 저장된 토큰 가져오기
        val tokenManager = TokenManager(context)
        val accessToken = tokenManager.getAccessToken() // TokenManager에 getAccessToken 구현 필요

        // 토큰이 없으면 그냥 보냄 (로그인 요청 등)
        if (accessToken.isNullOrEmpty()) {
            return chain.proceed(originalRequest)
        }

        // 토큰이 있으면 헤더에 추가
        val newRequest = originalRequest.newBuilder()
            .addHeader("Authorization", "Bearer $accessToken")
            .build()

        return chain.proceed(newRequest)
    }
}

// 2. Retrofit 클라이언트 객체
object RetrofitClient {
    private const val BASE_URL = "http://20.41.104.242:8000/"

    private var retrofit: Retrofit? = null

    // Context를 받아야 TokenManager를 만들 수 있음
    fun getInstance(context: Context): AuthService {
        if (retrofit == null) {

            // 1. 로깅 인터셉터 생성 (로그 레벨 설정)
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                // LEVEL.BODY로 설정해야 주고받는 JSON 내용을 다 볼 수 있습니다.
                level = HttpLoggingInterceptor.Level.BODY
            }

            // 2. OkHttpClient에 추가
            val client = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(context)) // 기존 토큰 인터셉터
                .addInterceptor(loggingInterceptor)       // ★ [추가] 로깅 인터셉터 연결
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client) // 만든 클라이언트 장착
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
        return retrofit!!.create(AuthService::class.java)
    }}