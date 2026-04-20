package com.keder.zply

import android.app.Application
import android.content.Intent
import kotlin.system.exitProcess

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 예기치 않은 앱 크래시가 발생했을 때 가로채는 로직
        Thread.setDefaultUncaughtExceptionHandler { _, _ ->
            val intent = Intent(applicationContext, CrashErrorActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            }
            startActivity(intent)

            // 기존의 오류 난 프로세스는 안전하게 죽입니다.
            android.os.Process.killProcess(android.os.Process.myPid())
            exitProcess(2)
        }
    }
}