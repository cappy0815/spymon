package de.cappy.spymon

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid

/** Bluetooth: BLE (Tracker, Beacons, Wanzen) + klassisches Bluetooth (Mikrofone, Headsets). */
@SuppressLint("MissingPermission")
class BtScan(private val a: MainActivity) {
    private val ad: BluetoothAdapter? = a.getSystemService(BluetoothManager::class.java)?.adapter
    private val h = Handler(Looper.getMainLooper())
    private val c get() = a.con

    private class Agg(val addr: String) {
        var name: String? = null
        var rssi = -127
        var count = 0
        var manu = HashMap<Int, ByteArray>()
        var uuids = HashSet<ParcelUuid>()
        var sdata = HashMap<ParcelUuid, ByteArray>()
        var classic = false
        var cls: BluetoothClass? = null
    }

    private var leCb: ScanCallback? = null
    private var rcv: BroadcastReceiver? = null
    private var timer: Runnable? = null
    var running = false; private set

    fun ready(): Boolean {
        if (ad == null) { c.err("Kein Bluetooth an Bord?"); return false }
        if (!ad.isEnabled) {
            c.err("Bluetooth ist aus – bitte einschalten.")
            try { a.startActivity(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)) } catch (_: Exception) {}
            return false
        }
        return true
    }

    fun run(secs: Int, done: () -> Unit) {
        c.head("Bluetooth scannen ($secs s, BLE + klassisch)")
        if (!ready()) { done(); return }
        val seen = HashMap<String, Agg>()
        running = true

        leCb = object : ScanCallback() {
            override fun onScanResult(type: Int, r: ScanResult) { synchronized(seen) { addLe(seen, r) } }
            override fun onBatchScanResults(l: MutableList<ScanResult>) { synchronized(seen) { l.forEach { addLe(seen, it) } } }
            override fun onScanFailed(e: Int) { c.err("BLE-Scan fehlgeschlagen (Code $e)") }
        }
        val st = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        try { ad!!.bluetoothLeScanner?.startScan(null, st, leCb) } catch (e: Exception) { c.err("BLE: ${e.message}") }

        rcv = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, i: Intent) {
                if (i.action != BluetoothDevice.ACTION_FOUND) return
                val d = if (android.os.Build.VERSION.SDK_INT >= 33)
                    i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice::class.java)
                else @Suppress("DEPRECATION") i.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE)
                d ?: return
                val rssi = i.getShortExtra(BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE).toInt()
                synchronized(seen) {
                    val g = seen.getOrPut(d.address) { Agg(d.address) }
                    g.classic = true
                    g.count++
                    if (rssi > g.rssi) g.rssi = rssi
                    try { d.name?.let { g.name = it } } catch (_: SecurityException) {}
                    g.cls = d.bluetoothClass
                }
            }
        }
        a.reg(rcv!!, IntentFilter(BluetoothDevice.ACTION_FOUND))
        try { ad!!.startDiscovery() } catch (_: Exception) {}

        var left = secs
        timer = object : Runnable {
            override fun run() {
                if (!running) return
                val n = synchronized(seen) { seen.size }
                a.status("scanne Bluetooth … ${left}s · $n Geräte")
                if (left-- <= 0) {
                    stop()
                    val list = synchronized(seen) { seen.values.toList() }
                    analyse(list)
                    done()
                } else {
                    if (left % 12 == 0) try { ad!!.startDiscovery() } catch (_: Exception) {}
                    h.postDelayed(this, 1000)
                }
            }
        }
        h.post(timer!!)
    }

    fun stop() {
        running = false
        timer?.let { h.removeCallbacks(it) }
        try { leCb?.let { ad?.bluetoothLeScanner?.stopScan(it) } } catch (_: Exception) {}
        try { ad?.cancelDiscovery() } catch (_: Exception) {}
        try { rcv?.let { a.unregisterReceiver(it) } } catch (_: Exception) {}
        leCb = null; rcv = null
        a.status(null)
    }

    private fun addLe(m: HashMap<String, Agg>, r: ScanResult) {
        val g = m.getOrPut(r.device.address) { Agg(r.device.address) }
        g.count++
        g.rssi = if (g.count == 1) r.rssi else maxOf(g.rssi, r.rssi)
        val rec = r.scanRecord ?: return
        rec.deviceName?.let { if (it.isNotBlank()) g.name = it }
        val md = rec.manufacturerSpecificData
        if (md != null) for (i in 0 until md.size()) g.manu[md.keyAt(i)] = md.valueAt(i)
        rec.serviceUuids?.let { g.uuids.addAll(it) }
        rec.serviceData?.let { g.sdata.putAll(it) }
    }

    private fun analyse(list: List<Agg>) {
        val l = list.map { a.findings.put(classify(it)) }
            .sortedWith(compareByDescending<Finding> { it.level }.thenByDescending { it.rssi ?: -200 })
        val bad = l.count { it.level == 2 }
        val unk = l.count { it.level == 1 }
        c.sub("${l.size} Geräte · $bad verdächtig · $unk unklar")
        c.line()
        for (f in l) Fmt.print(c, f)
        c.line()
        if (bad + unk > 0) c.dim("  Tipp: »track <nr>« zum Aufspüren (heiß/kalt)")
    }

    companion object {
        fun u16(x: Int): ParcelUuid = ParcelUuid.fromString(String.format("0000%04X-0000-1000-8000-00805F9B34FB", x))
        val SMARTTAG = u16(0xFD5A); val TILE1 = u16(0xFEED); val TILE2 = u16(0xFEEC)
        val CHIPOLO = u16(0xFE33); val EDDY = u16(0xFEAA); val FMDN = u16(0xFEAA)
    }

    private fun classify(g: Agg): Finding {
        val f = Finding(if (g.classic && g.manu.isEmpty() && g.uuids.isEmpty()) Src.BT else Src.BLE, g.addr)
        val rnd = (g.addr.substring(0, 2).toIntOrNull(16) ?: 0) and 0xC0 == 0xC0 || Oui.isRandom(g.addr)
        val vendor = if (!rnd) a.oui.vendor(g.addr) else null
        f.rssi = if (g.rssi > -127) g.rssi else null
        var title = g.name
        var lvl = 0
        val why = f.reasons

        // Apple
        g.manu[0x004C]?.let { b ->
            val type = b.getOrNull(0)?.toInt()?.and(0xFF)
            when (type) {
                0x12 -> {
                    val len = b.getOrNull(1)?.toInt()?.and(0xFF) ?: 0
                    if (len >= 0x19) { title = title ?: "AirTag / Find-My"; lvl = 2; why += "Apple-Tracker, vom Besitzer GETRENNT" }
                    else { title = title ?: "AirTag / Find-My"; lvl = maxOf(lvl, 1); why += "Apple Find-My-Gerät (Besitzer in der Nähe?)" }
                }
                0x02 -> { title = title ?: "iBeacon"; lvl = maxOf(lvl, 1); why += "iBeacon-Sender" }
                0x07 -> title = title ?: "Apple Audio (AirPods …)"
                0x10, 0x0F, 0x0C, 0x09, 0x0B, 0x05 -> title = title ?: "Apple-Gerät"
                else -> title = title ?: "Apple (Typ ${type ?: "?"})"
            }
        }
        // Samsung SmartTag
        if (g.uuids.contains(SMARTTAG) || g.sdata.containsKey(SMARTTAG)) {
            title = title ?: "Samsung SmartTag"; lvl = 2; why += "Samsung-Tracker (falls nicht deiner!)"
        } else if (g.manu.containsKey(0x0075)) title = title ?: "Samsung-Gerät"
        // Tile / Chipolo
        if (g.uuids.contains(TILE1) || g.uuids.contains(TILE2) || g.sdata.containsKey(TILE1) || g.sdata.containsKey(TILE2)) {
            title = title ?: "Tile-Tracker"; lvl = 2; why += "Tile-Tracker"
        }
        if (g.uuids.contains(CHIPOLO) || g.sdata.containsKey(CHIPOLO)) { title = title ?: "Chipolo"; lvl = 2; why += "Chipolo-Tracker" }
        // Google Find Hub / Eddystone
        g.sdata[EDDY]?.let { b ->
            val fr = b.getOrNull(0)?.toInt()?.and(0xFF)
            if (fr == 0x40 || fr == 0x41) { title = title ?: "Google-Find-Hub-Tag"; lvl = 2; why += "Tracker im Google-Netzwerk" }
            else { title = title ?: "Eddystone-Beacon"; lvl = maxOf(lvl, 1); why += "Beacon-Sender" }
        }
        if (g.manu.containsKey(0x0006)) title = title ?: "Microsoft/Windows-Gerät"
        if (g.manu.containsKey(0x00E0) && title == null) title = "Google-Gerät"

        // Name
        g.name?.let { n ->
            if (Rules.btBad.containsMatchIn(n)) { lvl = 2; why += "Name deutet auf Kamera/Mikro/Tracker" }
            else if (Rules.btModule.containsMatchIn(n)) { lvl = maxOf(lvl, 1); why += "Bastel-Funkmodul (HC-05, ESP32 …)" }
        }
        // klassische Geräteklasse
        g.cls?.let { k ->
            when (k.deviceClass) {
                BluetoothClass.Device.AUDIO_VIDEO_MICROPHONE -> { lvl = 2; why += "meldet sich als MIKROFON" }
                BluetoothClass.Device.AUDIO_VIDEO_VIDEO_CAMERA, BluetoothClass.Device.AUDIO_VIDEO_CAMCORDER -> { lvl = 2; why += "meldet sich als KAMERA" }
                BluetoothClass.Device.AUDIO_VIDEO_UNCATEGORIZED -> { lvl = maxOf(lvl, 1); why += "Audio-Gerät ohne Kategorie" }
            }
            if (title == null) title = when (k.majorDeviceClass) {
                BluetoothClass.Device.Major.PHONE -> "Telefon"
                BluetoothClass.Device.Major.COMPUTER -> "Computer"
                BluetoothClass.Device.Major.AUDIO_VIDEO -> "Audio/Video"
                BluetoothClass.Device.Major.WEARABLE -> "Wearable"
                BluetoothClass.Device.Major.IMAGING -> "Bild-Gerät (Kamera/Drucker)"
                else -> null
            }
        }
        val vl = Rules.vendorLevel(vendor)
        if (vl == 2) { lvl = 2; why += "Hersteller baut Überwachungstechnik" }
        else if (vl == 1) { lvl = maxOf(lvl, 1); why += "IoT-Chip-Hersteller" }

        if (title == null && lvl == 0 && (f.rssi ?: -127) >= -60 && g.count >= 3) {
            lvl = 1; why += "unbekanntes Gerät ohne Namen, ganz nah"
        }
        if ((f.rssi ?: -127) >= -50 && lvl > 0) why += "Signal sehr stark → vermutlich im Zimmer"

        f.title = title ?: "‹unbekannt›"
        f.detail = "${g.addr}  ${vendor ?: if (rnd) "zufällige Adresse" else "Hersteller ?"}${if (g.classic) "  klassisch" else ""}"
        f.level = lvl
        return f
    }

    // ---------- Tracking (heiß / kalt) ----------
    private var trackCb: ScanCallback? = null

    fun track(addr: String, onRssi: (Int) -> Unit): Boolean {
        if (!ready()) return false
        trackCb = object : ScanCallback() {
            override fun onScanResult(t: Int, r: ScanResult) { if (r.device.address == addr) onRssi(r.rssi) }
        }
        val st = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        ad!!.bluetoothLeScanner?.startScan(null, st, trackCb)
        return true
    }

    fun untrack() {
        try { trackCb?.let { ad?.bluetoothLeScanner?.stopScan(it) } } catch (_: Exception) {}
        trackCb = null
    }
}
