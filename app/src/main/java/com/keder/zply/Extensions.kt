package com.keder.zply

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment

// 1. Context 확장 함수 (액티비티에서 사용)
fun Context.showCustomToast(msg: String) {
    val inflater = LayoutInflater.from(this)
    val layout = inflater.inflate(R.layout.view_custom_toast, null)

    val textView = layout.findViewById<TextView>(R.id.tvToastMessage)
    textView.text = msg

    val toast = Toast(this)
    toast.duration = Toast.LENGTH_SHORT
    toast.view = layout

    toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 250)
    toast.show()
}

// 2. Fragment 확장 함수 (프래그먼트에서 바로 사용하기 위함)
fun Fragment.showCustomToast(msg: String) {
    // 프래그먼트가 attach된 Context를 가져와서 위의 함수를 호출
    context?.showCustomToast(msg)
}

fun Context.showCustomToast2(msg: String) {
    val inflater = LayoutInflater.from(this)
    val layout = inflater.inflate(R.layout.view_success_toast, null)

    val textView = layout.findViewById<TextView>(R.id.tvToastMessage)
    textView.text = msg

    val toast = Toast(this)
    toast.duration = Toast.LENGTH_SHORT
    toast.view = layout

    toast.setGravity(Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL, 0, 250)
    toast.show()
}

fun Fragment.showCustomToast2(msg: String) {
    // 프래그먼트가 attach된 Context를 가져와서 위의 함수를 호출
    context?.showCustomToast2(msg)
}