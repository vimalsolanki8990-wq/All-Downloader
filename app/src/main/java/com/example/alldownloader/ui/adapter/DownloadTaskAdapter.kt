package com.example.alldownloader.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.alldownloader.R
import com.example.alldownloader.data.model.DownloadItem
import com.example.alldownloader.data.model.DownloadStatus
import com.example.alldownloader.databinding.ItemDownloadTaskBinding
import com.example.alldownloader.utils.FileUtils

class DownloadTaskAdapter(
    private val onPauseResumeClicked: (DownloadItem) -> Unit,
    private val onCancelClicked: (DownloadItem) -> Unit
) : ListAdapter<DownloadItem, DownloadTaskAdapter.TaskViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TaskViewHolder {
        val binding = ItemDownloadTaskBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return TaskViewHolder(binding)
    }

    override fun onBindViewHolder(holder: TaskViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class TaskViewHolder(private val binding: ItemDownloadTaskBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DownloadItem) {
            val context = binding.root.context
            binding.tvFileName.text = item.fileName
            binding.ivCategoryIcon.setImageResource(item.category.iconRes)
            binding.ivCategoryIcon.setBackgroundResource(item.category.bgRes)
            binding.ivCategoryIcon.setColorFilter(ContextCompat.getColor(context, item.category.colorRes))

            val percent = item.progressPercent
            val speed = FileUtils.formatSpeed(item.speedBytesPerSec)
            val downloadedStr = FileUtils.formatFileSize(item.downloadedBytes)
            val totalStr = if (item.totalBytes > 0) FileUtils.formatFileSize(item.totalBytes) else "—"

            binding.progressBar.isIndeterminate = item.isIndeterminate
            if (!item.isIndeterminate) {
                binding.progressBar.progress = percent
            }

            binding.tvProgressPercent.text = "$percent%"
            binding.tvProgressSize.text = "$downloadedStr / $totalStr"

            when (item.status) {
                DownloadStatus.DOWNLOADING -> {
                    binding.tvStatusBadge.text = "${context.getString(R.string.status_downloading)} • $speed"
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.primary))
                    binding.btnPauseResume.setImageResource(R.drawable.ic_pause)
                    binding.btnPauseResume.contentDescription = context.getString(R.string.btn_pause)
                    binding.btnPauseResume.visibility = View.VISIBLE
                }
                DownloadStatus.PAUSED -> {
                    binding.tvStatusBadge.text = context.getString(R.string.status_paused)
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.color_warning))
                    binding.btnPauseResume.setImageResource(R.drawable.ic_play)
                    binding.btnPauseResume.contentDescription = context.getString(R.string.btn_resume)
                    binding.btnPauseResume.visibility = View.VISIBLE
                }
                DownloadStatus.QUEUED -> {
                    binding.tvStatusBadge.text = context.getString(R.string.status_queued)
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.color_info))
                    binding.btnPauseResume.setImageResource(R.drawable.ic_pause)
                    binding.btnPauseResume.visibility = View.VISIBLE
                }
                else -> {
                    binding.tvStatusBadge.text = context.getString(item.status.titleRes)
                    binding.btnPauseResume.visibility = View.GONE
                }
            }

            binding.btnPauseResume.setOnClickListener {
                onPauseResumeClicked(item)
            }

            binding.btnCancel.setOnClickListener {
                onCancelClicked(item)
            }
        }
    }

    companion object DiffCallback : DiffUtil.ItemCallback<DownloadItem>() {
        override fun areItemsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean =
            oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: DownloadItem, newItem: DownloadItem): Boolean =
            oldItem == newItem
    }
}
