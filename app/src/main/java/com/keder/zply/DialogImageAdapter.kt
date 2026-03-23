package com.keder.zply

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import java.io.File

class DialogImageAdapter(private val images: List<String>) : RecyclerView.Adapter<DialogImageAdapter.ImageViewHolder>() {

    inner class ImageViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val imageView = ImageView(parent.context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        return ImageViewHolder(imageView)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val imagePath = images[position]
        // http면 URL로, 아니면 File 객체로 로컬 이미지 로드
        val imageModel = if (imagePath.startsWith("http")) imagePath else File(imagePath)

        Glide.with(holder.imageView.context)
            .load(imageModel)
            .centerCrop()
            .into(holder.imageView)
    }

    override fun getItemCount(): Int = images.size
}