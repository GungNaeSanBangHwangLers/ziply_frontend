package com.keder.zply

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import com.keder.zply.databinding.FragmentAfterLightBinding

class AfterLightFragment : Fragment() {

    private var _binding: FragmentAfterLightBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAfterLightBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as? AfterExploreActivity
        val session = activity?.currentSession
        val safeList = session?.scheduleList ?: emptyList()
        val sortedList = safeList.sortedBy { it.time }

        // [체크] 측정된 채광 데이터가 하나라도 있는지 확인
        val hasMeasuredData = sortedList.any { it.measuredLight > 0 }

        if (hasMeasuredData) {
            // 1. 데이터 있음
            binding.beforeGraphRv.visibility = View.VISIBLE
            binding.afterLightTv.visibility = View.VISIBLE
            binding.afterLightDesTv.visibility = View.VISIBLE
            binding.emptyStateTv.visibility = View.GONE

            // 텍스트 업데이트
            val bestLightItem = sortedList.maxByOrNull { it.measuredLight }
            val bestRank = if (bestLightItem != null) ('A'.code + sortedList.indexOf(bestLightItem)).toChar().toString() else "-"

            val fullText = "채광은 $bestRank 가 \n가장 좋네요!"
            val spannable = SpannableString(fullText)
            val start = fullText.indexOf(bestRank)
            if (start != -1) {
                spannable.setSpan(
                    ForegroundColorSpan(ContextCompat.getColor(requireContext(), R.color.brand_500)),
                    start, start + bestRank.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            binding.afterLightTv.text = spannable

            // 어댑터 연결 (작성해주신 LightGraphAdapter 사용)
            val adapter = LightGraphAdapter(sortedList)
            binding.beforeGraphRv.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            binding.beforeGraphRv.adapter = adapter

        } else {
            // 2. 데이터 없음 -> 안내 멘트
            binding.graphContainer.visibility = View.GONE
            binding.afterLightTv.visibility = View.GONE
            binding.afterLightDesTv.visibility = View.GONE
            binding.emptyStateTv.visibility = View.VISIBLE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}