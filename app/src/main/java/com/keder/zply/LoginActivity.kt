package com.keder.zply

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
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
    private lateinit var binding : ActivityLoginBinding
    private lateinit var credentialManager: CredentialManager
    private lateinit var tokenManager : TokenManager

    private val TAG = "LoginDebug"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Log.d(TAG, "1. onCreate 시작 - TokenManager 초기화")
        tokenManager = TokenManager(this)

        // ==============================================================
        // ★ [자동 로그인] 토큰이 존재하면 화면을 그리기 전에 바로 메인으로 직행!
        // ==============================================================
        val existingToken = tokenManager.getAccessToken()
        if (!existingToken.isNullOrEmpty()) {
            Log.d(TAG, "✅ 유효한 토큰 발견! 자동 로그인 진행")
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
            return // 여기서 함수를 종료해야 밑의 화면 그리는 코드가 실행되지 않습니다.
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        Log.d(TAG, "2. CredentialManager 초기화")
        credentialManager = CredentialManager.create(this)

        binding.loginBtnMb.setOnClickListener {
            Log.d(TAG, "3. 구글 로그인 버튼 클릭됨")
            showLoadingState(true)
            signInWithGoogle()
        }
    }

    private fun signInWithGoogle() {
        Log.d(TAG, "4. signInWithGoogle 호출됨")

        try {
            val webClientId = getString(R.string.web_client_id)
            Log.d(TAG, "5. Web Client ID 확인: $webClientId")

            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(false)
                .setServerClientId(webClientId)
                .setAutoSelectEnabled(true)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            lifecycleScope.launch {
                try {
                    Log.d(TAG, "6. credentialManager.getCredential 요청 시작")
                    val result: GetCredentialResponse = credentialManager.getCredential(
                        request = request,
                        context = this@LoginActivity
                    )
                    Log.d(TAG, "7. 구글 로그인 성공! 핸들러로 전달")
                    handleSignIn(result)

                } catch (e: GetCredentialException) {
                    Log.e(TAG, "❌ [구글 로그인 실패] 에러 타입: ${e.javaClass.simpleName}")
                    Log.e(TAG, "❌ [구글 로그인 실패] 상세 메시지: ${e.message}", e)
                    showCustomToast("로그인에 실패했어요, 다시 시도해주세요.")
                    showLoadingState(false)
                } catch (e: Exception) {
                    Log.e(TAG, "❌ [알 수 없는 에러] ${e.message}", e)
                    showLoadingState(false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ [요청 객체 생성 에러] web_client_id 확인 필요: ${e.message}")
            showLoadingState(false)
        }
    }

    private fun handleSignIn(result: GetCredentialResponse) {
        Log.d(TAG, "8. handleSignIn 호출됨")
        val credential = result.credential

        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                Log.d(TAG, "9. GoogleIdTokenCredential 변환 시도")
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val idToken = googleIdTokenCredential.idToken

                Log.d(TAG, "10. ID 토큰 추출 성공! 백엔드로 전송 시작")
                sendTokenToBackend(idToken)

            } catch (e: Exception) {
                Log.e(TAG, "❌ [토큰 추출 실패] ${e.message}", e)
                showCustomToast("인증 정보를 읽어오는데 실패했습니다.")
                showLoadingState(false)
            }
        } else {
            Log.e(TAG, "❌ [잘못된 인증 방식] type: ${credential.type}")
            showCustomToast("알 수 없는 인증 방식입니다.")
            showLoadingState(false)
        }
    }

    private fun sendTokenToBackend(idToken: String) {
        Log.d(TAG, "11. 백엔드 API 통신 시작")
        lifecycleScope.launch {
            try {
                val request = GoogleLoginRequest(idToken = idToken)
                val response = RetrofitClient.getInstance(this@LoginActivity).googleLogin(request)

                if (response.isSuccessful && response.body() != null) {
                    val loginResponse = response.body()!!
                    val accessToken = loginResponse.accessToken
                    val refreshToken = loginResponse.refreshToken

                    Log.d(TAG, "✅ 12. 백엔드 로그인 완전 성공! 토큰 저장")
                    tokenManager.saveTokens(accessToken, refreshToken)
                    onLoginSuccess()
                } else {
                    Log.e(TAG, "❌ [백엔드 에러] HTTP 코드: ${response.code()}")
                    showCustomToast("로그인에 실패했어요. 다시 시도해주세요")
                    showLoadingState(false)
                }
            } catch (e: Exception) {
                Log.e(TAG, "❌ [네트워크 오류] ${e.message}", e)
                showCustomToast("서버와 연결할 수 없습니다.")
                showLoadingState(false)
            }
        }
    }

    private fun onLoginSuccess() {
        Log.d(TAG, "13. 로그인 성공 처리 완료, 메인 액티비티로 이동")
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun showLoadingState(isLoading: Boolean) {
        if (isLoading) {
            binding.loginMainCv.visibility = View.GONE
            binding.loginProgressBar.visibility = View.VISIBLE
        } else {
            binding.loginMainCv.visibility = View.VISIBLE
            binding.loginProgressBar.visibility = View.GONE
        }
    }

    // (참고) showCustomToast 함수가 없다면 기본 Toast로 대체하거나, Activity에 추가해 주세요.
    private fun showCustomToast(message: String) {
        android.widget.Toast.makeText(this, message, android.widget.Toast.LENGTH_SHORT).show()
    }
}