package com.keder.zply

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class HorizontalSpaceItemDecoration(private val horizontalSpaceWidth: Int) : RecyclerView.ItemDecoration() {

    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        // 현재 아이템의 위치를 가져옵니다.
        val position = parent.getChildAdapterPosition(view)
        // 전체 아이템 개수를 가져옵니다.
        val itemCount = parent.adapter?.itemCount ?: 0

        // 아이템 사이에만 간격을 주기 위해 오른쪽(right) 마진을 설정합니다.
        // 마지막 아이템(itemCount - 1)이 아닐 때만 오른쪽 마진을 줍니다.
        if (position != RecyclerView.NO_POSITION && position < itemCount - 1) {
            outRect.right = horizontalSpaceWidth
        }

        // 시작과 끝에는 마진이 필요 없으므로 outRect.left나 outRect.top/bottom은 설정하지 않습니다.
    }
}