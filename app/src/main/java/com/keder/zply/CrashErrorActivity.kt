package com.keder.zply

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity

class CrashErrorActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.fragment_error) // 제공해주신 레이아웃 사용

        findViewById<Button>(R.id.error_main_reload_bt).setOnClickListener {
            // 앱이 죽었던 상태이므로 메인 화면으로 앱을 아예 새로 시작합니다.
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }
}