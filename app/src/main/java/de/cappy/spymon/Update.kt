package de.cappy.spymon

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Update-Prüfung wie bei Claude Limits, Soundboard und Player:
 *  1. Versionsdatei auf der Projektseite  https://cappy0815.github.io/downloads/spymon.json
 *     {"version":"1.1","page":"…","download":"…apk","sha256":"…"}
 *  2. Rückfall: neuestes GitHub-Release von cappy0815/spymon
 * Es wird nichts automatisch installiert – die App zeigt nur an und öffnet den Download im Browser.
 * Gesendet wird nur eine normale Abfrage (Kennung "SpyMon/<Version>"), keine Daten aus der App.
 */
object Update {
    const val VERSION_URL = "https://cappy0815.github.io/downloads/spymon.json"
    const val RELEASE_API = "https://api.github.com/repos/cappy0815/spymon/releases/latest"
    const val HOME_PAGE = "https://cappy0815.github.io"
    const val PROJECT_PAGE = "https://cappy0815.github.io/#spymon"
    const val GITHUB_PAGE = "https://github.com/cappy0815/spymon"
    private const val DAY_MS = 24L * 3600 * 1000

    @Volatile var busy = false; private set

    private fun sp(c: Context): SharedPreferences = c.getSharedPreferences("update", Context.MODE_PRIVATE)

    fun installed(c: Context): String = try {
        c.packageManager.getPackageInfo(c.packageName, 0).versionName ?: "?"
    } catch (_: Exception) { "?" }

    fun auto(c: Context) = sp(c).getBoolean("auto", true)
    fun setAuto(c: Context, on: Boolean) = sp(c).edit().putBoolean("auto", on).apply()
    fun lastCheck(c: Context) = sp(c).getLong("last", 0)
    fun latest(c: Context): String = sp(c).getString("latest", "") ?: ""

    /** gibt es laut letzter Prüfung eine neuere Version? */
    fun available(c: Context): Boolean {
        val l = latest(c)
        return l.isNotEmpty() && newer(l, installed(c))
    }

    /** automatische Prüfung fällig (eingeschaltet, letzte Prüfung > 24 h) */
    fun due(c: Context) = auto(c) && System.currentTimeMillis() - lastCheck(c) > DAY_MS

    /** Prüfung im Hintergrund; cb kommt im UI-Thread, ok=false = nicht erreichbar */
    fun check(c: Context, cb: ((Boolean) -> Unit)?) {
        if (busy) return
        busy = true
        val app = c.applicationContext
        val ua = "SpyMon/" + installed(app)
        Thread {
            var r: Array<String>? = null // version, download, page
            try {
                val o = JSONObject(get(VERSION_URL, ua))
                val v = o.optString("version").trim()
                if (v.isNotEmpty()) r = arrayOf(strip(v), o.optString("download"), o.optString("page", PROJECT_PAGE))
            } catch (_: Exception) {
            }
            if (r == null) try {
                val o = JSONObject(get(RELEASE_API, ua))
                val v = o.optString("tag_name").trim()
                if (v.isNotEmpty()) {
                    var dl = ""
                    val ass = o.optJSONArray("assets")
                    if (ass != null) for (i in 0 until ass.length()) {
                        val x = ass.getJSONObject(i)
                        if (x.optString("name").lowercase().endsWith(".apk")) { dl = x.optString("browser_download_url"); break }
                    }
                    r = arrayOf(strip(v), dl, o.optString("html_url", GITHUB_PAGE))
                }
            } catch (_: Exception) {
            }
            val ok = r != null
            val e = sp(app).edit().putLong("last", System.currentTimeMillis())
            if (r != null) e.putString("latest", r[0]).putString("download", r[1]).putString("page", r[2])
            e.apply()
            Handler(Looper.getMainLooper()).post {
                busy = false
                cb?.invoke(ok)
            }
        }.start()
    }

    /** Download der neuen APK im Browser öffnen (sonst Projektseite) */
    fun openDownload(c: Context) {
        val u = sp(c).getString("download", "") ?: ""
        open(c, u.ifEmpty { sp(c).getString("page", PROJECT_PAGE) ?: PROJECT_PAGE })
    }

    fun open(c: Context, url: String) {
        try {
            c.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
            // kein Browser installiert
        }
    }

    /** Statuszeile für Einstellungen / Hilfe */
    fun status(c: Context): String {
        val inst = installed(c)
        if (busy) return "Prüfe auf Updates …"
        if (available(c)) return "Neu: v${latest(c)} (installiert: v$inst)"
        val last = lastCheck(c)
        if (last == 0L) return "Installiert: v$inst · noch nicht geprüft"
        return "v$inst ist aktuell · geprüft " + SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY).format(Date(last))
    }

    // ------------------------------------------------------------------ intern
    private fun strip(v: String) = if (v.startsWith("v") || v.startsWith("V")) v.substring(1) else v

    /** true, wenn a neuer ist als b ("1.10" > "1.9", "1.1.1" > "1.1") */
    fun newer(a: String, b: String): Boolean {
        val x = strip(a).split('.', '-')
        val y = strip(b).split('.', '-')
        for (i in 0 until maxOf(x.size, y.size)) {
            val p = x.getOrNull(i)?.trim()?.toIntOrNull() ?: 0
            val q = y.getOrNull(i)?.trim()?.toIntOrNull() ?: 0
            if (p != q) return p > q
        }
        return false
    }

    private fun get(url: String, ua: String): String {
        val h = URL(url).openConnection() as HttpURLConnection
        h.connectTimeout = 10000
        h.readTimeout = 10000
        h.useCaches = false
        h.setRequestProperty("User-Agent", ua)
        h.setRequestProperty("Accept", "application/vnd.github+json, application/json")
        try {
            if (h.responseCode != 200) throw Exception("HTTP ${h.responseCode}")
            val out = ByteArrayOutputStream()
            h.inputStream.use { inp ->
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = inp.read(buf)
                    if (n <= 0 || total >= (1 shl 20)) break
                    out.write(buf, 0, n); total += n
                }
            }
            return out.toString("UTF-8")
        } finally {
            h.disconnect()
        }
    }
}
