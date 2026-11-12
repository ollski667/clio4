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
    addView(tv(labelText, 13, Pal.muted, 500, 1))
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
                    addView(tv(it.name, 17, Pal.fg, 500, 1), lp(0, WRAP, 1f))
                    addView(pill(d.txt, d.lv))
                })
                addView(tv("Tous les " + st.every(it), 14, Pal.muted, 400, 1))
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
        addView(tv("Entre deux saisies, l'appli ajoute les km mesurés par le GPS. Intervalles indicatifs pour le 1.5 dCi : vérifiez votre carnet d'entretien.", 13, Pal.muted))
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
        addView(tv(if (d.lv < 0) "Indiquez le kilométrage et la date du dernier entretien." else "Prochaine échéance : " + listOfNotNull(d.dueKm, d.dueDate).joinToString(" ou ") + ", au premier des deux.", 13, Pal.muted))
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
        addView(tv(v, 24, Pal.fg, 400, 1)); addView(tv(l, 12.5, Pal.muted, 400, 1))
    }
    val list = col {
        if (log.isEmpty()) addView(tv("Aucune ligne pour l'instant. Ajoutez un relevé ou un entretien à droite.", 14, Pal.muted).apply { setPadding(px(8), px(20), px(8), px(20)) })
        log.forEachIndexed { i, e ->
            if (i > 0) addView(line())
            addView(row(12) {
                setPadding(px(8), px(12), px(8), px(12))
                addView(tv(Store.frDate(e.d), 15, Pal.fg, 400, 1), lp(px(104), WRAP))
                addView(FrameLayout(context).apply {
                    background = rounded(Pal.surface2, 9)
                    addView(icon(if (e.t == "km") R.drawable.ic_gauge else R.drawable.ic_wrench, 17, Pal.accent), FrameLayout.LayoutParams(px(17), px(17), Gravity.CENTER))
                }, lp(px(30), px(30)))
                addView(col(2) {
                    addView(tv(e.name, 15, Pal.fg, 500, 1))
                    if (e.note.isNotEmpty()) addView(tv(e.note, 13, Pal.muted, 400, 1))
                }, lp(0, WRAP, 1f))
                addView(tv("${Store.nf(e.km)} km", 15, Pal.fg, 400, 1).apply { gravity = Gravity.END }, lp(px(110), WRAP))
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
            addView(tv("L'export crée un fichier pour Excel ou LibreOffice. Choisissez une clé USB ou la mémoire de l'autoradio.", 13, Pal.muted))
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
            if (list.isEmpty()) addView(tv(if (st.lastLat == 0f) "Il faut d'abord une position GPS." else "Aucune station trouvée à moins de 10 km pour ce carburant.", 15, Pal.muted), lp(MATCH, 0, 1f))
            else addView(scroll(col {
                list.take(30).forEachIndexed { i, s ->
                    if (i > 0) addView(line())
                    addView(row(16) {
                        setPadding(px(12), px(14), px(12), px(14))
                        addView(col(3) {
                            addView(tv(s.addr.ifEmpty { s.city }, 17, Pal.fg, 500, 1))
                            addView(tv("${s.city} · ${km(s.km)}", 14, Pal.muted, 400, 1))
                        }, lp(0, WRAP, 1f))
                        addView(col(3) {
                            gravity = Gravity.END
                            addView(tv(euro(s.price, 3) + " €/L", 28, if (s.price == best) Pal.accent else Pal.fg, 300, 1))
                            addView(tv("Plein 45 L : " + euro(s.price * 45, 2) + " €", 13, Pal.muted, 400, 1))
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
                    addView(tv("Heure d'arrivée", 13, Pal.muted, 500, 1))
                    addView(tv(p?.at ?: "Maintenant", 17, Pal.fg, 500).apply {
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
            addView(tv("Durée autorisée", 13, Pal.muted, 500, 1))
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
                addView(tv("Touchez le bouton en arrivant. L'heure et la fin du stationnement autorisé restent affichées à votre retour.", 15, Pal.muted))
                addView(btn("Je me gare maintenant", R.drawable.ic_park, Btn.ACCENT, 64, 18) { parkNow() })
            }, lp(MATCH, 0, 1f))
        } else {
            addView(row { addView(label("Garé à ${p.at}"), lp(0, WRAP, 1f)); if (p.end.isNotEmpty()) addView(tv("jusqu'à ${p.end}", 15, Pal.fg, 500)) })
            val big = BigText(context).apply { maxSize = 80f; text = p.big; color = if (p.lv > 0) Pal.level(p.lv) else Pal.fg }
            val unit = tv(p.unit, 18, Pal.muted)
            val b = bar(p.r, Pal.level(p.lv), 10)
            addView(col(10) {
                gravity = Gravity.CENTER_VERTICAL
                addView(big, lp(MATCH, px(86))); addView(unit)
                if (p.limit > 0) addView(b)
                if (st.parkNote.isNotEmpty()) addView(row(6) { addView(icon(R.drawable.ic_pin, 18, Pal.muted)); addView(tv(st.parkNote, 16, Pal.muted, 400, 1)) })
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
    val grid = col(22) {
        tiles.chunked(cols).forEach { chunk ->
            addView(row {
                gravity = Gravity.TOP
                for (t in chunk) addView(col(10) {
                    gravity = Gravity.CENTER_HORIZONTAL
                    addView(FrameLayout(context).apply {
                        background = rounded(Pal.surface, 20, Pal.line, 1)
                        if (t.app != null) addView(ImageView(context).apply { setImageDrawable(t.app.icon(context)) }, FrameLayout.LayoutParams(px(52), px(52), Gravity.CENTER))
                        else addView(icon(t.res, 34, Pal.accent), FrameLayout.LayoutParams(px(34), px(34), Gravity.CENTER))
                    }, LinearLayout.LayoutParams(px(76), px(76)))
                    addView(tv(t.label, 14, Pal.fg, 500, 2).apply { gravity = Gravity.CENTER })
                    onTap(t.f)
                }, lp(0, WRAP, 1f))
                repeat(cols - chunk.size) { addView(View(context), lp(0, 1, 1f)) }
            })
        }
    }
    return card { addView(scroll(grid), lp(MATCH, 0, 1f)) }
}

// =====================================================================
// Réglages
// =====================================================================
fun MainActivity.settingsScreen(compact: Boolean): View {
    fun rowBlock(title: String, sub: String = "", vararg v: View) = col(10) {
        addView(col(3) {
            addView(tv(title, 16, Pal.fg, 500))
            if (sub.isNotEmpty()) addView(tv(sub, 13, Pal.muted))
        })
        for (x in v) addView(x)
    }
    fun swatches(cur: Int, colors: List<Int>, square: Boolean, set: (Int) -> Unit) = row(10) {
        for (c in colors) addView(View(context).apply {
            background = rounded(c, if (square) 12 else 999, if (c == cur) Pal.fg else Pal.line, if (c == cur) 3 else 2)
            onTap { set(c); render() }
        }, LinearLayout.LayoutParams(px(40), px(40)))
    }
    fun block(vararg blocks: View) = card(14) {
        blocks.forEachIndexed { i, b -> if (i > 0) addView(View(context).apply { setBackgroundColor(Pal.line) }, lp(MATCH, 1)); addView(b) }
    }
    fun chooser(title: String, cur: AppInfo?, save: (String) -> Unit) = btn("$title : " + (cur?.label ?: "à choisir")) {
        val apps = sys.launchableApps()
        AlertDialog.Builder(this).setTitle(title).setItems(apps.map { it.label }.toTypedArray()) { _, i -> save(apps[i].pkg); render() }.show()
    }

    val photo = File(filesDir, "car.jpg")
    val (nameBox, nameE) = field("Nom", st.carName); nameE.onChange { st.carName = it.trim() }
    val (subBox, subE) = field("Sous-titre", st.carSub); subE.onChange { st.carSub = it.trim() }
    val (plateBox, plateE) = field("Plaque d'immatriculation", st.plate)
    plateE.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
    plateE.onChange { st.plate = it.uppercase().trim().replace(Regex("[/\\s]+"), "-") }

    val left = block(
        rowBlock("Taille de l'interface", "Agrandit ou réduit tout l'écran : textes, boutons et widgets.",
            seg(listOf("0.85" to "Petite", "1.0" to "Normale", "1.15" to "Grande", "1.3" to "Très grande"), st.ui.toString()) { st.ui = it.toFloat(); render() }),
        rowBlock("Mode jour / nuit", "« Auto » passe en nuit à la tombée du jour, ou quand l'autoradio signale les phares.",
            seg(listOf("auto" to "Auto", "day" to "Jour", "night" to "Nuit"), st.themeMode) { st.themeMode = it; render() }),
        rowBlock("Couleur d'accent", "", swatches(st.accent, listOf(0xFF0A84FF, 0xFF30D158, 0xFFFF9F0A, 0xFFFF453A, 0xFFBF5AF2, 0xFF64D2FF, 0xFFE8C547).map { it.toInt() }, false) { st.accent = it }),
        rowBlock("Fond de jour", "", swatches(st.bgDay, listOf(0xFFEEF1F4, 0xFFFFFFFF, 0xFFE6EBF0, 0xFFECEEE8, 0xFFF1ECEF).map { it.toInt() }, true) { st.bgDay = it }),
        rowBlock("Fond de nuit", "", swatches(st.bgNight, listOf(0xFF0B0D10, 0xFF000000, 0xFF0E1420, 0xFF101613, 0xFF17121A).map { it.toInt() }, true) { st.bgNight = it }),
        rowBlock("Sur l'accueil, afficher", "La photo de la voiture, ou seulement son nom.",
            row { addView(seg(listOf("photo" to "La photo", "name" to "Le nom"), st.carShow) { st.carShow = it; render() }) },
            *(if (st.carShow == "name") arrayOf<View>(row(12) { addView(nameBox, lp(0, WRAP, 1f)); addView(subBox, lp(0, WRAP, 1f)) }) else arrayOf())),
        rowBlock("Plaque", "", plateBox),
        rowBlock("Photo de la voiture", "Réduite automatiquement pour économiser la mémoire.", row(10) {
            addView(btn(if (photo.exists()) "Changer la photo" else "Choisir une photo") { pickPhoto() })
            if (photo.exists()) addView(btn("Retirer", style = Btn.DANGER) { photo.delete(); render() })
        }),
    )
    val right = block(
        rowBlock("Ouvrir CarPlay automatiquement", "Au démarrage de l'autoradio, un bandeau laisse 5 secondes pour annuler.",
            row { addView(seg(listOf("off" to "Jamais", "boot" to "Au démarrage"), st.autoCP) { st.autoCP = it; render() }) }),
        rowBlock("Applis liées", "Détectées automatiquement. Changez-les si ce n'est pas la bonne.",
            row { addView(chooser("CarPlay", sys.carplayApp()) { st.cpPkg = it }) },
            row { addView(chooser("Radio", sys.radioApp()) { st.radioPkg = it }) }),
        rowBlock("Barre latérale", "",
            row { addView(tv("Carburant", 16, Pal.fg, 500), lp(0, WRAP, 1f)); addView(Toggle(context, st.sideFuel) { st.sideFuel = it; render() }) },
            row { addView(tv("Radio", 16, Pal.fg, 500), lp(0, WRAP, 1f)); addView(Toggle(context, st.sideRadio) { st.sideRadio = it; render() }) }),
        rowBlock("Système", "Pour retrouver l'écran du fabricant ou tous les réglages d'Android.",
            row(10) {
                addView(btn("Paramètres Android", R.drawable.ic_android) { sys.openAndroidSettings() })
                addView(btn("Interface d'origine", R.drawable.ic_home) { if (!sys.openStockLauncher()) toast("Écran d'origine introuvable") })
            },
            row { addView(btn("Changer l'écran d'accueil par défaut") { sys.chooseDefaultHome() }) }),
        rowBlock("Infos véhicule (CAN-bus)", "Boîtier Raise, profil Renault. La température extérieure du capteur de la voiture sera affichée dès que la lecture du CAN sera branchée."),
        rowBlock("Tout réinitialiser", "Remet les réglages, la disposition et efface le suivi.",
            row { addView(btn(if (armed == "all") "Confirmer : tout effacer" else "Réinitialiser", style = if (armed == "all") Btn.DANGER_ARMED else Btn.DANGER) {
                if (armed != "all") { armed = "all"; render() } else { st.resetAll(); photo.delete(); armed = null; toast("Réglages réinitialisés"); render() }
            }) }),
        tv("Clio Home ${BuildInfo.version}", 12, Pal.muted),
    )
    val content = if (compact) col(14) { addView(left); addView(right) } else row(14) {
        gravity = Gravity.TOP
        addView(left, LinearLayout.LayoutParams(0, WRAP, 1f)); addView(right, LinearLayout.LayoutParams(0, WRAP, 1f))
    }
    return scroll(content)
}

object BuildInfo { const val version = "0.1" }

@Suppress("unused") private val unusedColor = Color.TRANSPARENT
