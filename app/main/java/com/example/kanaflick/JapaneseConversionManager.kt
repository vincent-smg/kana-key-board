package com.example.kanaflick

import android.content.Context
import java.util.LinkedHashMap
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/**
 * Uses Mozc when its native library and engine data are packaged, with the bundled dictionary
 * retained as a fallback for local builds without the Mozc build artifacts.
 */
class JapaneseConversionManager(
    context: Context,
    private val dictionary: Dictionary,
    private val onCandidatesReady: () -> Unit
) {
    private val mozc = MozcNativeEngine(context)
    private val cacheLock = Any()
    private val cache = object : LinkedHashMap<String, List<String>>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<String>>): Boolean =
            size > MAX_CACHED_READINGS
    }
    private val requestLock = Any()
    private val worker: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "KanaFlick-Mozc").apply { isDaemon = true }
    }
    private var pendingReading: String? = null
    private var pendingRequest: ScheduledFuture<*>? = null
    private var closed = false

    fun candidates(reading: String, limit: Int = 20): List<String> {
        val hiragana = Kana.toHira(reading)
        if (hiragana.isBlank() || limit <= 0) return emptyList()
        val nativeCandidates = synchronized(cacheLock) { cache[hiragana] }
        if (nativeCandidates == null) requestCandidates(hiragana)
        return linkedSetOf<String>().apply {
            addAll(nativeCandidates.orEmpty())
            addAll(dictionary.convert(hiragana, limit))
            addAll(dictionary.exact(hiragana))
            addAll(dictionary.predict(hiragana, limit))
        }.take(limit)
    }

    fun isDictionaryCandidate(candidate: String): Boolean = dictionary.has(candidate)

    fun hasMozcResult(reading: String): Boolean {
        val hiragana = Kana.toHira(reading)
        return synchronized(cacheLock) { cache.containsKey(hiragana) }
    }

    fun close() {
        synchronized(requestLock) {
            closed = true
            pendingRequest?.cancel(false)
            pendingRequest = null
            pendingReading = null
        }
        worker.shutdownNow()
    }

    private fun requestCandidates(reading: String) {
        synchronized(requestLock) {
            if (closed || pendingReading == reading) return
            pendingRequest?.cancel(false)
            pendingReading = reading
            pendingRequest = worker.schedule({
                val result = mozc.candidates(reading).orEmpty()
                synchronized(cacheLock) { cache[reading] = result }
                val shouldNotify = synchronized(requestLock) {
                    if (pendingReading == reading) {
                        pendingReading = null
                        pendingRequest = null
                    }
                    !closed
                }
                if (shouldNotify) onCandidatesReady()
            }, REQUEST_DEBOUNCE_MS, TimeUnit.MILLISECONDS)
        }
    }

    private companion object {
        const val MAX_CACHED_READINGS = 128
        const val REQUEST_DEBOUNCE_MS = 80L
    }
}
