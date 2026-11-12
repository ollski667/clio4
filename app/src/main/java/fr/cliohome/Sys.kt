package fr.cliohome

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.graphics.drawable.Drawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale
import kotlin.concurrent.thread

class AppInfo(val label: String, val pkg: String, val cls: String, val ri: ResolveInfo) {
    fun icon(ctx: Context): Drawable = ri.loadIcon(ctx.packageManager)
}

/** Accès au système : applis, GPS, son, réseau, données en ligne. */
class Sys(private val ctx: Context, private val st: Store) {
    private val main = Handler(Looper.getMainLooper())

    // ---------- Applis ----------
    fun launchableApps(): List<AppInfo> {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return ctx.packageManager.queryIntentActivities(i, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map { AppInfo(it.loadLabel(ctx.packageManager).toString(), it.activityInfo.packageName, it.activityInfo.name, it) }
            .sortedBy { it.label.lowercase(Locale.FRANCE) }
    }

    fun launch(pkg: String, cls: String? = null): Boolean = try {
        val i = if (cls != null) Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setComponent(ComponentName(pkg, cls))
        else ctx.packageManager.getLaunchIntentForPackage(pkg)
        if (i == null) false else { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true }
    } catch (e: Exception) { false }

    private fun find(saved: String, vararg words: String): AppInfo? {
        val apps = launchableApps()
        if (saved.isNotEmpty()) apps.firstOrNull { it.pkg == saved }?.let { return it }
        for (w in words) apps.firstOrNull { it.label.lowercase().contains(w) || it.pkg.lowercase().contains(w) }?.let { return it }
        return null
    }

    fun carplayApp() = find(st.cpPkg, "zlink", "carplay", "autokit", "carbit", "tlink")
    fun radioApp() = find(st.radioPkg, "radio", "fm")

    fun openAndroidSettings() = start(Intent(Settings.ACTION_SETTINGS))

    fun chooseDefaultHome(): Boolean = start(Intent(Settings.ACTION_HOME_SETTINGS)) || openAndroidSettings()

    /** Ouvre l'écran d'accueil du fabricant (le premier autre écran d'accueil trouvé). */
    fun openStockLauncher(): Boolean {
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val other = ctx.packageManager.queryIntentActivities(i, 0).firstOrNull {
            it.activityInfo.packageName != ctx.packageName && it.activityInfo.packageName != "com.android.settings" &&
                !it.activityInfo.packageName.contains("fallbackhome", true)
        } ?: return false
        return start(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
            .setComponent(ComponentName(other.activityInfo.packageName, other.activityInfo.name)))
    }

    private fun start(i: Intent) = try { ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); true } catch (e: Exception) { false }

    // ---------- Son ----------
    private val audio = ctx.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    val volume get() = audio.getStreamVolume(AudioManager.STREAM_MUSIC)
    val volumeMax get() = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
    fun volumeStep(dir: Int) = audio.adjustStreamVolume(AudioManager.STREAM_MUSIC,
        if (dir > 0) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER, 0)

    // ---------- Réseau / Bluetooth ----------
    fun online(): Boolean = try {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    } catch (e: Exception) { false }

    @SuppressLint("MissingPermission")
    fun bluetoothOn(): Boolean = try { BluetoothAdapter.getDefaultAdapter()?.isEnabled == true } catch (e: Exception) { false }

    // ---------- GPS ----------
    var speedKmh = -1
    var gpsFix = false
    private var lastLoc: Location? = null
    private var listening = false
    var onGps: (() -> Unit)? = null

    private val listener = object : LocationListener {
        override fun onLocationChanged(l: Location) {
            gpsFix = true
            speedKmh = if (l.hasSpeed()) (l.speed * 3.6f).toInt() else speedKmh.coerceAtLeast(0)
            // Kilométrage estimé : on additionne les déplacements fiables
            val prev = lastLoc
            if (prev != null && l.accuracy < 30f && speedKmh >= 5) {
                val d = prev.distanceTo(l)
                if (d < 500f) st.gpsMeters = st.gpsMeters + d
            }
            lastLoc = l
            st.lastLat = l.latitude.toFloat(); st.lastLon = l.longitude.toFloat()
            onGps?.invoke()
        }
        override fun onProviderDisabled(provider: String) { gpsFix = false; onGps?.invoke() }
        override fun onProviderEnabled(provider: String) {}
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    }

