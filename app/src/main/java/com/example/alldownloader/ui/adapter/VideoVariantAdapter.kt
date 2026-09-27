package com.example.alldownloader.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.alldownloader.data.model.MediaVariant
import com.example.alldownloader.databinding.ItemVideoVariantBinding
import com.example.alldownloader.utils.FileUtils

class VideoVariantAdapter(
    private val variants: List<MediaVariant>,
    private val onDownloadClicked: (MediaVariant) -> Unit
) : RecyclerView.Adapter<VideoVariantAdapter.VariantViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VariantViewHolder {
        val binding = ItemVideoVariantBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return VariantViewHolder(binding)
    }

    override fun onBindViewHolder(holder: VariantViewHolder, position: Int) {
        holder.bind(variants[position])
    }

    override fun getItemCount(): Int = variants.size

    inner class VariantViewHolder(private val binding: ItemVideoVariantBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(variant: MediaVariant) {
            binding.tvQuality.text = variant.qualityLabel
            binding.tvFormat.text = variant.format
            binding.tvSize.text = variant.sizeBytes?.let { FileUtils.formatFileSize(it) } ?: "—"

            binding.btnDownloadVariant.setOnClickListener {
                onDownloadClicked(variant)
            }
        }
    }
}
