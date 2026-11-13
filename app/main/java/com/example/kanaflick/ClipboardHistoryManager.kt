package com.example.kanaflick

import android.content.Context
import android.content.ClipboardManager
import org.json.JSONArray
import org.json.JSONObject

data class ClipboardEntry(val text: String, val pinned: Boolean)

class ClipboardHistoryManager(context: Context, private val onAccessError: (SecurityException) -> Unit) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("clipboard_history", Context.MODE_PRIVATE)
    private val clipboard = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val listener = ClipboardManager.OnPrimaryClipChangedListener { capturePrimaryClip() }
    private var observing = false

    fun entries(): List<ClipboardEntry> = decode(prefs.getString(KEY_ENTRIES, null))

    /** Observe clipboard changes only while the IME is active, respecting Android limits. */
    fun startObserving() {
        if (observing) return
        clipboard.addPrimaryClipChangedListener(listener)
        observing = true
    }

    fun stopObserving() {
        if (!observing) return
        clipboard.removePrimaryClipChangedListener(listener)
        observing = false
    }

    fun refreshFromSystemClipboard(): List<ClipboardEntry> {
        capturePrimaryClip()
        return entries()
    }

    private fun capturePrimaryClip() {
        try {
            val clip = clipboard.primaryClip ?: return
            if (clip.itemCount == 0) return
            val text = clip.getItemAt(0).coerceToText(appContext)?.toString()?.trim().orEmpty()
            if (text.isNotEmpty()) add(text)
        } catch (error: SecurityException) {
            stopObserving()
            onAccessError(error)
        }
    }

    fun add(text: String) {
        if (text.isBlank()) return
        val current = entries()
        val existing = current.firstOrNull { it.text == text }
        val updated = buildList {
            if (existing != null) add(existing.copy(pinned = existing.pinned))
            else add(ClipboardEntry(text, false))
            addAll(current.filterNot { it.text == text })
        }
        save(updated.filter { it.pinned } + updated.filterNot { it.pinned }.take(MAX_UNPINNED_ENTRIES))
    }

    fun togglePinned(text: String) {
        save(entries().map { if (it.text == text) it.copy(pinned = !it.pinned) else it })
    }

    fun delete(text: String) {
        save(entries().filterNot { it.text == text })
    }

    fun clearHistory() {
        save(emptyList())
    }

    private fun save(entries: List<ClipboardEntry>) {
        val json = JSONArray()
        entries.forEach { entry ->
            json.put(JSONObject().put("text", entry.text).put("pinned", entry.pinned))
        }
        prefs.edit().putString(KEY_ENTRIES, json.toString()).apply()
    }

    private fun decode(serialized: String?): List<ClipboardEntry> {
        if (serialized.isNullOrEmpty()) return emptyList()
        val json = JSONArray(serialized)
        return buildList {
            for (index in 0 until json.length()) {
                val item = json.optJSONObject(index) ?: continue
                val text = item.optString("text")
                if (text.isNotEmpty()) add(ClipboardEntry(text, item.optBoolean("pinned")))
            }
        }.sortedByDescending { it.pinned }
    }

    private companion object {
        const val KEY_ENTRIES = "entries"
        const val MAX_UNPINNED_ENTRIES = 40
    }
}
