package com.example.kanaflick

/**
 * Lightweight conversion layer for Kana -> candidate generation.
 *
 * This is intentionally modular so the existing Romaji-to-Hiragana input can be kept,
 * while the candidate ranking logic can later be upgraded to a fuller Mozc-like engine.
 */
class JapaneseConverter(private val dictionary: Dictionary, private val prefs: Prefs) {

    fun buildCandidates(raw: String, limit: Int = 8): List<String> {
        val text = raw.trim()
        if (text.isEmpty()) return emptyList()

        val base = Kana.toHira(text)
        val candidates = LinkedHashSet<String>()

        candidates.add(text)
        candidates.add(base)
        candidates.add(Kana.toKata(base))
        candidates.addAll(dictionary.exact(base))
        candidates.addAll(dictionary.predict(base, limit * 3))

        val fallback = listOf(
            "こんにちは",
            "ありがとう",
            "こんばんは",
            "おはようございます",
            "すみません",
            "日本語",
            "学校",
            "明日",
            "名前",
            "友達",
            "大丈夫",
            "ありがとう",
            "こんばんは"
        )
        fallback.filter { it.contains(base) || base.contains(it) || it.startsWith(base) }
            .forEach(candidates::add)

        return candidates
            .filter { it.isNotBlank() }
            .sortedWith(compareByDescending<String> { score(it, base) }.thenByDescending { it.length })
            .take(limit)
    }

    private fun score(candidate: String, base: String): Int {
        var score = prefs.freq(candidate)
        if (candidate == base) score += 200
        if (candidate.contains(base)) score += 100
        if (base.contains(candidate)) score += 40
        if (candidate.length > 1) score += candidate.length * 2
        return score
    }
}