    @SuppressLint("MissingPermission")
    fun startGps() {
        if (listening) return
        try {
            val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000L, 0f, listener, Looper.getMainLooper())
            listening = true
        } catch (e: Exception) { /* pas de permission ou pas de GPS */ }
    }

    fun stopGps() {
        if (!listening) return
        try { (ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager).removeUpdates(listener) } catch (e: Exception) {}
        listening = false
    }

    // ---------- Données en ligne (avec cache hors ligne) ----------
    private fun get(url: String): String? = try {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8000; c.readTimeout = 10000
        c.setRequestProperty("User-Agent", "ClioHome/0.1")
        if (c.responseCode == 200) c.inputStream.bufferedReader().use { it.readText() } else null
    } catch (e: Exception) { null }

    fun refreshWeather(force: Boolean = false, done: () -> Unit) {
        if (st.lastLat == 0f || !online()) return
        if (!force && System.currentTimeMillis() - st.weatherAt < 30 * 60_000L) return
        val url = "https://api.open-meteo.com/v1/forecast?latitude=${st.lastLat}&longitude=${st.lastLon}" +
            "&current=temperature_2m,weather_code&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
        thread {
            val s = get(url)
            if (s != null && s.contains("current")) main.post { st.weatherJson = s; st.weatherAt = System.currentTimeMillis(); done() }
        }
    }

    fun refreshFuel(force: Boolean = false, done: () -> Unit) {
        if (st.lastLat == 0f || !online()) return
        if (!force && System.currentTimeMillis() - st.fuelAt < 60 * 60_000L) return
        val where = URLEncoder.encode("within_distance(geom, geom'POINT(${st.lastLon} ${st.lastLat})', 10km)", "UTF-8")
        val sel = URLEncoder.encode("id,adresse,ville,geom,gazole_prix,gazole_maj,e10_prix,e10_maj,sp95_prix,sp95_maj,sp98_prix,sp98_maj,e85_prix,e85_maj,gplc_prix,gplc_maj", "UTF-8")
        val url = "https://data.economie.gouv.fr/api/explore/v2.1/catalog/datasets/prix-des-carburants-en-france-flux-instantane-v2/records?where=$where&select=$sel&limit=60"
        thread {
            val s = get(url)
            if (s != null && s.contains("results")) main.post { st.fuelJson = s; st.fuelAt = System.currentTimeMillis(); done() }
        }
    }

    class Weather(val temp: Int, val min: Int, val max: Int, val text: String, val at: Long)

    fun weather(): Weather? = try {
        val o = JSONObject(st.weatherJson)
        val cur = o.getJSONObject("current"); val d = o.getJSONObject("daily")
        Weather(Math.round(cur.getDouble("temperature_2m")).toInt(),
            Math.round(d.getJSONArray("temperature_2m_min").getDouble(0)).toInt(),
            Math.round(d.getJSONArray("temperature_2m_max").getDouble(0)).toInt(),
            weatherText(cur.getInt("weather_code")), st.weatherAt)
    } catch (e: Exception) { null }

    private fun weatherText(c: Int) = when (c) {
        0 -> "Ciel dégagé"; 1 -> "Peu nuageux"; 2 -> "Partiellement nuageux"; 3 -> "Couvert"
        45, 48 -> "Brouillard"; in 51..57 -> "Bruine"; in 61..67 -> "Pluie"; in 71..77 -> "Neige"
        in 80..82 -> "Averses"; 85, 86 -> "Averses de neige"; in 95..99 -> "Orage"; else -> "—"
    }

    class Station(val addr: String, val city: String, val km: Float, val price: Double, val maj: String)

    fun stations(): List<Station> = try {
        val a = JSONObject(st.fuelJson).getJSONArray("results")
        val key = st.fuelType
        val here = Location("").apply { latitude = st.lastLat.toDouble(); longitude = st.lastLon.toDouble() }
        (0 until a.length()).mapNotNull { i ->
            val r = a.getJSONObject(i)
            if (r.isNull("${key}_prix")) return@mapNotNull null
            val g = r.optJSONObject("geom")
            val there = Location("").apply { latitude = g?.optDouble("lat") ?: 0.0; longitude = g?.optDouble("lon") ?: 0.0 }
            Station(r.optString("adresse").trim(), r.optString("ville").trim(), here.distanceTo(there) / 1000f,
                r.getDouble("${key}_prix"), r.optString("${key}_maj"))
        }
    } catch (e: Exception) { emptyList() }

    companion object {
        val FUELS = listOf("gazole" to "Gazole", "e10" to "SP95-E10", "sp95" to "SP95", "sp98" to "SP98", "e85" to "E85", "gplc" to "GPLc")
        fun fuelName(k: String) = FUELS.firstOrNull { it.first == k }?.second ?: k
    }
}
