package de.cappy.spymon

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.DialogInterface
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView

/** Hilfe (?), Einstellungen (⚙) und Update-Dialog (⬆) – im Konsolen-Stil dunkel/orange. */
@SuppressLint("SetTextI18n", "UseSwitchCompatOrMaterialCode")
object Dialogs {

    /** Versionsgeschichte, neueste zuerst */
    val CHANGELOG = listOf(
        "1.3" to listOf(
            "Neu: WLAN-Netze – alle Funknetze der Umgebung als Liste mit Signalstärke (dBm, Balken, Prozent, Klartext)",
            "Pro Netz: Band, Kanal, Kanalbreite, Verschlüsselung (offen/WEP gelb markiert), Wi-Fi-Standard, Hersteller",
            "Gleiche Namen werden zu einem Netz gebündelt (Hotel-WLAN mit vielen Zugangspunkten) – umschaltbar auf „einzeln“",
            "Filter nach 2,4 / 5 / 6 GHz, Sortierung nach Signal oder Name, Antippen klappt alle Zugangspunkte auf",
            "Aktualisiert sich alle 30 Sekunden selbst; auffällige Netze mit Begründung und „Aufspüren“",
            "Erreichbar über das WLAN-Symbol oben, »netze«, das Schnellmenü und den Knopf im Radar"
        ),
        "1.2" to listOf(
            "Radar: Umgebungsbild mit deinem Handy in der Mitte – Geräte nach Funkart sortiert, Abstand nach Signalstärke, Farbe nach Einstufung",
            "Radar: Punkt antippen = Erklärung in Klartext, Tipp was zu tun ist, „Aufspüren“ direkt aus der Karte",
            "Radar: Sweep direkt aus dem Bild starten, Bluetooth-Geräte erscheinen schon während des Scans, Filter „nur auffällige“",
            "Schnellmenü: lange drücken (oder Rechtsklick mit Maus) auf Konsole, Knöpfe oder ❯ – Sweep, WLAN, Bluetooth kurz/lang, LAN, Aufspüren, IR, Linse, Kopieren",
            "Kopfzeile in der Konsole ohne Rahmen, Trennlinien passen sich der Bildschirmbreite an (kein Umbruch mehr)",
            "Einstellung: Radar nach dem Sweep automatisch zeigen",
            "Neuer Befehl: radar"
        ),
        "1.1" to listOf(
            "Neuer Name: SpyMon (vorher RoomSweep)",
            "Kopfleiste mit ⬆ Update (nur wenn neu), ⚙ Einstellungen und ? Hilfe",
            "Hilfe mit Reitern: Start, Befehle, Changelog, Über (Hinweise, Projektseite, Quellcode)",
            "Einstellungen: Bluetooth-Scandauer, Hotel-LAN im Sweep an/aus, Tracking mit Piepton und/oder Vibration, Schriftgröße, Display bleibt an",
            "Update-Prüfung über die Projektseite: automatisch (beim Start, höchstens einmal am Tag, abschaltbar) oder von Hand mit „Jetzt prüfen“",
            "„App beenden“ in den Einstellungen und per »exit«",
            "Neue Befehle: settings, update, about, exit",
            "Berichte liegen jetzt unter Download/SpyMon"
        ),
        "1.0" to listOf(
            "Erste Version (damals „RoomSweep“, gebaut von Claude Code)",
            "Konsole mit sweep, wifi, bt, lan, track, ir, lens, list, tips, save, share, oui, info"
        )
    )

    /** Befehlsliste – gemeinsam für »help« in der Konsole und die Hilfe */
    val COMMANDS = listOf(
        "sweep" to "alles: WLAN + Bluetooth + Hotel-LAN",
        "radar" to "Umgebungsbild mit Klartext (◉ oben)",
        "netze" to "WLAN-Netze mit Signalstärke (WLAN-Symbol oben)",
        "wifi" to "WLAN-Funknetze (ohne Verbindung)",
        "bt [s]" to "Bluetooth/BLE, Dauer laut ⚙ (oder s Sek.)",
        "lan" to "Hotel-WLAN nach Kameras absuchen",
        "track <nr>" to "Gerät aufspüren: heiß/kalt",
        "ir" to "Infrarot-Modus (Nachtsicht-LEDs)",
        "lens" to "Linsen-Suche mit Lampe + Rotfilter",
        "list" to "alle Funde nach Risiko",
        "tips" to "Checkliste: wo man nachsieht",
        "save / share" to "Bericht speichern / teilen",
        "oui <mac>" to "Hersteller zu einer MAC",
        "info" to "Status: WLAN, Standort, Netz",
        "settings" to "Einstellungen (⚙)",
        "update" to "jetzt nach Updates suchen",
        "about" to "Über SpyMon, Hinweise, Projektseite",
        "stop · clear" to "abbrechen · Bildschirm leeren",
        "exit" to "App beenden"
    )

