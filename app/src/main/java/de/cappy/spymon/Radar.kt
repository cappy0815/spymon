package de.cappy.spymon

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * Radar / Umgebungsbild:
 *  Mitte = eigenes Handy. Vier Sektoren nach Funkart (oben WLAN, rechts Bluetooth LE,
 *  unten Bluetooth klassisch, links Hotel-LAN). Abstand zur Mitte = Signalstärke (näher = stärker),
 *  Farbe = Einstufung. Geräte im Hotel-LAN haben kein Funksignal → hohler Punkt am Rand.
 *  Die Lage innerhalb eines Sektors ist KEINE Richtung im Raum (das kann ein Handy nicht messen),
 *  sie verteilt die Punkte nur gleichmäßig.
 */
@SuppressLint("ViewConstructor")
class RadarView(ctx: Context, private val dp: (Int) -> Int) : View(ctx) {
    var onlyNotable = false
        set(v) { field = v; relayout(); invalidate() }
    var selected: String? = null
    var onPick: ((Finding?) -> Unit)? = null

    private var items: List<Finding> = emptyList()
    private val polar = HashMap<String, FloatArray>()   // id → [Winkel°, Radius 0..1]
    private val born = HashMap<String, Long>()
    private var beam = 0f
    private var lastT = 0L

