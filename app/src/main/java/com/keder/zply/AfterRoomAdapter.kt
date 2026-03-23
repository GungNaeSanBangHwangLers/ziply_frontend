package com.keder.zply

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.keder.zply.databinding.ItemAfterDirectionRoomBinding

class AfterRoomAdapter(
    private val windows: List<DirectionWindowResponse>,
    private val onRoomSelected: (DirectionWindowResponse) -> Unit
) : RecyclerView.Adapter<AfterRoomAdapter.ViewHolder>() {

    private var selectedPosition = 0

    inner class ViewHolder(val binding: ItemAfterDirectionRoomBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(window: DirectionWindowResponse, position: Int) {
            // 서버가 준 방 이름 (예: "거실 정면")
            binding.itemRoomNameTv.text = window.windowLocation

            val context = binding.root.context

            if (position == selectedPosition) {
                binding.itemRoomNameTv.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.brand_800))
                binding.itemRoomNameTv.setTextColor(ContextCompat.getColor(context, R.color.white))
            } else {
                binding.itemRoomNameTv.backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, R.color.gray_700))
                binding.itemRoomNameTv.setTextColor(ContextCompat.getColor(context, R.color.gray_400))
            }

            binding.root.setOnClickListener {
                val previousPosition = selectedPosition
                selectedPosition = position
                notifyItemChanged(previousPosition)
                notifyItemChanged(selectedPosition)
                onRoomSelected(window)
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemAfterDirectionRoomBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(windows[position], position)
    }

    override fun getItemCount(): Int = windows.size
}