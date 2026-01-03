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
import com.keder.zply.databinding.FragmentAfterLengthBinding

class AfterLengthFragment : Fragment() {

    private var _binding: FragmentAfterLengthBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAfterLengthBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activity = requireActivity() as? AfterExploreActivity
        val session = activity?.currentSession

        // 데이터가 없어도 죽지 않고 빈 리스트로 처리
        val safeList = session?.scheduleList ?: emptyList()
        val sortedList = safeList.sortedBy { it.time }

        if (sortedList.isNotEmpty()) {
            binding.afterLengthRankRv.adapter = LengthRankAdapter(sortedList)
            binding.afterLengthRankRv.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
            updateShortestText(sortedList)
        } else {
            // 리스트가 비어있다면 처리 (필요시 안내 텍스트 표시)
            // binding.afterLengthRankRv.visibility = View.GONE
            // binding.emptyView.visibility = View.VISIBLE
        }
    }

    private fun updateShortestText(list: List<ScheduleItem>) {
        if (list.isNotEmpty()) {
            val shortestRank = "A"
            val fullText = "직주거리는 $shortestRank 가 \n가장 짧아요"
            val spannable = SpannableString(fullText)

            val startIndex = fullText.indexOf(shortestRank)
            if (startIndex != -1) {
                val endIndex = startIndex + shortestRank.length
                val blueColor = ContextCompat.getColor(requireContext(), R.color.brand_500)

                spannable.setSpan(
                    ForegroundColorSpan(blueColor),
                    startIndex,
                    endIndex,
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            binding.afterLengthRankTv.text = spannable
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}