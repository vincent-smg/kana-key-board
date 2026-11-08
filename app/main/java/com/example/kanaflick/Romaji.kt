package com.example.kanaflick

/** Romaji -> hiragana state machine for the QWERTY "JP" mode. */
object Romaji {
    val T: Map<String, String> = HashMap<String, String>().apply {
        fun row(c: String, k: String) { "aiueo".forEachIndexed { i, v -> put(c + v, k[i].toString()) } }
        row("", "あいうえお"); row("k", "かきくけこ"); row("s", "さしすせそ"); row("t", "たちつてと")
        row("n", "なにぬねの"); row("h", "はひふへほ"); row("m", "まみむめも"); row("r", "らりるれろ")
        row("g", "がぎぐげご"); row("z", "ざじずぜぞ"); row("d", "だぢづでど"); row("b", "ばびぶべぼ"); row("p", "ぱぴぷぺぽ")
        put("ya", "や"); put("yu", "ゆ"); put("yo", "よ"); put("wa", "わ"); put("wo", "を"); put("n'", "ん")
        put("shi", "し"); put("chi", "ち"); put("tsu", "つ"); put("fu", "ふ"); put("ji", "じ")
        put("si", "し"); put("ti", "ち"); put("tu", "つ"); put("hu", "ふ"); put("zi", "じ")
        put("she", "しぇ"); put("che", "ちぇ"); put("je", "じぇ")
        put("fa", "ふぁ"); put("fi", "ふぃ"); put("fe", "ふぇ"); put("fo", "ふぉ")
        for ((c, k) in listOf("ky" to "き", "sh" to "し", "ch" to "ち", "ny" to "に", "hy" to "ひ", "my" to "み",
            "ry" to "り", "gy" to "ぎ", "j" to "じ", "by" to "び", "py" to "ぴ")) {
            put(c + "a", k + "ゃ"); put(c + "u", k + "ゅ"); put(c + "o", k + "ょ")
        }
        put("xtu", "っ"); put("ltu", "っ"); put("xya", "ゃ"); put("xyu", "ゅ"); put("xyo", "ょ")
        put("xa", "ぁ"); put("xi", "ぃ"); put("xu", "ぅ"); put("xe", "ぇ"); put("xo", "ぉ")
    }

    fun feed(rom: StringBuilder, out: StringBuilder, ch: Char) {
        rom.append(ch.lowercaseChar())
        while (rom.isNotEmpty()) {
            val s = rom.toString()
            T[s]?.let { out.append(it); rom.setLength(0); return }
            if (s.length >= 2 && s[0] == s[1] && s[0] in "bcdfghjklmpqrstvwxyz") { out.append('っ'); rom.deleteCharAt(0); continue }
            if (s.length >= 3 && s[0] == 'n' && s[1] == 'n') { out.append('ん'); rom.deleteCharAt(0); continue }
            if (s.length >= 2 && s[0] == 'n' && s[1] != 'n' && s[1] !in "aiueoy'") { out.append('ん'); rom.deleteCharAt(0); continue }
            if (s == "nn" || T.keys.any { it.startsWith(s) }) return
            out.append(s[0]); rom.deleteCharAt(0)
        }
    }
}
