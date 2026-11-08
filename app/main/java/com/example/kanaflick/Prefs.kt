package com.example.kanaflick

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color

class Prefs(ctx: Context) {
    private val p = ctx.applicationContext.getSharedPreferences("kana", Context.MODE_PRIVATE)
    var theme: Int                // 0 system, 1 light, 2 dark
        get() = p.getInt("theme", 0)
        set(v) { p.edit().putInt("theme", v).apply() }
    var haptic: Boolean
        get() = p.getBoolean("haptic", true)
        set(v) { p.edit().putBoolean("haptic", v).apply() }
    var showRomaji: Boolean
        get() = p.getBoolean("romaji", true)
        set(v) { p.edit().putBoolean("romaji", v).apply() }
    var keyH: Int                 // dp per key row
        get() = p.getInt("keyH", 56)
        set(v) { p.edit().putInt("keyH", v).apply() }
    var kanaSp: Int
        get() = p.getInt("kanaSp", 22)
        set(v) { p.edit().putInt("kanaSp", v).apply() }
    var romajiSp: Int
        get() = p.getInt("romajiSp", 10)
        set(v) { p.edit().putInt("romajiSp", v).apply() }
    var useBg: Boolean
        get() = p.getBoolean("useBg", false)
        set(v) { p.edit().putBoolean("useBg", v).apply() }
    var bgStamp: Long             // changes when a new image is saved so the keyboard reloads
        get() = p.getLong("bgStamp", 0)
        set(v) { p.edit().putLong("bgStamp", v).apply() }
    var bgAlpha: Int              // background image opacity, percent
        get() = p.getInt("bgAlpha", 100)
        set(v) { p.edit().putInt("bgAlpha", v).apply() }
    var alphaLast: Boolean        // false: #AARRGGBB (Android), true: #RRGGBBAA (web style)
        get() = p.getBoolean("alphaLast", false)
        set(v) { p.edit().putBoolean("alphaLast", v).apply() }

    fun freq(w: String) = p.getInt("f_$w", 0)
    fun bump(w: String) { p.edit().putInt("f_$w", freq(w) + 1).apply() }

    fun parse(s: String): Int? {
        var h = s.trim().removePrefix("#")
        if (!Regex("[0-9a-fA-F]{6}|[0-9a-fA-F]{8}").matches(h)) return null
        if (h.length == 8 && alphaLast) h = h.substring(6) + h.substring(0, 6)
        return Color.parseColor("#$h")
    }
    fun colorText(k: String) = p.getString("c_$k", "") ?: ""
    fun setColorText(k: String, s: String) { p.edit().putString("c_$k", s).apply() }
    fun color(k: String): Int? = colorText(k).let { if (it.isEmpty()) null else parse(it) }

    fun hidden(id: String) = id in (p.getStringSet("hidden", emptySet()) ?: emptySet())
    fun setHidden(id: String, h: Boolean) {
        val s = HashSet(p.getStringSet("hidden", emptySet()) ?: emptySet<String>())
        if (h) s.add(id) else s.remove(id)
        p.edit().putStringSet("hidden", s).apply()
    }
    fun resetLook() {
        val e = p.edit()
        for (k in p.all.keys) if (k.startsWith("c_") || k == "hidden") e.remove(k)
        e.putBoolean("useBg", false).apply()
    }
    fun listen(l: SharedPreferences.OnSharedPreferenceChangeListener) = p.registerOnSharedPreferenceChangeListener(l)

    companion object {
        val KEY_LABELS = listOf(
            "k0" to "あ key", "k1" to "か key", "k2" to "さ key", "k3" to "た key", "k4" to "な key", "k5" to "は key",
            "k6" to "ま key", "k7" to "や key", "k8" to "ら key", "k9" to "わ key", "k10" to "、。？！ key",
            "emoji" to "Emoji key", "sym" to "☆123 key", "kata" to "ア/あ key", "abc" to "ABC key", "mod" to "小゛゜ key",
            "bs" to "Backspace", "sp" to "Space", "ent" to "Enter", "globe" to "Keyboard switch button (🌐)"
        )
    }
}
