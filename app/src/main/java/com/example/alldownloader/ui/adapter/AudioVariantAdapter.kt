package com.example.alldownloader.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.example.alldownloader.data.model.AudioVariant
import com.example.alldownloader.databinding.ItemAudioVariantBinding
import com.example.alldownloader.utils.FileUtils

class AudioVariantAdapter(
    private val variants: List<AudioVariant>,
    private val onDownloadClicked: (AudioVariant) -> Unit
) : RecyclerView.Adapter<AudioVariantAdapter.AudioViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AudioViewHolder {
        val binding = ItemAudioVariantBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return AudioViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AudioViewHolder, position: Int) {
        holder.bind(variants[position])
    }

    override fun getItemCount(): Int = variants.size

    inner class AudioViewHolder(private val binding: ItemAudioVariantBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(variant: AudioVariant) {
            binding.tvFormat.text = "${variant.format} Audio"
            binding.tvBitrate.text = variant.bitrateKbps?.let { "$it kbps" } ?: "Standard Bitrate"
            binding.tvSize.text = variant.sizeBytes?.let { FileUtils.formatFileSize(it) } ?: "—"

            binding.btnDownloadAudio.setOnClickListener {
                onDownloadClicked(variant)
            }
        }
    }
}
