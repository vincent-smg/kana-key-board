package com.example.kanaflick

import android.content.Context

/** Small bundled reading -> words dictionary (assets/dict.txt, "reading|word1,word2"). Not Mozc. */
class Dictionary(ctx: Context, private val prefs: Prefs) {
    private val map = HashMap<String, List<String>>()
    private val words = HashSet<String>()

    init {
        ctx.assets.open("dict.txt").bufferedReader().useLines { ls ->
            ls.forEach { l ->
                val t = l.split('|')
                if (t.size == 2) { val w = t[1].split(','); map[t[0]] = w; words.addAll(w) }
            }
        }
    }

    fun has(w: String) = w in words
    fun exact(r: String): List<String> = (map[r] ?: emptyList()).sortedByDescending { prefs.freq(it) }
    fun predict(r: String, n: Int): List<String> =
        map.entries.filter { it.key.length > r.length && it.key.startsWith(r) }
            .sortedBy { it.key.length }.take(n)
            .map { e -> e.value.maxByOrNull { prefs.freq(it) } ?: e.value[0] }
}
