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

        loadAddressFromDraft()

        setFragmentResultListener("request_address"){ _, bundle ->
            val address = bundle.getString("address_data")
            if(!address.isNullOrEmpty()){
                updateAddressUI(address)
            }
        }

        binding.addressInputFl.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.main_frm, SearchAddressFragment())
                .addToBackStack(null)
                .commit()
        }

        binding.addressInputTv.setOnClickListener {
            binding.addressInputFl.performClick()
        }

        binding.backBtnIv.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        binding.addressNextBtnMb.setOnClickListener {
            // ★ 핵심 수정: 변수에 의존하지 않고, 화면에 보이는 텍스트를 직접 읽어서 검사합니다.
            val currentText = binding.addressInputTv.text.toString()

            // "학교/직장 주소"는 기본 힌트 텍스트이므로 이것과 같으면 입력 안 한 것으로 간주
            if(currentText.isBlank() || currentText == "학교/직장 주소"){
                showCustomToast("전체 내용을 입력해주세요")
            } else {
                saveAddressToDraft(currentText)

                parentFragmentManager.beginTransaction()
                    .replace(R.id.main_frm, ExploreScheduleFragment())
                    .addToBackStack(null)
                    .commit()
            }
        }
    }

    private fun loadAddressFromDraft() {
        val sharedPref = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val savedAddress = sharedPref.getString(KEY_ADDRESS, "")

        if (!savedAddress.isNullOrEmpty()) {
            updateAddressUI(savedAddress)
        }
    }

    private fun updateAddressUI(address: String) {
        binding.addressInputTv.text = address
        binding.addressInputTv.setTextColor(requireContext().getColor(android.R.color.white))
        applyEndFadeColoring()
        saveAddressToDraft(address)
    }

    private fun saveAddressToDraft(address: String) {
        val sharedPref = requireContext().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        sharedPref.edit().putString(KEY_ADDRESS, address).apply()
    }

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