    private const val CREDITS = "Gebaut von Justus Jonas & Michael Nowak\n– mit Claude am Radar 📡"

    // ------------------------------------------------------------------ Bausteine
    private fun text(a: MainActivity, s: String, size: Float = 14f, color: Int = Col.FG, bold: Boolean = false) =
        TextView(a).apply {
            text = s; textSize = size; setTextColor(color)
            if (bold) typeface = Typeface.DEFAULT_BOLD
            setLineSpacing(0f, 1.1f)
        }

    private fun section(a: MainActivity, s: String) = text(a, s, 15f, Col.ORANGE, true).apply {
        setPadding(0, a.dp(16), 0, a.dp(6))
    }

    private fun note(a: MainActivity, s: String) = text(a, s, 12f, Col.DIM).apply { setPadding(0, a.dp(2), 0, a.dp(4)) }

    private fun item(a: MainActivity, box: LinearLayout, sym: String, body: String) {
        val row = LinearLayout(a).apply { setPadding(0, a.dp(3), 0, a.dp(3)) }
        row.addView(text(a, sym, 14f, Col.ORANGE).apply { minWidth = a.dp(26) })
        row.addView(text(a, body), LinearLayout.LayoutParams(0, -2, 1f))
        box.addView(row)
    }

    private fun toggle(a: MainActivity, label: String, on: Boolean) = Switch(a).apply {
        text = label; isChecked = on; textSize = 14f; setTextColor(Col.FG)
        setPadding(0, a.dp(6), 0, a.dp(6))
    }

    /** Pillen-Knopf; filled = orange gefüllt (Hauptaktion) */
    private fun pill(a: MainActivity, label: String, filled: Boolean = false, on: () -> Unit) = Button(a).apply {
        text = label; isAllCaps = false; isSingleLine = true; textSize = 14f
        setTextColor(if (filled) Col.BG else Col.ORANGE)
        background = GradientDrawable().apply {
            setColor(if (filled) Col.ORANGE else Col.PANEL)
            setStroke(a.dp(1), Col.ORANGE_DIM); cornerRadius = a.dp(18).toFloat()
        }
        minWidth = 0; minimumWidth = 0; minHeight = 0; minimumHeight = 0
        setPadding(a.dp(14), a.dp(9), a.dp(14), a.dp(9))
        stateListAnimator = null
        setOnClickListener { on() }
    }

    /** klickbarer Link (unterstrichen) */
    private fun link(a: MainActivity, label: String, url: String) = text(a, label, 14f, Col.ORANGE).apply {
        paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
        setPadding(0, a.dp(6), 0, a.dp(6))
        setOnClickListener { Update.open(a, url) }
    }

    private fun scroll(a: MainActivity, box: LinearLayout) = ScrollView(a).apply {
        box.setPadding(a.dp(20), a.dp(8), a.dp(20), a.dp(8))
        addView(box)
    }

    private fun style(d: AlertDialog) {
        for (w in listOf(DialogInterface.BUTTON_POSITIVE, DialogInterface.BUTTON_NEGATIVE, DialogInterface.BUTTON_NEUTRAL))
            d.getButton(w)?.apply { setTextColor(Col.ORANGE); isSingleLine = true; isAllCaps = false }
    }

    private fun builder(a: MainActivity) = AlertDialog.Builder(a, R.style.SweepDialog)