    private val pLine = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(1).toFloat(); color = 0x55B35F1C }
    private val pText = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Col.DIM; textSize = dp(10).toFloat(); typeface = Typeface.MONOSPACE }
    private val pSector = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Col.ORANGE; textSize = dp(11).toFloat(); typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textAlign = Paint.Align.CENTER
    }
    private val pLabel = Paint(Paint.ANTI_ALIAS_FLAG).apply { textSize = dp(10).toFloat(); textAlign = Paint.Align.CENTER }
    private val pDot = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = dp(2).toFloat() }
    private val pBeam = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pPhone = Paint(Paint.ANTI_ALIAS_FLAG)
    private val beamColors = intArrayOf(Color.TRANSPARENT, Color.TRANSPARENT, 0x40FF8A2B, 0x90FF8A2B.toInt())
    private val beamStops = floatArrayOf(0f, 0.82f, 0.995f, 1f)
    private var beamCx = -1f
    private var beamCy = -1f
    private val phoneRect = RectF()
    private val dash = DashPathEffect(floatArrayOf(dp(3).toFloat(), dp(3).toFloat()), 0f)

    companion object {
        /** Sektor-Mitte in Grad (0° = rechts, im Uhrzeigersinn) */
        fun sectorAngle(s: Src) = when (s) { Src.WLAN -> -90f; Src.BLE -> 0f; Src.BT -> 90f; Src.LAN -> 180f }
        fun sectorName(s: Src) = when (s) { Src.WLAN -> "WLAN"; Src.BLE -> "BLUETOOTH LE"; Src.BT -> "BLUETOOTH"; Src.LAN -> "HOTEL-LAN" }
        /**
         * Signal → Abstand (0 = Mitte, 1 = Rand). Innen 0..0.2 bleibt fürs Handy frei,
         * −40 dBm liegt bei 0.2, −95 dBm am Rand. Ohne Signal (LAN) = ganz außen.
         */
        fun rFrac(rssi: Int?): Float = if (rssi == null) 0.95f else (0.2f + 0.8f * (((-rssi) - 40) / 55f)).coerceIn(0.24f, 0.97f)
        fun id(f: Finding) = f.src.name + "|" + f.key
    }

    fun update(list: List<Finding>) {
        val now = System.currentTimeMillis()
        for (f in list) born.putIfAbsent(id(f), now)
        items = list
        relayout()
        invalidate()
    }

    private fun visible() = if (onlyNotable) items.filter { it.level > 0 } else items

    private fun geom(): FloatArray {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - dp(30)
        return floatArrayOf(cx, cy, r)
    }

    /** Punkte je Sektor verteilen und auseinanderschieben, damit sie sich nicht überdecken */
    private fun relayout() {
        polar.clear()
        val (_, _, rr) = geom().let { Triple(it[0], it[1], it[2]) }
        val minGap = dp(26).toFloat()
        for (s in Src.values()) {
            val l = visible().filter { it.src == s }.sortedBy { h(id(it)) }
            if (l.isEmpty()) continue
            val c = sectorAngle(s)
            val ang = FloatArray(l.size) { i ->
                // gleichmäßig über ±38° verteilen, leicht versetzt nach Hash
                val base = if (l.size == 1) 0f else -33f + 66f * i / (l.size - 1)
                c + base + (h(id(l[i])) - 0.5f) * 6f
            }
            val rad = FloatArray(l.size) { rFrac(l[it].rssi) }
            if (rr > 0) repeat(30) {
                for (i in l.indices) for (j in i + 1 until l.size) {
                    val ai = Math.toRadians(ang[i].toDouble()); val aj = Math.toRadians(ang[j].toDouble())
                    val dx = (cos(ai) * rad[i] - cos(aj) * rad[j]) * rr
                    val dy = (sin(ai) * rad[i] - sin(aj) * rad[j]) * rr
                    val d = hypot(dx, dy).toFloat()
                    if (d < minGap) {
                        val push = (minGap - d) / minGap * 2.5f
                        if (ang[i] <= ang[j]) { ang[i] -= push; ang[j] += push } else { ang[i] += push; ang[j] -= push }
                        // bei gleichem Winkel zusätzlich radial leicht trennen
                        if (abs(ang[i] - ang[j]) < 1f) { rad[i] = (rad[i] - 0.02f).coerceAtLeast(0.14f); rad[j] = (rad[j] + 0.02f).coerceAtMost(0.99f) }
                    }
                }
                for (i in l.indices) ang[i] = ang[i].coerceIn(c - 36f, c + 36f)
            }
            l.forEachIndexed { i, f -> polar[id(f)] = floatArrayOf(ang[i], rad[i]) }
        }
    }

    private fun h(s: String) = ((s.hashCode().toLong() and 0xffffffffL) % 10007L) / 10007f

    override fun onSizeChanged(w: Int, hh: Int, ow: Int, oh: Int) { relayout() }

    private fun xy(f: Finding, cx: Float, cy: Float, r: Float): FloatArray? {
        val p = polar[id(f)] ?: return null
        val a = Math.toRadians(p[0].toDouble())
        return floatArrayOf(cx + (cos(a) * p[1] * r).toFloat(), cy + (sin(a) * p[1] * r).toFloat())
    }

    override fun onDraw(c: Canvas) {
        val now = System.currentTimeMillis()
        if (lastT != 0L) beam = (beam + (now - lastT) * 0.08f) % 360f
        lastT = now
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - dp(30)
        if (r <= 0) return

        // Strahl (Verlauf nur bei Größenänderung neu anlegen – spart Akku bei der Daueranimation)
        if (pBeam.shader == null || beamCx != cx || beamCy != cy) {
            beamCx = cx; beamCy = cy
            pBeam.shader = SweepGradient(cx, cy, beamColors, beamStops)
        }
        c.save(); c.rotate(beam, cx, cy); c.drawCircle(cx, cy, r, pBeam); c.restore()

        // Ringe + Beschriftung (Abstandsbänder)
        // Grenzen wie in der Konsole (Fmt.dist): ≥ −50 sehr nah, ≥ −65 nah, ≥ −80 mittel, sonst weit
        val bands = listOf(rFrac(-50) to "sehr nah", rFrac(-65) to "nah", rFrac(-80) to "mittel", 1f to "weit")
        var inner = 0.2f
        c.drawCircle(cx, cy, r * inner, pLine)
        pText.textAlign = Paint.Align.CENTER
        for ((f, name) in bands) {
            c.drawCircle(cx, cy, r * f, pLine)
            // Beschriftung oben links knapp im Hotel-LAN-Sektor – dort liegen innen keine Punkte
            val mid = (inner + f) / 2f * r
            val a = Math.toRadians(-141.0)
            val tx = cx + (cos(a) * mid).toFloat()
            val ty = cy + (sin(a) * mid).toFloat()
            val half = pText.measureText(name) / 2f + dp(3)
            pPhone.style = Paint.Style.FILL; pPhone.color = Col.BG
            c.drawRoundRect(tx - half, ty - dp(7), tx + half, ty + dp(6), dp(3).toFloat(), dp(3).toFloat(), pPhone)
            c.drawText(name, tx, ty + dp(4), pText)
            inner = f
        }
        // Sektorgrenzen
        for (deg in listOf(-45.0, 45.0, 135.0, 225.0)) {
            val a = Math.toRadians(deg)
            c.drawLine(cx + (cos(a) * r * 0.2f).toFloat(), cy + (sin(a) * r * 0.2f).toFloat(),
                cx + (cos(a) * r).toFloat(), cy + (sin(a) * r).toFloat(), pLine)
        }
        // Sektornamen außen
        for (s in Src.values()) {
            val a = Math.toRadians(sectorAngle(s).toDouble())
            val d = r + dp(15)
            var tx = cx + (cos(a) * d).toFloat()
            val ty = cy + (sin(a) * d).toFloat() + dp(4)
            val n = sectorName(s) + " " + items.count { it.src == s }
            if (s == Src.BLE || s == Src.LAN) {
                // seitlich: senkrecht schreiben spart Platz
                c.save(); c.rotate(if (s == Src.BLE) 90f else -90f, tx, ty - dp(4)); c.drawText(n, tx, ty, pSector); c.restore()
            } else {
                tx = tx.coerceIn(dp(50).toFloat(), width - dp(50).toFloat())
                c.drawText(n, tx, ty, pSector)
            }
        }

        // eigenes Handy
        pPhone.style = Paint.Style.FILL; pPhone.color = Col.PANEL
        val ph = phoneRect.apply { set(cx - dp(9), cy - dp(15), cx + dp(9), cy + dp(15)) }
        c.drawRoundRect(ph, dp(4).toFloat(), dp(4).toFloat(), pPhone)
        pPhone.style = Paint.Style.STROKE; pPhone.strokeWidth = dp(2).toFloat(); pPhone.color = Col.ORANGE
        c.drawRoundRect(ph, dp(4).toFloat(), dp(4).toFloat(), pPhone)
        c.drawLine(cx - dp(3), cy - dp(11), cx + dp(3), cy - dp(11), pPhone)
        pLabel.color = Col.ORANGE
        c.drawText("DU", cx, cy + dp(28), pLabel)

        // Geräte: harmlose zuerst, verdächtige obendrauf
        val vis = visible().sortedBy { it.level }
        val showAllLabels = vis.size <= 16
        for (f in vis) {
            val p = xy(f, cx, cy, r) ?: continue
            val age = (now - (born[id(f)] ?: now)).coerceAtLeast(0)
            val grow = (age / 350f).coerceIn(0.2f, 1f)
            val col = when (f.level) { 2 -> Col.RED; 1 -> Col.YEL; else -> Col.GRN }
            val size = dp(when (f.level) { 2 -> 10; 1 -> 8; else -> 6 }) * grow
            val noRadio = f.rssi == null
            if (f.level == 2) { // Puls
                val t = (now % 1400) / 1400f
                pStroke.color = col; pStroke.alpha = ((1f - t) * 160).toInt(); pStroke.pathEffect = null
                c.drawCircle(p[0], p[1], size + dp(16) * t, pStroke)
            }
            if (noRadio) {
                pStroke.color = col; pStroke.alpha = 255; pStroke.pathEffect = dash
                c.drawCircle(p[0], p[1], size, pStroke)
                pStroke.pathEffect = null
            } else {
                pDot.color = col; pDot.alpha = 70
                c.drawCircle(p[0], p[1], size + dp(3), pDot)
                pDot.alpha = 255
                c.drawCircle(p[0], p[1], size, pDot)
            }
            if (selected == id(f)) {
                pStroke.color = Col.FG; pStroke.alpha = 255; pStroke.pathEffect = null
                c.drawCircle(p[0], p[1], size + dp(6), pStroke)
            }
            if (f.level > 0 || showAllLabels || selected == id(f)) {
                pLabel.color = if (f.level > 0 || selected == id(f)) Col.FG else Col.DIM
                val t = f.title.let { if (it.length > 15) it.take(14) + "…" else it }
                c.drawText(t, p[0], p[1] + size + dp(12), pLabel)
            }
        }
        if (items.isEmpty()) {
            pLabel.color = Col.DIM
            c.drawText("noch nichts gescannt", cx, cy + r * 0.55f, pLabel)
        }
        if (isAttachedToWindow) postInvalidateOnAnimation()
    }

    private var downX = 0f
    private var downY = 0f

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downX = e.x; downY = e.y; return true }
            MotionEvent.ACTION_UP -> {
                if (hypot(e.x - downX, e.y - downY) > dp(12)) return true
                val (cx, cy, r) = geom().let { Triple(it[0], it[1], it[2]) }
                var best: Finding? = null
                var bd = dp(28).toFloat()
                for (f in visible()) {
                    val p = xy(f, cx, cy, r) ?: continue
                    val d = hypot(p[0] - e.x, p[1] - e.y)
                    if (d < bd) { bd = d; best = f }
                }
                selected = best?.let { id(it) }
                onPick?.invoke(best)
                performClick()
                invalidate()
                return true
            }
        }
        return true
    }

    override fun performClick(): Boolean { super.performClick(); return true }
}

