# SpyMon – Entwicklernotizen (Android)

> Seit v1.1 heißt die App SpyMon (Paket `de.cappy.spymon`, neuer Schlüssel `keystore/spymon.jks`, nicht im Repo).
> Neu in 1.1: `Prefs.kt` (Einstellungen), `Update.kt` (Update-Prüfung über `cappy0815.github.io/downloads/spymon.json`, Rückfall GitHub-Release), `Dialogs.kt` (Hilfe, Einstellungen, Update-Dialog, CHANGELOG, Befehlsliste).
> Neu in 1.2: `Radar.kt` (RadarView + Vollbild-Ansicht mit Detailkarte), Schnellmenü (`quickMenu()` in MainActivity), `Console.fill()` für Linien auf Bildschirmbreite.
> Neu in 1.3: `Networks.kt` (Ansicht WLAN-Netze, Klasse `Net`, `SignalBars`, `WifiGlyph` für die Kopfleiste).
> Neu in 1.5: App-Shortcuts (`res/xml/shortcuts.xml`, Symbole `drawable/sc_*.xml`, Texte `values/strings.xml`). Sie zielen auf `ShortcutActivity` (unsichtbar, `taskAffinity=""`), weil Android feste Shortcuts mit CLEAR_TASK startet; die reicht die Aktion an `MainActivity` (`launchMode=singleTask`, `onNewIntent` → `shortcut()`) weiter.
> Bei jeder Version: `versionCode`/`versionName` in `app/build.gradle.kts`, `CHANGELOG` in `Dialogs.kt`, `CHANGELOG.md`, `docs/spymon.json`.

Erste Version (RoomSweep 1.0) von Claude Code am 2026-11-08, ab 1.1 von Justus weitergeführt.
Ziel-Handy: Samsung Galaxy S25 Ultra. Der User installiert die APK selbst (Sideload).

## Was die App macht
Konsolen-Oberfläche (dunkel/orange, Monospace, Prompt `❯`, Schnellbefehl-Chips), Ausgabe als Textzeilen.
Funde werden markiert: `[!!]` verdächtig (rot), `[ ?]` unklar (gelb), `[ok]` harmlos (grün), jeder Fund bekommt eine `#nr`.

| Befehl | Funktion |
|---|---|
| `sweep` | WLAN + Bluetooth (25 s) + Hotel-LAN (nur falls verbunden) + Zusammenfassung |
| `wifi` | WLAN-Funknetze in der Umgebung, OHNE Verbindung. SSID-Muster (IPC-, V380, HDWiFiCam …), Hersteller per MAC-Präfix (Hikvision, Dahua, Espressif, Tuya …), versteckte Netze, Signalstärke |
| `bt [s]` | BLE + klassisches Bluetooth. Erkennt AirTag/Find-My (getrennt = `[!!]`), Samsung SmartTag (FD5A), Tile (FEED/FEEC), Chipolo (FE33), Google Find Hub (FEAA Frame 0x40/0x41), iBeacon/Eddystone, Namen wie cam/spy/mic/gps, Bastelmodule (HC-05, ESP32), BT-Geräteklasse Mikrofon/Kamera |
| `lan` | Im verbundenen WLAN: SSDP/UPnP, ONVIF-WS-Discovery, Port-Sweep im eigenen /24 (554, 8554, 80, 81, 8000, 8080, 8899, 34567, 37777, 1935, 23, 6668 …), HTTP-Banner/Titel gegen Kamera-Begriffe. Erkennt Client-Isolation |
| `track <nr>` | Heiß/kalt-Suche für BLE- oder WLAN-Fund: Live-Statuszeile mit dBm, Balken, Trend, Piepton schneller je näher |
| `ir` | Frontkamera-Vorschau (IR-LEDs von Nachtsicht-Kameras sichtbar machen) + Anleitung |
| `lens` | Hauptkamera + Taschenlampe + Rotfilter (umschaltbar) zum Finden von Linsenreflexen |
| `list`, `tips`, `save`, `share`, `oui <mac>`, `info`, `stop`, `clear`, `help` | siehe App |

`save` schreibt nach `Download/SpyMon/SpyMon_JJJJMMTT_HHMM.txt`.

## Grenzen (dem User so erklärt)
- Geräte ohne Funk (SD-Karten-Kamera, Diktiergerät) sieht keine App → `ir`, `lens`, `tips`.
- Ohne Root kein Monitor-Mode: WLAN-Clients in fremden Netzen sind unsichtbar, nur Access Points.
- Android drosselt WLAN-Scans (4 pro 2 Min.). In den Entwickleroptionen „WLAN-Scan-Drosselung“ ausschalten.
- Standort muss AN sein (Android-Vorgabe für WLAN/BLE-Scans).
- BLE-Adressen mit erstem Byte ≥ C0 werden als zufällig behandelt (kein Hersteller-Lookup).

## Projekt
- Kotlin, KEINE externen Bibliotheken (nur Android-SDK). Kein AndroidX, kein Compose.
- AGP 8.7.3, Kotlin 2.1.0, Gradle 8.11.1 (Wrapper liegt bei), JDK 17, compileSdk 35, targetSdk 34 (bewusst, um Edge-to-Edge-Zwang zu vermeiden), minSdk 29.
- Paket: `de.cappy.spymon`
- Dateien in `app/src/main/java/de/cappy/spymon/`:
  - `MainActivity.kt`: UI (programmatisch, kein XML-Layout), Befehls-Parser `exec()`, Sweep, Tracking, Bericht, Berechtigungen
  - `Console.kt`: Farben (`Col`), Konsolen-Ausgabe, Datenmodell `Finding`/`Findings`, Formatierung `Fmt`
  - `Oui.kt`: Hersteller-DB + `Rules` (alle Erkennungsmuster an einer Stelle; hier am einfachsten erweitern)
  - `WifiScan.kt`, `BtScan.kt`, `LanScan.kt`, `CamView.kt` (Camera2 + TextureView)
- `app/src/main/assets/oui.txt`: ~38.000 MAC-Präfixe aus der Wireshark-„manuf“-Liste (Format `AABBCC<TAB>Firma`).
- Signatur: `keystore/spymon.jks` + `keystore/keystore.properties` (privat, nicht im Repo; liegt im Quellcode-ZIP auf Google Drive). **Immer diesen Schlüssel benutzen**, sonst lässt sich die neue APK nicht über die alte installieren.

## Bauen
```
gradlew assembleRelease
→ app/build/outputs/apk/release/app-release.apk
```
Braucht `local.properties` mit `sdk.dir=…` oder die Umgebungsvariable ANDROID_HOME.
Bei jeder Änderung `versionCode` und `versionName` in `app/build.gradle.kts` hochzählen.

## Ideen für später
- Fund-Historie pro Hotel/Zimmer vergleichen
- mDNS (`_rtsp._tcp`, `_http._tcp`) im LAN
- Magnetometer-Modus (Metall/Elektronik in Wänden, eher Spielerei)
- Akustischer Ultraschall-Check (manche Wanzen piepen hochfrequent)
