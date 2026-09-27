package com.example.alldownloader.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import coil.load
import com.example.alldownloader.R
import com.example.alldownloader.data.model.DownloadItem
import com.example.alldownloader.data.model.MediaCategory
import com.example.alldownloader.databinding.ItemFileBinding
import com.example.alldownloader.utils.FileUtils
import java.io.File

class FileItemAdapter(
    private val onItemClicked: (DownloadItem) -> Unit,
    private val onOpenClicked: (DownloadItem) -> Unit,
    private val onShareClicked: (DownloadItem) -> Unit,
    private val onRenameClicked: (DownloadItem) -> Unit,
    private val onDeleteClicked: (DownloadItem) -> Unit
) : ListAdapter<DownloadItem, FileItemAdapter.FileViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FileViewHolder {
        val binding = ItemFileBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FileViewHolder(binding)
    }

    override fun onBindViewHolder(holder: FileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class FileViewHolder(private val binding: ItemFileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DownloadItem) {
            val context = binding.root.context
            binding.tvFileName.text = item.fileName
            val ext = item.fileName.substringAfterLast('.', "FILE").uppercase()
            binding.tvFileType.text = ext

            val file = File(item.filePath)
            val actualSize = if (file.exists()) file.length() else item.totalBytes
            binding.tvFileSize.text = FileUtils.formatFileSize(actualSize)
            binding.tvFileDate.text = FileUtils.formatDate(item.completedAt ?: item.createdAt)

            if (item.category == MediaCategory.IMAGE && file.exists()) {
                binding.ivThumbnail.setPadding(0, 0, 0, 0)
                binding.ivThumbnail.clearColorFilter()
                binding.ivThumbnail.load(file) {
                    crossfade(true)
                    placeholder(R.drawable.ic_image)
                    error(R.drawable.ic_image)
                }
            } else {
                binding.ivThumbnail.setPadding(20, 20, 20, 20)
                binding.ivThumbnail.setImageResource(item.category.iconRes)
                binding.ivThumbnail.setBackgroundResource(item.category.bgRes)
                binding.ivThumbnail.setColorFilter(ContextCompat.getColor(context, item.category.colorRes))
            }

            binding.root.setOnClickListener {
                onItemClicked(item)
            }

            binding.btnFileMenu.setOnClickListener { v ->
                val popup = PopupMenu(context, v)
                popup.menu.add(0, 1, 0, context.getString(R.string.btn_open))
                popup.menu.add(0, 2, 1, context.getString(R.string.btn_share))
                popup.menu.add(0, 3, 2, context.getString(R.string.btn_rename))
                popup.menu.add(0, 4, 3, context.getString(R.string.btn_delete))

                popup.setOnMenuItemClickListener { menuItem ->
                    when (menuItem.itemId) {
                        1 -> onOpenClicked(item)
                        2 -> onShareClicked(item)
                        3 -> onRenameClicked(item)
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
