package com.example.alldownloader.ui.history

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.alldownloader.MainActivity
import com.example.alldownloader.R
import com.example.alldownloader.data.db.DownloadRepository
import com.example.alldownloader.data.model.DownloadItem
import com.example.alldownloader.data.model.DownloadStatus
import com.example.alldownloader.databinding.FragmentHistoryBinding
import com.example.alldownloader.service.DownloadManager
import com.example.alldownloader.ui.adapter.HistoryAdapter
import com.example.alldownloader.utils.FileUtils
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: DownloadRepository
    private lateinit var historyAdapter: HistoryAdapter

    private var selectedStatusFilter: DownloadStatus? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHistoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        repository = DownloadRepository.getInstance(requireContext())

        setupRecyclerView()
        setupStatusChips()
        setupClearHistory()
        observeDatabaseChanges()
        loadHistory()
    }

    override fun onResume() {
        super.onResume()
        loadHistory()
    }

    private fun setupRecyclerView() {
        val context = requireContext()
        historyAdapter = HistoryAdapter(
            onItemClicked = { item ->
                if (item.status == DownloadStatus.COMPLETED) {
                    FileUtils.openFile(context, item.filePath, item.mimeType)
                }
            },
            onOpenClicked = { item ->
                FileUtils.openFile(context, item.filePath, item.mimeType)
            },
            onShareClicked = { item ->
                FileUtils.shareFile(context, item.filePath, item.mimeType)
            },
            onDeleteClicked = { item ->
                lifecycleScope.launch {
                    repository.deleteDownload(item.id)
                    loadHistory()
                }
            },
            onDownloadAgainClicked = { item ->
                DownloadManager.getInstance(context).retryDownload(item.id)
                (activity as? MainActivity)?.selectTab(2)
            }
        )

        binding.rvHistory.layoutManager = LinearLayoutManager(context)
        binding.rvHistory.adapter = historyAdapter
    }

    private fun setupStatusChips() {
        binding.chipGroupStatus.setOnCheckedStateChangeListener { _, checkedIds ->
            selectedStatusFilter = when {
                checkedIds.contains(R.id.chipStatusCompleted) -> DownloadStatus.COMPLETED
                checkedIds.contains(R.id.chipStatusFailed) -> DownloadStatus.FAILED
                checkedIds.contains(R.id.chipStatusCancelled) -> DownloadStatus.CANCELLED
                else -> null
            }
            loadHistory()
        }
    }

    private fun setupClearHistory() {
        binding.btnClearHistory.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.clear_history)
                .setMessage(R.string.clear_history_confirm)
                .setPositiveButton(R.string.clear_history_btn) { _, _ ->
                    lifecycleScope.launch {
                        repository.clearAllHistory()
                        loadHistory()
                    }
                    Toast.makeText(requireContext(), "Download history cleared", Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private fun loadHistory() {
        lifecycleScope.launch {
            val all = repository.getAllDownloads()
            val filtered = if (selectedStatusFilter != null) {
                all.filter { it.status == selectedStatusFilter }
            } else {
                all
            }

            historyAdapter.submitList(filtered)
            binding.layoutEmptyHistory.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
            binding.rvHistory.visibility = if (filtered.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun observeDatabaseChanges() {
        lifecycleScope.launch {
            repository.dbChanges.collectLatest {
                loadHistory()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
