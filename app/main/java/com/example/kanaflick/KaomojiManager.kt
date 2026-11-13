package com.example.kanaflick

import android.content.Context

class KaomojiManager(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("kaomoji", Context.MODE_PRIVATE)

    val categories = linkedMapOf(
        "Happy" to listOf("(⁠◕⁠ᴗ⁠◕⁠✿⁠)", "(⁠≧⁠▽⁠≦⁠)", "(＾▽＾)", "٩(◕‿◕｡)۶"),
        "Sad" to listOf("(⁠╥⁠﹏⁠╥⁠)", "(；＿；)", "(｡•́︿•̀｡)", "ಥ_ಥ"),
        "Love" to listOf("(⁠♡⁠ω⁠♡⁠)", "(っ˘з(˘⌣˘ )", "♡(˃͈ દ ˂͈ ༶ )"),
        "Angry" to listOf("(＃`Д´)", "( ` ω ´ )", "ヽ( `д´*)ノ"),
        "Cute" to listOf("(ᵔᴥᵔ)", "(｡･ω･｡)", "ฅ(＾・ω・＾ฅ)"),
        "Surprised" to listOf("(・o・)", "Σ(°ロ°)", "w(°ｏ°)w"),
        "Shrug" to listOf("¯\\_(ツ)_/¯", "┐(￣ヘ￣)┌", "╮(￣～￣)╭"),
        "Actions" to listOf("ᕕ( ᐛ )ᕗ", "(ง •̀_•́)ง", "ε=ε=ε=┌(；￣▽￣)┘")
    )

    fun favorites(): Set<String> = HashSet(prefs.getStringSet(KEY_FAVORITES, emptySet()) ?: emptySet())

    fun toggleFavorite(value: String) {
        val values = favorites().toMutableSet()
        if (!values.add(value)) values.remove(value)
        prefs.edit().putStringSet(KEY_FAVORITES, values).apply()
    }

    private companion object {
        const val KEY_FAVORITES = "favorites"
    }
}
