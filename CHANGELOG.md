# Changelog

## 1.3 (08.11.2026)
- Neu: WLAN-Netze – alle Funknetze der Umgebung als Liste mit Signalstärke (dBm, Balken, Prozent, Klartext)
- Pro Netz: Band, Kanal, Kanalbreite, Verschlüsselung (offen/WEP gelb markiert), Wi-Fi-Standard, Hersteller
- Gleiche Namen werden zu einem Netz gebündelt (Hotel-WLAN mit vielen Zugangspunkten) – umschaltbar auf „einzeln“
- Filter nach 2,4 / 5 / 6 GHz, Sortierung nach Signal oder Name, Antippen klappt alle Zugangspunkte auf
- Aktualisiert sich alle 30 Sekunden selbst; auffällige Netze mit Begründung und „Aufspüren“
- Erreichbar über das WLAN-Symbol oben, `netze`, das Schnellmenü und den Knopf im Radar

## 1.2 (08.11.2026)
- Radar: Umgebungsbild mit deinem Handy in der Mitte – Geräte nach Funkart sortiert (oben WLAN, rechts Bluetooth LE, unten Bluetooth, links Hotel-LAN), Abstand nach Signalstärke, Farbe nach Einstufung
- Radar: Punkt antippen = Erklärung in Klartext, Tipp was zu tun ist, „Aufspüren“ direkt aus der Karte
- Radar: Sweep direkt aus dem Bild starten, Bluetooth-Geräte erscheinen schon während des Scans, Filter „nur auffällige“
- Schnellmenü: lange drücken (oder Rechtsklick mit Maus) auf Konsole, Knöpfe oder ❯
- Kopfzeile in der Konsole ohne Rahmen, Trennlinien passen sich der Bildschirmbreite an (kein Umbruch mehr)
- Einstellung: Radar nach dem Sweep automatisch zeigen
- Neuer Befehl: `radar`

## 1.1 (08.11.2026)
- Neuer Name: SpyMon (vorher RoomSweep), Paket `de.cappy.spymon`
- Kopfleiste mit ⬆ Update (nur wenn neu), ⚙ Einstellungen und ? Hilfe
- Hilfe mit Reitern: Start, Befehle, Changelog, Über (Hinweise, Projektseite, Quellcode)
- Einstellungen: Bluetooth-Scandauer, Hotel-LAN im Sweep an/aus, Tracking mit Piepton und/oder Vibration, Schriftgröße, Display bleibt an
- Update-Prüfung über die Projektseite: beim Start höchstens einmal am Tag (abschaltbar) oder von Hand mit „Jetzt prüfen“
- „App beenden“ in den Einstellungen und per `exit`
- Neue Befehle: `settings`, `update`, `about`, `exit`
- Berichte liegen jetzt unter `Download/SpyMon`

## 1.0 (08.11.2026, als „RoomSweep“)
- Erste Version (Claude Code): Konsole mit sweep, wifi, bt, lan, track, ir, lens, list, tips, save, share, oui, info
