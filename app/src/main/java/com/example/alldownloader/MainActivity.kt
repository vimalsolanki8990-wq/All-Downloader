package com.example.alldownloader

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.viewpager2.widget.ViewPager2
import com.example.alldownloader.data.model.DownloadStatus
import com.example.alldownloader.databinding.ActivityMainBinding
import com.example.alldownloader.service.DownloadManager
import com.example.alldownloader.ui.adapter.MainPagerAdapter
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var pagerAdapter: MainPagerAdapter

    private val requestNotificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { _ ->
            // Notification permission handled
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, 0)
            insets
        }

        setupViewPagerAndNavigation()
        requestNotificationPermission()
        observeActiveDownloadsBadge()
        handleIncomingIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun setupViewPagerAndNavigation() {
        pagerAdapter = MainPagerAdapter(this)
        binding.viewPager.adapter = pagerAdapter
        binding.viewPager.isUserInputEnabled = false // Prevent accidental swiping across complex lists
        binding.viewPager.offscreenPageLimit = 4

        binding.bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> binding.viewPager.setCurrentItem(0, false)
                R.id.nav_downloads -> binding.viewPager.setCurrentItem(1, false)
                R.id.nav_queue -> binding.viewPager.setCurrentItem(2, false)
                R.id.nav_history -> binding.viewPager.setCurrentItem(3, false)
                R.id.nav_settings -> binding.viewPager.setCurrentItem(4, false)
            }
            true
        }

        binding.viewPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    super.onPageSelected(position)
                    val navItemId = when (position) {
                        0 -> R.id.nav_home
                        1 -> R.id.nav_downloads
                        2 -> R.id.nav_queue
                        3 -> R.id.nav_history
                        4 -> R.id.nav_settings
                        else -> R.id.nav_home
                    }
                    if (binding.bottomNav.selectedItemId != navItemId) {
                        binding.bottomNav.selectedItemId = navItemId
                    }
                }
            },
        )
    }

    fun selectTab(position: Int) {
        binding.viewPager.setCurrentItem(position, true)
    }

    private fun observeActiveDownloadsBadge() {
        val downloadManager = DownloadManager.getInstance(this)
        lifecycleScope.launch {
            downloadManager.runningDownloads.collectLatest { list ->
                val activeCount = list.count { (it.status == DownloadStatus.DOWNLOADING) || (it.status == DownloadStatus.QUEUED) }
                val badge = binding.bottomNav.getOrCreateBadge(R.id.nav_queue)
                if (activeCount > 0) {
                    badge.isVisible = true
                    badge.number = activeCount
                } else {
                    badge.isVisible = false
                }
            }
        }
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        if (intent.action == Intent.ACTION_SEND && intent.type != null) {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
            if (!sharedText.isNullOrBlank()) {
                // Extract URL from shared text (which may contain titles + URL)
                val url = extractUrlFromText(sharedText)
                if (url.isNotBlank()) {
                    selectTab(0)
                    binding.root.post {
                        pagerAdapter.homeFragment.setAndAnalyzeUrl(url)
                    }
                }
            }
        }
    }

    private fun extractUrlFromText(text: String): String {
        val words = text.split("\\s+".toRegex())
        return words.firstOrNull { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) } ?: text.trim()
    }
}