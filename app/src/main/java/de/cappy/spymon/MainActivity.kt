package de.cappy.spymon

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.LocationManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@SuppressLint("SetTextI18n")
class MainActivity : Activity() {
    lateinit var con: Console
    lateinit var oui: Oui
    lateinit var root: FrameLayout
    val findings = Findings()
    private lateinit var wifi: WifiScan
    private lateinit var bt: BtScan
    private lateinit var lan: LanScan
    private lateinit var statusTv: TextView
    private lateinit var input: EditText
    private lateinit var tv: TextView
    private lateinit var updChip: TextView
    private val h = Handler(Looper.getMainLooper())
    private var busy = false
    private var cam: CamView? = null
    private var statusText: String? = null
    private var spin = 0
    private val frames = "⠋⠙⠹⠸⠼⠴⠦⠧⠇⠏"
    private val history = mutableListOf<String>()
    private var pendingCam: String? = null

    fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        Prefs.init(this)
        buildUi()
        applyPrefs()
        oui = Oui(this)
        wifi = WifiScan(this); bt = BtScan(this); lan = LanScan(this)
        banner()
        askPerms()
        h.post(spinner)
        refreshUpdate()
        if (Update.due(this)) Update.check(this) { ok -> if (ok) refreshUpdate(announce = true) }
    }

    /** Einstellungen anwenden (Start und nach »Speichern«) */
    fun applyPrefs() {
        tv.textSize = Prefs.fontSp
        statusTv.textSize = Prefs.fontSp
        if (Prefs.keepOn) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    /** „⬆ Update“ in der Kopfleiste zeigen, wenn laut letzter Prüfung eine neue Version da ist */
    fun refreshUpdate(announce: Boolean = false) {
        val avail = Update.available(this)
        updChip.visibility = if (avail) View.VISIBLE else View.GONE
        if (avail && announce) con.parts("  ⬆ " to Col.GRN, "Neue Version ${Update.latest(this)} verfügbar – oben »⬆ Update«" to Col.GRN)
    }

    /** App beenden (Einstellungen → App beenden, »exit«) */
    fun quit() {
        stopAll(); closeCam()
        finishAndRemoveTask()
    }

    // ---------------- UI ----------------
    private fun buildUi() {
        root = FrameLayout(this).apply { setBackgroundColor(Col.BG); fitsSystemWindows = true }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

        // Kopfleiste: Name · ⬆ Update (nur wenn neu) · ⚙ · ?
        val top = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(8), dp(6))
            setBackgroundColor(Col.PANEL)
        }
        top.addView(TextView(this).apply {
            typeface = Typeface.MONOSPACE; textSize = 16f; setTextColor(Col.ORANGE); text = "✻ "
        })
        top.addView(TextView(this).apply {
            typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD); textSize = 16f; setTextColor(Col.FG); text = "SpyMon"
        }, LinearLayout.LayoutParams(0, -2, 1f))
        updChip = chip("⬆ Update") { Dialogs.update(this) }.apply {
            setTextColor(Col.BG)
            background = GradientDrawable().apply { setColor(Col.GRN); cornerRadius = dp(14).toFloat() }
            visibility = View.GONE
        }
        top.addView(updChip)
        top.addView(iconBtn("⚙") { Dialogs.settings(this) })
        top.addView(iconBtn("?") { Dialogs.help(this) })
        col.addView(top)
        col.addView(View(this).apply { setBackgroundColor(Col.ORANGE_DIM) }, LinearLayout.LayoutParams(-1, dp(1)))

        tv = TextView(this).apply {
            typeface = Typeface.MONOSPACE; textSize = 11.5f; setTextColor(Col.FG)
            setTextIsSelectable(true); setLineSpacing(0f, 1.08f)
            setPadding(dp(12), dp(8), dp(12), dp(8))
        }
        val sv = ScrollView(this).apply { addView(tv); isFillViewport = true }
        col.addView(sv, LinearLayout.LayoutParams(-1, 0, 1f))
        con = Console(tv, sv)

        statusTv = TextView(this).apply {
            typeface = Typeface.MONOSPACE; textSize = 11.5f; setTextColor(Col.ORANGE)
            setPadding(dp(12), dp(4), dp(12), dp(4)); visibility = View.GONE
        }
        col.addView(statusTv)

        // Schnellbefehle
        val chips = LinearLayout(this).apply { setPadding(dp(8), dp(4), dp(8), dp(4)) }
        for (cmd in listOf("sweep", "wifi", "bt", "lan", "track", "ir", "lens", "list", "stop", "save", "share", "help", "clear"))
            chips.addView(chip(cmd) { exec(cmd) })
        col.addView(HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false; addView(chips) })

        // Eingabezeile
        val inRow = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(10))
            background = GradientDrawable().apply { setColor(Col.PANEL); setStroke(dp(1), Col.ORANGE_DIM); cornerRadius = dp(10).toFloat() }
        }
        inRow.addView(TextView(this).apply { text = "❯ "; typeface = Typeface.MONOSPACE; textSize = 15f; setTextColor(Col.ORANGE) })
        input = EditText(this).apply {
            typeface = Typeface.MONOSPACE; textSize = 14f; setTextColor(Col.FG); setHintTextColor(Col.DIM)
            hint = "befehl eingeben … (help)"; background = null; isSingleLine = true
            imeOptions = EditorInfo.IME_ACTION_GO
            inputType = android.text.InputType.TYPE_CLASS_TEXT or android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            setOnEditorActionListener { v, _, _ -> exec(v.text.toString()); v.text = ""; true }
            setOnKeyListener { v, k, e ->
                if (e.action == KeyEvent.ACTION_DOWN && k == KeyEvent.KEYCODE_DPAD_UP && history.isNotEmpty()) {
                    (v as EditText).setText(history.last()); true
                } else false
            }
        }
        inRow.addView(input, LinearLayout.LayoutParams(0, -2, 1f))
        val wrap = FrameLayout(this).apply { setPadding(dp(8), 0, dp(8), dp(8)); addView(inRow) }
        col.addView(wrap)
        root.addView(col)
        setContentView(root)
    }

    /** runder Knopf in der Kopfleiste (⚙, ?) */
    private fun iconBtn(t: String, on: () -> Unit) = TextView(this).apply {
        text = t; textSize = 18f; setTextColor(Col.ORANGE); gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Col.BG); setStroke(dp(1), Col.ORANGE_DIM) }
        layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { leftMargin = dp(8) }
        setOnClickListener { on() }
    }

    fun chip(t: String, on: () -> Unit) = TextView(this).apply {
        text = t; typeface = Typeface.MONOSPACE; textSize = 13f; setTextColor(Col.ORANGE)
        setPadding(dp(12), dp(7), dp(12), dp(7))
        background = GradientDrawable().apply { setColor(Col.PANEL); setStroke(dp(1), Col.ORANGE_DIM); cornerRadius = dp(14).toFloat() }
        layoutParams = LinearLayout.LayoutParams(-2, -2).apply { setMargins(dp(3), 0, dp(3), 0) }
        setOnClickListener { on() }
    }

    private val spinner = object : Runnable {
        override fun run() {
            val s = statusText
            if (s != null) {
                statusTv.visibility = View.VISIBLE
                statusTv.text = "${frames[spin++ % frames.length]} $s"
            } else statusTv.visibility = View.GONE
            h.postDelayed(this, 90)
        }
    }

    fun status(t: String?) { statusText = t }

    private fun banner() {
        con.line("╭───────────────────────────────────────╮", Col.ORANGE)
        con.parts("│ " to Col.ORANGE, "✻ " to Col.ORANGE, "SpyMon " to Col.FG, ("v" + Update.installed(this)).padEnd(28) to Col.DIM, " │" to Col.ORANGE)
        con.parts("│ " to Col.ORANGE, "  Spy Monitor · Zimmer-Check".padEnd(37) to Col.DIM, " │" to Col.ORANGE)
        con.line("╰───────────────────────────────────────╯", Col.ORANGE)
        con.dim("  Hersteller-DB: ${oui.size} Einträge")
        con.dim("  »sweep« = alles scannen · »help« = Befehle")
        con.dim("  ⚙ = Einstellungen · ? = Hilfe")
        con.line()
    }

    // ---------------- Befehle ----------------
    fun exec(raw: String) {
        val cmd = raw.trim()
        if (cmd.isEmpty()) return
        history += cmd
        con.echo(cmd)
        val p = cmd.lowercase(Locale.ROOT).split(Regex("\\s+"))
        val arg = p.getOrNull(1)
        if (busy && p[0] !in setOf("stop", "help", "hilfe", "?", "clear", "cls", "list", "ls", "save", "share", "oui", "info",
                "settings", "einstellungen", "opt", "options", "update", "about", "über", "version", "exit", "beenden", "quit")) {
            con.warn("Läuft noch … (»stop« bricht ab)"); return
        }
        when (p[0]) {
            "help", "hilfe", "?" -> help()
            "sweep", "all", "check", "scan" -> sweep()
            "wifi", "wlan" -> job { d -> wifi.run(d) }
            "bt", "bluetooth" -> job { d -> bt.run(arg?.toIntOrNull()?.coerceIn(5, 120) ?: Prefs.btSecs, d) }
            "lan", "net" -> job { d -> lan.run(d) }
            "track", "find" -> track(arg?.removePrefix("#")?.toIntOrNull())
            "ir" -> camMode("ir")
            "lens", "linse" -> camMode("lens")
            "list", "ls" -> list()
            "stop" -> stopAll()
            "save" -> save()
            "share" -> share()
            "clear", "cls" -> con.clear()
            "oui" -> con.sub("${arg ?: "?"} → ${oui.vendor(arg) ?: "unbekannt"}")
            "info" -> info()
            "tips", "tipps" -> tips()
            "settings", "einstellungen", "opt", "options" -> Dialogs.settings(this)
            "about", "über", "version" -> Dialogs.help(this, 3)
            "update" -> updateCmd()
            "exit", "beenden", "quit" -> quit()
            else -> con.err("Unbekannt: ${p[0]} – »help«")
        }
    }

    private fun job(block: (() -> Unit) -> Unit) {
        busy = true
        block { busy = false; con.line("❯ bereit", Col.DIM) }
    }

    private fun help() {
        con.head("Befehle")
        for ((k, v) in Dialogs.COMMANDS) con.parts("  " to Col.FG, k.padEnd(13) to Col.ORANGE, v to Col.DIM)
        con.line()
        con.parts("  " to Col.FG, "[!!]" to Col.RED, " verdächtig  " to Col.DIM, "[ ?]" to Col.YEL, " unklar  " to Col.DIM, "[ok]" to Col.GRN, " harmlos" to Col.DIM)
        con.dim("  Ausführliche Hilfe: ? oben rechts")
        con.line()
    }

    private fun updateCmd() {
        con.head("Update-Prüfung")
        status("prüfe auf Updates …")
        Update.check(this) { ok ->
            status(null)
            when {
                !ok -> con.err("Update-Server nicht erreichbar – Internet prüfen")
                Update.available(this) -> { refreshUpdate(announce = true); Dialogs.update(this) }
                else -> con.ok("v${Update.installed(this)} ist aktuell")
            }
        }
    }

    private fun sweep() {
        findings.reset()
        busy = true
        val t0 = System.currentTimeMillis()
        con.head("SWEEP gestartet ${SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date())}")
        con.line()
        wifi.run {
            bt.run(Prefs.btSecs) {
                val next = {
                    busy = false
                    summary((System.currentTimeMillis() - t0) / 1000)
                }
                when {
                    !Prefs.sweepLan -> { con.head("Hotel-LAN"); con.sub("in ⚙ abgeschaltet – übersprungen (»lan« geht einzeln)"); con.line(); next() }
                    lan.net() != null -> lan.run(next)
                    else -> { con.head("Hotel-LAN"); con.sub("nicht verbunden – übersprungen (optional)"); con.line(); next() }
                }
            }
        }
    }

    private fun summary(secs: Long) {
        val all = findings.all.toList()
        val bad = all.filter { it.level == 2 }
        val unk = all.filter { it.level == 1 }
        con.line("═════════════ ERGEBNIS (${secs}s) ═════════════", Col.ORANGE)
        con.parts("  " to Col.FG, "${bad.size} verdächtig" to if (bad.isEmpty()) Col.GRN else Col.RED,
            " · " to Col.DIM, "${unk.size} unklar" to Col.YEL, " · ${all.size} gesamt" to Col.DIM)
        con.line()
        if (bad.isEmpty() && unk.isEmpty()) {
            con.ok("Funktechnisch nichts Auffälliges.")
            con.dim("  Kameras/Mikros OHNE Funk sieht keine App –")
            con.dim("  »ir«, »lens« und »tips« für die Sichtprüfung.")
        } else {
            for (f in bad + unk) Fmt.print(con, f)
            con.line()
            con.dim("  »track <nr>« = aufspüren · »lens« = Linse suchen")
        }
        con.line("❯ bereit", Col.DIM)
    }

    private fun list() {
        val all = findings.all.sortedWith(compareByDescending<Finding> { it.level }.thenByDescending { it.rssi ?: -200 })
        if (all.isEmpty()) { con.dim("  noch nichts gescannt – »sweep«"); return }
        for (s in Src.values()) {
            val l = all.filter { it.src == s }
            if (l.isEmpty()) continue
            con.head("${s.tag} (${l.size})")
            l.forEach { Fmt.print(con, it) }
            con.line()
        }
    }

    private fun stopAll() {
        bt.stop(); bt.untrack(); lan.cancel = true
        tracking = false
        status(null); busy = false
        con.dim("  gestoppt.")
    }

    // ---------------- Tracking ----------------
    private var tracking = false
    private var tone: ToneGenerator? = null

    private fun track(id: Int?) {
        if (id == null) {
            val cand = findings.all.filter { it.src == Src.BLE || it.src == Src.WLAN }
                .sortedWith(compareByDescending<Finding> { it.level }.thenByDescending { it.rssi ?: -200 }).take(8)
            if (cand.isEmpty()) { con.dim("  erst »bt« oder »wifi« scannen, dann »track <nr>«"); return }
            con.dim("  Welches Gerät? z. B. »track ${cand[0].id}«")
            cand.forEach { con.parts("   #${it.id} " to Col.ORANGE, "${it.title} " to Col.FG, "${it.rssi ?: ""} dBm" to Col.DIM) }
            return
        }
        val f = findings.byId(id) ?: run { con.err("#$id gibt es nicht"); return }
        if (f.src != Src.BLE && f.src != Src.WLAN) { con.err("Nur Bluetooth-LE- und WLAN-Geräte lassen sich orten."); return }
        busy = true; tracking = true
        con.head("Spüre #$id auf: ${f.title}")
        con.dim("  Langsam durchs Zimmer gehen. Je stärker, desto näher.")
        con.dim(when {
            Prefs.beep && Prefs.vibrate -> "  Piepen + Vibration werden schneller, je näher. »stop« beendet."
            Prefs.beep -> "  Piepen wird schneller, je näher du kommst. »stop« beendet."
            Prefs.vibrate -> "  Vibration wird schneller, je näher du kommst. »stop« beendet."
            else -> "  Signal-Ton aus (⚙) – nur Statuszeile. »stop« beendet."
        })
        tone = if (Prefs.beep) try { ToneGenerator(AudioManager.STREAM_MUSIC, 70) } catch (_: Exception) { null } else null
        val vib = if (Prefs.vibrate) vibrator() else null
        var ema = (f.rssi ?: -90).toDouble()
        var best = -127
        var last = System.currentTimeMillis()
        val onRssi = { r: Int ->
            ema = ema * 0.65 + r * 0.35
            if (r > best) best = r
            last = System.currentTimeMillis()
        }
        if (f.src == Src.BLE) {
            if (!bt.track(f.key) { onRssi(it) }) { busy = false; tracking = false; return }
        } else {
            // WLAN: wiederholte Scans (Android drosselt → in Entwickleroptionen "WLAN-Scan-Drosselung" aus = schneller)
            val loop = object : Runnable {
                override fun run() {
                    if (!tracking) return
                    wifi.scanRaw { res, _ ->
                        res.firstOrNull { it.BSSID == f.key }?.let { onRssi(it.level) }
                        h.postDelayed(this, 3000)
                    }
                }
            }
            h.post(loop)
        }
        var lastBeep = 0L
        val ui = object : Runnable {
            override fun run() {
                if (!tracking) { tone?.release(); tone = null; status(null); return }
                val r = ema.toInt()
                val stale = System.currentTimeMillis() - last > 6000
                val bars = Fmt.bars(r, 12)
                val trend = when { stale -> "kein Signal"; r >= best - 3 -> "▲ HEISS"; r >= best - 10 -> "warm"; else -> "▼ kalt" }
                status("#$id  ${r}dBm  $bars  $trend  (max $best)")
                val gap = if (stale) 99999L else (((-r) - 35).coerceIn(0, 60) * 25L + 80)
                val now = System.currentTimeMillis()
                if (now - lastBeep > gap) {
                    tone?.startTone(ToneGenerator.TONE_PROP_BEEP, 60)
                    try { vib?.vibrate(VibrationEffect.createOneShot(40, VibrationEffect.DEFAULT_AMPLITUDE)) } catch (_: Exception) {}
                    lastBeep = now
                }
                h.postDelayed(this, 60)
            }
        }
        h.post(ui)
    }

    private fun vibrator(): Vibrator? = try {
        if (Build.VERSION.SDK_INT >= 31) getSystemService(VibratorManager::class.java)?.defaultVibrator
        else @Suppress("DEPRECATION") getSystemService(Vibrator::class.java)
    } catch (_: Exception) { null }

    // ---------------- Kamera ----------------
    private fun camMode(m: String) {
        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            pendingCam = m
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 2); return
        }
        closeCam()
        con.head(if (m == "ir") "IR-Modus" else "Linsen-Suche")
        cam = CamView(this, front = m == "ir", torch = m == "lens").also { it.show() }
    }

    fun closeCam() { cam?.close(); cam = null }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (cam != null) closeCam() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    // ---------------- Bericht ----------------
    private fun reportText(): String {
        val sb = StringBuilder()
        sb.append("SpyMon ").append(Update.installed(this)).append(" – Bericht ").append(SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.GERMANY).format(Date())).append("\n\n")
        synchronized(con.log) { sb.append(con.log) }
        return sb.toString()
    }

    private fun save() {
        try {
            val name = "SpyMon_" + SimpleDateFormat("yyyyMMdd_HHmm", Locale.ROOT).format(Date()) + ".txt"
            val v = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/SpyMon")
            }
            val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, v)!!
            contentResolver.openOutputStream(uri)!!.use { it.write(reportText().toByteArray()) }
            con.ok("gespeichert: Download/SpyMon/$name")
        } catch (e: Exception) { con.err("Speichern: ${e.message}") }
    }

    private fun share() {
        val i = Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, reportText()); putExtra(Intent.EXTRA_SUBJECT, "SpyMon Bericht") }
        startActivity(Intent.createChooser(i, "Bericht teilen"))
    }

    // ---------------- Infos ----------------
    private fun tips() {
        con.head("Sichtprüfung – wo Kameras gern stecken")
        listOf(
            "Rauchmelder, Bewegungsmelder (zum Bett gerichtet?)",
            "Wecker, Radio, Uhr, Bilderrahmen, Deko",
            "USB-Ladegeräte & Steckdosenadapter (ungewöhnlich?)",
            "TV-Rand, Set-Top-Box, Lautsprecher",
            "Lüftungsgitter, Deckenleuchten, Schrauben mit Loch",
            "Spiegel: Fingernagel drauf – KEIN Spalt = evtl. Spionspiegel",
            "Bad: Föhn-Halter, Handtuchhaken, Duschablage",
            "Alles, was Strom hat und auf Bett/Bad zeigt",
            "Im Zweifel: abdecken oder ausstecken."
        ).forEach { con.parts("  □ " to Col.ORANGE, it to Col.FG) }
        con.line()
    }

    private fun info() {
        val wm = applicationContext.getSystemService(WifiManager::class.java)
        con.head("Status")
        con.sub("WLAN an: ${wm.isWifiEnabled} · Standort an: ${locationOn()}")
        con.sub("Hotel-WLAN: ${lan.net()?.let { "${it.ip}/${it.prefix}" } ?: "nicht verbunden"}")
        con.sub("Android ${Build.VERSION.RELEASE} · ${Build.MODEL}")
        con.dim("  Tipp: Entwickleroptionen → »WLAN-Scan-Drosselung«")
        con.dim("  ausschalten = schnellere WLAN-Scans & Tracking.")
        con.line()
    }

    fun locationOn(): Boolean = getSystemService(LocationManager::class.java)?.isLocationEnabled ?: false

    fun reg(r: BroadcastReceiver, f: IntentFilter) {
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(r, f, Context.RECEIVER_EXPORTED) else registerReceiver(r, f)
    }

    // ---------------- Rechte ----------------
    private fun askPerms() {
        val need = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 31) need += listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        if (Build.VERSION.SDK_INT >= 33) need += Manifest.permission.NEARBY_WIFI_DEVICES
        val missing = need.filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) requestPermissions(missing.toTypedArray(), 1)
        else con.ok("Berechtigungen ok")
        if (!locationOn()) con.warn("Standort einschalten – sonst kein WLAN/BT-Scan!")
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        if (code == 1) {
            val denied = perms.filterIndexed { i, _ -> res.getOrNull(i) != PackageManager.PERMISSION_GRANTED }
            if (denied.isEmpty()) con.ok("Berechtigungen ok")
            else con.err("Fehlt: " + denied.joinToString { it.substringAfterLast('.') } + " – Scans eingeschränkt")
        } else if (code == 2) {
            if (res.firstOrNull() == PackageManager.PERMISSION_GRANTED) pendingCam?.let { camMode(it) }
            else con.err("Kamera-Recht fehlt")
            pendingCam = null
        }
    }

    override fun onDestroy() {
        stopAll(); closeCam()
        super.onDestroy()
    }
}
