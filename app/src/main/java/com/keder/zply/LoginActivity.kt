package com.keder.zply

import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.GetCredentialResponse
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.keder.zply.databinding.ActivityLoginBinding
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {
    private  lateinit var binding : ActivityLoginBinding
    private lateinit var credentialManager: CredentialManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Credential Manager 초기화
        credentialManager = CredentialManager.create(this)

        binding.loginBtnMb.setOnClickListener {
            showLoadingState(true)
            signInWithGoogle()
        }
    }

    private fun signInWithGoogle(){
        // 구글 로그인 옵션 설정
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId(getString(R.string.web_client_id))
            .setAutoSelectEnabled(true)
            .build()

        // 요청 객체 생성
        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        lifecycleScope.launch {
            try{
                // 로그인 창 띄우고 결과 기다림
                val result : GetCredentialResponse = credentialManager.getCredential(
                    request = request,
                    context = this@LoginActivity
                )
                handleSignIn(result)
            }catch (e : GetCredentialException){
                Log.e("Login", "로그인 실패 또는 취소 : ${e.message}")
                showCustomToast("로그인에 실패했어요, 다시 시도해주세요.")
            }
        }
    }

    private fun handleSignIn(result: GetCredentialResponse){
        val credential = result.credential
        if(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL){
            try{
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                sendToenToBackend(idToken)
            }catch(e: Exception){
                showCustomToast("인증 정보를 읽어오는데 실패했습니다.")
                showLoadingState(false)
            }
        }else{
            showCustomToast("알 수 없는 인증 방식입니다.");
            showLoadingState(false)
        }
    }

    private fun sendToenToBackend(idToken: String){
        /*
        val request = GoogleLoginRequest(idToken = idToken)
        RetrofitClient.instance.googleLogin(request).enqueue(object : Callback<LoginResponse> {
            override fun onResponse(call: Call<LoginResponse>, response: Response<LoginResponse>) {
                if (response.isSuccessful && response.body() != null) {
                    val loginResponse = response.body()!!
                    val accessToken = loginResponse.accessToken
                    val refreshToken = loginResponse.refreshToken

                    Log.d("Login", "백엔드 로그인 성공! Token: $accessToken")

                    // TODO: 여기서 accessToken을 SharedPreferences에 저장

                    onLoginSuccess(accessToken)
                } else {
                    Log.e("Login", "백엔드 에러: ${response.code()} ${response.errorBody()?.string()}")
                    showToast("서버 로그장에 실패했습니다.")
                    showLoadingState(false)
                }
            }

            override fun onFailure(call: Call<LoginResponse>, t: Throwable) {
                Log.e("Login", "네트워크 오류", t)
                showToast("서버와 연결할 수 없습니다.")
                showLoadingState(false)
            }
        })
        */
        Handler(mainLooper).postDelayed({
            onLoginSuccess("DUMMY_TOKEN")
        }, 1000)
    }

    private fun onLoginSuccess(appToken: String){
        val intent = Intent(this, MainActivity::class.java)
        intent.putExtra("ACCESS_TOKEN", appToken) // 필요하면 토큰 넘김
        startActivity(intent)
        finish()
    }

    private fun showLoadingState(isLoading : Boolean){
        if(isLoading){
            binding.loginMainCv.visibility = View.GONE
            binding.loginProgressBar.visibility = View.VISIBLE
        }else{
            binding.loginMainCv.visibility = View.VISIBLE
            binding.loginProgressBar.visibility = View.GONE
        }
    }

}