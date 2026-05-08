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
class AuthInterceptor(context: Context) : Interceptor {
    private val tokenManager = TokenManager(context.applicationContext)

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()
        val accessToken = tokenManager.getAccessToken()

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
    private const val BASE_URL = "http://40.82.136.28:8000/"

    private var retrofit: Retrofit? = null
    private var service: AuthService? = null

    fun getInstance(context: Context): AuthService {
        if (retrofit == null) {
            val loggingInterceptor = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BODY
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(AuthInterceptor(context.applicationContext))
                .addInterceptor(loggingInterceptor)
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build()

            retrofit = Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()

            service = retrofit!!.create(AuthService::class.java)
        }
        return service!!
    }}