package de.cappy.spymon

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.wifi.ScanResult
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Ein WLAN-Zugangspunkt, so wie ihn die Netze-Ansicht braucht (aus ScanResult + Einstufung). */
class Net(
    val ssid: String?,          // null = verstecktes Netz
    val bssid: String,
    val rssi: Int,
    val freq: Int,
    val widthMhz: Int?,
    val security: String,
    val standard: String?,      // "Wi-Fi 6" usw. (ab Android 11)
    val vendor: String?,
    val finding: Finding?       // für Einstufung und »Aufspüren«
) {
    val band get() = when { freq >= 5925 -> "6 GHz"; freq >= 4900 -> "5 GHz"; else -> "2,4 GHz" }
    val bandKey get() = when { freq >= 5925 -> 6; freq >= 4900 -> 5; else -> 2 }
    val channel get() = when {
        freq == 2484 -> 14
        freq in 2412..2472 -> (freq - 2407) / 5
        freq in 5925..7125 -> (freq - 5950) / 5
        freq in 4900..5899 -> (freq - 5000) / 5
        else -> 0
    }
    val level get() = finding?.level ?: 0
    val name get() = ssid ?: "‹verstecktes Netz›"

    companion object {
        /** Signal in Prozent (−100 dBm = 0 %, −50 dBm und besser = 100 %) */
        fun pct(rssi: Int) = ((rssi + 100) * 2).coerceIn(0, 100)

        /** Klartext zur Signalstärke */
        fun quality(rssi: Int) = when {
            rssi >= -55 -> "sehr gut"
            rssi >= -67 -> "gut"
            rssi >= -75 -> "mäßig"
            rssi >= -85 -> "schwach"
            else -> "sehr schwach"
        }

        fun qColor(rssi: Int) = when {
            rssi >= -67 -> Col.GRN
            rssi >= -80 -> Col.YEL
            else -> Col.RED
        }

        /** Verschlüsselung aus den Fähigkeiten des Scan-Ergebnisses ("[WPA2-PSK-CCMP][RSN-SAE…]") */
        fun security(caps: String): String {
            val c = caps.uppercase(Locale.ROOT)
            val eap = c.contains("EAP")
            return when {
                c.contains("SAE") && c.contains("PSK") -> "WPA2/WPA3"
                c.contains("SAE") -> "WPA3"
                c.contains("OWE") -> "OWE (verschlüsselt, ohne Passwort)"
                (c.contains("RSN") || c.contains("WPA2")) && eap -> "WPA2-Enterprise"
                c.contains("RSN") || c.contains("WPA2") -> "WPA2"
                c.contains("WPA") -> "WPA (alt)"
                c.contains("WEP") -> "WEP (unsicher)"
                else -> "offen"
            }
        }

        @SuppressLint("NewApi")
        @Suppress("DEPRECATION")
        fun from(r: ScanResult, vendor: String?, f: Finding?): Net {
            val w = when (r.channelWidth) {
                ScanResult.CHANNEL_WIDTH_20MHZ -> 20
                ScanResult.CHANNEL_WIDTH_40MHZ -> 40
                ScanResult.CHANNEL_WIDTH_80MHZ -> 80
                ScanResult.CHANNEL_WIDTH_160MHZ, ScanResult.CHANNEL_WIDTH_80MHZ_PLUS_MHZ -> 160
                ScanResult.CHANNEL_WIDTH_320MHZ -> 320 // Konstante wird beim Bauen eingesetzt (Android 13+)
                else -> null
            }
            val std = if (Build.VERSION.SDK_INT >= 30) when (r.wifiStandard) {
                ScanResult.WIFI_STANDARD_11N -> "Wi-Fi 4"
                ScanResult.WIFI_STANDARD_11AC -> "Wi-Fi 5"
                ScanResult.WIFI_STANDARD_11AX -> "Wi-Fi 6"
                ScanResult.WIFI_STANDARD_11BE -> "Wi-Fi 7"
                ScanResult.WIFI_STANDARD_11AD -> "WiGig"
                else -> null
            } else null
            return Net(r.SSID?.takeIf { it.isNotBlank() }, r.BSSID ?: "?", r.level, r.frequency, w,
                security(r.capabilities ?: ""), std, vendor, f)
        }
    }
}

/** Kleine Signalbalken (wie in der Statusleiste), Füllung und Farbe nach dBm */
@SuppressLint("ViewConstructor")
class SignalBars(ctx: Context, private val dp: (Int) -> Int) : View(ctx) {
    var rssi = -100
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rc = RectF()

    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(dp(30), dp(20))

