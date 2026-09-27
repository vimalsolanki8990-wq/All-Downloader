package com.example.alldownloader.ui.queue

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.alldownloader.R
import com.example.alldownloader.data.model.DownloadStatus
import com.example.alldownloader.databinding.FragmentQueueBinding
import com.example.alldownloader.service.DownloadManager
import com.example.alldownloader.ui.adapter.DownloadTaskAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class QueueFragment : Fragment() {

    private var _binding: FragmentQueueBinding? = null
    private val binding get() = _binding!!

    private lateinit var downloadManager: DownloadManager
    private lateinit var taskAdapter: DownloadTaskAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentQueueBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        downloadManager = DownloadManager.getInstance(requireContext())

        setupRecyclerView()
        observeQueue()
    }

    private fun setupRecyclerView() {
        taskAdapter = DownloadTaskAdapter(
            onPauseResumeClicked = { item ->
                if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED) {
                    downloadManager.pauseDownload(item.id)
                } else if (item.status == DownloadStatus.PAUSED) {
                    downloadManager.resumeDownload(item.id)
                }
            },
            onCancelClicked = { item ->
                downloadManager.cancelDownload(item.id)
            }
        )

        binding.rvQueue.layoutManager = LinearLayoutManager(requireContext())
        binding.rvQueue.adapter = taskAdapter
    }

    private fun observeQueue() {
        lifecycleScope.launch {
            downloadManager.runningDownloads.collectLatest { list ->
                val activeList = list.filter { it.status == DownloadStatus.DOWNLOADING || it.status == DownloadStatus.QUEUED || it.status == DownloadStatus.PAUSED }
                taskAdapter.submitList(activeList)

                val count = activeList.size
                binding.tvActiveCount.text = "$count Active"
                binding.tvActiveCount.visibility = if (count > 0) View.VISIBLE else View.GONE
                binding.layoutEmptyQueue.visibility = if (activeList.isEmpty()) View.VISIBLE else View.GONE
                binding.rvQueue.visibility = if (activeList.isEmpty()) View.GONE else View.VISIBLE
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
