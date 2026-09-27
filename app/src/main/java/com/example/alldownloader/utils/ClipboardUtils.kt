package com.example.alldownloader.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.util.Patterns

object ClipboardUtils {

    fun getClipboardUrl(context: Context): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return null
        if (!clipboard.hasPrimaryClip()) return null
        val clipData: ClipData? = clipboard.primaryClip
        if (clipData != null && clipData.itemCount > 0) {
            val item = clipData.getItemAt(0)
            val text = item.text?.toString()?.trim() ?: ""
            if (isValidUrl(text)) {
                return text
            }
        }
        return null
    }

    fun isValidUrl(url: String): Boolean {
        if (url.isBlank()) return false
        val trimmed = url.trim()
        val hasHttp = trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)
        return hasHttp && Patterns.WEB_URL.matcher(trimmed).matches()
    }
}
