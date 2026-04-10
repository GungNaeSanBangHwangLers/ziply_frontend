package com.keder.zply

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide // ★ 필수 임포트
import com.keder.zply.databinding.ItemIngImageBinding
import java.io.File

class IngImageAdapter(private var images: List<String>,
                      private val onImageClick: (Int) -> Unit) : RecyclerView.Adapter<IngImageAdapter.ViewHolder>() {

    fun updateImages(newImages: List<String>) {
        this.images = newImages
        notifyDataSetChanged()
    }

    inner class ViewHolder(val binding: ItemIngImageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(imagePath: String) {
            // 서버(http)인지 기기 로컬 사진인지 구분하여 안전하게 객체화
            val imageModel = if (imagePath.startsWith("http")) imagePath else File(imagePath)

            // Glide로 뷰에 이미지 꽂기
            Glide.with(binding.root.context)
                .load(imageModel)
                .centerCrop()
                .into(binding.itemImgIv)

            binding.root.setOnClickListener {
                onImageClick(bindingAdapterPosition)
            }
        }
    }


    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemIngImageBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(images[position])
    }

    override fun getItemCount(): Int = images.size
}