package fr.cliohome

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private const val NUM = InputType.TYPE_CLASS_NUMBER
private const val DEC = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL

private fun EditText.onChange(f: (String) -> Unit) = addTextChangedListener(object : TextWatcher {
    override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
    override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
    override fun afterTextChanged(s: Editable?) { f(s?.toString() ?: "") }
})

private fun MainActivity.line() = View(this).apply { setBackgroundColor(Pal.line); layoutParams = lp(MATCH, 1) }

private fun MainActivity.twoCols(compact: Boolean, left: View, right: View, leftWeight: Float = 1.45f): View =
    if (compact) scroll(col(14) { addView(left, lp(MATCH, px(440))); addView(right, lp(MATCH, px(440))) })
    else row(14) {
        gravity = Gravity.TOP
        addView(left, LinearLayout.LayoutParams(0, MATCH, leftWeight))
        addView(right, LinearLayout.LayoutParams(0, MATCH, 1f))
    }

/** Champ date qui ouvre le calendrier d'Android. */
private fun MainActivity.dateField(labelText: String, value: String, onPick: (String) -> Unit) = col(6) {
    addView(tv(labelText, 15, Pal.muted, 500, 1))
    addView(tv(if (value.isEmpty()) "Choisir…" else Store.frDate(value), 17, if (value.isEmpty()) Pal.muted else Pal.fg, 500, 1).apply {
        gravity = Gravity.CENTER_VERTICAL
        background = rounded(Pal.surface2, 12, Pal.line, 1)
        setPadding(px(14), 0, px(14), 0); minHeight = px(46)
        onTap {
            val c = Store.parseDate(value) ?: Calendar.getInstance()
            DatePickerDialog(this@dateField, { _, y, m, d ->
                onPick(String.format(Locale.FRANCE, "%04d-%02d-%02d", y, m + 1, d))
            }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
        }
    }, lp())
}

// =====================================================================
// Suivi : échéances + historique
// =====================================================================
fun MainActivity.maintScreen(compact: Boolean): View = col(14) {
    addView(row { addView(seg(listOf("due" to "Échéances", "log" to "Historique et kilométrage"), mTab) { mTab = it; armed = null; render() }) })
    addView(if (mTab == "log") logView(compact) else dueView(compact), lp(MATCH, 0, 1f))
}

private fun MainActivity.dueView(compact: Boolean): View {
    val items = st.items
    val cur = items.firstOrNull { it.id == mSel } ?: items.firstOrNull()
    val list = col {
        items.forEachIndexed { i, it ->
            if (i > 0) addView(line())
            val d = st.due(it)
            addView(col(7) {
                setPadding(px(12), px(14), px(12), px(14))
                if (it.id == cur?.id) background = rounded(Pal.surface2, 14)
                addView(row(10) {
                    addView(tv(it.name, 19.5, Pal.fg, 500, 1), lp(0, WRAP, 1f))
                    addView(pill(d.txt, d.lv))
                })
                addView(tv("Tous les " + st.every(it), 16, Pal.muted, 400, 1))
                if (d.lv >= 0) addView(bar(d.r, Pal.level(d.lv)))
                onTap { mSel = it.id; armed = null; render() }
            })
        }
    }
    val left = card {
        addView(label("Échéances"))
        addView(scroll(list), lp(MATCH, 0, 1f))
        addView(row { addView(btn("Ajouter un entretien", R.drawable.ic_plus) {
            val m = Maint(Store.newId(), "Nouvel entretien", 10000, 12, st.odo, Store.today())
            st.items = st.items.apply { add(m) }; mSel = m.id; render()
        }) })
    }
    val (odoBox, odoEdit) = field("Kilométrage actuel (compteur)", if (st.odo > 0) st.odo.toString() else "", NUM, "Ex. 98450")
    val right = card {
        addView(row(12) {
            gravity = Gravity.BOTTOM
            addView(odoBox, lp(0, WRAP, 1f))
            addView(btn("Mettre à jour", style = Btn.ACCENT) {
                val n = odoEdit.text.toString().trim().toIntOrNull()
                if (n == null || n < 0) { toast("Entrez un kilométrage valide"); return@btn }
                st.odoBase = n; st.gpsMeters = 0f
                st.addLog(LogE(Store.newId(), Store.today(), n, "km", "Relevé kilométrique", "", ""))
                toast("Kilométrage enregistré"); render()
            })
        })
        addView(tv("Entre deux saisies, l'appli ajoute les km mesurés par le GPS. Intervalles indicatifs pour le 1.5 dCi : vérifiez votre carnet d'entretien.", 15, Pal.muted))
        if (cur != null) addView(scroll(editor(cur)), lp(MATCH, 0, 1f))
    }
    return twoCols(compact, left, right)
}

private fun MainActivity.editor(it: Maint): View {
    var lastDate = it.lastDate
    val (nBox, nE) = field("Nom", it.name)
    val (kmBox, kmE) = field("Intervalle km", it.km.toString(), NUM)
    val (moBox, moE) = field("Intervalle mois", it.mo.toString(), NUM)
    val (lkBox, lkE) = field("Dernier (km)", if (it.lastKm >= 0) it.lastKm.toString() else "", NUM, "km")
    val d = st.due(it)
    fun save(done: Boolean) {
        val items = st.items
        val m = items.firstOrNull { x -> x.id == it.id } ?: return
        m.name = nE.text.toString().trim().ifEmpty { m.name }
        m.km = kmE.text.toString().toIntOrNull() ?: 0
        m.mo = moE.text.toString().toIntOrNull() ?: 0
        m.lastKm = lkE.text.toString().toIntOrNull() ?: -1
        m.lastDate = lastDate
        if (done) {
            m.lastKm = st.odo; m.lastDate = Store.today()
            st.addLog(LogE(Store.newId(), Store.today(), st.odo, "ent", m.name, "", ""))
        }
        st.items = items
        toast(if (done) "Marqué comme fait aujourd'hui" else "Enregistré"); render()
    }
    return col(12) {
        addView(label("Détail"))
        addView(nBox)
        addView(row(12) { addView(kmBox, lp(0, WRAP, 1f)); addView(moBox, lp(0, WRAP, 1f)) })
        lateinit var dateBox: View
        addView(row(12) {
            addView(lkBox, lp(0, WRAP, .8f))
            dateBox = dateField("Dernier (date)", lastDate) { v -> lastDate = v; save(false) }
            addView(dateBox, lp(0, WRAP, 1.2f))
        })
        addView(tv(if (d.lv < 0) "Indiquez le kilométrage et la date du dernier entretien." else "Prochaine échéance : " + listOfNotNull(d.dueKm, d.dueDate).joinToString(" ou ") + ", au premier des deux.", 15, Pal.muted))
        addView(row(10) {
            addView(btn("Fait aujourd'hui", R.drawable.ic_check, Btn.ACCENT) { save(true) })
            addView(btn("Enregistrer") { save(false) })
        })
        addView(row {
            addView(btn(if (armed == it.id) "Confirmer la suppression" else "Supprimer", style = if (armed == it.id) Btn.DANGER_ARMED else Btn.DANGER) {
                if (armed != it.id) { armed = it.id; render() } else {
                    st.items = st.items.apply { removeAll { x -> x.id == it.id } }; armed = null; mSel = null; render()
                }
            })
        })
    }
}

private fun MainActivity.logView(compact: Boolean): View {
    val log = st.log.sortedWith(compareByDescending<LogE> { it.d }.thenByDescending { it.km })
    val asc = log.reversed()
    var avg = "—"
    if (asc.size > 1) {
        val a = Store.parseDate(asc.first().d); val b = Store.parseDate(asc.last().d)
        if (a != null && b != null) {
            val months = (b.timeInMillis - a.timeInMillis) / 86_400_000.0 / 30.44
            if (months > 0.5) avg = Store.nf(((asc.last().km - asc.first().km) / months).toInt())
        }
    }
    fun cost(e: LogE) = e.cost.replace(',', '.').toDoubleOrNull() ?: 0.0
    val year = Calendar.getInstance().get(Calendar.YEAR).toString()
    val spentY = log.filter { it.d.startsWith(year) }.sumOf { cost(it) }
    val spentT = log.sumOf { cost(it) }
    fun stat(v: String, l: String) = col(2) {
        background = rounded(Pal.surface2, 14); setPadding(px(14), px(12), px(14), px(12))
        addView(tv(v, 24, Pal.fg, 400, 1)); addView(tv(l, 14.5, Pal.muted, 400, 1))
    }
    val list = col {
        if (log.isEmpty()) addView(tv("Aucune ligne pour l'instant. Ajoutez un relevé ou un entretien à droite.", 16, Pal.muted).apply { setPadding(px(8), px(20), px(8), px(20)) })
        log.forEachIndexed { i, e ->
            if (i > 0) addView(line())
            addView(row(12) {
                setPadding(px(8), px(12), px(8), px(12))
                addView(tv(Store.frDate(e.d), 17, Pal.fg, 400, 1), lp(px(104), WRAP))
                addView(FrameLayout(context).apply {
                    background = rounded(Pal.surface2, 9)
                    addView(icon(if (e.t == "km") R.drawable.ic_gauge else R.drawable.ic_wrench, 17, Pal.accent), FrameLayout.LayoutParams(px(17), px(17), Gravity.CENTER))
                }, lp(px(30), px(30)))
                addView(col(2) {
                    addView(tv(e.name, 17, Pal.fg, 500, 1))
                    if (e.note.isNotEmpty()) addView(tv(e.note, 15, Pal.muted, 400, 1))
                }, lp(0, WRAP, 1f))
                addView(tv("${Store.nf(e.km)} km", 17, Pal.fg, 400, 1).apply { gravity = Gravity.END }, lp(px(110), WRAP))
                addView(tv(if (e.cost.isNotEmpty()) "${e.cost} €" else "—", 15, Pal.fg, 400, 1).apply { gravity = Gravity.END }, lp(px(80), WRAP))
                addView(roundBtn(if (armed == e.id) R.drawable.ic_check else R.drawable.ic_x, 40) {
                    if (armed != e.id) { armed = e.id; render() } else { st.log = st.log.apply { removeAll { x -> x.id == e.id } }; armed = null; toast("Ligne supprimée"); render() }
                }.apply { if (armed == e.id) background = rounded(Pal.bad, 12) })
            })
        }
    }
    val left = card {
        addView(row(12) {
            addView(stat(avg, "km par mois en moyenne"), lp(0, WRAP, 1f))
            addView(stat(String.format(Locale.FRANCE, "%.0f €", spentY), "dépensés en $year"), lp(0, WRAP, 1f))
            addView(stat(String.format(Locale.FRANCE, "%.0f €", spentT), "dépensés au total"), lp(0, WRAP, 1f))
        })
        addView(scroll(list), lp(MATCH, 0, 1f))
    }
    var date = Store.today()
    val (kmBox, kmE) = field("Kilométrage", if (st.odo > 0) st.odo.toString() else "", NUM)
    val (nBox, nE) = field("Intervention", "", hint = "Ex. Vidange, pneus avant…")
    val (cBox, cE) = field("Coût (€)", "", DEC, "0")
    val (noteBox, noteE) = field("Note", "", hint = "Garage, pièce…")
    val right = card {
        addView(label("Ajouter une ligne"))
        addView(scroll(col(12) {
            addView(row { addView(seg(listOf("km" to "Relevé kilométrique", "ent" to "Entretien / réparation"), logType) { logType = it; render() }) })
            addView(row(12) {
                addView(dateField("Date", date) { v -> date = v; toast("Date : " + Store.frDate(v)) }, lp(0, WRAP, 1f))
                addView(kmBox, lp(0, WRAP, 1f))
            })
            if (logType == "ent") {
                addView(nBox)
                addView(row(12) { addView(cBox, lp(0, WRAP, 1f)); addView(noteBox, lp(0, WRAP, 1f)) })
            }
            addView(row(10) {
                addView(btn("Ajouter", R.drawable.ic_plus, Btn.ACCENT) {
                    val km = kmE.text.toString().toIntOrNull()
                    if (km == null) { toast("Indiquez le kilométrage"); return@btn }
                    val ent = logType == "ent"
                    val name = if (ent) nE.text.toString().trim() else "Relevé kilométrique"
                    if (ent && name.isEmpty()) { toast("Indiquez l'intervention"); return@btn }
                    st.addLog(LogE(Store.newId(), date, km, if (ent) "ent" else "km", name, if (ent) cE.text.toString().trim() else "", if (ent) noteE.text.toString().trim() else ""))
                    if (km > st.odo) { st.odoBase = km; st.gpsMeters = 0f }
                    if (ent) {
                        val items = st.items
                        items.firstOrNull { it.name.equals(name, true) }?.let { if (date >= it.lastDate) { it.lastDate = date; it.lastKm = km } }
                        st.items = items
                    }
                    toast("Ligne ajoutée"); render()
                })
                addView(btn("Exporter (CSV)", R.drawable.ic_download) { exportCsv() })
            })
            addView(tv("L'export crée un fichier pour Excel ou LibreOffice. Choisissez une clé USB ou la mémoire de l'autoradio.", 15, Pal.muted))
        }), lp(MATCH, 0, 1f))
    }
    return twoCols(compact, left, right)
}

// =====================================================================
// Carburant
// =====================================================================
fun MainActivity.fuelScreen(): View {
    val online = sys.online()
    val list = sys.stations().let { s -> if (st.fuelSort == "prix") s.sortedBy { it.price } else s.sortedBy { it.km } }
    val best = list.minOfOrNull { it.price }
    return col(14) {
        addView(row(12) {
            addView(seg(Sys.FUELS, st.fuelType) { st.fuelType = it; render() })
            addView(View(context), lp(0, 1, 1f))
            addView(seg(listOf("prix" to "Moins cher", "dist" to "Plus proche"), st.fuelSort) { st.fuelSort = it; render() })
        })
        addView(card {
            addView(row(10) {
                val at = if (st.fuelAt > 0) "Mis à jour à " + SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(st.fuelAt)) else "Jamais mis à jour"
                addView(tv(at + (if (online) "" else " · hors ligne"), 13, Pal.muted, 400, 1), lp(0, WRAP, 1f))
                if (online) addView(btn("Actualiser", height = 38, size = 14) { sys.refreshFuel(true) { render() }; toast("Recherche des prix…") })
            })
            if (list.isEmpty()) addView(tv(if (st.lastLat == 0f) "Il faut d'abord une position GPS." else "Aucune station trouvée à moins de 10 km pour ce carburant.", 17, Pal.muted), lp(MATCH, 0, 1f))
            else addView(scroll(col {
                list.take(30).forEachIndexed { i, s ->
                    if (i > 0) addView(line())
                    addView(row(16) {
                        setPadding(px(12), px(14), px(12), px(14))
                        addView(col(3) {
                            addView(tv(s.addr.ifEmpty { s.city }, 19.5, Pal.fg, 500, 1))
                            addView(tv("${s.city} · ${km(s.km)}", 16, Pal.muted, 400, 1))
                        }, lp(0, WRAP, 1f))
                        addView(col(3) {
                            gravity = Gravity.END
                            addView(dtv(euro(s.price, 3) + " €/L", 30, if (s.price == best) Pal.accent else Pal.fg, 300, .02f))
                            addView(tv("Plein ${st.tank} L : " + euro(s.price * st.tank, 2) + " €", 15, Pal.muted, 400, 1))
                        })
                    })
                }
            }), lp(MATCH, 0, 1f))
        }, lp(MATCH, 0, 1f))
    }
}