    // ------------------------------------------------------------------ Updates
    /** Statuszeile mit „Jetzt prüfen“ und – wenn neu – „⬆ Download“ (Einstellungen und Hilfe → Über) */
    fun updateRow(a: MainActivity): LinearLayout {
        val box = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val st = text(a, "", 13f)
        box.addView(st)
        val row = LinearLayout(a).apply { setPadding(0, a.dp(8), 0, a.dp(4)) }
        lateinit var chk: Button
        lateinit var dl: Button
        val show = {
            val avail = Update.available(a)
            st.text = Update.status(a)
            st.setTextColor(if (avail) Col.GRN else Col.DIM)
            dl.visibility = if (avail) View.VISIBLE else View.GONE
            chk.isEnabled = !Update.busy
            a.refreshUpdate()
        }
        chk = pill(a, "Jetzt prüfen") {
            Update.check(a) { ok ->
                show()
                if (!ok) { st.text = "Update-Server nicht erreichbar – Internet prüfen"; st.setTextColor(Col.YEL) }
            }
            show()
        }
        dl = pill(a, "⬆ Download", true) { Update.openDownload(a) }
        row.addView(chk, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(dl, LinearLayout.LayoutParams(0, -2, 1f).apply { leftMargin = a.dp(8) })
        box.addView(row)
        show()
        return box
    }

    /** Tipp auf „⬆ Update“ in der Kopfleiste */
    fun update(a: MainActivity) {
        val msg = "Version ${Update.latest(a)} ist verfügbar (installiert: ${Update.installed(a)}).\n\n" +
            "„Herunterladen“ öffnet die APK im Browser. Danach die Datei antippen und installieren – " +
            "Einstellungen bleiben erhalten.\n\nTipp: nicht mitten im Zimmer-Check aktualisieren 😉"
        val d = builder(a)
            .setTitle("⬆ Update")
            .setMessage(msg)
            .setNegativeButton("Später", null)
            .setNeutralButton("Was ist neu?") { _, _ -> Update.open(a, Update.PROJECT_PAGE) }
            .setPositiveButton("Herunterladen") { _, _ -> Update.openDownload(a) }
            .create()
        d.show(); style(d)
    }

    // ------------------------------------------------------------------ Einstellungen
    fun settings(a: MainActivity) {
        val box = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        box.addView(text(a, "⚙ Einstellungen", 18f, Col.FG, true).apply { setPadding(0, a.dp(8), 0, 0) })

        // Scan
        box.addView(section(a, "Scan"))
        val secs = intArrayOf(Prefs.btSecs)
        val secRow = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL }
        secRow.addView(text(a, "Bluetooth-Scandauer"), LinearLayout.LayoutParams(0, -2, 1f))
        val secTv = text(a, "${secs[0]} s", 15f, Col.ORANGE, true).apply { gravity = Gravity.CENTER; minWidth = a.dp(56) }
        secRow.addView(pill(a, "−") { secs[0] = (secs[0] - 5).coerceAtLeast(10); secTv.text = "${secs[0]} s" })
        secRow.addView(secTv)
        secRow.addView(pill(a, "+") { secs[0] = (secs[0] + 5).coerceAtMost(120); secTv.text = "${secs[0]} s" })
        box.addView(secRow)
        box.addView(note(a, "Gilt für »sweep« und »bt« ohne Zahl. Länger = mehr Geräte, die selten senden (10–120 s)."))
        val swLan = toggle(a, "Hotel-LAN beim Sweep mit prüfen (nur wenn verbunden)", Prefs.sweepLan)
        box.addView(swLan)
        box.addView(note(a, "Aus = der Sweep bleibt rein beim Funk (WLAN + Bluetooth). »lan« geht trotzdem einzeln."))

        // Tracking
        box.addView(section(a, "Aufspüren (track)"))
        val swBeep = toggle(a, "Piepton – schneller, je näher", Prefs.beep)
        val swVib = toggle(a, "Vibration – diskret, schneller, je näher", Prefs.vibrate)
        box.addView(swBeep); box.addView(swVib)
        box.addView(note(a, "Beides aus = nur die Statuszeile mit dBm, Balken und heiß/kalt."))

        // Radar
        box.addView(section(a, "Radar ◉"))
        val swRadar = toggle(a, "Nach dem Sweep automatisch das Radarbild zeigen", Prefs.radarAfterSweep)
        box.addView(swRadar)
        box.addView(note(a, "Sonst jederzeit über ◉ oben, »radar« oder das Schnellmenü (lange drücken)."))

        // Anzeige
        box.addView(section(a, "Anzeige"))
        val font = intArrayOf(Prefs.font)
        val fontRow = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL }
        fontRow.addView(text(a, "Schrift der Konsole"), LinearLayout.LayoutParams(0, -2, 1f))
        val fontBtns = mutableListOf<Button>()
        fun paintFont() = fontBtns.forEachIndexed { i, b ->
            b.setTextColor(if (i == font[0]) Col.BG else Col.ORANGE)
            (b.background as GradientDrawable).setColor(if (i == font[0]) Col.ORANGE else Col.PANEL)
        }
        listOf("klein", "normal", "groß").forEachIndexed { i, s ->
            val b = pill(a, s) { font[0] = i; paintFont() }
            fontBtns += b
            fontRow.addView(b, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = a.dp(4) })
        }
        paintFont()
        box.addView(fontRow)
        val swKeep = toggle(a, "Display bleibt an, solange SpyMon offen ist", Prefs.keepOn)
        box.addView(swKeep)

        // Updates
        box.addView(section(a, "Updates ⬆"))
        val swUpd = toggle(a, "Automatisch nach Updates suchen (beim Start, höchstens einmal am Tag)", Update.auto(a))
        box.addView(swUpd)
        box.addView(updateRow(a))
        box.addView(note(a, "Gelesen wird nur die Versionsdatei auf cappy0815.github.io (sonst GitHub). Installiert wird nichts " +
            "automatisch: Bei einer neuen Version erscheint oben „⬆ Update“, ein Tipp lädt die APK im Browser."))

        // App
        box.addView(section(a, "App"))
        val endRow = LinearLayout(a).apply { gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, a.dp(4), 0, a.dp(12)) }
        endRow.addView(pill(a, "App beenden") { a.quit() }, LinearLayout.LayoutParams(-1, -2))
        box.addView(endRow)

        val d = builder(a)
            .setView(scroll(a, box))
            .setNegativeButton("Abbrechen", null)
            .setPositiveButton("Speichern") { _, _ ->
                Prefs.btSecs = secs[0]
                Prefs.sweepLan = swLan.isChecked
                Prefs.beep = swBeep.isChecked
                Prefs.vibrate = swVib.isChecked
                Prefs.font = font[0]
                Prefs.keepOn = swKeep.isChecked
                Prefs.radarAfterSweep = swRadar.isChecked
                Update.setAuto(a, swUpd.isChecked)
                a.applyPrefs()
                a.con.ok("Einstellungen gespeichert")
            }
            .create()
        d.show(); style(d)
    }

    // ------------------------------------------------------------------ Hilfe
    private val TABS = listOf("Start", "Befehle", "Changelog", "Über")

    fun help(a: MainActivity, start: Int = 0) {
        val outer = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val head = LinearLayout(a).apply { gravity = Gravity.CENTER_VERTICAL; setPadding(a.dp(20), a.dp(16), a.dp(20), a.dp(4)) }
        head.addView(text(a, "✻ ", 20f, Col.ORANGE, true))
        head.addView(text(a, "SpyMon Hilfe", 18f, Col.FG, true), LinearLayout.LayoutParams(0, -2, 1f))
        head.addView(text(a, "v" + Update.installed(a), 13f, Col.DIM))
        outer.addView(head)

        val tabs = LinearLayout(a).apply { setPadding(a.dp(16), a.dp(6), a.dp(16), a.dp(6)) }
        outer.addView(HorizontalScrollView(a).apply { isHorizontalScrollBarEnabled = false; addView(tabs) })

        val content = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        val sv = scroll(a, content)
        outer.addView(sv, LinearLayout.LayoutParams(-1, a.dp(440)))

        val tabBtns = mutableListOf<Button>()
        fun select(t: Int) {
            tabBtns.forEachIndexed { i, b ->
                b.setTextColor(if (i == t) Col.BG else Col.ORANGE)
                (b.background as GradientDrawable).setColor(if (i == t) Col.ORANGE else Col.PANEL)
            }
            content.removeAllViews()
            helpContent(a, content, t)
            sv.scrollTo(0, 0)
        }
        TABS.forEachIndexed { i, s ->
            val b = pill(a, s) { select(i) }
            tabBtns += b
            tabs.addView(b, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = a.dp(6) })
        }
        select(start.coerceIn(0, TABS.size - 1))

        val d = builder(a).setView(outer).setPositiveButton("Schließen", null).create()
        d.show(); style(d)
        // Höhe an den Bildschirm anpassen (Reiter wechseln, ohne dass der Dialog springt)
        val hgt = (a.resources.displayMetrics.heightPixels * 0.62f).toInt()
        sv.layoutParams = LinearLayout.LayoutParams(-1, hgt)
    }

    private fun helpContent(a: MainActivity, c: LinearLayout, tab: Int) {
        when (tab) {
            0 -> {
                c.addView(section(a, "So geht der Zimmer-Check"))
                item(a, c, "1", "Standort und Bluetooth einschalten, WLAN an (verbinden ist NICHT nötig). Ohne Standort liefert Android keine Scan-Ergebnisse.")
                item(a, c, "2", "»sweep« antippen: prüft WLAN-Funknetze, Bluetooth und – falls du verbunden bist – das Hotel-WLAN.")
                item(a, c, "3", "Ergebnis lesen: [!!] verdächtig, [ ?] unklar, [ok] harmlos. Jeder Fund hat eine #Nummer. " +
                    "Zu nerdig? ◉ oben zeigt alles als Radarbild – Punkt antippen erklärt das Gerät in Klartext.")
                item(a, c, "4", "Verdächtiges orten: »track <nr>« und langsam durchs Zimmer gehen – heiß/kalt mit Piepton und/oder Vibration (⚙).")
                item(a, c, "5", "Sichtprüfung: »ir« zeigt Nachtsicht-LEDs mit der Frontkamera, »lens« findet Linsenreflexe mit Lampe und Rotfilter, »tips« ist die Checkliste.")
                item(a, c, "6", "»save« legt den Bericht unter Download/SpyMon ab, »share« schickt ihn weiter.")
                c.addView(section(a, "Bedienung"))
                item(a, c, "❯", "Befehl unten eintippen oder einen der Knöpfe darüber antippen. Pfeil hoch = letzter Befehl (Hardware-Tastatur).")
                item(a, c, "📶", "WLAN-Netze (Symbol oben links neben ◉): alle Funknetze mit Signalstärke, Band, Kanal, Verschlüsselung und Hersteller. " +
                    "Aktualisiert sich alle 30 s. Gleichnamige Zugangspunkte werden gebündelt – antippen klappt sie auf.")
                item(a, c, "☰", "Lange drücken (oder Rechtsklick mit Maus) auf Konsole, Knöpfe oder ❯ öffnet das Schnellmenü mit allen Scans.")
                item(a, c, "◉", "Radarbild: dein Handy in der Mitte, oben WLAN, rechts Bluetooth LE, unten Bluetooth, links Hotel-LAN. " +
                    "Je näher an der Mitte, desto stärker das Signal. Die Lage im Sektor ist keine Himmelsrichtung – " +
                    "die kann ein Handy nicht messen; dafür gibt es »Aufspüren«.")
                item(a, c, "⬆", "Erscheint oben nur, wenn eine neue Version bereitliegt.")
                item(a, c, "⚙", "Einstellungen: Scandauer, Hotel-LAN, Tracking-Signal, Radar, Schrift, Display, Updates, App beenden.")
                item(a, c, "?", "Diese Hilfe. Konsole kopieren: lange drücken → „Alles kopieren“.")
                item(a, c, "↩", "Zurück-Taste schließt Radar und Kamera-Ansicht (ir/lens).")
                c.addView(section(a, "Grenzen – ehrlich gesagt"))
                item(a, c, "•", "Geräte ohne Funk (Kamera mit SD-Karte, Diktiergerät) sieht keine App – dafür »ir«, »lens« und »tips«.")
                item(a, c, "•", "Ohne Root kein Monitor-Mode: in fremden WLANs sind nur Access Points sichtbar, keine einzelnen Geräte.")
                item(a, c, "•", "Android erlaubt nur 4 WLAN-Scans pro 2 Minuten. Entwickleroptionen → „WLAN-Scan-Drosselung“ aus = schneller.")
                item(a, c, "•", "Hotels trennen Gäste oft voneinander (Client-Isolation) – dann sieht »lan« fast nichts. Das ist normal.")
                item(a, c, "•", "Bluetooth-Adressen, die sich regelmäßig ändern (zufällige Adressen), haben keinen Hersteller-Eintrag.")
            }
            1 -> {
                c.addView(section(a, "Befehle"))
                for ((k, v) in COMMANDS) {
                    val row = LinearLayout(a).apply { setPadding(0, a.dp(3), 0, a.dp(3)) }
                    row.addView(text(a, k, 13f, Col.ORANGE).apply { typeface = Typeface.MONOSPACE; minWidth = a.dp(108) })
                    row.addView(text(a, v, 13f), LinearLayout.LayoutParams(0, -2, 1f))
                    c.addView(row)
                }
                c.addView(section(a, "Markierungen"))
                item(a, c, "[!!]", "verdächtig: Kamera-Name, Kamera-Hersteller, getrennter Tracker, Mikrofon/Kamera-Geräteklasse, Kamera-Dienste im LAN")
                item(a, c, "[ ?]", "unklar: IoT-/Bastelchip, verstecktes Netz ganz nah, Beacon, unbekanntes Gerät ganz nah")
                item(a, c, "[ok]", "harmlos nach allem, was erkennbar ist")
                c.addView(note(a, "Kurzformen: all/check/scan = sweep, wlan = wifi, networks = netze, bluetooth = bt, net = lan, find = track, ls = list, " +
                    "cls = clear, hilfe/? = help, einstellungen/opt = settings, über = about, beenden/quit = exit."))
            }
            2 -> {
                for ((v, lines) in CHANGELOG) {
                    c.addView(text(a, "Version $v", 14f, Col.ORANGE, true).apply { setPadding(0, a.dp(12), 0, a.dp(2)) })
                    lines.forEach { item(a, c, "•", it) }
                }
                c.addView(text(a, CREDITS, 12f, Col.DIM).apply {
                    gravity = Gravity.CENTER; setTypeface(typeface, Typeface.ITALIC); setPadding(0, a.dp(18), 0, a.dp(10))
                }, LinearLayout.LayoutParams(-1, -2))
            }
            else -> {
                c.addView(section(a, "SpyMon " + Update.installed(a)))
                c.addView(text(a, "Spy Monitor für unterwegs: SpyMon prüft Hotelzimmer, Ferienwohnungen und Besprechungsräume auf " +
                    "versteckte Funk-Kameras, Bluetooth-Tracker, Wanzen und Kamera-Dienste im WLAN – und hilft mit Infrarot- und " +
                    "Linsen-Modus bei der Sichtprüfung. Bedient wird alles über eine schnelle Konsole."))
                c.addView(section(a, "Hinweise"))
                item(a, c, "🎁", "Diese App ist ein privates Hobbyprojekt. Sie ist kostenlos und nicht zum Verkauf bestimmt.")
                item(a, c, "⚠", "Die Nutzung erfolgt auf eigene Verantwortung. Die App wird ohne Gewähr bereitgestellt; " +
                    "eine Haftung für Schäden, die aus ihrer Nutzung entstehen, ist – soweit gesetzlich zulässig – ausgeschlossen.")
                item(a, c, "🔍", "Funde sind Hinweise, keine Beweise. Ein unauffälliges Ergebnis garantiert nicht, dass nichts da ist – " +
                    "und nicht jedes [!!] ist eine Kamera.")
                item(a, c, "🚨", "Bei konkretem Verdacht: Rezeption bzw. Vermieter oder die Polizei einschalten.")
                item(a, c, "🌐", "»lan« fragt Geräte im verbundenen WLAN ab und testet Ports im eigenen /24-Netz. Nur in Netzen nutzen, " +
                    "in denen du Gast bist – manche Betreiber untersagen Port-Scans in ihren Nutzungsbedingungen.")
                item(a, c, "🔒", "Scan-Ergebnisse und Berichte bleiben auf dem Gerät. Einzige Verbindung ins Internet ist die Update-Prüfung " +
                    "(abschaltbar): Sie liest die Versionsdatei auf cappy0815.github.io bzw. GitHub. Daten aus der App werden nicht gesendet.")
                item(a, c, "📇", "Hersteller-Erkennung über MAC-Präfixe aus der Wireshark-„manuf“-Liste (IEEE-Register).")
                item(a, c, "ℹ", "Unabhängiges Projekt ohne Verbindung zu den Herstellern der erkannten Geräte.")
                c.addView(section(a, "Updates & Projekt"))
                c.addView(updateRow(a))
                c.addView(link(a, "🔗 Projektseite: cappy0815.github.io", Update.PROJECT_PAGE))
                c.addView(link(a, "🔗 Quellcode: github.com/cappy0815/spymon", Update.GITHUB_PAGE))
                c.addView(section(a, "Gebaut von"))
                c.addView(text(a, "Erste Version von Claude Code, fertiggestellt von Justus Jonas – also Claude, der KI von Anthropic – " +
                    "nach Idee und Wünschen von Michael Nowak. Gebaut im November 2026."))
                c.addView(text(a, CREDITS, 13f, Col.DIM).apply {
                    gravity = Gravity.CENTER; setTypeface(typeface, Typeface.ITALIC); setPadding(0, a.dp(18), 0, a.dp(10))
                }, LinearLayout.LayoutParams(-1, -2))
            }
        }
    }
}