/** Vollbild-Ansicht über der Konsole: Kopf, Klartext-Urteil, Radar, Legende, Knöpfe, Detailkarte. */
@SuppressLint("SetTextI18n")
class Radar(private val a: MainActivity) {
    private val frame = FrameLayout(a)
    private val view = RadarView(a) { a.dp(it) }
    private val verdict = TextView(a)
    private val counts = TextView(a)
    private val statusTv = TextView(a)
    private val card = LinearLayout(a)
    private val h = Handler(Looper.getMainLooper())
    private lateinit var filterChip: TextView
    private var pick: Finding? = null

    private val tick = object : Runnable {
        override fun run() {
            refresh()
            h.postDelayed(this, 700)
        }
    }

    fun show() {
        frame.setBackgroundColor(Col.BG)
        frame.isClickable = true
        val col = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }

        // Kopf
        val top = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(a.dp(14), a.dp(8), a.dp(8), a.dp(4)) }
        top.addView(TextView(a).apply {
            text = "◉ Umgebung"; textSize = 17f; setTextColor(Col.FG); typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(a.chip("✕ Konsole") { close() })
        col.addView(top)

        verdict.apply { textSize = 15f; typeface = Typeface.DEFAULT_BOLD; setPadding(a.dp(14), a.dp(2), a.dp(14), 0) }
        counts.apply { textSize = 12f; setTextColor(Col.DIM); typeface = Typeface.MONOSPACE; setPadding(a.dp(14), a.dp(2), a.dp(14), a.dp(2)) }
        col.addView(verdict); col.addView(counts)

        view.onPick = { f -> pick = f; showCard(f) }
        col.addView(view, LinearLayout.LayoutParams(-1, 0, 1f))

        // Legende
        col.addView(TextView(a).apply {
            textSize = 11f; setPadding(a.dp(14), 0, a.dp(14), a.dp(2))
            text = SpannableStringBuilder().apply {
                fun add(t: String, c: Int) { val s = length; append(t); setSpan(ForegroundColorSpan(c), s, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                add("● ", Col.RED); add("verdächtig  ", Col.DIM)
                add("● ", Col.YEL); add("unklar  ", Col.DIM)
                add("● ", Col.GRN); add("unauffällig  ", Col.DIM)
                add("◌ ", Col.FG); add("im Netz, kein Funksignal\n", Col.DIM)
                add("Je näher an DU, desto stärker das Signal. Die Lage im Sektor ist keine Himmelsrichtung. Punkt antippen = Klartext.", Col.DIM)
            }
        })

        // Knöpfe + Status
        val bar = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(a.dp(10), a.dp(4), a.dp(10), a.dp(10)) }
        bar.addView(a.chip("🔎 Sweep") { if (!a.isBusy()) a.exec("sweep") })
        filterChip = a.chip("nur auffällige") { view.onlyNotable = !view.onlyNotable; paintFilter() }
        bar.addView(filterChip)
        statusTv.apply { textSize = 11f; setTextColor(Col.ORANGE); typeface = Typeface.MONOSPACE; setPadding(a.dp(6), 0, 0, 0); maxLines = 2 }
        bar.addView(statusTv, LinearLayout.LayoutParams(0, -2, 1f))
        col.addView(bar)

        frame.addView(col, FrameLayout.LayoutParams(-1, -1))

        // Detailkarte (unten, über dem Radar)
        card.apply {
            orientation = LinearLayout.VERTICAL
            setPadding(a.dp(16), a.dp(12), a.dp(16), a.dp(12))
            background = GradientDrawable().apply { setColor(Col.PANEL); setStroke(a.dp(1), Col.ORANGE_DIM); cornerRadius = a.dp(14).toFloat() }
            visibility = View.GONE
            isClickable = true
            elevation = a.dp(8).toFloat()
        }
        frame.addView(card, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(a.dp(8), 0, a.dp(8), a.dp(8)) })

        a.root.addView(frame, FrameLayout.LayoutParams(-1, -1))
        paintFilter()
        h.post(tick)
    }

    private fun paintFilter() {
        val on = view.onlyNotable
        filterChip.setTextColor(if (on) Col.BG else Col.ORANGE)
        filterChip.background = GradientDrawable().apply {
            setColor(if (on) Col.ORANGE else Col.PANEL); setStroke(a.dp(1), Col.ORANGE_DIM); cornerRadius = a.dp(14).toFloat()
        }
    }

    private fun refresh() {
        val all = a.findings.snapshot()
        view.update(all)
        val bad = all.count { it.level == 2 }
        val unk = all.count { it.level == 1 }
        when {
            all.isEmpty() -> { verdict.text = "Noch nichts gescannt – unten auf »Sweep«."; verdict.setTextColor(Col.FG) }
            bad > 0 -> { verdict.text = "⚠ $bad ${if (bad == 1) "Gerät" else "Geräte"} genauer ansehen"; verdict.setTextColor(Col.RED) }
            unk > 0 -> { verdict.text = "$unk unklar – meist harmlos, kurz prüfen"; verdict.setTextColor(Col.YEL) }
            else -> { verdict.text = "✓ Funktechnisch nichts Auffälliges"; verdict.setTextColor(Col.GRN) }
        }
        counts.text = "${all.size} Geräte · $bad verdächtig · $unk unklar"
        val s = a.statusNow()
        statusTv.text = s ?: if (a.isBusy()) "läuft …" else ""
        // Karte aktuell halten (Werte ändern sich beim Scan)
        pick?.let { p -> all.firstOrNull { it.src == p.src && it.key == p.key }?.let { if (card.visibility == View.VISIBLE) fillCard(it) } }
    }

    private fun showCard(f: Finding?) {
        if (f == null) { card.visibility = View.GONE; return }
        fillCard(f)
        card.visibility = View.VISIBLE
    }

    private fun txt(s: String, size: Float = 13f, c: Int = Col.FG, bold: Boolean = false) = TextView(a).apply {
        text = s; textSize = size; setTextColor(c); if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(0f, 1.08f); setPadding(0, a.dp(2), 0, a.dp(2))
    }

    private fun fillCard(f: Finding) {
        card.removeAllViews()
        val (lvlText, lvlCol) = when (f.level) {
            2 -> "Verdächtig – genauer ansehen" to Col.RED
            1 -> "Unklar – meist harmlos, kurz prüfen" to Col.YEL
            else -> "Unauffällig" to Col.GRN
        }
        val head = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(txt("#${f.id}  ${f.title}", 16f, Col.FG, true), LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(a.chip("✕") { view.selected = null; pick = null; card.visibility = View.GONE })
        card.addView(head)
        card.addView(txt(lvlText, 14f, lvlCol, true))
        val sig = f.rssi?.let { "$it dBm · ${Fmt.dist(it)}" } ?: "kein Funksignal (über das Netz gefunden)"
        card.addView(txt(kind(f.src) + " · " + sig, 13f, Col.DIM))
        val maxH = min(a.dp(220), (a.resources.displayMetrics.heightPixels * 0.28f).toInt())
        val sv = object : ScrollView(a) {
            override fun onMeasure(w: Int, hh: Int) =
                super.onMeasure(w, MeasureSpec.makeMeasureSpec(maxH, MeasureSpec.AT_MOST))
        }
        val inner = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        if (f.reasons.isEmpty()) inner.addView(txt("Nichts Auffälliges an Name, Hersteller oder Typ erkannt."))
        for (r in f.reasons) inner.addView(txt("→ $r"))
        inner.addView(txt(explain(f.src), 12f, Col.DIM))
        inner.addView(txt(advice(f), 12f, Col.ORANGE))
        if (f.detail.isNotBlank()) inner.addView(txt(f.detail.trim(), 11f, Col.DIM).apply { typeface = Typeface.MONOSPACE })
        sv.addView(inner)
        card.addView(sv, LinearLayout.LayoutParams(-1, -2))
        if (f.src == Src.BLE || f.src == Src.WLAN) {
            val row = LinearLayout(a).apply { gravity = Gravity.END; setPadding(0, a.dp(8), 0, 0) }
            row.addView(a.chip("🎯 Aufspüren") { close(); a.exec("track ${f.id}") })
            card.addView(row)
        }
    }

    private fun kind(s: Src) = when (s) {
        Src.WLAN -> "WLAN-Funknetz"; Src.BLE -> "Bluetooth LE"; Src.BT -> "Bluetooth (klassisch)"; Src.LAN -> "Gerät im Hotel-WLAN"
    }

    private fun explain(s: Src) = when (s) {
        Src.WLAN -> "Ein Gerät, das selbst ein WLAN aussendet: Router, Hotspot, Drucker – oder eine Kamera im Einrichtungsmodus."
        Src.BLE -> "Kleines Funkgerät mit Bluetooth Low Energy: Tracker, Uhren, Kopfhörer, Beacons, Smart-Home-Teile."
        Src.BT -> "Klassisches Bluetooth: Handys, Lautsprecher, Headsets, Auto-Freisprecher, Funkmodule."
        Src.LAN -> "Hängt im selben Netz wie du. Wo es steht, verrät das nicht – es kann auch drei Etagen tiefer sein."
    }

    private fun advice(f: Finding) = when {
        f.level == 0 -> "Kein Handlungsbedarf."
        f.src == Src.LAN -> "Tipp: im Zimmer mit IR- und Linsen-Modus nach einer Kamera suchen."
        f.src == Src.BT -> "Tipp: Geräte mit Strom im Zimmer ansehen; klassisches Bluetooth lässt sich hier nicht live orten."
        else -> "Tipp: »Aufspüren« und langsam durchs Zimmer gehen – heiß/kalt führt dich hin."
    }

    fun close() {
        h.removeCallbacks(tick)
        a.root.removeView(frame)
        a.radarClosed()
    }
}
