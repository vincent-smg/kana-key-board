package com.example.kanaflick

import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.inputmethodservice.InputMethodService
import android.text.InputType
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.io.File

class Th(val bg: Int, val key: Int, val alt: Int, val text: Int, val sub: Int, val acc: Int)

class FlickIME : InputMethodService() {
    private lateinit var prefs: Prefs
    private lateinit var dict: Dictionary
    private lateinit var conversion: JapaneseConversionManager
    private lateinit var suggestionManager: SuggestionManager
    private lateinit var clipboardHistory: ClipboardHistoryManager
    private lateinit var kaomojiManager: KaomojiManager
    private lateinit var th: Th
    private lateinit var bar: LinearLayout
    private lateinit var content: FrameLayout
    private lateinit var expBtn: TextView
    private val comp = StringBuilder()   // kana being composed
    private val rom = StringBuilder()    // pending romaji (QWERTY JP mode)
    private var mode = 0                 // 0 kana, 1 qwerty, 2 symbols, 3 emoji
    private var kata = false
    private var jp = true
    private var shift = false
    private var symPage = 0
    private var expanded = false
    private var returnMode = 0
    private var kaomojiCategory = "Happy"
    private var clipboardAllowed = true
    private val dm get() = resources.displayMetrics
    private val MP = ViewGroup.LayoutParams.MATCH_PARENT
    private fun dp(v: Int) = (v * dm.density).toInt()
    private fun col(s: String) = Color.parseColor(s)

