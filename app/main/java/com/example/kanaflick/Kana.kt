package com.example.kanaflick

object Kana {
    /** chars/romaji order: tap, left, up, right, down */
    class K(val chars: List<String>, val romaji: List<String>)
    private fun k(c: String, r: String) = K(c.map { it.toString() }, r.split(" "))

    val keys = listOf(
        k("あいうえお", "a i u e o"),
        k("かきくけこ", "ka ki ku ke ko"),
        k("さしすせそ", "sa shi su se so"),
        k("たちつてと", "ta chi tsu te to"),
        k("なにぬねの", "na ni nu ne no"),
        k("はひふへほ", "ha hi fu he ho"),
        k("まみむめも", "ma mi mu me mo"),
        K(listOf("や", "「", "ゆ", "」", "よ"), listOf("ya", "[", "yu", "]", "yo")),
        k("らりるれろ", "ra ri ru re ro"),
        k("わをんー〜", "wa wo n - ~"),
        K(listOf("、", "。", "？", "！", "…"), listOf(",", ".", "?", "!", "..."))
    )

    /** Dakuten / handakuten / small-kana cycles used by the 小゛゜ key. */
    val CY = listOf(
        "かが", "きぎ", "くぐ", "けげ", "こご", "さざ", "しじ", "すず", "せぜ", "そぞ",
        "ただ", "ちぢ", "つっづ", "てで", "とど", "はばぱ", "ひびぴ", "ふぶぷ", "へべぺ", "ほぼぽ",
        "あぁ", "いぃ", "うぅゔ", "えぇ", "おぉ", "やゃ", "ゆゅ", "よょ", "わゎ"
    )
    const val DIRECT = "、。？！…「」〜"

    fun toKata(s: String) = s.map { if (it in 'ぁ'..'ゖ') it + 0x60 else it }.joinToString("")
    fun toHira(s: String) = s.map { if (it in 'ァ'..'ヶ') it - 0x60 else it }.joinToString("")
}