    override fun onDraw(c: Canvas) {
        val n = 5
        val filled = when {
            rssi >= -55 -> 5; rssi >= -67 -> 4; rssi >= -75 -> 3; rssi >= -85 -> 2; rssi > -95 -> 1; else -> 0
        }
        val gap = dp(2).toFloat()
        val bw = (width - gap * (n - 1)) / n
        for (i in 0 until n) {
            val bh = height * (i + 1) / n.toFloat()
            val x = i * (bw + gap)
            rc.set(x, height - bh, x + bw, height.toFloat())
            p.color = if (i < filled) Net.qColor(rssi) else 0x33FFFFFF
            c.drawRoundRect(rc, dp(1).toFloat(), dp(1).toFloat(), p)
        }
    }
}

/** WLAN-Symbol für die Kopfleiste (Kreis mit drei Bögen) */
@SuppressLint("ViewConstructor")
class WifiGlyph(ctx: Context, private val dp: (Int) -> Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }
    private val rc = RectF()

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = minOf(width, height) / 2f - dp(1)
        p.color = Col.ORANGE_DIM; p.strokeWidth = dp(1).toFloat(); p.style = Paint.Style.STROKE
        c.drawCircle(cx, cy, r, p)
        p.color = Col.ORANGE; p.strokeWidth = dp(2).toFloat()
        val base = cy + r * 0.42f
        for (k in 1..3) {
            val rr = r * 0.22f * k
            rc.set(cx - rr, base - rr, cx + rr, base + rr)
            c.drawArc(rc, 225f, 90f, false, p)
        }
        p.style = Paint.Style.FILL
        c.drawCircle(cx, base, dp(2).toFloat(), p)
    }
}

/**
 * Vollbild-Ansicht »WLAN-Netze«: alle Funknetze der Umgebung mit Signalstärke, Band, Kanal,
 * Verschlüsselung und Hersteller. Aktualisiert sich alle 30 s selbst (Android erlaubt
 * Apps im Vordergrund 4 Scans pro 2 Minuten).
 */
@SuppressLint("SetTextI18n")
class Networks(private val a: MainActivity) {
    private val frame = FrameLayout(a)
    private val list = LinearLayout(a)
    private val sum = TextView(a)
    private val stat = TextView(a)
    private val h = Handler(Looper.getMainLooper())
    private val chips = mutableMapOf<String, TextView>()
    private var nets: List<Net> = emptyList()
    private var lastScan = 0L
    private var nextScan = 0L
    private var scanning = false
    private var throttled = false
    private val open = HashSet<String>()     // aufgeklappte Einträge
    private var band = 0                     // 0 alle, 2/5/6
    private var byName = Prefs.netsSortName
    private var group = Prefs.netsGroup

    companion object { const val PERIOD = 30_000L }

    private val tick = object : Runnable {
        override fun run() {
            val now = System.currentTimeMillis()
            if (!scanning && nextScan in 1..now && !a.isBusy()) scan()
            paintStatus()
            h.postDelayed(this, 1000)
        }
    }

    fun show() {
        frame.setBackgroundColor(Col.BG)
        frame.isClickable = true
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }

        val top = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(a.dp(14), a.dp(8), a.dp(8), a.dp(2)) }
        top.addView(TextView(a).apply {
            text = "WLAN-Netze"; textSize = 17f; setTextColor(Col.FG); typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(a.chip("✕ Konsole") { close() })
        col.addView(top)

        sum.apply { textSize = 13f; setTextColor(Col.FG); setPadding(a.dp(14), a.dp(2), a.dp(14), 0) }
        stat.apply { textSize = 11f; setTextColor(Col.DIM); typeface = Typeface.MONOSPACE; setPadding(a.dp(14), a.dp(2), a.dp(14), a.dp(4)) }
        col.addView(sum); col.addView(stat)

        // Filter / Sortierung
        // Filter / Sortierung: gleich breite, einzeilige Knöpfe
        val fr = a.btnRow("Alle" to { band = 0; render() }, "2,4 GHz" to { band = 2; render() },
            "5 GHz" to { band = 5; render() }, "6 GHz" to { band = 6; render() })
        fr.setPadding(a.dp(7), a.dp(2), a.dp(7), a.dp(6))
        with(a) { listOf("b0", "b2", "b5", "b6").forEachIndexed { i, k -> chips[k] = fr.btns[i] } }
        col.addView(fr)
        val fr2 = a.btnRow("" to { byName = !byName; Prefs.netsSortName = byName; render() },
            "" to { group = !group; Prefs.netsGroup = group; render() },
            "↻ Scan" to { if (!scanning) scan() })
        fr2.setPadding(a.dp(7), 0, a.dp(7), a.dp(6))
        with(a) { chips["sort"] = fr2.btns[0]; chips["grp"] = fr2.btns[1]; chips["scan"] = fr2.btns[2] }
        col.addView(fr2)
        col.addView(View(a).apply { setBackgroundColor(Col.ORANGE_DIM) }, LinearLayout.LayoutParams(-1, a.dp(1)))

        list.orientation = LinearLayout.VERTICAL
        list.setPadding(a.dp(8), a.dp(6), a.dp(8), a.dp(16))
        col.addView(ScrollView(a).apply { addView(list) }, LinearLayout.LayoutParams(-1, 0, 1f))

        col.addView(TextView(a).apply {
            textSize = 11f; setTextColor(Col.DIM); setPadding(a.dp(14), a.dp(4), a.dp(14), a.dp(8))
            text = "Antippen = Details und Zugangspunkte. „gebündelt“ fasst gleichnamige Zugangspunkte zusammen. " +
                "Signal: −50 dBm sehr gut … −90 dBm sehr schwach. " +
                "Verbinden ist nicht nötig – SpyMon hört nur zu."
        })

        frame.addView(col, FrameLayout.LayoutParams(-1, -1))
        a.root.addView(frame, FrameLayout.LayoutParams(-1, -1))

        // Start mit dem, was schon da ist (letzter Scan des Systems), dann frisch scannen
        render()
        h.post(tick)
        scan()
    }

    /** für Tests/Vorschau: Daten direkt setzen */
    fun setNets(l: List<Net>, fresh: Boolean) {
        nets = l; throttled = !fresh; scanning = false; lastScan = System.currentTimeMillis(); nextScan = lastScan + PERIOD
        render()
    }

    private fun scan() {
        val pre = a.wifiProblem()
        if (pre != null) { stat.text = pre; stat.setTextColor(Col.RED); nextScan = System.currentTimeMillis() + PERIOD; return }
        scanning = true
        paintStatus()
        a.wifiScan().scanRaw { res, fresh ->
            scanning = false
            val l = res.map { r ->
                val f = a.findings.put(a.wifiScan().classify(r))
                Net.from(r, a.oui.vendor(r.BSSID), f)
            }
            if (frame.parent != null) setNets(l, fresh)
        }
    }

    private fun paintStatus() {
        val now = System.currentTimeMillis()
        val tf = SimpleDateFormat("HH:mm:ss", Locale.GERMANY)
        stat.setTextColor(if (throttled) Col.YEL else Col.DIM)
        stat.text = when {
            scanning -> "scanne …"
            lastScan == 0L -> "noch kein Scan"
            else -> {
                val left = ((nextScan - now) / 1000).coerceAtLeast(0)
                (if (throttled) "Android drosselt – letzte Werte · " else "") +
                    "Stand ${tf.format(Date(lastScan))} · nächster Scan in ${left} s"
            }
        }
        chips["scan"]?.alpha = if (scanning) 0.5f else 1f
    }

    private fun paintChip(c: TextView?, on: Boolean) { if (c != null) a.sel(c, on) }

    private fun render() {
        for (b in listOf(0, 2, 5, 6)) paintChip(chips["b$b"], band == b)
        chips["sort"]?.text = if (byName) "nach Name" else "nach Signal"
        chips["grp"]?.text = if (group) "gebündelt" else "einzeln"
        paintChip(chips["sort"], false); paintChip(chips["grp"], group)

        val vis = nets.filter { band == 0 || it.bandKey == band }
        val c2 = nets.count { it.bandKey == 2 }
        val c5 = nets.count { it.bandKey == 5 }
        val c6 = nets.count { it.bandKey == 6 }
        val names = nets.map { it.ssid ?: "\u0000" + it.bssid }.toSet().size
        sum.text = if (nets.isEmpty()) "Noch keine Netze – Scan läuft oder WLAN/Standort ist aus."
        else "$names Netze · ${nets.size} Zugangspunkte · 2,4 GHz $c2 · 5 GHz $c5 · 6 GHz $c6"

        // Gruppen bilden (gleicher Name = ein Netz mit mehreren Zugangspunkten)
        val groups: List<List<Net>> = if (group)
            vis.groupBy { it.ssid ?: ("\u0000" + it.bssid) }.values.map { g -> g.sortedByDescending { it.rssi } }
        else vis.map { listOf(it) }
        val sorted = if (byName) groups.sortedWith(compareBy<List<Net>> { it[0].ssid == null }.thenBy { it[0].name.lowercase(Locale.GERMANY) })
        else groups.sortedByDescending { it[0].rssi }

        list.removeAllViews()
        if (sorted.isEmpty() && nets.isNotEmpty()) list.addView(txt("Keine Netze auf diesem Band.", 13f, Col.DIM).apply { setPadding(a.dp(8), a.dp(12), 0, 0) })
        for (g in sorted) list.addView(row(g))
        paintStatus()
    }

    private fun txt(s: String, size: Float = 13f, c: Int = Col.FG, bold: Boolean = false) = TextView(a).apply {
        text = s; textSize = size; setTextColor(c); if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(0f, 1.05f)
    }

    private fun row(g: List<Net>): View {
        val best = g[0]
        val key = if (group) (best.ssid ?: best.bssid) else best.bssid
        val lvl = g.maxOf { it.level }
        val box = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(a.dp(12), a.dp(9), a.dp(12), a.dp(9))
            background = GradientDrawable().apply {
                setColor(Col.PANEL); cornerRadius = a.dp(10).toFloat()
                setStroke(a.dp(1), when (lvl) { 2 -> Col.RED; 1 -> 0x88E5C07B.toInt(); else -> 0x33B35F1C })
            }
        }
        val lp = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = a.dp(6) }
        box.layoutParams = lp

        // Zeile 1: Name … dBm + Balken
        val r1 = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL }
        val tag = when (lvl) { 2 -> "⚠ "; 1 -> "? "; else -> "" }
        r1.addView(txt(tag + best.name, 15f, when (lvl) { 2 -> Col.RED; 1 -> Col.YEL; else -> if (best.ssid == null) Col.DIM else Col.FG }, true).apply {
            maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(0, -2, 1f))
        r1.addView(txt("${best.rssi} dBm", 14f, Net.qColor(best.rssi), true).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); setPadding(a.dp(8), 0, a.dp(8), 0)
        })
        r1.addView(SignalBars(a) { a.dp(it) }.apply { rssi = best.rssi })
        box.addView(r1)

        // Zeile 2: Band · Kanal · Breite · Verschlüsselung · Standard · Hersteller
        val bands = g.map { it.band }.distinct().joinToString(" + ")
        val parts = mutableListOf<String>()
        if (g.size == 1) {
            parts += "${best.band} · Kanal ${best.channel}" + (best.widthMhz?.let { " · $it MHz" } ?: "")
        } else {
            parts += "$bands · ${g.size} Zugangspunkte"
        }
        parts += best.security
        best.standard?.let { parts += it }
        val ven = g.mapNotNull { it.vendor }.firstOrNull()
        if (ven != null) parts += ven.take(22)
        box.addView(txt(parts.joinToString(" · "), 12f, if (best.security.startsWith("offen") || best.security.startsWith("WEP")) Col.YEL else Col.DIM))

        // Zeile 3: Signal in Worten
        box.addView(txt("${Net.quality(best.rssi)} · ${Net.pct(best.rssi)} % · ${Fmt.dist(best.rssi)}", 12f, Col.DIM))

        // Gründe bei auffälligen Netzen
        val reasons = g.flatMap { it.finding?.reasons ?: emptyList() }.distinct()
        if (lvl > 0) for (rs in reasons) box.addView(txt("→ $rs", 12f, if (lvl == 2) Col.RED else Col.YEL))

        // Details (aufgeklappt)
        if (key in open) {
            box.addView(View(a).apply { setBackgroundColor(0x33B35F1C) }, LinearLayout.LayoutParams(-1, a.dp(1)).apply { topMargin = a.dp(6); bottomMargin = a.dp(4) })
            for (n in g) {
                box.addView(txt("${n.bssid}  ${n.rssi} dBm", 12f, Col.FG).apply { typeface = Typeface.MONOSPACE })
                val d = mutableListOf("${n.band} · Kanal ${n.channel}" + (n.widthMhz?.let { " · $it MHz" } ?: ""), n.security)
                n.standard?.let { d += it }
                d += n.vendor ?: if (Oui.isRandom(n.bssid)) "zufällige MAC (kein Hersteller)" else "Hersteller unbekannt"
                box.addView(txt(d.joinToString(" · "), 11f, Col.DIM).apply { setPadding(0, 0, 0, a.dp(4)) })
            }
            val f = best.finding
            val br = LinearLayout(a).apply { gravity = Gravity.END; setPadding(0, a.dp(4), 0, 0) }
            if (f != null) br.addView(a.btn("🎯 Aufspüren #${f.id}") { close(); a.exec("track ${f.id}") },
                LinearLayout.LayoutParams(a.dp(190), a.dp(40)))
            box.addView(br)
        }
        box.setOnClickListener { if (!open.add(key)) open.remove(key); render() }
        return box
    }

    fun close() {
        h.removeCallbacks(tick)
        a.root.removeView(frame)
        a.netsClosed()
    }
}