    private val prefL = SharedPreferences.OnSharedPreferenceChangeListener { _, k ->
        if (k?.startsWith("f_") != true && ::bar.isInitialized) setInputView(buildRoot())
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        dict = Dictionary(this, prefs)
        conversion = JapaneseConversionManager(dict)
        suggestionManager = SuggestionManager(conversion)
        clipboardHistory = ClipboardHistoryManager(this) {
            Toast.makeText(this, "Clipboard access is unavailable", Toast.LENGTH_SHORT).show()
        }
        kaomojiManager = KaomojiManager(this)
        prefs.listen(prefL)
    }
    override fun onCreateInputView(): View = buildRoot()

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val v = info.inputType and InputType.TYPE_MASK_VARIATION
        val plain = listOf(InputType.TYPE_TEXT_VARIATION_PASSWORD, InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD,
            InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD, InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS,
            InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS, InputType.TYPE_TEXT_VARIATION_URI)
        val previousClipboardAllowed = clipboardAllowed
        val previousMode = mode
        clipboardAllowed = cls == InputType.TYPE_CLASS_TEXT && v !in plain
        if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) clipboardAllowed = false
        if (clipboardAllowed) clipboardHistory.startObserving() else clipboardHistory.stopObserving()
        if (restarting) {
            if (!clipboardAllowed && mode == 4) mode = returnMode
            if ((previousClipboardAllowed != clipboardAllowed || previousMode != mode) && ::bar.isInitialized) {
                setInputView(buildRoot())
            }
            return
        }

        comp.setLength(0); rom.setLength(0); expanded = false; shift = false
        if (cls == InputType.TYPE_CLASS_NUMBER || cls == InputType.TYPE_CLASS_PHONE) { mode = 2; symPage = 0 }
        else if (cls == InputType.TYPE_CLASS_TEXT && v in plain) { mode = 1; jp = false }
        else { mode = 0; jp = true }
        returnMode = mode
        setInputView(buildRoot())
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        clipboardHistory.stopObserving()
        commitAll()
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        clipboardHistory.stopObserving()
        super.onDestroy()
    }

    override fun onUpdateSelection(os: Int, oe: Int, ns: Int, ne: Int, cs: Int, ce: Int) {
        super.onUpdateSelection(os, oe, ns, ne, cs, ce)
        if ((comp.isNotEmpty() || rom.isNotEmpty()) && (cs == -1 || ns != ce)) { comp.setLength(0); rom.setLength(0); updateBar() }
    }

    // ---------- theme & root ----------
    private fun theme(): Th {
        val dark = when (prefs.theme) {
            1 -> false; 2 -> true
            else -> (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        }
        val d = if (dark) Th(col("#1A1A1C"), col("#3A3A40"), col("#2A2A2D"), col("#F1F0ED"), col("#AAA7A0"), col("#E2553F"))
        else Th(col("#E9E7E2"), Color.WHITE, col("#D5D2CB"), col("#1D1D1F"), col("#6D6A63"), col("#E2553F"))
        return Th(prefs.color("bg") ?: d.bg, prefs.color("key") ?: d.key, prefs.color("func") ?: d.alt,
            prefs.color("text") ?: d.text, prefs.color("romaji") ?: d.sub, prefs.color("go") ?: d.acc)
    }

    private fun buildRoot(): View {
        th = theme()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; background = bgDrawable() }
        val barRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val sv = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        bar = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        sv.addView(bar, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(44)))
        expBtn = barBtn("▾") { toggleExpand() }
        barRow.addView(sv, LinearLayout.LayoutParams(0, dp(44), 1f))
        if (clipboardAllowed) barRow.addView(barBtn("📋") {
            if (mode != 4 && mode != 5) returnMode = mode
            clipboardHistory.refreshFromSystemClipboard()
            mode = 4
            expanded = false
            expBtn.text = "▾"
            showMode()
        }, LinearLayout.LayoutParams(dp(40), dp(44)))
        barRow.addView(barBtn("☺") {
            if (mode != 4 && mode != 5) returnMode = mode
            mode = 5
            expanded = false
            expBtn.text = "▾"
            showMode()
        }, LinearLayout.LayoutParams(dp(40), dp(44)))
        if (!prefs.hidden("globe")) barRow.addView(barBtn("🌐") {
            (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        barRow.addView(barBtn("⚙") {
            startActivity(Intent(this, SettingsActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }, LinearLayout.LayoutParams(dp(44), dp(44)))
        barRow.addView(expBtn, LinearLayout.LayoutParams(dp(44), dp(44)))
        content = FrameLayout(this)
        root.addView(barRow, LinearLayout.LayoutParams(MP, ViewGroup.LayoutParams.WRAP_CONTENT))
        root.addView(content, LinearLayout.LayoutParams(MP, dp(prefs.keyH * 4 + 8)))
        showMode(); updateBar(); return root
    }

    private fun barBtn(t: String, f: () -> Unit) = TextView(this).apply {
        text = t; textSize = 18f; setTextColor(th.text); gravity = Gravity.CENTER; setOnClickListener { f() }
    }

    private fun bgDrawable(): android.graphics.drawable.Drawable {
        val f = File(filesDir, "bg.jpg")
        val bmp = if (prefs.useBg && f.exists()) BitmapFactory.decodeFile(f.path) else null
        return CropBg(bmp, th.bg, prefs.bgAlpha)
    }

    private fun showMode() {
        content.removeAllViews()
        val v = if (expanded) expandView() else when (mode) {
            0 -> kanaView()
            1 -> qwertyView()
            2 -> symView()
            3 -> emojiView()
            4 -> clipboardView()
            else -> kaomojiView()
        }
        content.addView(v, FrameLayout.LayoutParams(MP, MP))
    }

    private fun closePanel() {
        mode = returnMode
        showMode()
    }

    private fun toggleExpand() {
        if (comp.isEmpty() && rom.isEmpty()) return
        expanded = !expanded; expBtn.text = if (expanded) "▴" else "▾"; showMode()
    }

    // ---------- views ----------
    private fun idOf(l: String) = when (l) {
        "⌫" -> "bs"; "␣" -> "sp"; "⏎" -> "ent"; "ABC" -> "abc"; "😀" -> "emoji"; "☆123" -> "sym"; "小゛゜" -> "mod"
        "ア" -> "kata"; "あ" -> if (mode == 0) "kata" else ""
        else -> ""
    }

    private fun kv(label: String = "", kind: Int = 0, small: Boolean = false, rep: Boolean = false, pick: (Int) -> Unit) =
        KeyView(this, prefs, th).also {
            it.label = label; it.kind = kind; it.small = small; it.repeat = rep; it.pick = pick
            val id = idOf(label)
            if (id.isNotEmpty() && prefs.hidden(id)) it.visibility = View.INVISIBLE
        }

    private fun kanaView(): View {
        val g = GridLayout(this).apply { columnCount = 5; rowCount = 4; setPadding(dp(3), dp(3), dp(3), dp(3)) }
        fun add(v: View, c: Int, r: Int, rs: Int = 1) {
            val lp = GridLayout.LayoutParams(GridLayout.spec(r, rs, GridLayout.FILL, 1f), GridLayout.spec(c, 1, GridLayout.FILL, 1f))
            lp.width = 0; lp.height = 0; lp.setMargins(dp(3), dp(3), dp(3), dp(3)); g.addView(v, lp)
        }
        Kana.keys.forEachIndexed { i, k ->
            val ch = k.chars.map { if (kata) Kana.toKata(it) else it }
            val v = kv { idx -> kanaInput(ch[idx]) }
            v.chars = ch; v.subs = k.romaji; v.label = ch[0]
            if (prefs.hidden("k$i")) v.visibility = View.INVISIBLE
            val pos = if (i < 9) Pair(1 + i % 3, i / 3) else if (i == 9) Pair(2, 3) else Pair(3, 3)
            add(v, pos.first, pos.second)
        }
        add(kv("😀", 1, true) { returnMode = mode; mode = 3; showMode() }, 0, 0)
        add(kv("☆123", 1, true) { mode = 2; symPage = 0; showMode() }, 0, 1)
        add(kv(if (kata) "あ" else "ア", 1, true) { kata = !kata; showMode() }, 0, 2)
        add(kv("ABC", 1, true) { mode = 1; showMode() }, 0, 3)
        add(kv("☺", 1, true) { returnMode = mode; mode = 5; showMode() }, 1, 3)
        add(kv("小゛゜", 1, true) { modify() }, 2, 3)
        add(kv("⌫", 1, true, true) { backspace() }, 4, 0)
        add(kv("␣", 1, true) { space() }, 4, 1)
        add(kv("⏎", 2, true) { enter() }, 4, 2, 2)
        return g
    }

    private fun rowOf(vararg items: Pair<View, Float>): LinearLayout {
        val r = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        for ((v, w) in items) {
            val lp = LinearLayout.LayoutParams(0, MP, w); lp.setMargins(dp(3), dp(3), dp(3), dp(3)); r.addView(v, lp)
        }
        return r
    }

    private fun colOf(vararg rows: View): LinearLayout {
        val c = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(3), dp(3), dp(3), dp(3)) }
        rows.forEach { c.addView(it, LinearLayout.LayoutParams(MP, 0, 1f)) }
        return c
    }

    private fun keys(s: String, f: (Char) -> Unit, upper: Boolean = false) =
        s.map { ch -> Pair<View, Float>(kv(if (upper) ch.uppercase() else ch.toString()) { f(ch) }, 1f) }.toTypedArray()

    private fun qwertyView(): View {
        val sp = Pair<View, Float>(View(this), 0.5f)
        return colOf(
            rowOf(*keys("qwertyuiop", ::qwerty, shift)),
            rowOf(sp, *keys("asdfghjkl", ::qwerty, shift), sp),
            rowOf(Pair(kv("⇧", if (shift) 2 else 1, true) { shift = !shift; showMode() }, 1.5f),
                *keys("zxcvbnm", ::qwerty, shift), Pair(kv("⌫", 1, true, true) { backspace() }, 1.5f)),
            rowOf(Pair(kv("☆123", 1, true) { mode = 2; symPage = 0; showMode() }, 1.3f),
                Pair(kv(if (jp) "JP" else "EN", 1, true) { jp = !jp; commitAll(); showMode() }, 1.2f),
                Pair(kv("␣", 0, true) { space() }, 4f),
                Pair(kv("あ", 1, true) { mode = 0; showMode() }, 1.3f),
                Pair(kv("⏎", 2, true) { enter() }, 1.5f))
        )
    }

    private fun symView(): View {
        val p0 = listOf("1234567890", "-/:;()¥&@\"", ".,?!'")
        val p1 = listOf("[]{}#%^*+=", "_\\|~<>€£\$•", ".,?!'")
        val p = if (symPage == 0) p0 else p1
        return colOf(
            rowOf(*keys(p[0], ::symInput)),
            rowOf(*keys(p[1], ::symInput)),
            rowOf(Pair(kv(if (symPage == 0) "#+=" else "123", 1, true) { symPage = 1 - symPage; showMode() }, 1.4f),
                *keys(p[2], ::symInput), Pair(kv("⌫", 1, true, true) { backspace() }, 1.4f)),
            rowOf(Pair(kv("ABC", 1, true) { mode = 1; showMode() }, 1.4f),
                Pair(kv("あ", 1, true) { mode = 0; showMode() }, 1.4f),
                Pair(kv("␣", 0, true) { space() }, 4f),
                Pair(kv("⏎", 2, true) { enter() }, 1.5f))
        )
    }

    private val emoji = ("😀 😃 😄 😁 😆 😅 😂 🤣 😊 😇 🙂 😉 😍 🥰 😘 😋 😛 😜 🤪 😎 🤩 🥳 😏 😒 😞 😔 😟 😕 🙁 😣 😖 😫 " +
        "😩 🥺 😢 😭 😤 😠 😡 🤯 😳 🥵 🥶 😱 😨 😰 😓 🤗 🤔 🤭 🤫 🙄 😬 😴 🤤 😷 🤒 🤕 🤢 🥴 😵 🤠 👍 👎 👏 🙌 🙏 💪 👋 🤝 ✨ 🔥 ⭐ 🌸 🎉 🎌 " +
        "🍣 🍜 🍙 🐱 🐶 🐼 🐰 🦊 🐵 🐻 🌈 ☀️ 🌙 🌊 🚀 🧳 🧠 💡 💊 🧸 🧁 🍰 🍕 🍔 🍟 🥐 🥨 🍦 🍉 🍓 🍇 🥝 🥑 🍋 🍊 🍍 🥥 🥭").split(" ")

    private fun emojiView(): View {
        val sv = ScrollView(this)
        val g = GridLayout(this).apply { columnCount = 8 }
        emoji.forEach { e ->
            val t = TextView(this).apply {
                text = e; textSize = 24f; gravity = Gravity.CENTER
                setOnClickListener { commitAll(); currentInputConnection?.commitText(e, 1) }
            }
            g.addView(t, GridLayout.LayoutParams().apply { width = dm.widthPixels / 8; height = dp(48) })
        }
        sv.addView(g)
        val l = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        l.addView(sv, LinearLayout.LayoutParams(MP, 0, 1f))
        val b = rowOf(Pair(kv("あ", 1, true) { closePanel() }, 1f),
            Pair(kv("☺", 1, true) { mode = 5; kaomojiCategory = "Happy"; showMode() }, 1f),
            Pair(kv("ABC", 1, true) { mode = 1; showMode() }, 1f),
            Pair(kv("⌫", 1, true, true) { backspace() }, 1f))
        l.addView(b, LinearLayout.LayoutParams(MP, dp(prefs.keyH)))
        return l
    }

    private fun clipboardView(): View {
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(6), dp(4), dp(6), dp(4)) }
        val actions = rowOf(
            Pair(barBtn("Keyboard") { closePanel() }, 1f),
            Pair(barBtn("Clear history") { clipboardHistory.clearHistory(); showMode() }, 1f)
        )
        outer.addView(actions, LinearLayout.LayoutParams(MP, dp(42)))

        val scroll = ScrollView(this)
        val entries = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        clipboardHistory.entries().forEach { entry ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            val value = TextView(this).apply {
                text = entry.text
                textSize = 16f
                setTextColor(th.text)
                setPadding(dp(10), dp(8), dp(6), dp(8))
                maxLines = 2
                setOnClickListener {
                    commitAll()
                    currentInputConnection?.commitText(entry.text, 1)
                    closePanel()
                }
            }
            row.addView(value, LinearLayout.LayoutParams(0, MP, 1f))
            row.addView(barBtn(if (entry.pinned) "★" else "☆") {
                clipboardHistory.togglePinned(entry.text)
                showMode()
            }, LinearLayout.LayoutParams(dp(40), dp(44)))
            row.addView(barBtn("×") {
                clipboardHistory.delete(entry.text)
                showMode()
            }, LinearLayout.LayoutParams(dp(40), dp(44)))
            entries.addView(row, LinearLayout.LayoutParams(MP, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        if (entries.childCount == 0) {
            entries.addView(TextView(this).apply {
                text = "No copied text"
                textSize = 16f
                gravity = Gravity.CENTER
                setTextColor(th.sub)
            }, LinearLayout.LayoutParams(MP, dp(72)))
        }
        scroll.addView(entries)
        outer.addView(scroll, LinearLayout.LayoutParams(MP, 0, 1f))
        return outer
    }

    private fun kaomojiView(): View {
        val outer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(4), dp(2), dp(4), dp(2)) }
        val categoryScroll = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val categories = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val names = listOf("Favorites") + kaomojiManager.categories.keys
        names.forEach { name ->
            categories.addView(barBtn(if (name == kaomojiCategory) "[$name]" else name) {
                kaomojiCategory = name
                showMode()
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40)))
        }
        categoryScroll.addView(categories)
        outer.addView(categoryScroll, LinearLayout.LayoutParams(MP, dp(40)))

        val values = if (kaomojiCategory == "Favorites") kaomojiManager.favorites().toList()
            else kaomojiManager.categories[kaomojiCategory].orEmpty()
        val scroll = ScrollView(this)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        values.forEach { face ->
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
            row.addView(TextView(this).apply {
                text = face
                textSize = 20f
                gravity = Gravity.CENTER
                setTextColor(th.text)
                setOnClickListener {
                    commitAll()
                    currentInputConnection?.commitText(face, 1)
                }
            }, LinearLayout.LayoutParams(0, dp(48), 1f))
            row.addView(barBtn(if (face in kaomojiManager.favorites()) "★" else "☆") {
                kaomojiManager.toggleFavorite(face)
                showMode()
            }, LinearLayout.LayoutParams(dp(48), dp(48)))
            list.addView(row, LinearLayout.LayoutParams(MP, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        if (values.isEmpty()) {
            list.addView(TextView(this).apply {
                text = "No kaomoji in this category"
                gravity = Gravity.CENTER
                setTextColor(th.sub)
            }, LinearLayout.LayoutParams(MP, dp(72)))
        }
        scroll.addView(list)
        outer.addView(scroll, LinearLayout.LayoutParams(MP, 0, 1f))
        outer.addView(rowOf(
            Pair(barBtn("Keyboard") { closePanel() }, 1f),
            Pair(barBtn("ABC") { mode = 1; showMode() }, 1f)
        ), LinearLayout.LayoutParams(MP, dp(prefs.keyH)))
        return outer
    }

    private fun expandView(): View {
        val sv = ScrollView(this)
        val g = GridLayout(this).apply { columnCount = 3; setPadding(dp(6), dp(6), dp(6), dp(6)) }
        candidates(30).forEach { t ->
            g.addView(chip(t, true), GridLayout.LayoutParams().apply {
                width = dm.widthPixels / 3 - dp(12); height = dp(48); setMargins(dp(3), dp(3), dp(3), dp(3))
            })
        }
        sv.addView(g)
        return sv
    }

    // ---------- suggestion bar ----------
    private fun chip(t: String, bg: Boolean) = TextView(this).apply {
        text = t; textSize = 18f; setTextColor(th.text); gravity = Gravity.CENTER; setPadding(dp(14), 0, dp(14), 0)
        if (bg) background = GradientDrawable().apply { cornerRadius = dp(10).toFloat(); setColor(th.alt) }
        setOnClickListener { choose(t) }
    }

    private fun updateBar() {
        if (!::bar.isInitialized) return
        bar.removeAllViews()
        val cs = candidates(8)
        cs.forEachIndexed { i, t ->
            bar.addView(chip(t, i == 0), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, MP).apply { setMargins(dp(2), dp(4), dp(2), dp(4)) })
        }
        (bar.parent as? HorizontalScrollView)?.scrollTo(0, 0)
        if (cs.isEmpty() && expanded) { expanded = false; expBtn.text = "▾"; showMode() }
    }

    /** Order: as typed, hiragana, katakana, dictionary kanji/words, then predictions. */
    private fun candidates(extra: Int): List<String> {
        return suggestionManager.suggestions(comp.toString(), rom.toString(), extra)
    }

    private fun choose(w: String) {
        val ic = currentInputConnection ?: return
        ic.commitText(w, 1)
        if (dict.has(w)) prefs.bump(w)
        comp.setLength(0); rom.setLength(0)
        if (expanded) { expanded = false; expBtn.text = "▾"; showMode() }
        if (mode == 4 || mode == 5) closePanel()
        updateBar()
    }

    // ---------- input logic ----------
    private fun refresh() {
        val ic = currentInputConnection ?: return
        val t = comp.toString() + rom
        ic.setComposingText(t, 1)
        updateBar()
    }

    private fun flushRom() {
        if (rom.isEmpty()) return
        val s = rom.toString(); rom.setLength(0)
        comp.append(if (s == "n" || s == "nn") "ん" else s)
    }

    private fun commitAll() {
        flushRom()
        val t = comp.toString(); comp.setLength(0)
        if (t.isNotEmpty()) currentInputConnection?.commitText(t, 1)
        updateBar()
    }

    private fun kanaInput(ch: String) {
        if (Kana.DIRECT.contains(ch)) { commitAll(); currentInputConnection?.commitText(ch, 1); return }
        flushRom(); comp.append(ch); refresh()
    }

    private fun qwerty(ch: Char) {
        if (mode == 1 && jp && !shift) { Romaji.feed(rom, comp, ch); refresh() }
        else { commitAll(); currentInputConnection?.commitText((if (shift) ch.uppercaseChar() else ch).toString(), 1) }
        if (shift) { shift = false; showMode() }
    }

    private fun symInput(ch: Char) { commitAll(); currentInputConnection?.commitText(ch.toString(), 1) }

    private fun modify() {
        fun tr(ch: Char): Char? {
            val h = Kana.toHira(ch.toString())[0]
            val cy = Kana.CY.firstOrNull { h in it } ?: return null
            val n = cy[(cy.indexOf(h) + 1) % cy.length]
            return if (ch != h) Kana.toKata(n.toString())[0] else n
        }
        if (comp.isNotEmpty()) {
            val n = tr(comp.last()) ?: return
            comp.setLength(comp.length - 1); comp.append(n); refresh(); return
        }
        val ic = currentInputConnection ?: return
        val b = ic.getTextBeforeCursor(1, 0)?.toString() ?: return
        if (b.length != 1) return
        val n = tr(b[0]) ?: return
        ic.deleteSurroundingText(1, 0); ic.commitText(n.toString(), 1)
    }

    private fun backspace() {
        if (rom.isNotEmpty()) rom.setLength(rom.length - 1)
        else if (comp.isNotEmpty()) comp.setLength(comp.length - 1)
        else { sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL); return }
        refresh()
    }

    private fun space() {
        if (comp.isEmpty() && rom.isEmpty()) { currentInputConnection?.commitText(" ", 1); return }
        val base = candidates(8).firstOrNull() ?: return
        val reading = Kana.toHira(base)
        val converted = conversion.candidates(reading, 30).firstOrNull { it != reading }
        choose(converted ?: base)
    }

    private fun enter() {
        if (comp.isNotEmpty() || rom.isNotEmpty()) { commitAll(); return }
        val ei = currentInputEditorInfo
        val a = ei.imeOptions and EditorInfo.IME_MASK_ACTION
        if ((ei.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) == 0 &&
            a != EditorInfo.IME_ACTION_NONE && a != EditorInfo.IME_ACTION_UNSPECIFIED) currentInputConnection?.performEditorAction(a)
        else sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
    }
}
