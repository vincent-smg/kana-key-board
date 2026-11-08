package com.example.kanaflick

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputFilter
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.*
import java.io.File

class SettingsActivity : Activity() {
    private lateinit var p: Prefs

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        p = Prefs(this)
        val d = resources.displayMetrics.density
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val pad = (20 * d).toInt(); setPadding(pad, pad, pad, pad)
        }
        fun text(t: String, size: Float = 16f) = TextView(this).apply { text = t; textSize = size; setPadding(0, (12 * d).toInt(), 0, 0) }.also { col.addView(it) }
        fun button(t: String, f: () -> Unit) = Button(this).apply { text = t; setOnClickListener { f() } }.also { col.addView(it) }
        fun switch(t: String, v: Boolean, f: (Boolean) -> Unit) =
            col.addView(Switch(this).apply { text = t; isChecked = v; setOnCheckedChangeListener { _, c -> f(c) } })
        fun seek(label: String, min: Int, max: Int, get: () -> Int, set: (Int) -> Unit) {
            val t = text("$label: ${get()}")
            val s = SeekBar(this); s.min = min; s.max = max; s.progress = get()
            s.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, v: Int, u: Boolean) { set(v); t.text = "$label: $v" }
                override fun onStartTrackingTouch(sb: SeekBar?) {}
                override fun onStopTrackingTouch(sb: SeekBar?) {}
            })
            col.addView(s)
        }

        text("Kana Flick (Romaji)", 24f)
        button("1. Enable keyboard") { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        button("2. Switch to this keyboard") { (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker() }
        col.addView(EditText(this).apply { hint = "Tap here to preview the keyboard" })

        text("General", 20f)
        text("Theme (used for default colors)")
        val rg = RadioGroup(this)
        listOf("System", "Light", "Dark").forEachIndexed { i, n ->
            rg.addView(RadioButton(this).apply { text = n; id = 100 + i; isChecked = p.theme == i })
        }
        rg.setOnCheckedChangeListener { _, id -> p.theme = id - 100 }
        col.addView(rg)
        switch("Haptic feedback", p.haptic) { p.haptic = it }
        switch("Show romaji under keys", p.showRomaji) { p.showRomaji = it }
        seek("Key height (dp)", 44, 76, { p.keyH }, { p.keyH = it })
        seek("Character size (sp)", 14, 32, { p.kanaSp }, { p.kanaSp = it })
        seek("Romaji size (sp)", 7, 16, { p.romajiSp }, { p.romajiSp = it })

        text("Background image", 20f)
        button("Choose image") {
            startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).apply { addCategory(Intent.CATEGORY_OPENABLE); type = "image/*" }, 1)
        }
        button("Remove image") { p.useBg = false; File(filesDir, "bg.jpg").delete(); p.bgStamp = System.currentTimeMillis() }
        seek("Image opacity (%)", 10, 100, { p.bgAlpha }, { p.bgAlpha = it })

        text("Colors (hex)", 20f)
        text("Use #RRGGBB or with opacity #AARRGGBB (e.g. #80FFFFFF = 50% white; 00 = invisible, FF = solid). Leave empty for the default. Turn on the switch below if you prefer #RRGGBBAA.", 13f)
        switch("Opacity at the end (#RRGGBBAA)", p.alphaLast) { p.alphaLast = it }
        listOf("bg" to "Keyboard background", "key" to "Key (button) color", "func" to "Function key color",
            "go" to "Enter key / flick highlight color", "text" to "Letter color", "romaji" to "Romaji color").forEach { (k, label) ->
            text(label)
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val et = EditText(this).apply {
                hint = "default"; setSingleLine(); filters = arrayOf(InputFilter.LengthFilter(9)); setText(p.colorText(k))
            }
            val sw = View(this).apply { p.color(k)?.let { setBackgroundColor(it) } }
            et.addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) {
                    val t = s.toString().trim()
                    if (t.isEmpty()) { p.setColorText(k, ""); sw.setBackgroundColor(Color.TRANSPARENT); et.error = null; return }
                    val c = p.parse(t)
                    if (c == null) et.error = "Use #RRGGBB or #AARRGGBB"
                    else { p.setColorText(k, t); sw.setBackgroundColor(c); et.error = null }
                }
            })
            row.addView(et, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            row.addView(sw, LinearLayout.LayoutParams((40 * d).toInt(), (32 * d).toInt()).apply { leftMargin = (12 * d).toInt() })
            col.addView(row)
        }

        text("Visible keys", 20f)
        text("A hidden key stays in its place but is invisible and does not respond. Letter keys on QWERTY and the ⚙ button can't be hidden.", 13f)
        Prefs.KEY_LABELS.forEach { (id, label) -> switch(label, !p.hidden(id)) { p.setHidden(id, !it) } }

        button("Reset colors and visibility") { p.resetLook(); recreate() }
        setContentView(ScrollView(this).apply { addView(col) })
    }

    override fun onActivityResult(req: Int, res: Int, data: Intent?) {
        super.onActivityResult(req, res, data)
        val uri = data?.data
        if (req != 1 || res != RESULT_OK || uri == null) return
        try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (maxOf(o.outWidth, o.outHeight) / s > 1600) s *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = s }
            val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o2) }
            if (bmp == null) { Toast.makeText(this, "Could not read that image", Toast.LENGTH_SHORT).show(); return }
            File(filesDir, "bg.jpg").outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            p.useBg = true; p.bgStamp = System.currentTimeMillis()
            Toast.makeText(this, "Background set", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Could not load image", Toast.LENGTH_SHORT).show()
        }
    }
}
