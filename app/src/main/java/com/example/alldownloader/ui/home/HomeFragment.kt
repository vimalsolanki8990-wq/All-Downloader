package com.example.alldownloader.ui.home

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.alldownloader.MainActivity
import com.example.alldownloader.R
import com.example.alldownloader.data.db.DownloadRepository
import com.example.alldownloader.data.model.DownloadItem
import com.example.alldownloader.data.model.MediaCategory
import com.example.alldownloader.databinding.FragmentHomeBinding
import com.example.alldownloader.network.UrlAnalyzerEngine
import com.example.alldownloader.service.DownloadManager
import com.example.alldownloader.ui.adapter.HistoryAdapter
import com.example.alldownloader.ui.analyzer.AnalyzeResultBottomSheet
import com.example.alldownloader.utils.ClipboardUtils
import com.example.alldownloader.utils.FileUtils
import com.example.alldownloader.utils.PreferencesManager
import com.example.alldownloader.utils.StorageUtils
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: DownloadRepository
    private lateinit var prefManager: PreferencesManager
    private lateinit var recentAdapter: HistoryAdapter
    private var detectedClipboardUrl: String? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val context = requireContext()
        repository = DownloadRepository.getInstance(context)
        prefManager = PreferencesManager.getInstance(context)

        setupRecentDownloadsList()
        setupListeners()
        observeDatabaseChanges()
        loadStorageInfo()
        loadRecentDownloads()
    }

    override fun onResume() {
        super.onResume()
        checkClipboardForUrl()
        loadStorageInfo()
        loadRecentDownloads()
    }

    fun setAndAnalyzeUrl(url: String) {
        binding.etUrlInput.setText(url)
        triggerUrlAnalysis(url)
    }

    private fun setupRecentDownloadsList() {
        val context = requireContext()
        recentAdapter = HistoryAdapter(
            onItemClicked = { item ->
                if (item.status.isTerminal && item.totalBytes > 0) {
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
                    loadRecentDownloads()
                }
            },
            onDownloadAgainClicked = { item ->
                DownloadManager.getInstance(context).retryDownload(item.id)
                (activity as? MainActivity)?.selectTab(2) // Switch to queue
            }
        )

        binding.rvRecentDownloads.layoutManager = LinearLayoutManager(context)
        binding.rvRecentDownloads.adapter = recentAdapter
    }

    private fun setupListeners() {
        binding.btnHeaderSettings.setOnClickListener {
            (activity as? MainActivity)?.selectTab(4) // Settings tab
        }

        binding.tvViewAllDownloads.setOnClickListener {
            (activity as? MainActivity)?.selectTab(1) // Downloads tab
        }

        binding.btnPasteUrl.setOnClickListener {
            val clipboardText = ClipboardUtils.getClipboardUrl(requireContext())
            if (!clipboardText.isNullOrBlank()) {
                binding.etUrlInput.setText(clipboardText)
                binding.cardClipboardDetected.visibility = View.GONE
            } else {
                Toast.makeText(requireContext(), "No valid link found in clipboard", Toast.LENGTH_SHORT).show()
            }
        }

        binding.btnClearUrl.setOnClickListener {
            binding.etUrlInput.text?.clear()
            binding.tvSmartDetectionBadge.text = ""
        }

        binding.etUrlInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                val text = s?.toString()?.trim() ?: ""
                binding.btnClearUrl.visibility = if (text.isNotEmpty()) View.VISIBLE else View.GONE
                updateSmartUrlBadge(text)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.btnAnalyze.setOnClickListener {
            val url = binding.etUrlInput.text?.toString()?.trim() ?: ""
            if (url.isBlank()) {
                Toast.makeText(requireContext(), "Please enter or paste a URL first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            triggerUrlAnalysis(url)
        }

        // Quick Test sample links
        binding.btnTestDirectJpg.setOnClickListener {
            val sample = "https://images.unsplash.com/photo-1506744038136-46273834b3fb?w=1600&q=80"
            setAndAnalyzeUrl(sample)
        }

        binding.btnTestDirectMp4.setOnClickListener {
            val sample = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/BigBuckBunny.mp4"
            setAndAnalyzeUrl(sample)
        }

        binding.btnTestDirectPdf.setOnClickListener {
            val sample = "https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf"
            setAndAnalyzeUrl(sample)
        }

        binding.btnTestWebpageOg.setOnClickListener {
            val sample = "https://en.wikipedia.org/wiki/Aurora"
            setAndAnalyzeUrl(sample)
        }

        binding.btnTestWebpageVideo.setOnClickListener {
            val sample = "https://html5demos.com/video/"
            setAndAnalyzeUrl(sample)
        }

        // Clipboard detection banner actions
        binding.btnClipboardPaste.setOnClickListener {
            detectedClipboardUrl?.let { url ->
                binding.etUrlInput.setText(url)
                binding.cardClipboardDetected.visibility = View.GONE
                triggerUrlAnalysis(url)
            }
        }

        binding.btnClipboardDismiss.setOnClickListener {
            binding.cardClipboardDetected.visibility = View.GONE
        }
    }

    private fun updateSmartUrlBadge(url: String) {
        if (url.isBlank() || !ClipboardUtils.isValidUrl(url)) {
            binding.tvSmartDetectionBadge.text = ""
            return
        }

        val cleanPath = url.substringBefore("?").substringBefore("#")
        val ext = cleanPath.substringAfterLast(".", "").lowercase()
        val category = MediaCategory.fromMimeType(null, cleanPath)

        val badgeText = when {
            ext in listOf("jpg", "jpeg", "png", "webp", "svg", "gif", "bmp", "avif") ->
                "✓ Direct Image (${ext.uppercase()}) • Instant Fast-Download"
            ext in listOf("mp4", "mkv", "webm", "mov", "avi", "flv", "3gp", "m4v") ->
                "✓ Direct Video (${ext.uppercase()}) • Instant Fast-Download"
            ext in listOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus") ->
                "✓ Direct Audio (${ext.uppercase()}) • Instant Fast-Download"
            ext in listOf("pdf", "zip", "rar", "7z", "tar", "gz", "doc", "docx", "xls", "xlsx") ->
                "✓ Direct Document (${ext.uppercase()}) • Fast-Download ready"
            ext == "apk" ->
                "✓ Android Package (APK) • Fast-Download ready"
            category == MediaCategory.VIDEO ->
                "✓ Video resource detected"
            category == MediaCategory.AUDIO ->
                "✓ Audio resource detected"
            category == MediaCategory.IMAGE ->
                "✓ Image resource detected"
            else ->
                "⚡ Web resource ready to analyze"
        }
        binding.tvSmartDetectionBadge.text = badgeText
    }

    private fun checkClipboardForUrl() {
        if (!prefManager.getSettings().autoDetectClipboard) {
            binding.cardClipboardDetected.visibility = View.GONE
            return
        }

        val url = ClipboardUtils.getClipboardUrl(requireContext())
        val currentInput = binding.etUrlInput.text?.toString()?.trim()

        if (!url.isNullOrBlank() && url != currentInput) {
            detectedClipboardUrl = url
            binding.tvClipboardSnippet.text = url
            binding.cardClipboardDetected.visibility = View.VISIBLE
        } else {
            binding.cardClipboardDetected.visibility = View.GONE
        }
    }

    private fun triggerUrlAnalysis(url: String) {
        binding.pbAnalyzing.visibility = View.VISIBLE
        binding.btnAnalyze.isEnabled = false
        binding.btnAnalyze.text = getString(R.string.btn_analyzing)

        lifecycleScope.launch {
            val result = UrlAnalyzerEngine.getInstance().analyzeUrl(url)
            binding.pbAnalyzing.visibility = View.GONE
            binding.btnAnalyze.isEnabled = true
            binding.btnAnalyze.text = getString(R.string.btn_analyze)

            val sheet = AnalyzeResultBottomSheet.newInstance(result)
            sheet.onDownloadStarted = {
                loadRecentDownloads()
                loadStorageInfo()
            }
            sheet.show(parentFragmentManager, "AnalyzeResultBottomSheet")
        }
    }

    private fun loadStorageInfo() {
        lifecycleScope.launch {
            val storageInfo = StorageUtils.getStorageInfo(requireContext())
            val freeStr = FileUtils.formatFileSize(storageInfo.freeBytes)
            val usedStr = FileUtils.formatFileSize(storageInfo.usedBytes)
            val totalStr = FileUtils.formatFileSize(storageInfo.totalBytes)

            binding.tvStorageSummary.text = "$freeStr free"
            binding.pbStorage.progress = storageInfo.usedPercentage
            binding.tvStorageDetailed.text = getString(R.string.storage_used_of, usedStr, totalStr, freeStr)
        }
    }

    private fun loadRecentDownloads() {
        lifecycleScope.launch {
            val recents = repository.getRecentDownloads(5)
            recentAdapter.submitList(recents)
            binding.layoutEmptyRecent.visibility = if (recents.isEmpty()) View.VISIBLE else View.GONE
            binding.rvRecentDownloads.visibility = if (recents.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun observeDatabaseChanges() {
        lifecycleScope.launch {
            repository.dbChanges.collectLatest {
                loadRecentDownloads()
                loadStorageInfo()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
