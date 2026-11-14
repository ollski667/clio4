package fr.cliohome

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

/** Un entretien à suivre. lastKm = -1 : pas encore renseigné. km = 0 : pas de limite en km. */
class Maint(var id: String, var name: String, var km: Int, var mo: Int, var lastKm: Int, var lastDate: String) {
    fun json() = JSONObject().put("id", id).put("name", name).put("km", km).put("mo", mo).put("lastKm", lastKm).put("lastDate", lastDate)
    companion object {
        fun of(o: JSONObject) = Maint(o.getString("id"), o.getString("name"), o.optInt("km"), o.optInt("mo"), o.optInt("lastKm", -1), o.optString("lastDate"))
    }
}

/** Une ligne de l'historique : relevé kilométrique (t = "km") ou entretien (t = "ent"). */
class LogE(var id: String, var d: String, var km: Int, var t: String, var name: String, var cost: String, var note: String) {
    fun json() = JSONObject().put("id", id).put("d", d).put("km", km).put("t", t).put("name", name).put("cost", cost).put("note", note)
    companion object {
        fun of(o: JSONObject) = LogE(o.getString("id"), o.getString("d"), o.optInt("km"), o.optString("t"), o.optString("name"), o.optString("cost"), o.optString("note"))
    }
}

/** Échéance calculée : ratio d'usure (0..1), niveau (0 ok, 1 bientôt, 2 à faire), texte court. */
class Due(val r: Float, val lv: Int, val txt: String, val dueDate: String, val dueKm: String?)

class Store(ctx: Context) {
    private val p = ctx.getSharedPreferences("cliohome", Context.MODE_PRIVATE)
    private fun put(k: String, v: Any) {
        val e = p.edit()
        when (v) {
            is Int -> e.putInt(k, v); is Long -> e.putLong(k, v); is Float -> e.putFloat(k, v)
            is Boolean -> e.putBoolean(k, v); else -> e.putString(k, v.toString())
        }
        e.apply()
    }

    // Apparence
    var accent get() = p.getInt("accent", 0xFF0A84FF.toInt()); set(v) = put("accent", v)
    var bgDay get() = p.getInt("bgDay", 0xFFEEF1F4.toInt()); set(v) = put("bgDay", v)
    var bgNight get() = p.getInt("bgNight", 0xFF0B0D10.toInt()); set(v) = put("bgNight", v)
    var themeMode get() = p.getString("themeMode", "auto")!!; set(v) = put("themeMode", v)
    var ui get() = p.getFloat("ui", 1.15f); set(v) = put("ui", v)
    var font get() = p.getString("font", "angular")!!; set(v) = put("font", v)

    // Accueil
    var layout: MutableList<String>
        get() = list("layout", listOf("car", "speed", "temp", "maint", "park", "fuel"))
        set(v) = put("layout", JSONArray(v).toString())
    var hidden: MutableList<String>
        get() = list("hidden", listOf("volume", "clock", "conn"))
        set(v) = put("hidden", JSONArray(v).toString())
    private fun list(k: String, def: List<String>): MutableList<String> {
        val s = p.getString(k, null) ?: return def.toMutableList()
        val a = JSONArray(s); return MutableList(a.length()) { a.getString(it) }
    }

    /** Température du capteur de la voiture (boîtier CAN). Désactivé : la température vient de la météo. */
    var useCarTemp get() = p.getBoolean("useCarTemp", false); set(v) = put("useCarTemp", v)
    var sideFuel get() = p.getBoolean("sideFuel", true); set(v) = put("sideFuel", v)
    var sideRadio get() = p.getBoolean("sideRadio2", false); set(v) = put("sideRadio2", v)
    var sidePark get() = p.getBoolean("sidePark", true); set(v) = put("sidePark", v)
    var settingsTab get() = p.getString("settingsTab", "affichage")!!; set(v) = put("settingsTab", v)

    // Applis liées
    var autoCP get() = p.getString("autoCP", "off")!!; set(v) = put("autoCP", v)
    var cpPkg get() = p.getString("cpPkg", "")!!; set(v) = put("cpPkg", v)
    var radioPkg get() = p.getString("radioPkg", "")!!; set(v) = put("radioPkg", v)

