package de.cappy.spymon

import android.annotation.SuppressLint
import android.graphics.SurfaceTexture
import android.graphics.Typeface
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.os.Handler
import android.os.HandlerThread
import android.view.Gravity
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView

/**
 * Kamera-Modi:
 *  ir   = Infrarot-LEDs von Nachtsicht-Kameras als helle Punkte. Hauptkamera (Standard) oder Frontkamera,
 *         umschaltbar – welche Kamera IR durchlässt, ist je Handy verschieden (Test mit TV-Fernbedienung).
 *  lens = Hauptkamera + Taschenlampe + Rotfilter → Kameralinsen reflektieren als Glitzerpunkt
 */
@SuppressLint("MissingPermission", "SetTextI18n")
class CamView(private val a: MainActivity, private var front: Boolean, private val torch: Boolean, private val ir: Boolean = !torch) {
    private val frame = FrameLayout(a)
    private val tex = TextureView(a)
    private var dev: CameraDevice? = null
    private var ses: CameraCaptureSession? = null
    private val th = HandlerThread("cam").apply { start() }
    private val h = Handler(th.looper)
    private var tint: View? = null
    private lateinit var hint: TextView

    fun show() {
        frame.setBackgroundColor(Col.BG)
        frame.isClickable = true
        frame.addView(tex, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        if (torch) {
            tint = View(a).apply { setBackgroundColor(0x66FF0000) }
            frame.addView(tint, FrameLayout.LayoutParams(-1, -1))
        }
        hint = TextView(a).apply {
            typeface = Typeface.MONOSPACE; textSize = 12f; setTextColor(Col.FG)
            setBackgroundColor(0xCC0E0B09.toInt()); setPadding(28, 20, 28, 20)
            text = if (!torch) irText()
            else
                "● LINSEN-SUCHE (Lampe + Rotfilter)\n" +
                    "  Raum abdunkeln, Handy auf Augenhöhe,\n" +
                    "  langsam schwenken. Eine Linse glitzert als\n" +
                    "  kleiner heller Punkt, der beim Bewegen\n" +
                    "  ›mitwandert‹. Nah ran an Verdächtiges!"
        }
        frame.addView(hint, FrameLayout.LayoutParams(-1, -2, Gravity.TOP))
        val bar = if (torch) a.btnRow(
            "Rotfilter" to { tint?.let { it.visibility = if (it.visibility == View.VISIBLE) View.GONE else View.VISIBLE } },
            "✕ Schließen" to { a.closeCam() })
        else a.btnRow(
            "Hauptkamera" to { switchCam(false) },
            "Frontkamera" to { switchCam(true) },
            "✕ Schließen" to { a.closeCam() })
        camBar = bar
        bar.setPadding(a.dp(8), 0, a.dp(8), a.dp(16))
        frame.addView(bar, FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM))
        paintBar()
        a.root.addView(frame, FrameLayout.LayoutParams(-1, -1))

        tex.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(s: SurfaceTexture, w: Int, hh: Int) = open()
            override fun onSurfaceTextureSizeChanged(s: SurfaceTexture, w: Int, hh: Int) {}
            override fun onSurfaceTextureDestroyed(s: SurfaceTexture) = true
            override fun onSurfaceTextureUpdated(s: SurfaceTexture) {}
        }
    }

    private var camBar: LinearLayout? = null

    private fun irText() =
        "● IR-MODUS (" + (if (front) "Frontkamera" else "Hauptkamera") + ")\n" +
            "  Licht im Zimmer AUS. Langsam schwenken:\n" +
            "  Rauchmelder, Wecker, Ladegeräte, TV, Bilder,\n" +
            "  Lüftung, Steckdosen. Lila/weiße Punkte = IR!\n" +
            "  Erst testen: TV-Fernbedienung in die Kamera\n" +
            "  halten, Taste drücken – blinkt es, sieht diese\n" +
            "  Kamera IR. Sonst die andere Kamera nehmen."

    private fun paintBar() {
        val b = camBar ?: return
        if (ir) with(a) { sel(b.btns[0], !front); sel(b.btns[1], front) }
    }

    /** IR-Modus: zwischen Haupt- und Frontkamera wechseln (Wahl wird gemerkt) */
    private fun switchCam(toFront: Boolean) {
        if (toFront == front) return
        front = toFront
        Prefs.irFront = toFront
        hint.text = irText()
        paintBar()
        h.post {
            try { ses?.close() } catch (_: Exception) {}
            try { dev?.close() } catch (_: Exception) {}
            ses = null; dev = null
            a.runOnUiThread { if (tex.isAvailable) open() }
        }
    }

    private fun open() {
        try {
            val cm = a.getSystemService(CameraManager::class.java)
            val want = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
            val id = cm.cameraIdList.firstOrNull { cm.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == want }
                ?: cm.cameraIdList.first()
            val ch = cm.getCameraCharacteristics(id)
            val map = ch.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)!!
            val sizes = map.getOutputSizes(SurfaceTexture::class.java)
            val size = sizes.filter { it.width <= 1920 && it.height <= 1080 }.maxByOrNull { it.width * it.height } ?: sizes[0]
            a.runOnUiThread {
                val w = frame.width
                tex.layoutParams = FrameLayout.LayoutParams(w, w * size.width / size.height, Gravity.CENTER)
            }
            tex.surfaceTexture!!.setDefaultBufferSize(size.width, size.height)
            cm.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(d: CameraDevice) { dev = d; preview(d) }
                override fun onDisconnected(d: CameraDevice) { d.close() }
                override fun onError(d: CameraDevice, e: Int) { d.close(); a.con.err("Kamera-Fehler $e") }
            }, h)
        } catch (e: Exception) {
            a.con.err("Kamera: ${e.message}")
        }
    }

    @Suppress("DEPRECATION")
    private fun preview(d: CameraDevice) {
        val surf = Surface(tex.surfaceTexture)
        val rb = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
            addTarget(surf)
            set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            set(CaptureRequest.CONTROL_AE_MODE, CaptureRequest.CONTROL_AE_MODE_ON)
            if (torch) set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
        }
        d.createCaptureSession(listOf(surf), object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(s: CameraCaptureSession) {
                ses = s
                try { s.setRepeatingRequest(rb.build(), null, h) } catch (_: Exception) {}
            }
            override fun onConfigureFailed(s: CameraCaptureSession) { a.con.err("Kamera-Vorschau fehlgeschlagen") }
        }, h)
    }

    fun close() {
        try { ses?.close() } catch (_: Exception) {}
        try { dev?.close() } catch (_: Exception) {}
        th.quitSafely()
        a.root.removeView(frame)
    }
}
