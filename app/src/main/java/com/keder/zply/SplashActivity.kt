package com.keder.zply

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 스플래시 화면 레이아웃 (로고 등)이 있다면 설정
        // setContentView(R.layout.activity_splash)

        // 2초 정도 대기 후 로그인 상태 체크 (UX적으로 로고를 보여주기 위함)
        Handler(Looper.getMainLooper()).postDelayed({
            checkLoginStatus()
        }, 2000) // 2000ms = 2초
    }

    private fun checkLoginStatus() {
        val tokenManager = TokenManager(this)
        val accessToken = tokenManager.getAccessToken()

        if (!accessToken.isNullOrEmpty()) {
            // 1. 토큰이 있다 -> 메인 화면으로 이동
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
        } else {
            // 2. 토큰이 없다 -> 로그인 화면으로 이동
            val intent = Intent(this, LoginActivity::class.java)
            startActivity(intent)
        }

        // 스플래시 액티비티 종료 (뒤로가기 방지)
        finish()
    }
}