    // Voiture
    var plate get() = p.getString("plate", "EA-846-SL")!!; set(v) = put("plate", v)
    var carShow get() = p.getString("carShow", "name")!!; set(v) = put("carShow", v)
    var carName get() = p.getString("carName", "Clio 4")!!; set(v) = put("carName", v)
    var carSub get() = p.getString("carSub", "Phase 2 · 1.5 dCi · 2016")!!; set(v) = put("carSub", v)

    // Kilométrage : valeur saisie + distance mesurée au GPS depuis la saisie
    var odoBase get() = p.getInt("odoBase", 0); set(v) = put("odoBase", v)
    var gpsMeters get() = p.getFloat("gpsMeters", 0f); set(v) = put("gpsMeters", v)
    val odo get() = odoBase + (gpsMeters / 1000f).roundToInt()

    // Carburant
    var fuelType get() = p.getString("fuelType", "gazole")!!; set(v) = put("fuelType", v)
    var tank get() = p.getInt("tank", 45); set(v) = put("tank", v)
    var fuelSort get() = p.getString("fuelSort", "prix")!!; set(v) = put("fuelSort", v)

    // Stationnement
    var parkAt get() = p.getLong("parkAt", 0L); set(v) = put("parkAt", v)
    var parkLimit get() = p.getInt("parkLimit", 60); set(v) = put("parkLimit", v)
    var parkNote get() = p.getString("parkNote", "")!!; set(v) = put("parkNote", v)

    // Position de secours quand le GPS ne capte pas
    var city get() = p.getString("city", "")!!; set(v) = put("city", v)
    var cityLat get() = p.getFloat("cityLat", 0f); set(v) = put("cityLat", v)
    var cityLon get() = p.getFloat("cityLon", 0f); set(v) = put("cityLon", v)

    // Caches hors ligne
    var lastLat get() = p.getFloat("lat", 0f); set(v) = put("lat", v)
    var lastLon get() = p.getFloat("lon", 0f); set(v) = put("lon", v)
    var weatherJson get() = p.getString("weather", "")!!; set(v) = put("weather", v)
    var weatherAt get() = p.getLong("weatherAt", 0L); set(v) = put("weatherAt", v)
    var fuelJson get() = p.getString("fuel", "")!!; set(v) = put("fuel", v)
    var fuelAt get() = p.getLong("fuelAt", 0L); set(v) = put("fuelAt", v)

    // Entretiens et historique
    var items: MutableList<Maint>
        get() {
            val s = p.getString("items", null) ?: return defaultItems()
            val a = JSONArray(s); return MutableList(a.length()) { Maint.of(a.getJSONObject(it)) }
        }
        set(v) = put("items", JSONArray(v.map { it.json() }).toString())

    var log: MutableList<LogE>
        get() {
            val s = p.getString("log", null) ?: return mutableListOf()
            val a = JSONArray(s); return MutableList(a.length()) { LogE.of(a.getJSONObject(it)) }
        }
        set(v) = put("log", JSONArray(v.map { it.json() }).toString())

    fun addLog(e: LogE) { log = log.apply { add(e) } }

    fun resetAll() { p.edit().clear().apply() }

    /** Copie de toutes les données (réglages, kilométrage, entretiens, historique) au format JSON. */
    fun exportJson(photo: ByteArray?): String {
        val data = JSONObject()
        for ((k, v) in p.all) {
            val t = when (v) { is Int -> "i"; is Long -> "l"; is Float -> "f"; is Boolean -> "b"; else -> "s" }
            data.put(k, JSONObject().put("t", t).put("v", v))
        }
        val o = JSONObject().put("app", "Clio Home").put("format", 1).put("date", today()).put("data", data)
        if (photo != null) o.put("photo", android.util.Base64.encodeToString(photo, android.util.Base64.NO_WRAP))
        return o.toString(1)
    }

    /** Restaure une copie faite par exportJson. Renvoie la photo à réécrire, ou null. Lève une erreur si le fichier n'est pas une sauvegarde. */
    fun importJson(s: String): ByteArray? {
        val o = JSONObject(s)
        require(o.optString("app") == "Clio Home") { "pas une sauvegarde Clio Home" }
        val data = o.getJSONObject("data")
        val e = p.edit().clear()
        for (k in data.keys()) {
            val it = data.getJSONObject(k)
            when (it.getString("t")) {
                "i" -> e.putInt(k, it.getInt("v")); "l" -> e.putLong(k, it.getLong("v"))
                "f" -> e.putFloat(k, it.getDouble("v").toFloat()); "b" -> e.putBoolean(k, it.getBoolean("v"))
                else -> e.putString(k, it.getString("v"))
            }
        }
        e.apply()
        return if (o.has("photo")) android.util.Base64.decode(o.getString("photo"), android.util.Base64.NO_WRAP) else null
    }

