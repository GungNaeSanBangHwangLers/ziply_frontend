package com.keder.zply

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.keder.zply.databinding.ItemIngBottomDirectionBinding

class DirectionAdapter(
    private val items : MutableList<Measurement>,
    private val onItemRemove : (Int) -> Unit
) : RecyclerView.Adapter<DirectionAdapter.ViewHolder>(){
    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): ViewHolder {
        val binding = ItemIngBottomDirectionBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: ViewHolder,
        position: Int
    ) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    inner class ViewHolder(val binding: ItemIngBottomDirectionBinding) : RecyclerView.ViewHolder(binding.root){
        fun bind (item: Measurement, position:Int){
            binding.numTv.text = "${position + 1}차 측정"
            binding.directionTv.text = "${item.azimuth}°"
            binding.removeIv.setOnClickListener {
                onItemRemove(position)
            }
        }
    }

}