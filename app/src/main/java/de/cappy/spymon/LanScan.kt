package de.cappy.spymon

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.wifi.WifiManager
import java.net.ConnectException
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.HttpURLConnection
import java.net.Inet4Address
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.net.URL
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** Scannt das verbundene (Hotel-)WLAN nach Kameras: SSDP, ONVIF, Port-Sweep, Web-Banner. */
class LanScan(private val a: MainActivity) {
    private val c get() = a.con
    @Volatile var cancel = false

    data class Net(val ip: String, val prefix: Int, val gw: String?)

    fun net(): Net? {
        val cm = a.getSystemService(ConnectivityManager::class.java)
        for (n in cm.allNetworks) {
            val caps = cm.getNetworkCapabilities(n) ?: continue
            if (!caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) continue
            val lp = cm.getLinkProperties(n) ?: continue
            val la = lp.linkAddresses.firstOrNull { it.address is Inet4Address } ?: continue
            val gw = lp.routes.firstOrNull { it.isDefaultRoute && it.gateway is Inet4Address }?.gateway?.hostAddress
            return Net(la.address.hostAddress!!, la.prefixLength, gw)
        }
        return null
    }

    private val ports = mapOf(
        554 to "RTSP-Videostream", 8554 to "RTSP-Videostream", 80 to "Web", 81 to "Web (Kamera-typisch)",
        8000 to "Hikvision-SDK/Web", 8080 to "Web", 8899 to "ONVIF", 34567 to "XMEye/Xiongmai-Kamera",
        37777 to "Dahua-Kamera", 1935 to "RTMP-Stream", 23 to "Telnet", 6668 to "Tuya-Gerät",
        9000 to "Kamera-Dienst", 5000 to "Web/UPnP", 443 to "HTTPS", 6789 to "Kamera-Dienst", 10554 to "RTSP"
    )
    private val camPorts = setOf(554, 8554, 10554, 34567, 37777, 1935, 8899)

    fun run(done: () -> Unit) {
        c.head("Hotel-WLAN untersuchen")
        val n = net()
        if (n == null) { c.warn("Nicht mit einem WLAN verbunden – übersprungen."); done(); return }
        cancel = false
        Thread {
            val lock = a.applicationContext.getSystemService(WifiManager::class.java).createMulticastLock("sweep")
            try {
                lock.acquire()
                work(n)
            } catch (e: Exception) {
                c.err("LAN-Scan: ${e.message}")
            } finally {
                try { lock.release() } catch (_: Exception) {}
                a.status(null)
                a.runOnUiThread(done)
            }
        }.start()
    }

    private fun work(n: Net) {
        c.sub("eigene IP ${n.ip}/${n.prefix} · Router ${n.gw ?: "?"}")
        if (n.prefix < 24) c.sub("großes Netz – prüfe nur das eigene /24")
        val base = n.ip.substringBeforeLast('.')
        val hits = ConcurrentHashMap<String, MutableList<String>>()     // ip -> Hinweise
        fun hit(ip: String, s: String) {
            hits.computeIfAbsent(ip) { java.util.Collections.synchronizedList(mutableListOf()) }.add(s)
        }
        val alive = ConcurrentHashMap.newKeySet<String>()
        val camHint = ConcurrentHashMap<String, Int>()

        // 1) SSDP / UPnP
        a.status("frage UPnP-Geräte ab …")
        ssdp { ip, server ->
            alive += ip
            hit(ip, "UPnP: ${server.take(60)}")
            if (Rules.httpCam.containsMatchIn(server)) camHint.merge(ip, 2, ::maxOf)
        }
        // 2) ONVIF (WS-Discovery)
        if (cancel) return
        a.status("suche ONVIF-Kameras …")
        onvif { ip, info ->
            alive += ip
            hit(ip, "ONVIF-Kamera! ${info.take(50)}")
            camHint.merge(ip, 2, ::maxOf)
        }
        // 3) Port-Sweep
        if (cancel) return
        val pool = Executors.newFixedThreadPool(64)
        val doneCnt = AtomicInteger()
        val hosts = (1..254).map { "$base.$it" }.filter { it != n.ip }
        for (ip in hosts) for (p in ports.keys) pool.execute {
            if (cancel) return@execute
            try {
                Socket().use { s -> s.connect(InetSocketAddress(ip, p), 350) }
                alive += ip
                hit(ip, "Port $p offen (${ports[p]})")
                if (p in camPorts) camHint.merge(ip, 2, ::maxOf) else camHint.merge(ip, 0, ::maxOf)
            } catch (e: ConnectException) {
                alive += ip     // "connection refused" = Gerät existiert
            } catch (_: SocketTimeoutException) {
            } catch (_: Exception) {
            }
            val d = doneCnt.incrementAndGet()
            if (d % 200 == 0) a.status("Port-Sweep … ${d * 100 / (hosts.size * ports.size)}% · ${alive.size} Geräte")
        }
        pool.shutdown()
        while (!pool.awaitTermination(500, TimeUnit.MILLISECONDS)) { if (cancel) { pool.shutdownNow(); return } }

        // 4) Web-Banner
        val web = hits.filter { (_, v) -> v.any { it.startsWith("Port 80 ") || it.startsWith("Port 81 ") || it.startsWith("Port 8000 ") || it.startsWith("Port 8080 ") } }.keys
        var i = 0
        for (ip in web) {
            if (cancel) return
            a.status("lese Web-Oberflächen … ${++i}/${web.size}")
            val ps = hits[ip]!!.mapNotNull { Regex("^Port (\\d+) ").find(it)?.groupValues?.get(1)?.toInt() }.filter { it in setOf(80, 81, 8000, 8080) }
            for (p in ps) {
                val b = banner(ip, p) ?: continue
                hit(ip, "Web :$p → ${b.take(60)}")
                if (Rules.httpCam.containsMatchIn(b)) camHint.merge(ip, 2, ::maxOf)
                break
            }
        }

        // 5) Ausgabe
        a.status("werte aus …")
        val list = alive.sortedBy { it.substringAfterLast('.').toInt() }.map { ip ->
            val f = Finding(Src.LAN, ip)
            val name = try { InetAddress.getByName(ip).canonicalHostName.takeIf { it != ip } } catch (_: Exception) { null }
            f.title = when { ip == n.gw -> "Router/Gateway"; name != null -> name; else -> ip }
            f.detail = if (f.title != ip) ip else ""
            val hs = hits[ip] ?: mutableListOf()
            f.reasons += hs.distinct()
            f.level = camHint[ip] ?: 0
            if (ip == n.gw && f.level < 2) f.level = 0
            if (f.level == 0 && hs.any { Regex("Port (23|6668|9000|6789) ").containsMatchIn(it) }) f.level = 1
            if (f.level == 2) f.reasons.add(0, "Kamera-typische Dienste")
            a.findings.put(f)
        }.sortedByDescending { it.level }
        a.status(null)
        val bad = list.count { it.level == 2 }
        c.sub("${list.size} Geräte im Netz · $bad kamera-verdächtig")
        if (list.size <= 1) c.warn("Fast nichts sichtbar → Hotel trennt die Gäste (Client-Isolation). Normal.")
        c.line()
        for (f in list) Fmt.print(c, f)
        c.line()
    }

