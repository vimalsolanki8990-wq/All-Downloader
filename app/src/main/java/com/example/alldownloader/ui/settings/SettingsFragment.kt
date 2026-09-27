package com.example.alldownloader.ui.settings

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import com.example.alldownloader.R
import com.example.alldownloader.data.model.ThemeMode
import com.example.alldownloader.databinding.FragmentSettingsBinding
import com.example.alldownloader.utils.PreferencesManager

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private lateinit var prefManager: PreferencesManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        prefManager = PreferencesManager.getInstance(requireContext())

        bindSettings()
        setupListeners()
    }

    private fun bindSettings() {
        val settings = prefManager.getSettings()

        when (settings.themeMode) {
            ThemeMode.SYSTEM -> binding.rbThemeSystem.isChecked = true
            ThemeMode.LIGHT -> binding.rbThemeLight.isChecked = true
            ThemeMode.DARK -> binding.rbThemeDark.isChecked = true
        }

        binding.tvDownloadFolder.text = settings.downloadFolder
        binding.switchWifiOnly.isChecked = settings.wifiOnly
        binding.switchAutoStart.isChecked = settings.autoStart
        binding.switchAutoClipboard.isChecked = settings.autoDetectClipboard
        binding.switchNotifications.isChecked = settings.notificationsEnabled

        val maxOptions = listOf(1, 2, 3, 5)
        val spinnerAdapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_spinner_dropdown_item,
            maxOptions.map { "$it concurrent downloads" }
        )
        binding.spinnerMaxConcurrent.adapter = spinnerAdapter
        val selectedIndex = maxOptions.indexOf(settings.maxConcurrentDownloads).coerceAtLeast(0)
        binding.spinnerMaxConcurrent.setSelection(selectedIndex)
    }

    private fun setupListeners() {
        binding.rgTheme.setOnCheckedChangeListener { _, checkedId ->
            val mode = when (checkedId) {
                R.id.rbThemeLight -> ThemeMode.LIGHT
                R.id.rbThemeDark -> ThemeMode.DARK
                else -> ThemeMode.SYSTEM
            }
            prefManager.setThemeMode(mode)
        }

        binding.switchWifiOnly.setOnCheckedChangeListener { _, isChecked ->
            prefManager.setWifiOnly(isChecked)
        }

        binding.switchAutoStart.setOnCheckedChangeListener { _, isChecked ->
            prefManager.setAutoStart(isChecked)
        }

        binding.switchAutoClipboard.setOnCheckedChangeListener { _, isChecked ->
            prefManager.setAutoDetectClipboard(isChecked)
        }

        binding.switchNotifications.setOnCheckedChangeListener { _, isChecked ->
            prefManager.setNotificationsEnabled(isChecked)
        }

        binding.spinnerMaxConcurrent.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val maxOptions = listOf(1, 2, 3, 5)
                prefManager.setMaxConcurrent(maxOptions[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
