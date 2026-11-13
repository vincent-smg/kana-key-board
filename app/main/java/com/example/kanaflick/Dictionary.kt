package com.example.kanaflick

import android.content.Context

/** Bundled reading -> candidates dictionary. Supports both reading|words and legacy id|reading|words lines. */
class Dictionary(ctx: Context, private val prefs: Prefs) {
    private val map = LinkedHashMap<String, List<String>>()
    private val words = HashSet<String>()

    init {
        ctx.assets.open("dict.txt").bufferedReader().useLines { lines ->
            lines.forEach { line ->
                val fields = line.trim().split('|')
                val reading: String
                val values: String
                when {
                    fields.size == 2 -> { reading = fields[0].trim(); values = fields[1].trim() }
                    fields.size >= 3 && fields[0].trim().toIntOrNull() != null -> {
                        reading = fields[1].trim(); values = fields.drop(2).joinToString("|").trim()
                    }
                    else -> return@forEach
                }
                if (reading.isEmpty() || values.isEmpty()) return@forEach
                val candidates = values.split(',').map(String::trim).filter(String::isNotEmpty).distinct()
                if (candidates.isNotEmpty()) {
                    map[ Kana.toHira(reading) ] = candidates
                    words.addAll(candidates)
                }
            }
        }
    }

    fun has(word: String) = word in words

    fun exact(reading: String): List<String> =
        map[Kana.toHira(reading)]?.sortedByDescending(prefs::freq) ?: emptyList()

    fun predict(reading: String, limit: Int): List<String> {
        val key = Kana.toHira(reading)
        if (key.isEmpty()) return emptyList()
        return map.entries.asSequence()
            .filter { it.key.length > key.length && it.key.startsWith(key) }
            .sortedWith(compareBy<Map.Entry<String, List<String>>> { it.key.length }
                .thenByDescending { entry -> entry.value.maxOfOrNull(prefs::freq) ?: 0 })
            .take(limit)
            .mapNotNull { entry -> entry.value.maxByOrNull(prefs::freq) }
            .toList()
    }

    /** Candidate words for a reading; suitable as a replaceable engine backend. */
    fun convert(reading: String, limit: Int = 16): List<String> {
        val key = Kana.toHira(reading)
        if (key.isEmpty()) return emptyList()
        val result = LinkedHashSet<String>()
        result.addAll(exact(key))

        // Combine dictionary entries into contiguous segments for compound readings (e.g. にほん + ご).
        // Bounded search avoids unbounded work on long compositions and retains the complete reading.
        fun segment(offset: Int, built: String, depth: Int) {
            if (result.size >= limit || depth > 8) return
            if (offset == key.length) {
                if (built.isNotEmpty()) result.add(built)
                return
            }
            for (end in key.length downTo offset + 1) {
                val entry = map[key.substring(offset, end)] ?: continue
                for (word in entry.sortedByDescending(prefs::freq).take(3)) {
                    segment(end, built + word, depth + 1)
                    if (result.size >= limit) return
                }
            }
        }
        segment(0, "", 0)
        return result.sortedByDescending(prefs::freq).take(limit)
    }
}
