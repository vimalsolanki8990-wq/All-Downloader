package com.example.alldownloader.ui.adapter

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.example.alldownloader.ui.downloads.DownloadsFragment
import com.example.alldownloader.ui.history.HistoryFragment
import com.example.alldownloader.ui.home.HomeFragment
import com.example.alldownloader.ui.queue.QueueFragment
import com.example.alldownloader.ui.settings.SettingsFragment

class MainPagerAdapter(activity: FragmentActivity) : FragmentStateAdapter(activity) {

    val homeFragment = HomeFragment()
    val downloadsFragment = DownloadsFragment()
    val queueFragment = QueueFragment()
    val historyFragment = HistoryFragment()
    val settingsFragment = SettingsFragment()

    override fun getItemCount(): Int = 5

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            0 -> homeFragment
            1 -> downloadsFragment
            2 -> queueFragment
            3 -> historyFragment
            4 -> settingsFragment
            else -> homeFragment
        }
    }
}