    private fun ssdp(onHit: (String, String) -> Unit) {
        val msg = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\nMAN: \"ssdp:discover\"\r\nMX: 2\r\nST: ssdp:all\r\n\r\n".toByteArray()
        listenUdp(msg, 1900, 3500) { ip, txt ->
            val server = Regex("(?im)^(server|x-user-agent):\\s*(.+)$").find(txt)?.groupValues?.get(2)?.trim()
                ?: Regex("(?im)^st:\\s*(.+)$").find(txt)?.groupValues?.get(1)?.trim() ?: "?"
            onHit(ip, server)
        }
    }

    private fun onvif(onHit: (String, String) -> Unit) {
        val xml = """<?xml version="1.0" encoding="UTF-8"?><e:Envelope xmlns:e="http://www.w3.org/2003/05/soap-envelope" xmlns:w="http://schemas.xmlsoap.org/ws/2004/08/addressing" xmlns:d="http://schemas.xmlsoap.org/ws/2005/04/discovery" xmlns:dn="http://www.onvif.org/ver10/network/wsdl"><e:Header><w:MessageID>uuid:${UUID.randomUUID()}</w:MessageID><w:To e:mustUnderstand="true">urn:schemas-xmlsoap-org:ws:2005:04:discovery</w:To><w:Action e:mustUnderstand="true">http://schemas.xmlsoap.org/ws/2005/04/discovery/Probe</w:Action></e:Header><e:Body><d:Probe><d:Types>dn:NetworkVideoTransmitter</d:Types></d:Probe></e:Body></e:Envelope>"""
        listenUdp(xml.toByteArray(), 3702, 3000) { ip, txt ->
            val scopes = Regex("onvif://www.onvif.org/(?:name|hardware)/([^\\s<]+)").findAll(txt).joinToString(" ") { it.groupValues[1] }
            onHit(ip, scopes.ifBlank { "ProbeMatch" })
        }
    }

    private fun listenUdp(msg: ByteArray, port: Int, ms: Long, onRx: (String, String) -> Unit) {
        try {
            DatagramSocket().use { s ->
                s.soTimeout = 400
                val grp = InetAddress.getByName("239.255.255.250")
                repeat(2) { s.send(DatagramPacket(msg, msg.size, grp, port)) }
                val end = System.currentTimeMillis() + ms
                val buf = ByteArray(8192)
                while (System.currentTimeMillis() < end && !cancel) {
                    try {
                        val p = DatagramPacket(buf, buf.size)
                        s.receive(p)
                        onRx(p.address.hostAddress!!, String(p.data, 0, p.length))
                    } catch (_: SocketTimeoutException) {}
                }
            }
        } catch (e: Exception) { c.dim("    (Multicast ${port}: ${e.message})") }
    }

    private fun banner(ip: String, port: Int): String? = try {
        val con = URL("http://$ip:$port/").openConnection() as HttpURLConnection
        con.connectTimeout = 1500; con.readTimeout = 1500
        con.instanceFollowRedirects = true
        con.setRequestProperty("User-Agent", "Mozilla/5.0")
        val server = con.getHeaderField("Server") ?: ""
        val realm = con.getHeaderField("WWW-Authenticate") ?: ""
        val body = try {
            (if (con.responseCode >= 400) con.errorStream else con.inputStream)?.bufferedReader()?.use { r ->
                val cb = CharArray(8192); val k = r.read(cb); if (k > 0) String(cb, 0, k) else ""
            } ?: ""
        } catch (_: Exception) { "" }
        con.disconnect()
        val title = Regex("(?is)<title>(.*?)</title>").find(body)?.groupValues?.get(1)?.trim() ?: ""
        listOf(server, title, realm).filter { it.isNotBlank() }.joinToString(" | ").ifBlank { null }
    } catch (_: Exception) { null }
}