    companion object {
        fun today(): String = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())
        fun newId() = "i" + System.currentTimeMillis() + (0..999).random()

        /** Intervalles indicatifs pour la Clio 4 1.5 dCi : à vérifier dans le carnet d'entretien. */
        fun defaultItems() = mutableListOf(
            Maint("vid", "Vidange + filtre à huile", 20000, 12, -1, ""),
            Maint("hab", "Filtre d'habitacle", 20000, 12, -1, ""),
            Maint("air", "Filtre à air", 40000, 24, -1, ""),
            Maint("gaz", "Filtre à gazole", 40000, 24, -1, ""),
            Maint("frein", "Liquide de frein", 0, 24, -1, ""),
            Maint("ct", "Contrôle technique", 0, 24, -1, ""),
            Maint("dist", "Courroie de distribution", 120000, 72, -1, ""),
        )

        fun parseDate(s: String): Calendar? = try {
            Calendar.getInstance().apply { time = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).parse(s)!! }
        } catch (e: Exception) { null }

        fun frDate(s: String): String = parseDate(s)?.let { SimpleDateFormat("dd/MM/yyyy", Locale.FRANCE).format(it.time) } ?: s

        fun nf(n: Int): String = String.format(Locale.FRANCE, "%,d", n).replace(' ', ' ').replace(' ', ' ')
    }

    /** Calcule l'échéance d'un entretien, au premier des deux termes (km ou date). */
    fun due(it: Maint): Due {
        if (it.lastKm < 0 || it.lastDate.isEmpty()) return Due(0f, -1, "À renseigner", "", null)
        val last = parseDate(it.lastDate) ?: return Due(0f, -1, "À renseigner", "", null)
        val dueCal = (last.clone() as Calendar).apply { add(Calendar.MONTH, it.mo) }
        val now = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
        var r = 0f
        val parts = mutableListOf<String>()
        if (it.km > 0) {
            val kl = it.lastKm + it.km - odo
            r = maxOf(r, 1f - kl.toFloat() / it.km)
            parts.add(if (kl < 0) "+${nf(-kl)} km" else "${nf(kl)} km")
        }
        if (it.mo > 0) {
            val dl = ((dueCal.timeInMillis - now.timeInMillis) / 86_400_000L).toInt()
            val tot = maxOf(1L, (dueCal.timeInMillis - last.timeInMillis) / 86_400_000L)
            r = maxOf(r, 1f - dl.toFloat() / tot)
            parts.add(if (dl < 0) "retard ${-dl} j" else if (dl <= 60) "$dl j" else "${(dl / 30.4).roundToInt()} mois")
        }
        val lv = if (r >= 1f) 2 else if (r >= .9f) 1 else 0
        val dueDate = SimpleDateFormat("d MMM yyyy", Locale.FRANCE).format(dueCal.time)
        return Due(r.coerceIn(0f, 1f), lv, (if (lv == 2) "À faire · " else "") + parts.joinToString(" · "), dueDate,
            if (it.km > 0) nf(it.lastKm + it.km) + " km" else null)
    }

    fun every(it: Maint) = listOfNotNull(if (it.km > 0) "${nf(it.km)} km" else null, if (it.mo > 0) "${it.mo} mois" else null).joinToString(" ou ")

    fun csv(): String {
        fun q(v: String) = if (v.contains(';') || v.contains('"') || v.contains('\n')) "\"" + v.replace("\"", "\"\"") + "\"" else v
        val rows = log.sortedWith(compareByDescending<LogE> { it.d }.thenByDescending { it.km })
        val sb = StringBuilder("﻿Date;Kilométrage;Type;Intervention;Coût (€);Note;Plaque\n")
        for (e in rows) sb.append(listOf(frDate(e.d), e.km.toString(), if (e.t == "km") "Relevé" else "Entretien", e.name, e.cost.replace('.', ','), e.note, plate).joinToString(";") { q(it) }).append('\n')
        return sb.toString()
    }
}
