package fr.cliohome

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Récapitulatif du suivi : page HTML lisible sur le téléphone, imprimable ou enregistrable en PDF. */
object Recap {
    private fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    fun html(st: Store): String {
        val now = Date()
        val log = st.log.sortedWith(compareByDescending<LogE> { it.d }.thenByDescending { it.km })
        val asc = log.reversed()
        var avg = "—"
        if (asc.size > 1) {
            val a = Store.parseDate(asc.first().d); val b = Store.parseDate(asc.last().d)
            if (a != null && b != null) {
                val months = (b.timeInMillis - a.timeInMillis) / 86_400_000.0 / 30.44
                if (months > 0.5) avg = Store.nf(((asc.last().km - asc.first().km) / months).toInt()) + " km"
            }
        }
        fun cost(e: LogE) = e.cost.replace(',', '.').toDoubleOrNull() ?: 0.0
        val year = Calendar.getInstance().get(Calendar.YEAR).toString()
        val spentY = log.filter { it.d.startsWith(year) }.sumOf { cost(it) }
        val spentT = log.sumOf { cost(it) }
        fun eur(v: Double) = String.format(Locale.FRANCE, "%.0f €", v)

        val items = st.items.map { it to st.due(it) }.sortedByDescending { it.second.r + if (it.second.lv < 0) -2f else 0f }
        val todo = items.count { it.second.lv == 2 }
        val soon = items.count { it.second.lv == 1 }

        val sb = StringBuilder()
        sb.append("""<!doctype html><html lang="fr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1">
<title>Suivi ${esc(st.carName)} ${esc(st.plate)}</title>
<style>
:root{--fg:#111418;--muted:#5a6169;--line:#d5dae0;--bg:#fff;--ok:#1c7e41;--warn:#9a5b00;--bad:#c2271a;--soft:#f2f4f6}
*{box-sizing:border-box}
body{margin:0;background:var(--bg);color:var(--fg);font:15px/1.5 -apple-system,system-ui,'Segoe UI',Roboto,sans-serif;padding:20px 16px 40px}
.wrap{max-width:860px;margin:0 auto}
h1{font-size:24px;margin:0 0 4px;letter-spacing:-.01em}
h2{font-size:17px;margin:28px 0 10px;padding-bottom:6px;border-bottom:2px solid var(--fg)}
.sub{color:var(--muted);margin:0 0 18px}
.plate{display:inline-block;border:1.5px solid #111;border-radius:4px;padding:0 8px;font-weight:700;letter-spacing:.06em;background:#fff;border-left:12px solid #1d4fb8}
.kpis{display:grid;grid-template-columns:repeat(auto-fit,minmax(150px,1fr));gap:10px}
.kpi{background:var(--soft);border-radius:10px;padding:12px 14px}
.kpi b{display:block;font-size:22px;font-weight:600;font-variant-numeric:tabular-nums}
.kpi span{color:var(--muted);font-size:13px}
.tbl{overflow-x:auto}
table{width:100%;border-collapse:collapse;font-variant-numeric:tabular-nums}
th{text-align:left;font-size:12px;text-transform:uppercase;letter-spacing:.05em;color:var(--muted);font-weight:600;padding:6px 8px;border-bottom:1px solid var(--line)}
td{padding:8px;border-bottom:1px solid var(--line);vertical-align:top}
td.n{text-align:right;white-space:nowrap}
.st{display:inline-block;font-size:12px;font-weight:600;padding:2px 8px;border-radius:999px;white-space:nowrap}
.ok{color:var(--ok);background:#e3f3e8}.warn{color:var(--warn);background:#fbefd9}.bad{color:var(--bad);background:#fbe3e0}.na{color:var(--muted);background:var(--soft)}
.note{color:var(--muted);font-size:13px}
footer{margin-top:32px;color:var(--muted);font-size:12px}
@media print{body{padding:0}h2{break-after:avoid}tr{break-inside:avoid}}
/* Téléphone : une carte par ligne au lieu d'un tableau trop large */
@media (max-width:640px){
  table,tbody,tr,td{display:block;width:100%}
  tr:first-child{display:none}
  tr{border:1px solid var(--line);border-radius:10px;padding:6px 12px;margin-bottom:10px}
  td{border:0;padding:4px 0;display:flex;justify-content:space-between;gap:12px;text-align:right}
  td::before{content:attr(data-l);color:var(--muted);font-size:13px;text-align:left;flex:none}
  td.t{font-weight:600;font-size:16px;text-align:left;display:block}
  td.t::before{content:none}
  td.n{white-space:normal}
}
</style></head><body><div class="wrap">""")
        sb.append("<h1>Suivi d'entretien · ${esc(st.carName)}</h1>")
        sb.append("<p class=\"sub\">${esc(st.carSub)} · <span class=\"plate\">${esc(st.plate)}</span> · récapitulatif du ${SimpleDateFormat("d MMMM yyyy", Locale.FRANCE).format(now)}</p>")

        sb.append("<div class=\"kpis\">")
        sb.append("<div class=\"kpi\"><b>${if (st.odo > 0) Store.nf(st.odo) + " km" else "—"}</b><span>kilométrage actuel</span></div>")
        sb.append("<div class=\"kpi\"><b>$avg</b><span>par mois en moyenne</span></div>")
        sb.append("<div class=\"kpi\"><b>${eur(spentY)}</b><span>dépensés en $year</span></div>")
        sb.append("<div class=\"kpi\"><b>${eur(spentT)}</b><span>dépensés au total</span></div>")
        sb.append("</div>")

        sb.append("<h2>Échéances</h2>")
        sb.append("<p class=\"note\">" + when {
            todo > 0 -> "$todo entretien${if (todo > 1) "s" else ""} à faire" + if (soon > 0) ", $soon bientôt." else "."
            soon > 0 -> "$soon entretien${if (soon > 1) "s" else ""} à prévoir bientôt."
            else -> "Rien d'urgent."
        } + " Prochaine échéance au premier des deux termes (km ou date).</p>")
        sb.append("<div class=\"tbl\"><table><tr><th>Entretien</th><th>Intervalle</th><th>Dernier</th><th>Prochain</th><th>État</th></tr>")
        for ((it, d) in items) {
            val last = if (it.lastKm >= 0 && it.lastDate.isNotEmpty()) "${Store.nf(it.lastKm)} km<br><span class=\"note\">${Store.frDate(it.lastDate)}</span>" else "—"
            val next = if (d.lv < 0) "—" else listOfNotNull(d.dueKm, d.dueDate).joinToString("<br>")
            val cls = when (d.lv) { 2 -> "bad"; 1 -> "warn"; 0 -> "ok"; else -> "na" }
            val state = when (d.lv) { 2 -> "À faire"; 1 -> "Bientôt"; 0 -> "À jour"; else -> "À renseigner" }
            sb.append("<tr><td class=\"t\">${esc(it.name)}</td><td data-l=\"Intervalle\">${esc(st.every(it))}</td><td class=\"n\" data-l=\"Dernier\"><span>$last</span></td><td class=\"n\" data-l=\"Prochain\"><span>$next</span></td>")
            sb.append("<td data-l=\"État\"><span><span class=\"st $cls\">$state</span>${if (d.lv >= 0) "<br><span class=\"note\">${esc(d.txt.removePrefix("À faire · "))}</span>" else ""}</span></td></tr>")
        }
        sb.append("</table></div>")

        sb.append("<h2>Historique</h2>")
        if (log.isEmpty()) sb.append("<p class=\"note\">Aucun relevé ni entretien enregistré pour l'instant.</p>")
        else {
            sb.append("<div class=\"tbl\"><table><tr><th>Intervention</th><th>Date</th><th>Kilométrage</th><th>Coût</th></tr>")
            for (e in log) {
                sb.append("<tr><td class=\"t\">${esc(e.name)}${if (e.note.isNotEmpty()) " <span class=\"note\">· ${esc(e.note)}</span>" else ""}</td><td class=\"n\" data-l=\"Date\">${Store.frDate(e.d)}</td>")
                sb.append("<td class=\"n\" data-l=\"Kilométrage\">${Store.nf(e.km)} km</td><td class=\"n\" data-l=\"Coût\">${if (e.cost.isNotEmpty()) esc(e.cost) + " €" else "—"}</td></tr>")
            }
            sb.append("</table></div>")
        }
        sb.append("<footer>Généré par Clio Home ${BuildInfo.version}. Intervalles indicatifs : se référer au carnet d'entretien.</footer>")
        sb.append("</div></body></html>")
        return sb.toString()
    }
}
