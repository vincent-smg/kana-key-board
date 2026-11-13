package com.example.kanaflick

class SuggestionManager(private val conversion: JapaneseConversionManager) {
    fun suggestions(composing: String, pendingRomaji: String, limit: Int): List<String> {
        if (composing.isEmpty() && pendingRomaji.isEmpty()) return emptyList()
        val pendingIsN = pendingRomaji == "n" || pendingRomaji == "nn"
        if (pendingRomaji.isNotEmpty() && !pendingIsN) {
            return listOf(composing + pendingRomaji)
        }

        val reading = composing + if (pendingIsN) "ん" else ""
        if (reading.isEmpty()) return emptyList()
        return linkedSetOf<String>().apply {
            add(reading)
            add(Kana.toHira(reading))
            add(Kana.toKata(reading))
            addAll(conversion.candidates(reading, limit))
        }.take(limit)
    }
}
