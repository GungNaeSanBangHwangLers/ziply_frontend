package com.keder.zply

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.keder.zply.databinding.ItemIngImageBinding

class IngImageAdapter(private val images: List<String>) : RecyclerView.Adapter<IngImageAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemIngImageBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(imagePath: String) {
            // Glide.with(binding.root).load(imagePath).into(binding.itemImgIv)
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