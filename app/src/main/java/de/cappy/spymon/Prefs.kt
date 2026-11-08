package de.cappy.spymon

import android.content.Context
import android.content.SharedPreferences

/** Einstellungen (⚙). Alles bleibt auf dem Gerät. */
object Prefs {
    private lateinit var sp: SharedPreferences

    fun init(c: Context) {
        sp = c.applicationContext.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    /** Bluetooth-Scandauer in Sekunden (für »sweep« und »bt« ohne Zahl) */
    var btSecs: Int
        get() = sp.getInt("btSecs", 25)
        set(v) = sp.edit().putInt("btSecs", v.coerceIn(10, 120)).apply()

    /** Beim Sweep das verbundene WLAN (Hotel-LAN) mit untersuchen */
    var sweepLan: Boolean
        get() = sp.getBoolean("sweepLan", true)
        set(v) = sp.edit().putBoolean("sweepLan", v).apply()

    /** Tracking: Piepton */
    var beep: Boolean
        get() = sp.getBoolean("beep", true)
        set(v) = sp.edit().putBoolean("beep", v).apply()

    /** Tracking: Vibration (diskreter als Piepen) */
    var vibrate: Boolean
        get() = sp.getBoolean("vibrate", false)
        set(v) = sp.edit().putBoolean("vibrate", v).apply()

    /** Schriftgröße der Konsole: 0 klein, 1 normal, 2 groß */
    var font: Int
        get() = sp.getInt("font", 1)
        set(v) = sp.edit().putInt("font", v.coerceIn(0, 2)).apply()

    val fontSp get() = when (font) { 0 -> 10f; 2 -> 13f; else -> 11.5f }
    val fontName get() = when (font) { 0 -> "klein"; 2 -> "groß"; else -> "normal" }

    /** Display bleibt an, solange SpyMon im Vordergrund ist */
    var keepOn: Boolean
        get() = sp.getBoolean("keepOn", true)
        set(v) = sp.edit().putBoolean("keepOn", v).apply()
}
