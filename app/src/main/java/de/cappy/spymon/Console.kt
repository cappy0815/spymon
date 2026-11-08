package de.cappy.spymon

import android.os.Handler
import android.os.Looper
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.ScrollView
import android.widget.TextView

object Col {
    const val BG = 0xFF0E0B09.toInt()
    const val PANEL = 0xFF1A1411.toInt()
    const val ORANGE = 0xFFFF8A2B.toInt()
    const val ORANGE_DIM = 0xFFB35F1C.toInt()
    const val FG = 0xFFE8E0D8.toInt()
    const val DIM = 0xFF7D6E62.toInt()
    const val RED = 0xFFFF5252.toInt()
    const val YEL = 0xFFFFC145.toInt()
    const val GRN = 0xFF8FD47E.toInt()
}

/** Konsolen-Ausgabe: farbige Zeilen, Auto-Scroll, Klartext-Log für den Bericht. */
class Console(private val tv: TextView, private val sv: ScrollView) {
    private val h = Handler(Looper.getMainLooper())
    val log = StringBuilder()

    /** Eine Zeile aus mehreren farbigen Teilen. */
    fun parts(vararg p: Pair<String, Int>) {
        val text = p.joinToString("") { it.first }
        synchronized(log) { log.append(text).append('\n') }
        h.post {
            val s = SpannableString(text + "\n")
            var i = 0
            for ((t, c) in p) {
                s.setSpan(ForegroundColorSpan(c), i, i + t.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                i += t.length
            }
            tv.append(s)
            trim()
            sv.post { sv.fullScroll(ScrollView.FOCUS_DOWN) }
        }
    }

    fun line(t: String = "", c: Int = Col.FG) = parts(t to c)
    fun dim(t: String) = line(t, Col.DIM)
    fun head(t: String) = parts("● " to Col.ORANGE, t to Col.FG)
    fun ok(t: String) = parts("  ✓ " to Col.GRN, t to Col.FG)
    fun warn(t: String) = parts("  ! " to Col.YEL, t to Col.YEL)
    fun err(t: String) = parts("  ✗ " to Col.RED, t to Col.RED)
    fun echo(cmd: String) = parts("❯ " to Col.ORANGE, cmd to Col.FG)
    fun sub(t: String) = parts("  ⎿ " to Col.DIM, t to Col.DIM)

    fun clear() {
        h.post { tv.text = "" }
    }

    private fun trim() {
        // Bildschirm nicht endlos wachsen lassen (Log bleibt vollständig)
        if (tv.lineCount > 3000) {
            val t = tv.text
            val cut = t.length / 3
            tv.text = t.subSequence(cut, t.length)
        }
    }
}

enum class Src(val tag: String) { WLAN("WLAN"), BLE("BLE"), BT("BT"), LAN("LAN") }

/** Ein Fund. level: 0 = harmlos, 1 = unklar, 2 = verdächtig */
class Finding(val src: Src, val key: String) {
    var id = 0
    var title = ""
    var detail = ""
    var rssi: Int? = null
    var level = 0
    val reasons = mutableListOf<String>()
}

class Findings {
    private var next = 1
    val all = mutableListOf<Finding>()

    @Synchronized
    fun put(f: Finding): Finding {
        val old = all.firstOrNull { it.src == f.src && it.key == f.key }
        if (old != null) {
            old.title = f.title; old.detail = f.detail; old.rssi = f.rssi; old.level = f.level
            old.reasons.clear(); old.reasons.addAll(f.reasons)
            return old
        }
        f.id = next++
        all.add(f)
        return f
    }

    @Synchronized
    fun byId(id: Int) = all.firstOrNull { it.id == id }

    @Synchronized
    fun reset() { all.clear(); next = 1 }
}

object Fmt {
    fun tag(level: Int): Pair<String, Int> = when (level) {
        2 -> "[!!]" to Col.RED
        1 -> "[ ?]" to Col.YEL
        else -> "[ok]" to Col.GRN
    }

    fun bars(rssi: Int?, n: Int = 5): String {
        if (rssi == null) return ""
        val v = ((rssi + 95).coerceIn(0, 60) * n) / 60
        return "▮".repeat(v) + "▯".repeat(n - v)
    }

    fun dist(rssi: Int?): String = when {
        rssi == null -> ""
        rssi >= -50 -> "sehr nah"
        rssi >= -65 -> "nah"
        rssi >= -80 -> "mittel"
        else -> "weit"
    }

    fun pad(s: String, n: Int) = if (s.length >= n) s.take(n) else s + " ".repeat(n - s.length)

    /** Fund im Konsolen-Format ausgeben */
    fun print(c: Console, f: Finding) {
        val t = tag(f.level)
        val r = f.rssi?.let { "${it}dBm ${bars(it)}" } ?: ""
        c.parts(t, " #${f.id} " to Col.ORANGE, pad(f.title, 22) to Col.FG, " $r" to Col.DIM)
        if (f.detail.isNotBlank()) c.line("     ${f.detail}", Col.DIM)
        for (x in f.reasons) c.parts("     → " to t.second, x to Col.FG)
    }
}
