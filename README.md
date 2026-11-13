# SpyMon – Spy Monitor

Android-App für den schnellen Zimmer-Check unterwegs: Hotelzimmer, Ferienwohnung oder Besprechungsraum
auf versteckte Funk-Kameras, Bluetooth-Tracker, Wanzen und Kamera-Dienste im WLAN prüfen – plus
Infrarot- und Linsen-Modus für die Sichtprüfung. Bedienung über eine schnelle Konsole (dunkel/orange) –
oder über das **Radarbild**: dein Handy in der Mitte, die gefundenen Geräte drumherum nach Funkart,
Signalstärke und Einstufung, jedes mit einer Erklärung in Klartext. Dazu eine Liste aller WLAN-Netze
der Umgebung mit Signalstärke, Kanal und Verschlüsselung.

**Download:** [cappy0815.github.io](https://cappy0815.github.io/#spymon) · aktuelle Version **1.5**

![SpyMon: Radarbild, Detailkarte zu einem Fund und Liste der WLAN-Netze mit Signalstärke](docs/screenshot.webp)

## Was SpyMon prüft

| Befehl | Funktion |
|---|---|
| `sweep` | alles: WLAN-Funknetze + Bluetooth + (falls verbunden) Hotel-LAN, mit Zusammenfassung |
| `radar` | Umgebungsbild (auch ◉ oben): Punkt antippen = Klartext, Tipp und „Aufspüren“ |
| `netze` | WLAN-Netze (WLAN-Symbol oben): Signal in dBm/Balken/Prozent, Band, Kanal, Kanalbreite, Verschlüsselung, Wi-Fi-Standard, Hersteller; alle 30 s neu |
| `wifi` | WLAN-Funknetze in der Umgebung, ohne Verbindung: Kamera-typische Namen, Hersteller per MAC-Präfix, versteckte Netze |
| `bt [s]` | Bluetooth LE + klassisch: AirTag/Find-My, Samsung SmartTag, Tile, Chipolo, Google Find Hub, Beacons, Mikrofon-/Kamera-Geräteklassen, Bastelmodule |
| `lan` | im verbundenen WLAN: UPnP, ONVIF, Port-Sweep im eigenen /24, Web-Oberflächen |
| `track <nr>` | heiß/kalt-Suche nach einem Fund mit Piepton und/oder Vibration |
| `ir` | Kamera macht Infrarot-LEDs von Nachtsicht-Kameras sichtbar (Haupt- oder Frontkamera umschaltbar – mit TV-Fernbedienung testen, welche IR sieht) |
| `lens` | Hauptkamera + Lampe + Rotfilter für Linsenreflexe |
| `list`, `tips`, `save`, `share`, `oui <mac>`, `info` | Funde, Checkliste, Bericht, Hersteller-Abfrage, Status |
| `settings`, `update`, `about`, `exit` | Einstellungen, Update-Prüfung, Über, App beenden |

Markierungen: `[!!]` verdächtig · `[ ?]` unklar · `[ok]` harmlos. Jeder Fund bekommt eine `#nr`.

**Schnellmenü:** lange drücken (oder Rechtsklick mit Maus) auf Konsole, Befehlsknöpfe oder `❯`.

**Schnellmenü am App-Icon:** lange aufs SpyMon-Icon drücken → Sweep starten, Radar, WLAN-Netze,
Linsen-Suche. Die Einträge lassen sich als eigene Icons auf den Startbildschirm ziehen.

**Radar:** oben WLAN, rechts Bluetooth LE, unten Bluetooth, links Hotel-LAN. Je näher an der Mitte,
desto stärker das Signal (Ringe bei −50, −65, −80 dBm). Die Lage innerhalb eines Sektors ist keine
Himmelsrichtung – die kann ein Handy nicht messen; dafür gibt es `track`. Für volle Umgebungen gibt es
Filter (Funkarten, Einstufung, Mindestsignal mit Zoom, Höchstzahl, gleichnamige WLANs bündeln) und
„bekannte“ Geräte, die sich ausblenden lassen. Das Urteil oben zählt trotzdem alle Funde.

## Grenzen

- Geräte ohne Funk (Kamera mit SD-Karte, Diktiergerät) sieht keine App – dafür `ir`, `lens`, `tips`.
- Ohne Root kein Monitor-Mode: in fremden WLANs sind nur Access Points sichtbar.
- Android erlaubt 4 WLAN-Scans pro 2 Minuten (Entwickleroptionen → „WLAN-Scan-Drosselung“ aus).
- Standort muss an sein, sonst liefert Android keine WLAN-/Bluetooth-Ergebnisse.
- Funde sind Hinweise, keine Beweise.

## Datenschutz

Scan-Ergebnisse und Berichte bleiben auf dem Gerät. Einzige Internetverbindung ist die abschaltbare
Update-Prüfung: Sie liest `https://cappy0815.github.io/downloads/spymon.json` (Rückfall: neuestes
GitHub-Release dieses Repos). Es werden keine Daten aus der App gesendet.

## Bauen

Kotlin, keine externen Bibliotheken (nur Android-SDK). AGP 8.7.3, Kotlin 2.1.0, Gradle 8.11.1 (Wrapper),
JDK 17, compileSdk 35, targetSdk 34, minSdk 29.

```
gradlew assembleRelease
→ app/build/outputs/apk/release/app-release.apk
```

Der Signaturschlüssel (`keystore/spymon.jks` + `keystore/keystore.properties`) liegt bewusst nicht im Repo.
Ohne ihn baut Gradle unsigniert bzw. mit Debug-Schlüssel – solche APKs lassen sich nicht über die
offizielle Version installieren.

## Hinweise

Privates Hobbyprojekt, kostenlos, ohne Gewähr. Nutzung auf eigene Verantwortung. `lan` nur in Netzen
verwenden, in denen du Gast bist – manche Betreiber untersagen Port-Scans.
Hersteller-Erkennung über MAC-Präfixe aus der Wireshark-„manuf“-Liste (IEEE-Register).

---
Gebaut von Justus Jonas & MCHLNWK – mit Claude am Radar 📡