// =====================================================================
// Stationnement
// =====================================================================
private val LIMITS = listOf(0 to "Sans limite", 15 to "15 min", 30 to "30 min", 60 to "1 h", 90 to "1 h 30", 120 to "2 h", 180 to "3 h")

fun MainActivity.parkScreen(compact: Boolean): View {
    val p = parkInfo()
    val (otherBox, otherE) = field("Autre durée (minutes)", if (LIMITS.any { it.first == st.parkLimit }) "" else st.parkLimit.toString(), NUM, "Ex. 45")
    otherE.setOnFocusChangeListener { _, has -> if (!has) otherE.text.toString().toIntOrNull()?.let { if (it > 0) { st.parkLimit = it; render() } } }
    val (noteBox, noteE) = field("Où je suis garé (facultatif)", st.parkNote, hint = "Ex. Parking de la gare, niveau -1")
    noteE.onChange { st.parkNote = it.trim() }
    val left = card {
        addView(scroll(col(14) {
            addView(row(8) { addView(icon(R.drawable.ic_park, 18, Pal.muted)); addView(label("Stationnement")) })
            addView(row(12) {
                addView(col(6) {
                    addView(tv("Heure d'arrivée", 15, Pal.muted, 500, 1))
                    addView(tv(p?.at ?: "Maintenant", 19.5, Pal.fg, 500).apply {
                        gravity = Gravity.CENTER_VERTICAL; background = rounded(Pal.surface2, 12, Pal.line, 1)
                        setPadding(px(14), 0, px(14), 0); minHeight = px(46)
                        onTap {
                            val c = Calendar.getInstance().apply { if (st.parkAt > 0) timeInMillis = st.parkAt }
                            TimePickerDialog(this@parkScreen, { _, h, m ->
                                val d = Calendar.getInstance().apply { set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m); set(Calendar.SECOND, 0) }
                                if (d.timeInMillis > System.currentTimeMillis()) d.add(Calendar.DAY_OF_MONTH, -1)
                                st.parkAt = d.timeInMillis; toast("Heure d'arrivée corrigée"); render()
                            }, c.get(Calendar.HOUR_OF_DAY), c.get(Calendar.MINUTE), true).show()
                        }
                    }, lp())
                }, lp(0, WRAP, 1f))
                addView(otherBox, lp(0, WRAP, 1f))
            })
            addView(tv("Durée autorisée", 15, Pal.muted, 500, 1))
            var r: LinearLayout? = null
            LIMITS.forEachIndexed { i, (v, t) ->
                if (i % 4 == 0) { r = row(8); addView(r) }
                r!!.addView(chip(t, st.parkLimit == v) { st.parkLimit = v; render() })
            }
            addView(noteBox)
        }), lp(MATCH, 0, 1f))
    }
    val right = card {
        if (p == null) {
            addView(label("Aucun stationnement en cours"))
            addView(col(16) {
                gravity = Gravity.CENTER_VERTICAL
                addView(tv("Touchez le bouton en arrivant. L'heure et la fin du stationnement autorisé restent affichées à votre retour.", 17, Pal.muted))
                addView(btn("Je me gare maintenant", R.drawable.ic_park, Btn.ACCENT, 64, 18) { parkNow() })
            }, lp(MATCH, 0, 1f))
        } else {
            addView(row { addView(label("Garé à ${p.at}"), lp(0, WRAP, 1f)); if (p.end.isNotEmpty()) addView(tv("jusqu'à ${p.end}", 17, Pal.fg, 500)) })
            val big = BigText(context).apply { maxSize = 80f; text = p.big; color = if (p.lv > 0) Pal.level(p.lv) else Pal.fg }
            val unit = tv(p.unit, 20.5, Pal.muted)
            val b = bar(p.r, Pal.level(p.lv), 10)
            addView(col(10) {
                gravity = Gravity.CENTER_VERTICAL
                addView(big, lp(MATCH, px(86))); addView(unit)
                if (p.limit > 0) addView(b)
                if (st.parkNote.isNotEmpty()) addView(row(6) { addView(icon(R.drawable.ic_pin, 18, Pal.muted)); addView(tv(st.parkNote, 18.5, Pal.muted, 400, 1)) })
            }, lp(MATCH, 0, 1f))
            ticks.add { parkInfo()?.let { q -> big.text = q.big; big.color = if (q.lv > 0) Pal.level(q.lv) else Pal.fg; unit.text = q.unit; b.ratio = q.r; b.color = Pal.level(q.lv) } }
            addView(row(10) {
                addView(btn("Je me gare maintenant") { parkNow() })
                addView(btn("Je repars", style = Btn.DANGER) { st.parkAt = 0L; toast("Stationnement terminé"); render() })
            })
        }
    }
    return twoCols(compact, left, right, 1f)
}

