package com.example.alldownloader.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.alldownloader.R
import com.example.alldownloader.data.model.DownloadItem
import com.example.alldownloader.data.model.DownloadStatus
import com.example.alldownloader.databinding.ItemHistoryBinding
import com.example.alldownloader.utils.FileUtils

class HistoryAdapter(
    private val onItemClicked: (DownloadItem) -> Unit,
    private val onOpenClicked: (DownloadItem) -> Unit,
    private val onShareClicked: (DownloadItem) -> Unit,
    private val onDeleteClicked: (DownloadItem) -> Unit,
    private val onDownloadAgainClicked: (DownloadItem) -> Unit
) : ListAdapter<DownloadItem, HistoryAdapter.HistoryViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HistoryViewHolder {
        val binding = ItemHistoryBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return HistoryViewHolder(binding)
    }

    override fun onBindViewHolder(holder: HistoryViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class HistoryViewHolder(private val binding: ItemHistoryBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DownloadItem) {
            val context = binding.root.context
            binding.tvFileName.text = item.fileName
            binding.ivCategoryIcon.setImageResource(item.category.iconRes)
            binding.ivCategoryIcon.setBackgroundResource(item.category.bgRes)
            binding.ivCategoryIcon.setColorFilter(ContextCompat.getColor(context, item.category.colorRes))

            binding.tvFileSize.text = if (item.totalBytes > 0) FileUtils.formatFileSize(item.totalBytes) else FileUtils.formatFileSize(item.downloadedBytes)
            binding.tvDate.text = FileUtils.formatDate(item.completedAt ?: item.createdAt)

            when (item.status) {
                DownloadStatus.COMPLETED -> {
                    binding.tvStatusBadge.text = context.getString(R.string.status_completed)
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.color_success))
                }
                DownloadStatus.FAILED -> {
                    binding.tvStatusBadge.text = context.getString(R.string.status_failed)
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.color_error))
                }
                DownloadStatus.CANCELLED -> {
                    binding.tvStatusBadge.text = context.getString(R.string.status_cancelled)
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.text_secondary_light))
                }
                else -> {
                    binding.tvStatusBadge.text = context.getString(item.status.titleRes)
                    binding.tvStatusBadge.setTextColor(ContextCompat.getColor(context, R.color.primary))
                }
            }

            binding.root.setOnClickListener {
                onItemClicked(item)
            }

            binding.btnItemMenu.setOnClickListener { v ->
                val popup = PopupMenu(context, v)
                if (item.status == DownloadStatus.COMPLETED) {
                    popup.menu.add(0, 1, 0, context.getString(R.string.btn_open))
                    popup.menu.add(0, 2, 1, context.getString(R.string.btn_share))
                }
                popup.menu.add(0, 3, 2, context.getString(R.string.btn_download_again))
                popup.menu.add(0, 4, 3, context.getString(R.string.btn_delete))

                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        1 -> onOpenClicked(item)
                        2 -> onShareClicked(item)
                        3 -> onDownloadAgainClicked(item)
                        4 -> onDeleteClicked(item)
                    }
                    true
                }
                popup.show()
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
