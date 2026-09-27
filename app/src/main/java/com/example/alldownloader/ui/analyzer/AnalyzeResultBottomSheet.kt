package com.example.alldownloader.ui.analyzer

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.alldownloader.R
import com.example.alldownloader.data.model.DiscoveredResource
import com.example.alldownloader.data.model.MediaAnalysisResult
import com.example.alldownloader.data.model.MediaCategory
import com.example.alldownloader.databinding.BottomSheetAnalyzerBinding
import com.example.alldownloader.service.DownloadManager
import com.example.alldownloader.ui.adapter.DiscoveredResourceAdapter
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class AnalyzeResultBottomSheet : BottomSheetDialogFragment() {

    private var _binding: BottomSheetAnalyzerBinding? = null
    private val binding get() = _binding!!

    private var analysisResult: MediaAnalysisResult? = null
    var onDownloadStarted: (() -> Unit)? = null
    private var isDebugLogVisible = false

    companion object {
        fun newInstance(result: MediaAnalysisResult): AnalyzeResultBottomSheet {
            return AnalyzeResultBottomSheet().apply {
                this.analysisResult = result
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetAnalyzerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnCloseSheet.setOnClickListener {
            dismiss()
        }

        val result = analysisResult ?: run {
            dismiss()
            return
        }

        bindAnalysisResult(result)
        setupDebugLogToggle(result.debugLog)
    }

    private fun bindAnalysisResult(result: MediaAnalysisResult) {
        val context = requireContext()

        if (!result.isSuccess || result.resources.isEmpty()) {
            binding.layoutFailure.visibility = View.VISIBLE
            binding.rvDiscoveredResources.visibility = View.GONE
            binding.tvSummarySubtitle.text = "Analysis Result"
            binding.tvFailureMessage.text = result.errorMessage ?: getString(R.string.unsupported_url_msg)
            return
        }

        binding.layoutFailure.visibility = View.GONE
        binding.rvDiscoveredResources.visibility = View.VISIBLE

        val count = result.resources.size
        binding.tvSummarySubtitle.text = when {
            result.isSingleDirectResource -> {
                val cat = result.resources.first().category
                when (cat) {
                    MediaCategory.IMAGE -> "✓ Direct Image endpoint detected"
                    MediaCategory.VIDEO -> "✓ Direct Video stream detected"
                    MediaCategory.AUDIO -> "✓ Direct Audio stream detected"
                    MediaCategory.DOCUMENT -> "✓ Direct Document detected"
                    else -> "✓ Direct downloadable resource detected"
                }
            }
            result.isFallbackThumbnailOnly -> {
                result.warningNotice ?: "Webpage thumbnail discovered (Video stream is protected/restricted)"
            }
            else -> {
                "✓ $count downloadable ${if (count == 1) "resource" else "resources"} discovered"
            }
        }

        val adapter = DiscoveredResourceAdapter(result.resources) { resource ->
            startDownload(resource)
        }

        binding.rvDiscoveredResources.layoutManager = LinearLayoutManager(context)
        binding.rvDiscoveredResources.adapter = adapter
    }

    private fun startDownload(resource: DiscoveredResource) {
        val context = requireContext()
        val downloadManager = DownloadManager.getInstance(context)

        downloadManager.enqueueDownload(
            url = resource.directUrl,
            title = resource.title,
            fileName = resource.fileName,
            mimeType = resource.mimeType,
            category = resource.category,
            totalBytes = resource.sizeBytes,
            resolution = resource.resolution,
            format = resource.fileName.substringAfterLast('.', "").uppercase()
        )

        Toast.makeText(context, "Download queued: ${resource.fileName}", Toast.LENGTH_SHORT).show()
        onDownloadStarted?.invoke()
        dismiss()
    }

    private fun setupDebugLogToggle(debugLog: String) {
        binding.tvDebugLogContent.text = debugLog.ifBlank { "No diagnostic logs available." }

        binding.btnToggleDebugLog.setOnClickListener {
            isDebugLogVisible = !isDebugLogVisible
            binding.tvDebugLogContent.visibility = if (isDebugLogVisible) View.VISIBLE else View.GONE
            binding.ivDebugChevron.rotation = if (isDebugLogVisible) 90f else 0f
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
