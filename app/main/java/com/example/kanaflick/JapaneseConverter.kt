package com.example.kanaflick

/**
 * Candidate-generation boundary for Japanese conversion. The current backend uses the
 * bundled reading dictionary; a full Mozc-compatible engine can replace this class
 * without changing the IME's Romaji or UI code.
 */
class JapaneseConverter(private val dictionary: Dictionary, private val prefs: Prefs) {
    fun buildCandidates(raw: String, limit: Int = 8): List<String> {
        val input = raw.trim()
        if (input.isEmpty() || limit <= 0) return emptyList()
        val reading = Kana.toHira(input)
        val out = LinkedHashSet<String>()
        out.add(input)
        out.add(reading)
        out.add(Kana.toKata(reading))
        out.addAll(dictionary.convert(reading, limit))
        out.addAll(dictionary.predict(reading, limit))
        return out.filter(String::isNotBlank).take(limit)
    }
}