// =====================================================================
// Applis
// =====================================================================
fun MainActivity.appsScreen(compact: Boolean): View {
    val cols = if (compact) 4 else 6
    class Tile(val label: String, val res: Int = 0, val app: AppInfo? = null, val f: () -> Unit)
    val tiles = mutableListOf(
        Tile("Réglages", R.drawable.ic_sliders) { go("settings") },
        Tile("Paramètres Android", R.drawable.ic_android) { if (!sys.openAndroidSettings()) toast("Paramètres indisponibles") },
        Tile("Interface d'origine", R.drawable.ic_home) { if (!sys.openStockLauncher()) toast("Écran d'origine introuvable") },
        Tile("Stationnement", R.drawable.ic_park) { go("park") },
    )
    for (a in sys.launchableApps()) tiles.add(Tile(a.label, app = a) { if (!sys.launch(a.pkg, a.cls)) toast("Impossible d'ouvrir « ${a.label} »") })
    val grid = col(24) {
        tiles.chunked(cols).forEach { chunk ->
            addView(row {
                gravity = Gravity.TOP
                for (t in chunk) addView(col(11) {
                    gravity = Gravity.CENTER_HORIZONTAL
                    addView(FrameLayout(context).apply {
                        background = rounded(Pal.surface2, 18, Pal.line, 1)
                        if (t.app != null) addView(ImageView(context).apply { setImageDrawable(t.app.icon(context)) }, FrameLayout.LayoutParams(px(52), px(52), Gravity.CENTER))
                        else addView(icon(t.res, 34, Pal.fg), FrameLayout.LayoutParams(px(34), px(34), Gravity.CENTER))
                    }, LinearLayout.LayoutParams(px(78), px(78)))
                    addView(dtv(t.label, 15, Pal.fg, 500, .08f, lines = 2).apply { gravity = Gravity.CENTER })
                    onTap(t.f)
                }, lp(0, WRAP, 1f))
                repeat(cols - chunk.size) { addView(View(context), lp(0, 1, 1f)) }
            })
        }
    }
    return card { addView(scroll(grid), lp(MATCH, 0, 1f)) }
}

