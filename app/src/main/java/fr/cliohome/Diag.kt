package fr.cliohome

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.LocationManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.View
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Diagnostic : cherche d'où l'autoradio tire la vitesse et la température.
 * - GPS : sources de position, satellites vus, dernières positions.
 * - Applis du fabricant liées à la voiture (CAN, MCU…), avec leurs services.
 * - Messages internes (broadcasts) envoyés par ces applis, enregistrés en direct.
 */
object Diag {
    /** Messages reçus depuis le démarrage de Clio Home (les 40 derniers). */
    val events = ArrayDeque<String>()
    var onEvent: (() -> Unit)? = null
    var satVisible = -1
    var satUsed = -1

    /** Actions de messages utilisées par les familles d'autoradios Android connues pour la voiture. */
    private val ACTIONS = listOf(
        "com.microntek.canbusdisplay", "com.microntek.CANBUS", "com.microntek.irkeyDown",
        "com.syu.ms.canbus", "com.syu.canbus", "com.syu.ms.action.canbus",
        "com.fyt.canbus", "com.fyt.boot.ACCON", "com.tw.canbus", "com.tw.service.canbus",
        "com.android.canbus", "android.intent.action.CANBUS", "com.canbus.data", "com.canbus.action",
        "com.ts.can.ACTION_CAN", "com.ts.canbus", "com.nwd.action.ACTION_CAN_DATA", "com.nwd.can",
        "com.autochips.canbus", "com.autochips.car", "com.yf.canbus", "com.hzbhd.canbus",
        "com.hzbhd.intent.action.canbus", "com.raise.canbus", "com.rs.canbus", "com.roadrover.canbus",
        "android.intent.action.CAR_SPEED", "android.intent.action.SPEED", "com.car.speed",
        "android.intent.action.ILLUMINATION", "com.microntek.light", "com.syu.ms.light",
        "android.intent.action.ACC_ON", "android.intent.action.ACC_OFF", "com.android.action.ACC_ON",
        "android.media.VOLUME_CHANGED_ACTION",
    )

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { log(i) }
    }

    private fun log(i: Intent) {
        val extras = try {
            i.extras?.keySet()?.joinToString(", ") { k -> "$k=${i.extras?.get(k)}" } ?: ""
        } catch (e: Exception) { "?" }
        val line = SimpleDateFormat("HH:mm:ss", Locale.FRANCE).format(Date()) + "  " + i.action + (if (extras.isNotEmpty()) "  { $extras }" else "")
        events.addFirst(line.take(400))
        while (events.size > 40) events.removeLast()
        onEvent?.invoke()
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    fun start(ctx: Context) {
        val f = IntentFilter(); ACTIONS.forEach { f.addAction(it) }
        try {
            // Les messages « collants » renvoient tout de suite leur dernière valeur
            val sticky = ctx.registerReceiver(receiver, f)
            if (sticky != null) log(sticky)
        } catch (e: Exception) {}
    }

    fun stop(ctx: Context) { try { ctx.unregisterReceiver(receiver) } catch (e: Exception) {} }

    private val gnss = object : GnssStatus.Callback() {
        override fun onSatelliteStatusChanged(s: GnssStatus) {
            satVisible = s.satelliteCount
            satUsed = (0 until s.satelliteCount).count { s.usedInFix(it) }
        }
    }

    private var gnssOn = false

    @SuppressLint("MissingPermission")
    fun startGnss(ctx: Context) {
        if (gnssOn) return
        try { (ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager).registerGnssStatusCallback(gnss, Handler(Looper.getMainLooper())); gnssOn = true } catch (e: Exception) {}
    }

    /** Rapport texte complet (affiché à l'écran et exportable). */
    @SuppressLint("MissingPermission")
    fun report(ctx: Context, sys: Sys): String {
        val sb = StringBuilder()
        fun h(t: String) { sb.append("\n== ").append(t).append(" ==\n") }
        fun l(t: String) { sb.append(t).append('\n') }
        val now = System.currentTimeMillis()

        h("Autoradio")
        l("Fabricant : ${Build.MANUFACTURER} · Modèle : ${Build.MODEL} · Produit : ${Build.PRODUCT}")
        l("Carte : ${Build.BOARD} · Matériel : ${Build.HARDWARE} · Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        l("Version : ${Build.DISPLAY}")

        h("GPS")
        l("Position autorisée : ${if (sys.hasLocationPermission()) "oui" else "NON"} · État Clio Home : ${sys.gpsState()}")
        l("Satellites : " + if (satVisible < 0) "aucune information reçue" else "$satVisible vus, $satUsed utilisés")
        try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            for (p in lm.allProviders) {
                val on = try { lm.isProviderEnabled(p) } catch (e: Exception) { false }
                val last = try { lm.getLastKnownLocation(p) } catch (e: Exception) { null }
                val lastTxt = if (last == null) "aucune position" else
                    "dernière position il y a ${(now - last.time) / 60000} min, précision ${last.accuracy.toInt()} m" + if (last.hasSpeed()) ", vitesse ${(last.speed * 3.6f).toInt()} km/h" else ""
                l("- $p : ${if (on) "activé" else "désactivé"} · $lastTxt")
            }
        } catch (e: Exception) { l("Erreur : ${e.message}") }
        l("Mode de localisation : " + try { Settings.Secure.getInt(ctx.contentResolver, Settings.Secure.LOCATION_MODE) } catch (e: Exception) { "?" })

        h("Applis système liées à la voiture")
        val re = Regex("canbus|\\.can\\.|can_|mcu|carinfo|car\\.|\\.car|vehicle|syu|microntek|fyt|\\.tw\\.|hct|raise|hzbhd|nwd|autochips|\\.ts\\.|obd|speed|gps|navi|zlink|radio|launcher|wanjian|\\.wj\\.|bhd|bt\\.", RegexOption.IGNORE_CASE)
        try {
            val pm = ctx.packageManager
            @Suppress("DEPRECATION")
            val pkgs = pm.getInstalledPackages(0)
            for (pi in pkgs.sortedBy { it.packageName }) {
                val ai = pi.applicationInfo ?: continue
                val system = ai.flags and ApplicationInfo.FLAG_SYSTEM != 0
                val label = try { ai.loadLabel(pm).toString() } catch (e: Exception) { "" }
                if (!re.containsMatchIn(pi.packageName) && !re.containsMatchIn(label)) continue
                if (pi.packageName.startsWith("com.google.") || pi.packageName.startsWith("com.android.providers") || pi.packageName == ctx.packageName) continue
                l("- ${pi.packageName} « $label »" + if (system) " [système]" else "")
                try {
                    @Suppress("DEPRECATION")
                    val full = pm.getPackageInfo(pi.packageName, PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS)
                    full.services?.take(8)?.forEach { l("    service ${it.name.removePrefix(pi.packageName)}") }
                    full.receivers?.take(8)?.forEach { l("    récepteur ${it.name.removePrefix(pi.packageName)}") }
                    full.providers?.take(4)?.forEach { l("    fournisseur ${it.authority}") }
                } catch (e: Exception) {}
            }
        } catch (e: Exception) { l("Erreur : ${e.message}") }

        h("Réglages système liés à la voiture")
        for ((name, uri) in listOf("system" to Settings.System.CONTENT_URI, "global" to Settings.Global.CONTENT_URI)) {
            try {
                ctx.contentResolver.query(uri, arrayOf("name", "value"), null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val k = c.getString(0) ?: continue
                        if (Regex("can|speed|temp|car|mcu|illum|light|acc|backlight|gps", RegexOption.IGNORE_CASE).containsMatchIn(k)) l("- $name/$k = ${c.getString(1)}")
                    }
                }
            } catch (e: Exception) { l("- $name : lecture refusée") }
        }

        h("Capteurs")
        try {
            val sm = ctx.getSystemService(Context.SENSOR_SERVICE) as SensorManager
            l(sm.getSensorList(Sensor.TYPE_ALL).joinToString(", ") { it.name }.ifEmpty { "aucun" })
        } catch (e: Exception) { l("?") }

        h("Messages reçus (les plus récents en premier)")
        if (events.isEmpty()) l("Aucun pour l'instant. Roulez quelques minutes, allumez les phares, puis revenez ici.")
        events.forEach { l(it) }
        return sb.toString()
    }
}

/** Écran Diagnostic (Réglages → À propos → Diagnostic). */
fun MainActivity.diagScreen(): View {
    val text = tv(Diag.report(this, sys), 14, Pal.fg).apply {
        typeface = android.graphics.Typeface.MONOSPACE
        setTextIsSelectable(true)
        setLineSpacing(0f, 1.15f)
    }
    Diag.onEvent = { if (screen == "diag") text.text = Diag.report(this, sys) }
    return col(14) {
        addView(row(10) {
            addView(tv("Prenez des photos de cet écran (en le faisant défiler) et envoyez-les. Roulez un peu et allumez les phares avant : les messages de la voiture s'ajoutent en bas.", 15, Pal.muted), lp(0, WRAP, 1f))
            addView(btn("Actualiser", R.drawable.ic_check) { text.text = Diag.report(this@diagScreen, sys) })
            addView(btn("Exporter", R.drawable.ic_download) { exportDiag(Diag.report(this@diagScreen, sys)) })
        })
        addView(card { addView(scroll(text), lp(MATCH, 0, 1f)) }, lp(MATCH, 0, 1f))
    }
}

