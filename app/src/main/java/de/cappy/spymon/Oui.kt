package de.cappy.spymon

import android.content.Context

/** Hersteller-Datenbank (MAC-Präfix → Firma), Quelle: Wireshark "manuf" (IEEE). */
class Oui(ctx: Context) {
    private val map = HashMap<String, String>(45000)

    init {
        ctx.assets.open("oui.txt").bufferedReader().useLines { lines ->
            for (l in lines) {
                val i = l.indexOf('\t')
                if (i == 6) map[l.substring(0, 6)] = l.substring(7)
            }
        }
    }

    val size get() = map.size

    fun vendor(mac: String?): String? {
        if (mac == null) return null
        val k = mac.replace(":", "").replace("-", "").uppercase()
        if (k.length < 6) return null
        return map[k.substring(0, 6)]
    }

    companion object {
        /** Zufällige / lokal vergebene MAC (Bit 2 im ersten Byte). */
        fun isRandom(mac: String?): Boolean {
            if (mac == null || mac.length < 2) return false
            val b = mac.substring(0, 2).toIntOrNull(16) ?: return false
            return b and 0x02 != 0
        }
    }
}

/** Erkennungsregeln – bewusst paranoid. */
object Rules {
    val camSsid = Regex(
        "(?i)^(ipc|ipcam|ip-?cam|hdwificam|hd-?cam|wifi-?cam|cam[-_ ]|v380|a9[-_]|mv\\d|xm[-_]|jxl|lookcam|" +
            "yi[-_]|mini[-_ ]?cam|spy|hidden|smart[-_ ]?cam|cloudcam|danale|sricam|wansview|p2p[-_]|gw_ap|" +
            "ezviz|imou|reolink|wyze|ycc365|icsee|hiseeu|ubox|xmeye|camhi|carecam|littlelf|hapsee|v720|bvcam)"
    )
    val camWord = Regex("(?i)(cam|kamera|camera|spy|ipc|dvr|nvr|video)")
    val iotSsid = Regex("(?i)^(esp[-_]|esp32|esp8266|ai-thinker|tuya|smartlife|sl-|smart_|hap-|shelly|tasmota|sonoff|ewelink|itead|beken|bk7231|wlan-setup|setup-)")

    val camVendors = listOf(
        "hikvision", "dahua", "ezviz", "uniview", "xiongmai", "foscam", "amcrest", "reolink", "wyze",
        "axis communications", "hanwha", "vivotek", "imou", "lorex", "swann", "annke", "vstarcam",
        "wansview", "sricam", "jooan", "hangzhou", "arlo", "eufy", "anker innovations", "blink", "ring llc"
    )
    val iotVendors = listOf(
        "espressif", "tuya", "shenzhen", "beken", "bouffalo", "winner micro", "winnermicro", "gaoshengda",
        "ampak", "azurewave", "universal global", "guangzhou", "zhejiang", "fn-link", "fn link", "altobeam",
        "ingenic", "anyka", "realtek semiconductor", "mediatek", "hui zhou", "sichuan", "xiaomi", "ruijie"
    )

    fun vendorLevel(v: String?): Int {
        if (v == null) return 0
        val l = v.lowercase()
        if (camVendors.any { l.contains(it) }) return 2
        if (iotVendors.any { l.contains(it) }) return 1
        return 0
    }

    val btBad = Regex("(?i)(cam|kamera|camera|spy|mic|mikro|recorder|voice|rec[-_ ]|bug|wanze|gps|tracker|listen|audio[-_ ]?rec|dictaphone|diktier)")
    val btModule = Regex("(?i)(^hc-0\\d|^jdy|esp32|^bt0\\d|^hm-?1\\d|^mlt-bt|^at-09|^ble[-_ ]?cam|^itag|^i-tag|^tag[-_ ]?\\d)")

    val httpCam = Regex(
        "(?i)(hikvision|dahua|webcam|ip ?cam|netsurveillance|nvr|dvr|camera|onvif|xmeye|ipcamera|live ?view|" +
            "kamera|reolink|foscam|amcrest|uniview|vstarcam|rtsp|dnvrs|app-webs|uc-httpd|hipcam|gm8136|" +
            "web ?client|surveillance|video ?server)"
    )
}
