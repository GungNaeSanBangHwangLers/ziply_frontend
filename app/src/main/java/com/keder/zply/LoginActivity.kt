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
    private lateinit var tokenManager : TokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Token Manager 초기화
        tokenManager = TokenManager(this)

        if (tokenManager.getAccessToken() != null) {
            // 이미 로그인된 상태이므로 메인으로 바로 이동
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK // 뒤로가기 방지
            startActivity(intent)
            finish() // 로그인 액티비티 즉시 종료
            return // 아래의 setContentView 등을 실행하지 않도록 리턴
        }

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
                showLoadingState(false)
            }
        }
    }

    private fun handleSignIn(result: GetCredentialResponse){
        val credential = result.credential
        if(credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL){
            try{
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken
                sendTokenToBackend(idToken)
            }catch(e: Exception){
                showCustomToast("인증 정보를 읽어오는데 실패했습니다.")
                showLoadingState(false)
            }
        }else{
            showCustomToast("알 수 없는 인증 방식입니다.");
            showLoadingState(false)
        }
    }

    private fun sendTokenToBackend(idToken: String){
        lifecycleScope.launch {
            try {
                val request = GoogleLoginRequest(idToken = idToken)
                val response = RetrofitClient.getInstance(this@LoginActivity).googleLogin(request)
                if(response.isSuccessful && response.body() != null){
                    val loginResponse = response.body()!!
                    val accessToken = loginResponse.accessToken
                    val refreshToken = loginResponse.refreshToken

                    Log.d("Login", "백엔드 로그인 성공!")

                    tokenManager.saveTokens(accessToken, refreshToken)
                    onLoginSuccess()
                }else{
                    Log.e("Login", "백엔드 에러: ${response.code()} ${response.errorBody()?.string()}")
                    showCustomToast("로그인에 실패했어요. 다시 시도해주세요")
                    showLoadingState(false)
                }
            }catch (e : Exception){
                Log.e("Login", "네트워크 오류", e)
                showCustomToast("서버와 연결할 수 없습니다.")
                showLoadingState(false)
            }
        }
    }

    private fun onLoginSuccess(){
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
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