package com.example.alldownloader.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.alldownloader.R
import com.example.alldownloader.data.model.DiscoveredResource
import com.example.alldownloader.data.model.MediaCategory
import com.example.alldownloader.databinding.ItemDiscoveredResourceBinding
import com.example.alldownloader.utils.FileUtils

class DiscoveredResourceAdapter(
    private val resources: List<DiscoveredResource>,
    private val onDownloadClicked: (DiscoveredResource) -> Unit
) : RecyclerView.Adapter<DiscoveredResourceAdapter.ResourceViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResourceViewHolder {
        val binding = ItemDiscoveredResourceBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ResourceViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ResourceViewHolder, position: Int) {
        holder.bind(resources[position])
    }

    override fun getItemCount(): Int = resources.size

    inner class ResourceViewHolder(private val binding: ItemDiscoveredResourceBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DiscoveredResource) {
            val context = binding.root.context

            // Badge text & color
            val badgeTitle = when {
                item.isThumbnailFallback -> "[ THUMBNAIL ]"
                item.category == MediaCategory.VIDEO -> "[ VIDEO ]"
                item.category == MediaCategory.AUDIO -> "[ AUDIO ]"
                item.category == MediaCategory.IMAGE -> "[ IMAGE ]"
                item.category == MediaCategory.DOCUMENT -> "[ DOCUMENT ]"
                item.category == MediaCategory.OTHER -> "[ FILE ]"
                else -> "[ MEDIA ]"
            }
            binding.tvCategoryBadge.text = badgeTitle
            binding.tvCategoryBadge.setBackgroundResource(item.category.bgRes)
            binding.tvCategoryBadge.setTextColor(ContextCompat.getColor(context, item.category.colorRes))
            binding.tvSourceType.text = item.sourceType

            // Title & Filename
            binding.tvFileName.text = item.fileName

            // Meta line: Format • Resolution • Size
            val ext = item.fileName.substringAfterLast('.', "BIN").uppercase()
            val metaParts = mutableListOf<String>()
            metaParts.add(ext)
            if (!item.resolution.isNullOrBlank()) {
                metaParts.add(item.resolution)
            }
            if (item.sizeBytes > 0L) {
                metaParts.add(FileUtils.formatFileSize(item.sizeBytes))
            }
            binding.tvFileMeta.text = metaParts.joinToString(" • ")

            // Thumbnail
            val preview = item.previewUrl ?: if (item.category == MediaCategory.IMAGE) item.directUrl else null
            if (!preview.isNullOrBlank()) {
                binding.ivThumbnail.setPadding(0, 0, 0, 0)
                binding.ivThumbnail.clearColorFilter()
                binding.ivThumbnail.load(preview) {
                    crossfade(true)
                    placeholder(item.category.iconRes)
                    error(item.category.iconRes)
                }
            } else {
                binding.ivThumbnail.setPadding(16, 16, 16, 16)
                binding.ivThumbnail.setImageResource(item.category.iconRes)
                binding.ivThumbnail.setBackgroundResource(item.category.bgRes)
                binding.ivThumbnail.setColorFilter(ContextCompat.getColor(context, item.category.colorRes))
            }

            // Webpage Thumbnail / Restricted Notice
            if (item.isThumbnailFallback && !item.notice.isNullOrBlank()) {
                binding.layoutRestrictedNotice.visibility = View.VISIBLE
                binding.tvRestrictedNotice.text = item.notice
            } else {
                binding.layoutRestrictedNotice.visibility = View.GONE
            }

            // APK Notice
            binding.layoutApkWarning.visibility = if (item.isSecurityRisky) View.VISIBLE else View.GONE

            // Download Button
            val downloadBtnText = when {
                item.isThumbnailFallback -> "Download Thumbnail"
                item.category == MediaCategory.IMAGE -> "Download Image"
                item.category == MediaCategory.VIDEO -> "Download Video"
                item.category == MediaCategory.AUDIO -> "Download Audio"
                item.category == MediaCategory.DOCUMENT -> "Download Document"
                item.isSecurityRisky -> "Download APK"
                else -> context.getString(R.string.download)
            }
            binding.btnDownload.text = downloadBtnText

            binding.btnDownload.setOnClickListener {
                onDownloadClicked(item)
            }
        }
    }
}
