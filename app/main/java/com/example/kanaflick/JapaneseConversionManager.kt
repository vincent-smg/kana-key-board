package com.example.kanaflick

/**
 * Provides conversion candidates from the bundled reading dictionary.
 *
 * This is deliberately not described as Mozc: Mozc's current Android library must be built
 * separately with Bazel and the Android NDK, and no Mozc native library is bundled here.
 */
class JapaneseConversionManager(private val dictionary: Dictionary) {
    fun candidates(reading: String, limit: Int = 20): List<String> {
        val hiragana = Kana.toHira(reading)
        if (hiragana.isBlank()) return emptyList()
        return linkedSetOf(hiragana).apply {
            addAll(dictionary.exact(hiragana))
            addAll(dictionary.predict(hiragana, limit))
        }.take(limit)
    }

    fun isDictionaryCandidate(candidate: String): Boolean = dictionary.has(candidate)
}
