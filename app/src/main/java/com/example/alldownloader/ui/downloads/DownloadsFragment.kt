package com.example.alldownloader.ui.downloads

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.alldownloader.R
import com.example.alldownloader.data.db.DownloadDatabaseHelper
import com.example.alldownloader.data.db.DownloadRepository
import com.example.alldownloader.data.model.DownloadItem
import com.example.alldownloader.data.model.MediaCategory
import com.example.alldownloader.databinding.DialogRenameBinding
import com.example.alldownloader.databinding.FragmentDownloadsBinding
import com.example.alldownloader.ui.adapter.FileItemAdapter
import com.example.alldownloader.utils.FileUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

class DownloadsFragment : Fragment() {

    private var _binding: FragmentDownloadsBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: DownloadRepository
    private lateinit var fileAdapter: FileItemAdapter

    private var selectedCategory: MediaCategory = MediaCategory.ALL
    private var currentSortOrder: String = "${DownloadDatabaseHelper.COL_CREATED_AT} DESC"
    private var currentSearchQuery: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDownloadsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = DownloadRepository.getInstance(requireContext())

        setupRecyclerView()
        setupCategoryChips()
        setupSearch()
        setupSort()
        setupSwipeRefresh()
        observeDatabaseChanges()
        loadDownloads()
    }

    override fun onResume() {
        super.onResume()
        loadDownloads()
    }

    private fun setupRecyclerView() {
        val context = requireContext()
        fileAdapter = FileItemAdapter(
            onItemClicked = { item ->
                FileUtils.openFile(context, item.filePath, item.mimeType)
            },
            onOpenClicked = { item ->
                FileUtils.openFile(context, item.filePath, item.mimeType)
            },
            onShareClicked = { item ->
                FileUtils.shareFile(context, item.filePath, item.mimeType)
            },
            onRenameClicked = { item ->
                showRenameDialog(item)
            },
            onDeleteClicked = { item ->
                showDeleteConfirmDialog(item)
            }
        )

        binding.rvFiles.layoutManager = LinearLayoutManager(context)
        binding.rvFiles.adapter = fileAdapter
    }

    private fun setupCategoryChips() {
        binding.chipGroupCategories.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedCategory = when {
                checkedIds.contains(R.id.chipVideo) -> MediaCategory.VIDEO
                checkedIds.contains(R.id.chipAudio) -> MediaCategory.AUDIO
                checkedIds.contains(R.id.chipImage) -> MediaCategory.IMAGE
                checkedIds.contains(R.id.chipDoc) -> MediaCategory.DOCUMENT
                checkedIds.contains(R.id.chipOther) -> MediaCategory.OTHER
                else -> MediaCategory.ALL
            }
            loadDownloads()
        }
    }

    private fun setupSearch() {
        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                val text = s?.toString()?.trim()
                binding.btnClearSearch.visibility = if (!text.isNullOrEmpty()) View.VISIBLE else View.GONE
                currentSearchQuery = if (!text.isNullOrBlank()) text else null
                loadDownloads()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnClearSearch.setOnClickListener {
            binding.etSearch.text?.clear()
        }
    }

    private fun setupSort() {
        binding.btnSort.setOnClickListener { v ->
            val popup = PopupMenu(requireContext(), v)
            popup.menu.add(0, 1, 0, getString(R.string.sort_date_desc))
            popup.menu.add(0, 2, 1, getString(R.string.sort_date_asc))
            popup.menu.add(0, 3, 2, getString(R.string.sort_size_desc))
            popup.menu.add(0, 4, 3, getString(R.string.sort_size_asc))
            popup.menu.add(0, 5, 4, getString(R.string.sort_name_asc))
            popup.menu.add(0, 6, 5, getString(R.string.sort_name_desc))

            popup.setOnMenuItemClickListener { item ->
                currentSortOrder = when (item.itemId) {
                    1 -> "${DownloadDatabaseHelper.COL_CREATED_AT} DESC"
                    2 -> "${DownloadDatabaseHelper.COL_CREATED_AT} ASC"
                    3 -> "${DownloadDatabaseHelper.COL_TOTAL_BYTES} DESC"
                    4 -> "${DownloadDatabaseHelper.COL_TOTAL_BYTES} ASC"
                    5 -> "${DownloadDatabaseHelper.COL_FILE_NAME} ASC"
                    6 -> "${DownloadDatabaseHelper.COL_FILE_NAME} DESC"
                    else -> "${DownloadDatabaseHelper.COL_CREATED_AT} DESC"
                }
                loadDownloads()
                true
            }
            popup.show()
        }
    }

    private fun setupSwipeRefresh() {
        binding.swipeRefreshLayout.setOnRefreshListener {
            loadDownloads()
        }
    }

    private fun loadDownloads() {
        lifecycleScope.launch {
            binding.swipeRefreshLayout.isRefreshing = true
            val items = repository.getAllDownloads(
                category = if (selectedCategory == MediaCategory.ALL) null else selectedCategory,
                searchQuery = currentSearchQuery,
                sortBy = currentSortOrder
            )
            fileAdapter.submitList(items)
            binding.layoutEmptyFiles.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            binding.rvFiles.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
            binding.swipeRefreshLayout.isRefreshing = false
        }
    }

    private fun showRenameDialog(item: DownloadItem) {
        val dialogBinding = DialogRenameBinding.inflate(layoutInflater)
        val baseName = item.fileName.substringBeforeLast('.')
        dialogBinding.etNewFileName.setText(baseName)
        dialogBinding.etNewFileName.selectAll()

        MaterialAlertDialogBuilder(requireContext())
            .setView(dialogBinding.root)
            .setPositiveButton(R.string.save) { _, _ ->
                val newName = dialogBinding.etNewFileName.text?.toString()?.trim()
                if (!newName.isNullOrBlank()) {
                    val renamedFile = FileUtils.renameFile(item.filePath, newName)
                    if (renamedFile != null) {
                        lifecycleScope.launch {
                            repository.updateFileName(item.id, renamedFile.name, renamedFile.absolutePath)
                            loadDownloads()
                        }
                        Toast.makeText(requireContext(), "Renamed successfully", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), "Failed to rename file", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun showDeleteConfirmDialog(item: DownloadItem) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_confirm_title)
            .setMessage(getString(R.string.delete_confirm_msg, item.fileName))
            .setPositiveButton(R.string.delete_btn) { _, _ ->
                FileUtils.deleteFileFromStorage(item.filePath)
                lifecycleScope.launch {
                    repository.deleteDownload(item.id)
                    loadDownloads()
                }
                Toast.makeText(requireContext(), "File deleted", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun observeDatabaseChanges() {
        lifecycleScope.launch {
            repository.dbChanges.collectLatest {
                loadDownloads()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
