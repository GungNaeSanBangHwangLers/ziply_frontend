package com.keder.zply

import android.app.Activity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment

// Activity용 확장 함수
fun Activity.showErrorOverlay(retryAction: () -> Unit) {
    // 안드로이드 기본 최상위 뷰를 가져옵니다.
    val rootView = findViewById<ViewGroup>(android.R.id.content)

    // 이미 에러 화면이 떠 있다면 중복으로 띄우지 않음
    if (rootView.findViewById<View>(R.id.error_main_ll) != null) return

    // 제공해주신 에러 레이아웃을 가져와서 뷰로 만듭니다.
    val errorView = layoutInflater.inflate(R.layout.fragment_error, rootView, false)
    val retryBtn = errorView.findViewById<Button>(R.id.error_main_reload_bt)

    // 새로고침 버튼 클릭 시
    retryBtn.setOnClickListener {
        rootView.removeView(errorView) // 에러 화면 제거
        retryAction.invoke()           // 전달받은 새로고침 로직 실행
    }

    // 최상단 뷰에 에러 화면 추가
    rootView.addView(errorView)
}

// Fragment용 확장 함수 (Activity의 함수를 호출하도록 연결)
fun Fragment.showErrorOverlay(retryAction: () -> Unit) {
    requireActivity().showErrorOverlay(retryAction)
}