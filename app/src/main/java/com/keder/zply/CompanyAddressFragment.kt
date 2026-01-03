package com.keder.zply

import android.content.Context
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.setFragmentResultListener
import com.keder.zply.databinding.FragmentCompanyAddressBinding

class CompanyAddressFragment : Fragment() {
    private lateinit var binding: FragmentCompanyAddressBinding

    private var selectedAddress : String? = null

    private val PREF_NAME = "schedule_draft_pref"
    private val KEY_ADDRESS = "draft_company_address"



    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentCompanyAddressBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)


        // 검색 결과 수신 (여기는 유지해야 검색하고 돌아왔을 때 값이 찍힘)
        setFragmentResultListener("request_address"){ _, bundle ->
            val address = bundle.getString("address_data")
            if(!address.isNullOrEmpty()){
                updateAddressUI(address)
            }
        }

        binding.addressInputTv.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.main_frm, SearchAddressFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.backBtnIv.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.addressNextBtnMb.setOnClickListener {
            if(selectedAddress.isNullOrEmpty()){
                showCustomToast("주소를 입력해주세요")
            }else{
                // [수정] 임시 저장소에 직장 주소 저장
                saveAddressToDraft(selectedAddress!!)

                parentFragmentManager.beginTransaction()
                    .replace(R.id.main_frm, ExploreScheduleFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    // UI 업데이트 (검색 결과용으로 필요)
    private fun updateAddressUI(address: String) {
        selectedAddress = address
        binding.addressInputTv.text = address
        binding.addressInputTv.setTextColor(requireContext().getColor(android.R.color.white))
        applyEndFadeColoring()
    }

    private fun saveAddressToDraft(address: String) {
        val sharedPref = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sharedPref.edit().putString(KEY_ADDRESS, address).apply()
    }

    // ★ loadAddressFromPref() 함수는 삭제함

    private fun applyEndFadeColoring() {
        val textView = binding.addressInputTv
        textView.viewTreeObserver.addOnGlobalLayoutListener(object :
            ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                textView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val layout = textView.layout ?: return
                val visibleEnd = layout.getEllipsisStart(0)
                if (visibleEnd <= 0) return

                val fullText = textView.text.toString()
                val span = SpannableString(fullText)
                val lastVisibleIndex = visibleEnd - 1
                if (lastVisibleIndex < 0 || lastVisibleIndex >= fullText.length) return

                span.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.gray_300)),
                    lastVisibleIndex,
                    lastVisibleIndex + 1,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
                textView.text = span
            }
        })
    }
}