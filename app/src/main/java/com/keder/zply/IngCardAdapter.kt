package com.keder.zply

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.keder.zply.databinding.ItemIngCardBinding

class IngCardAdapter(
    private val items: MutableList<ScheduleItem>,
    private val onLightClick: (Int, ScheduleItem) -> Unit,
    private val onDirectionClick: (Int, ScheduleItem) -> Unit,
    private val onPhotoClick: (Int, ScheduleItem) -> Unit
) : RecyclerView.Adapter<IngCardAdapter.ViewHolder>() {

    inner class ViewHolder(val binding: ItemIngCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: ScheduleItem, position: Int) {
            val context = binding.root.context

            // 1. 라벨 색상 적용 (A~G)
            binding.ingCardRankTv.text = item.rankLabel
            setRankStyle(binding.ingCardRankTv, item.rankLabel)

            binding.ingCardAddressTv.text = item.address
            binding.ingCardDateTv.text = "${item.time} 탐색 예정"

            val brand600 = ContextCompat.getColor(context, R.color.brand_600)
            val gray400 = ContextCompat.getColor(context, R.color.gray_400)
            val gray300 = ContextCompat.getColor(context, R.color.gray_300)
            val white = ContextCompat.getColor(context, R.color.white)

            // ==========================================================
            // 2. 방향 측정 UI 상태 전환 (패딩 및 배경 꼬임 방지)
            // ==========================================================
            val padH = (16 * context.resources.displayMetrics.density).toInt()
            val padV = (7 * context.resources.displayMetrics.density).toInt()

            if (item.measuredRoomCount > 0) {
                binding.ingCardDirectionLl.setBackgroundResource(R.drawable.ing_complete_badge)
                binding.ingCardDirectionLl.setPadding(padH, padV, padH, padV)

                binding.ingCardDirectionIconIv.visibility = View.VISIBLE
                binding.ingCardDirectionBadgeTv.visibility = View.GONE

                binding.ingCardDirectionValueTv.visibility = View.VISIBLE
                binding.ingCardDirectionValueTv.text = "총 ${item.measuredRoomCount}개 방"

                binding.ingCardDirectionTv.text = "다시 측정"
                binding.ingCardDirectionTv.background = null
                binding.ingCardDirectionTv.setTextColor(gray300)
                binding.ingCardDirectionTv.setPadding(0, 0, 0, 0)
            } else {
                binding.ingCardDirectionLl.setBackgroundResource(R.drawable.black_badge)
                binding.ingCardDirectionLl.setPadding(padH, padV, padH, padV)

                binding.ingCardDirectionIconIv.visibility = View.GONE
                binding.ingCardDirectionBadgeTv.visibility = View.VISIBLE
                binding.ingCardDirectionBadgeTv.text = "방향 측정 미완료"

                binding.ingCardDirectionValueTv.visibility = View.GONE

                binding.ingCardDirectionTv.text = "측정하기"
                binding.ingCardDirectionTv.setBackgroundResource(R.drawable.good_badge)
                binding.ingCardDirectionTv.backgroundTintList = ColorStateList.valueOf(brand600)
                binding.ingCardDirectionTv.setTextColor(white)

                val btnPadH = (28 * context.resources.displayMetrics.density).toInt()
                val btnPadV = (8 * context.resources.displayMetrics.density).toInt()
                binding.ingCardDirectionTv.setPadding(btnPadH, btnPadV, btnPadH, btnPadV)
            }

            // ==========================================================
            // 3. 채광 측정 UI 상태 전환
            // ==========================================================
            if (item.measuredLightLux >= 0f) {
                binding.ingCardLightLl.setBackgroundResource(R.drawable.ing_complete_badge)
                binding.ingCardLightLl.setPadding(padH, padV, padH, padV)

                binding.ingCardLightIconIv.visibility = View.VISIBLE
                binding.ingCardLightBadgeTv.visibility = View.GONE

                binding.ingCardLightValueTv.visibility = View.VISIBLE
                binding.ingCardLightValueTv.text = "${item.measuredLightLux.toInt()} lux"

                binding.ingCardLightTv.text = "다시 측정"
                binding.ingCardLightTv.background = null
                binding.ingCardLightTv.setTextColor(gray300)
                binding.ingCardLightTv.setPadding(0, 0, 0, 0)
            } else {
                binding.ingCardLightLl.setBackgroundResource(R.drawable.black_badge)
                binding.ingCardLightLl.setPadding(padH, padV, padH, padV)

                binding.ingCardLightIconIv.visibility = View.GONE
                binding.ingCardLightBadgeTv.visibility = View.VISIBLE
                binding.ingCardLightBadgeTv.text = "채광 측정 미완료"

                binding.ingCardLightValueTv.visibility = View.GONE

                binding.ingCardLightTv.text = "측정하기"
                binding.ingCardLightTv.setBackgroundResource(R.drawable.good_badge)
                binding.ingCardLightTv.backgroundTintList = ColorStateList.valueOf(brand600)
                binding.ingCardLightTv.setTextColor(white)

                val btnPadH = (28 * context.resources.displayMetrics.density).toInt()
                val btnPadV = (8 * context.resources.displayMetrics.density).toInt()
                binding.ingCardLightTv.setPadding(btnPadH, btnPadV, btnPadH, btnPadV)
            }

            // ==========================================================
            // 4. 사진 렌더링 (Glide 적용 완료)
            // ==========================================================
            if (item.imageList.isNotEmpty()) {
                binding.ingCardImgRv.visibility = View.VISIBLE
                binding.ingCardImgRv.layoutManager = LinearLayoutManager(context, LinearLayoutManager.HORIZONTAL, false)
                binding.ingCardImgRv.adapter = InternalImageAdapter(item.imageList)
            } else {
                binding.ingCardImgRv.visibility = View.GONE
            }

            binding.ingCardDirectionLl.setOnClickListener { onDirectionClick(position, item) }
            binding.ingCardDirectionTv.setOnClickListener { onDirectionClick(position, item) }
            binding.ingCardLightLl.setOnClickListener { onLightClick(position, item) }
            binding.ingCardLightTv.setOnClickListener { onLightClick(position, item) }
            binding.ingImageBtn.setOnClickListener { onPhotoClick(position, item) }
        }
    }

    private fun setRankStyle(textView: TextView, rank: String) {
        val context = textView.context
        val brand100 = ContextCompat.getColor(context, R.color.brand_100)
        val brand800 = ContextCompat.getColor(context, R.color.brand_800)
        val brand400 = ContextCompat.getColor(context, R.color.brand_400)
        val white = ContextCompat.getColor(context, R.color.white)
        val brand700 = ContextCompat.getColor(context, R.color.brand_700)
        val brand950 = ContextCompat.getColor(context, R.color.brand_950)
        val black = ContextCompat.getColor(context, R.color.black)
        val gray400 = ContextCompat.getColor(context, R.color.gray_400)
        val gray700 = ContextCompat.getColor(context, R.color.gray_700)
        val gray200 = ContextCompat.getColor(context, R.color.gray_200)

        val rankChar = if (rank.isNotEmpty()) rank[0] else '?'
        when (rankChar) {
            'A' -> { textView.backgroundTintList = ColorStateList.valueOf(brand100); textView.setTextColor(brand800) }
            'B' -> { textView.backgroundTintList = ColorStateList.valueOf(brand400); textView.setTextColor(white) }
            'C' -> { textView.backgroundTintList = ColorStateList.valueOf(brand700); textView.setTextColor(white) }
            'D' -> { textView.backgroundTintList = ColorStateList.valueOf(brand950); textView.setTextColor(white) }
            'E' -> { textView.backgroundTintList = ColorStateList.valueOf(white); textView.setTextColor(black) }
            'F' -> { textView.backgroundTintList = ColorStateList.valueOf(gray400); textView.setTextColor(white) }
            'G' -> { textView.backgroundTintList = ColorStateList.valueOf(gray700); textView.setTextColor(white) }
            else -> { textView.backgroundTintList = ColorStateList.valueOf(gray200); textView.setTextColor(black) }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemIngCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position], position)
    }

    override fun getItemCount(): Int = items.size

    // ★ 수정됨: 내부 사진 어댑터에 Glide 적용
    class InternalImageAdapter(private val imagePaths: List<String>) : RecyclerView.Adapter<InternalImageAdapter.ImageViewHolder>() {
        inner class ImageViewHolder(val imageView: ImageView) : RecyclerView.ViewHolder(imageView)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
            val dpToPx = { dp: Int -> (dp * parent.context.resources.displayMetrics.density).toInt() }
            val imageView = ImageView(parent.context).apply {
                layoutParams = ViewGroup.MarginLayoutParams(dpToPx(60), dpToPx(60)).apply { marginEnd = dpToPx(8) }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Color.parseColor("#E0E0E0"))
                clipToOutline = true
            }
            return ImageViewHolder(imageView)
        }

        override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
            val imagePath = imagePaths[position]

            // ★ 핵심: 서버 URL이든 로컬 경로이든 Glide가 알아서 부드럽게 그려줍니다.
            Glide.with(holder.imageView.context)
                .load(imagePath)
                .centerCrop()
                .into(holder.imageView)
        }

        override fun getItemCount(): Int = imagePaths.size
    }
}