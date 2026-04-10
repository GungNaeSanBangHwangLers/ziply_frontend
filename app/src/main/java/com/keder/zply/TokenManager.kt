package com.keder.zply

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys

class TokenManager(context: Context) {
    private val masterKey = MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC)

    private val prefs = EncryptedSharedPreferences.create(
        "auth_prefs",
        masterKey,
        context,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveTokens(accessToken : String, refreshToken: String){
        prefs.edit().apply{
            putString("ACCESS_TOKEN", accessToken)
            putString("REFRESH_TOKEN", refreshToken)
            apply()
        }
    }

    fun getAccessToken(): String?{
        return prefs.getString("ACCESS_TOKEN", null)
    }
    fun getRefreshToken():String?{
        return prefs.getString("REFRESH_TOKEN", null)
    }
    fun clearTokens() {
        prefs.edit().clear().apply()
    }
}