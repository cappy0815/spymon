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

    /** Nach dem Sweep automatisch das Radarbild öffnen */
    var radarAfterSweep: Boolean
        get() = sp.getBoolean("radarAfterSweep", false)
        set(v) = sp.edit().putBoolean("radarAfterSweep", v).apply()

    /** WLAN-Netze: gleiche Namen zu einem Eintrag bündeln */
    var netsGroup: Boolean
        get() = sp.getBoolean("netsGroup", true)
        set(v) = sp.edit().putBoolean("netsGroup", v).apply()

    /** WLAN-Netze: nach Name statt nach Signal sortieren */
    var netsSortName: Boolean
        get() = sp.getBoolean("netsSortName", false)
        set(v) = sp.edit().putBoolean("netsSortName", v).apply()

    /** IR-Modus: Frontkamera statt Hauptkamera (Standard: Hauptkamera) */
    var irFront: Boolean
        get() = sp.getBoolean("irFront", false)
        set(v) = sp.edit().putBoolean("irFront", v).apply()

    // ---- Radar-Filter ----
    /** sichtbare Funkarten (Src-Namen) */
    var radarSrc: Set<String>
        get() = sp.getStringSet("radarSrc", null)?.toSet() ?: Src.values().map { it.name }.toSet()
        set(v) = sp.edit().putStringSet("radarSrc", v).apply()

    /** 0 = alle, 1 = ab unklar, 2 = nur verdächtig */
    var radarLevel: Int
        get() = sp.getInt("radarLevel", 0)
        set(v) = sp.edit().putInt("radarLevel", v).apply()

    /** Mindestsignal in dBm, 0 = alle */
    var radarMinRssi: Int
        get() = sp.getInt("radarMinRssi", 0)
        set(v) = sp.edit().putInt("radarMinRssi", v).apply()

    /** höchstens so viele Geräte (stärkste zuerst, auffällige immer), 0 = alle */
    var radarMax: Int
        get() = sp.getInt("radarMax", 0)
        set(v) = sp.edit().putInt("radarMax", v).apply()

    /** WLAN-Zugangspunkte mit gleichem Namen zu einem Punkt bündeln */
    var radarBundle: Boolean
        get() = sp.getBoolean("radarBundle", true)
        set(v) = sp.edit().putBoolean("radarBundle", v).apply()

    /** als „bekannt“ markierte Geräte ausblenden */
    var radarHideKnown: Boolean
        get() = sp.getBoolean("radarHideKnown", true)
        set(v) = sp.edit().putBoolean("radarHideKnown", v).apply()

    /** Beschriftung: 0 automatisch, 1 alle, 2 nur auffällige */
    var radarLabels: Int
        get() = sp.getInt("radarLabels", 0)
        set(v) = sp.edit().putInt("radarLabels", v).apply()

    /** „bekannte“ Geräte (eigener Router, eigene Uhr …): Schlüssel "SRC|mac/ip" */
    var known: Set<String>
        get() = sp.getStringSet("known", emptySet())?.toSet() ?: emptySet()
        set(v) = sp.edit().putStringSet("known", v).apply()

    fun isKnown(f: Finding) = (f.src.name + "|" + f.key) in known
    fun setKnown(f: Finding, on: Boolean) {
        val k = f.src.name + "|" + f.key
        known = if (on) known + k else known - k
    }

    /** Display bleibt an, solange SpyMon im Vordergrund ist */
    var keepOn: Boolean
        get() = sp.getBoolean("keepOn", true)
        set(v) = sp.edit().putBoolean("keepOn", v).apply()
}
