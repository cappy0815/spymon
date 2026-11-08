package de.cappy.spymon

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper

/** Scannt WLAN-Funknetze in der Umgebung – funktioniert OHNE mit einem WLAN verbunden zu sein. */
class WifiScan(private val a: MainActivity) {
    /** Grund, warum kein Scan möglich ist – oder null */
    fun problem(): String? = when {
        !wm.isWifiEnabled && !wm.isScanAlwaysAvailable -> "WLAN ist aus – bitte einschalten (verbinden ist nicht nötig)"
        !a.locationOn() -> "Standort ist aus – ohne Standort liefert Android keine WLAN-Netze"
        else -> null
    }

    private val wm = a.applicationContext.getSystemService(WifiManager::class.java)
    private val h = Handler(Looper.getMainLooper())
    private val c get() = a.con

    /** Frischen Scan anstoßen; ruft cb mit den Ergebnissen (oder dem Cache, falls Android drosselt). */
    @SuppressLint("MissingPermission")
    fun scanRaw(cb: (List<ScanResult>, Boolean) -> Unit) {
        var finished = false
        lateinit var rcv: BroadcastReceiver
        val finish = { fresh: Boolean ->
            if (!finished) {
                finished = true
                try { a.unregisterReceiver(rcv) } catch (_: Exception) {}
                val res = try { wm.scanResults } catch (e: SecurityException) { emptyList() }
                cb(res, fresh)
            }
        }
        rcv = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, i: Intent) {
                finish(i.getBooleanExtra(WifiManager.EXTRA_RESULTS_UPDATED, false))
            }
        }
        a.reg(rcv, IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION))
        @Suppress("DEPRECATION")
        val started = try { wm.startScan() } catch (e: SecurityException) { false }
        if (!started) h.post { finish(false) } else h.postDelayed({ finish(false) }, 15000)
    }

    fun run(done: () -> Unit) {
        c.head("WLAN-Funknetze scannen")
        if (!wm.isWifiEnabled && !wm.isScanAlwaysAvailable) {
            c.err("WLAN ist aus. Bitte WLAN einschalten (verbinden ist NICHT nötig).")
            done(); return
        }
        if (!a.locationOn()) c.warn("Standort ist aus – Android liefert dann keine WLAN-Ergebnisse!")
        a.status("scanne WLAN …")
        scanRaw { res, fresh ->
            a.status(null)
            if (!fresh) c.warn("Android drosselt WLAN-Scans (4 pro 2 Min.) – zeige letzte Ergebnisse.")
            analyse(res)
            done()
        }
    }

    @Suppress("DEPRECATION")
    private fun analyse(res: List<ScanResult>) {
        if (res.isEmpty()) { c.warn("Keine Netze gefunden."); return }
        val list = res.map { r -> a.findings.put(classify(r)) }
            .sortedWith(compareByDescending<Finding> { it.level }.thenByDescending { it.rssi ?: -200 })
        val bad = list.count { it.level == 2 }
        val unk = list.count { it.level == 1 }
        c.sub("${list.size} Netze · ${bad} verdächtig · ${unk} unklar")
        c.line()
        for (f in list) Fmt.print(c, f)
        c.dim("  »netze« = übersichtliche Liste mit Signalstärke, Kanal, Verschlüsselung")
        c.line()
    }

    @Suppress("DEPRECATION")
    fun classify(r: ScanResult): Finding {
        val f = Finding(Src.WLAN, r.BSSID ?: "?")
        val ssid = r.SSID?.takeIf { it.isNotBlank() }
        val vendor = a.oui.vendor(r.BSSID)
        val rnd = Oui.isRandom(r.BSSID)
        val band = when { r.frequency >= 5925 -> "6GHz"; r.frequency >= 4900 -> "5GHz"; else -> "2.4GHz" }
        val open = !(r.capabilities.contains("WPA") || r.capabilities.contains("WEP") || r.capabilities.contains("SAE"))
        f.title = ssid ?: "‹versteckt›"
        f.rssi = r.level
        f.detail = "${r.BSSID}  ${vendor ?: if (rnd) "zufällige MAC" else "Hersteller ?"}  $band${if (open) " offen" else ""}"

        var lvl = 0
        if (ssid != null && Rules.camSsid.containsMatchIn(ssid)) { lvl = 2; f.reasons += "Name typisch für WLAN-Kamera" }
        else if (ssid != null && Rules.camWord.containsMatchIn(ssid)) { lvl = maxOf(lvl, 1); f.reasons += "Name enthält Kamera-Begriff" }
        if (ssid != null && Rules.iotSsid.containsMatchIn(ssid)) { lvl = maxOf(lvl, 1); f.reasons += "Name typisch für Bastel-/IoT-Modul" }
        val vl = Rules.vendorLevel(vendor)
        if (vl == 2) { lvl = 2; f.reasons += "Hersteller baut Überwachungskameras" }
        else if (vl == 1) { lvl = maxOf(lvl, 1); f.reasons += "IoT-Chip-Hersteller (oft in Billigkameras)" }
        if (ssid == null && r.level >= -60) { lvl = maxOf(lvl, 1); f.reasons += "verstecktes Netz, sehr nah" }
        if (open && lvl > 0) f.reasons += "offenes Netz (Einrichtungsmodus?)"
        if (r.level >= -50 && lvl > 0) f.reasons += "Signal sehr stark → vermutlich im Zimmer"
        if (ssid != null && ssid.startsWith("DIRECT-") && lvl == 0) f.reasons += "WLAN-Direct (Drucker/TV/Handy)"
        f.level = lvl
        return f
    }
}