// =====================================================================
// Réglages : catégories à gauche, détail à droite
// =====================================================================
private val STABS = listOf(
    Triple("affichage", "Affichage", R.drawable.ic_sliders),
    Triple("accueil", "Accueil", R.drawable.ic_home),
    Triple("carplay", "CarPlay et applis", R.drawable.ic_carplay),
    Triple("barre", "Barre latérale", R.drawable.ic_grid),
    Triple("suivi", "Suivi et carburant", R.drawable.ic_wrench),
    Triple("systeme", "Système", R.drawable.ic_android),
    Triple("apropos", "À propos", R.drawable.ic_check),
)

fun MainActivity.settingsScreen(compact: Boolean): View {
    val tab = STABS.firstOrNull { it.first == st.settingsTab } ?: STABS[0]
    fun blk(title: String, sub: String = "", vararg v: View) = col(10) {
        setPadding(0, px(14), 0, px(14))
        addView(col(4) {
            addView(dtv(title, 16, Pal.fg, 500, .14f))
            if (sub.isNotEmpty()) addView(tv(sub, 15, Pal.muted))
        })
        for (x in v) addView(x)
    }
    fun swatches(cur: Int, colors: List<Int>, set: (Int) -> Unit) = row(10) {
        for (c in colors) addView(View(context).apply {
            background = rounded(c, 999, if (c == cur) Pal.fg else Pal.line, if (c == cur) 3 else 2)
            onTap { set(c); render() }
        }, LinearLayout.LayoutParams(px(40), px(40)))
    }
    fun kv(k: String, v: String, f: (() -> Unit)? = null) = row(12) {
        setPadding(0, px(10), 0, px(10))
        addView(tv(k, 17, Pal.muted, 400, 1), lp(0, WRAP, 1f))
        addView(dtv(v + if (f != null) "  ›" else "", 17, Pal.fg, 500, .04f, keepCaps = false))
        if (f != null) onTap(f)
    }
    fun toggleRow(text: String, on: Boolean, set: (Boolean) -> Unit) = row(12) {
        setPadding(0, px(6), 0, px(6))
        addView(tv(text, 18, Pal.fg, 500), lp(0, WRAP, 1f)); addView(Toggle(context, on) { set(it); render() })
    }
    fun chooser(title: String, cur: AppInfo?, save: (String) -> Unit) = kv(title, cur?.label ?: "à choisir") {
        val apps = sys.launchableApps()
        AlertDialog.Builder(this).setTitle(title).setItems(apps.map { it.label }.toTypedArray()) { _, i -> save(apps[i].pkg); render() }.show()
    }

    val photo = File(filesDir, "car.jpg")
    val detail: List<View> = when (tab.first) {
        "affichage" -> listOf(
            blk("Taille de l'interface", "Agrandit ou réduit textes, boutons et widgets.",
                seg(listOf("0.85" to "Petite", "1.0" to "Normale", "1.15" to "Grande", "1.3" to "Très grande"), st.ui.toString()) { st.ui = it.toFloat(); render() }),
            blk("Typographie", "Anguleuse : majuscules espacées. Normale : police classique, plus facile à lire.",
                seg(listOf("angular" to "Anguleuse", "normal" to "Normale"), st.font) { st.font = it; render() }),
            blk("Mode jour / nuit", "« Auto » passe en Nuit à la tombée du jour, ou quand l'autoradio signale les phares.",
                seg(listOf("auto" to "Auto", "day" to "Jour", "night" to "Nuit"), st.themeMode) { st.themeMode = it; render() }),
            blk("Couleur d'accent", "", swatches(st.accent, listOf(0xFF0A84FF, 0xFF30D158, 0xFFFF9F0A, 0xFFFF453A, 0xFFBF5AF2, 0xFF64D2FF, 0xFFE8C547).map { it.toInt() }) { st.accent = it }),
        )
        "accueil" -> {
            val (nameBox, nameE) = field("Nom", st.carName); nameE.onChange { st.carName = it.trim() }
            val (subBox, subE) = field("Sous-titre", st.carSub); subE.onChange { st.carSub = it.trim() }
            val (plateBox, plateE) = field("Plaque d'immatriculation", st.plate)
            plateE.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            plateE.onChange { st.plate = it.uppercase().trim().replace(Regex("[/\\s]+"), "-") }
            listOf(
                blk("Widget voiture : afficher", "", seg(listOf("name" to "Le nom", "plate" to "La plaque", "photo" to "Une photo"), st.carShow) { st.carShow = it; render() }),
                *(if (st.carShow == "name") arrayOf<View>(blk("Nom de la voiture", "", row(12) { addView(nameBox, lp(0, WRAP, 1f)); addView(subBox, lp(0, WRAP, 1f)) })) else arrayOf()),
                *(if (st.carShow == "photo") arrayOf<View>(blk("Photo", "Réduite automatiquement pour économiser la mémoire.", row(10) {
                    addView(btn(if (photo.exists()) "Changer la photo" else "Choisir une photo") { pickPhoto() })
                    if (photo.exists()) addView(btn("Retirer", style = Btn.DANGER) { photo.delete(); render() })
                })) else arrayOf()),
                blk("Plaque", "", plateBox),
                blk("Disposition des widgets", "Échanger, masquer ou ajouter des widgets sur l'accueil.",
                    row { addView(btn("Modifier l'accueil", R.drawable.ic_sliders) { screen = "home"; edit = true; sel = null; render() }) }),
            )
        }
        "carplay" -> listOf(
            blk("Ouvrir CarPlay automatiquement", "Au démarrage de l'autoradio, un bandeau laisse 5 secondes pour annuler.",
                seg(listOf("off" to "Jamais", "boot" to "Au démarrage"), st.autoCP) { st.autoCP = it; render() }),
            blk("Applis liées", "Détectées automatiquement. Touchez pour en choisir une autre.",
                chooser("CarPlay", sys.carplayApp()) { st.cpPkg = it }, line(), chooser("Radio", sys.radioApp()) { st.radioPkg = it }),
        )
        "barre" -> listOf(
            blk("Boutons affichés", "CarPlay, Accueil, Suivi et Applis restent toujours visibles.",
                toggleRow("Carburant", st.sideFuel) { st.sideFuel = it },
                toggleRow("Parking", st.sidePark) { st.sidePark = it },
                toggleRow("Radio", st.sideRadio) { st.sideRadio = it }),
        )
        "suivi" -> listOf(
            blk("Carburant de la voiture", "Utilisé pour le widget et l'écran Carburant.", seg(Sys.FUELS, st.fuelType) { st.fuelType = it; render() }),
            blk("Volume du plein", "Pour le calcul du prix d'un plein.", seg(listOf("40" to "40 L", "45" to "45 L", "50" to "50 L"), st.tank.toString()) { st.tank = it.toInt(); render() }),
            blk("Historique", "Exporter tous les relevés et entretiens en fichier CSV.", row { addView(btn("Exporter (CSV)", R.drawable.ic_download) { exportCsv() }) }),
        )
        "systeme" -> listOf(
            blk("Android", "Pour retrouver l'écran du fabricant ou tous les réglages d'Android.",
                row(10) {
                    addView(btn("Paramètres Android", R.drawable.ic_android) { sys.openAndroidSettings() })
                    addView(btn("Interface d'origine", R.drawable.ic_home) { if (!sys.openStockLauncher()) toast("Écran d'origine introuvable") })
                },
                row { addView(btn("Changer l'écran d'accueil par défaut") { sys.chooseDefaultHome() }) }),
            blk("Tout réinitialiser", "Remet les réglages, la disposition et efface le suivi.",
                row { addView(btn(if (armed == "all") "Confirmer : tout effacer" else "Réinitialiser", style = if (armed == "all") Btn.DANGER_ARMED else Btn.DANGER) {
                    if (armed != "all") { armed = "all"; render() } else { st.resetAll(); photo.delete(); armed = null; toast("Réglages réinitialisés"); render() }
                }) }),
        )
        else -> listOf(
            blk("Clio Home", "Version ${BuildInfo.version}",
                kv("Autoradio", "AWESAFE PX9"), kv("Processeur", "MT8163 · 2 Go"), kv("Écran", "1280 × 720"), kv("Boîtier CAN", "Raise · Renault"),
                tv("Police Chakra Petch : licence SIL Open Font License.", 14, Pal.muted)),
        )
    }

    val menu = col {
        STABS.forEach { t ->
            val on = t.first == tab.first
            addView(row(14) {
                setPadding(px(14), px(16), px(12), px(16))
                if (on) background = rounded(Pal.mix(Pal.surface, Pal.accent, .12f), 10)
                val c = if (on) Pal.accent else Pal.muted
                addView(icon(t.third, 22, c))
                addView(dtv(t.second, 16, if (on) Pal.accent else Pal.fg, 500, .1f), lp(0, WRAP, 1f))
                addView(tv("›", 22, c))
                onTap { st.settingsTab = t.first; armed = null; render() }
            })
            addView(line())
        }
    }
    val left = card { addView(scroll(menu), lp(MATCH, 0, 1f)) }
    val right = card {
        addView(row(9) { addView(icon(tab.third, 18, Pal.muted)); addView(label(tab.second)) })
        addView(scroll(col {
            detail.forEachIndexed { i, v -> if (i > 0) addView(line()); addView(v) }
        }), lp(MATCH, 0, 1f))
    }
    return if (compact) twoCols(true, left, right) else row(14) {
        gravity = Gravity.TOP
        addView(left, LinearLayout.LayoutParams(0, MATCH, .8f)); addView(right, LinearLayout.LayoutParams(0, MATCH, 1.6f))
    }
}

object BuildInfo { const val version = "0.3.1" }
