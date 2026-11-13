package de.cappy.spymon

import android.app.Activity
import android.content.Intent
import android.os.Bundle

/**
 * Unsichtbarer Zwischenschritt für das Schnellmenü am App-Icon.
 * Android startet feste Shortcuts immer mit NEW_TASK + CLEAR_TASK – direkt auf MainActivity gerichtet
 * würde das die laufende App samt Funden und Scan beenden. Diese Activity läuft deshalb in einer
 * eigenen Aufgabe (taskAffinity="") und reicht den Wunsch nur an MainActivity weiter (singleTask →
 * onNewIntent, wenn SpyMon schon läuft).
 */
class ShortcutActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        startActivity(Intent(this, MainActivity::class.java)
            .setAction(intent?.action)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        finish()
    }
}
