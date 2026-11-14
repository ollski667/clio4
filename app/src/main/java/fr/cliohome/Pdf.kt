package fr.cliohome

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Récapitulatif du suivi en PDF (A4), dessiné directement par Android.
 * Chaque ligne reste entière sur une page ; les en-têtes de colonnes se répètent sur chaque page.
 */
object Pdf {
    private const val W = 595; private const val H = 842; private const val M = 40f
    private val FG = Color.rgb(17, 20, 24); private val MUTED = Color.rgb(90, 97, 105)
    private val LINE = Color.rgb(213, 218, 224); private val SOFT = Color.rgb(242, 244, 246)
    private val OK = Color.rgb(28, 126, 65); private val WARN = Color.rgb(154, 91, 0); private val BAD = Color.rgb(194, 39, 26)

    private fun paint(size: Float, color: Int = FG, bold: Boolean = false) = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = size; this.color = color; typeface = Typeface.create(Typeface.SANS_SERIF, if (bold) Typeface.BOLD else Typeface.NORMAL)
    }

    private fun layout(text: String, p: TextPaint, width: Float, align: Layout.Alignment = Layout.Alignment.ALIGN_NORMAL): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, p, width.toInt().coerceAtLeast(10)).setAlignment(align).setLineSpacing(0f, 1.1f).build()

    /** Une cellule : texte principal + petite ligne grise facultative. */
    private class Cell(val main: String, val sub: String = "", val color: Int = FG, val bold: Boolean = false, val right: Boolean = false, val pill: Int = 0)

    fun build(st: Store): ByteArray {
        val doc = PdfDocument()
        var pageNo = 0
        var page: PdfDocument.Page? = null
        var c: Canvas? = null
        var y = 0f
        val footer = paint(8f, MUTED)
        val day = SimpleDateFormat("d MMMM yyyy", Locale.FRANCE).format(Date())

        fun newPage() {
            page?.let { doc.finishPage(it) }
            pageNo++
            page = doc.startPage(PdfDocument.PageInfo.Builder(W, H, pageNo).create())
            c = page!!.canvas
            c!!.drawText("Suivi ${st.carName} · ${st.plate} · $day", M, H - 22f, footer)
            val pn = "page $pageNo"; c!!.drawText(pn, W - M - footer.measureText(pn), H - 22f, footer)
            y = M
        }
        fun room(h: Float) = y + h <= H - 46f

        newPage()
        // En-tête
        c!!.drawText("Suivi d'entretien · ${st.carName}", M, y + 18f, paint(19f, bold = true)); y += 28f
        c!!.drawText("${st.carSub} · ${st.plate} · récapitulatif du $day", M, y + 10f, paint(10f, MUTED)); y += 24f

        // Chiffres clés
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
        fun eur(v: Double) = String.format(Locale.FRANCE, "%.0f €", v)
        val kpis = listOf(
            (if (st.odo > 0) Store.nf(st.odo) + " km" else "—") to "kilométrage actuel",
            avg to "par mois en moyenne",
            eur(log.filter { it.d.startsWith(year) }.sumOf { cost(it) }) to "dépensés en $year",
            eur(log.sumOf { cost(it) }) to "dépensés au total")
        val kw = (W - 2 * M - 3 * 8f) / 4
        val box = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SOFT }
        kpis.forEachIndexed { i, (v, l) ->
            val x = M + i * (kw + 8f)
            c!!.drawRoundRect(RectF(x, y, x + kw, y + 46f), 6f, 6f, box)
            c!!.drawText(v, x + 9f, y + 21f, paint(14f, bold = true))
            c!!.drawText(l, x + 9f, y + 36f, paint(8.5f, MUTED))
        }
        y += 62f

        // Tableau générique : lignes jamais coupées, en-tête répété sur chaque page
        fun table(title: String, note: String, cols: List<Pair<String, Float>>, rows: List<List<Cell>>) {
            val head = paint(7.5f, MUTED, true); val main = paint(9.5f); val mainB = paint(9.5f, bold = true); val sub = paint(8f, MUTED)
            fun header() {
                var x = M
                for ((name, w) in cols) { c!!.drawText(name.uppercase(Locale.FRANCE), x + 4f, y + 9f, head); x += w }
                y += 14f
                c!!.drawLine(M, y, W - M, y, Paint().apply { color = LINE; strokeWidth = 0.8f })
                y += 2f
            }
            fun rowHeight(r: List<Cell>): Pair<Float, List<Pair<StaticLayout, StaticLayout?>>> {
                val ls = r.mapIndexed { i, cell ->
                    val w = cols[i].second - 8f
                    val al = if (cell.right) Layout.Alignment.ALIGN_OPPOSITE else Layout.Alignment.ALIGN_NORMAL
                    val m = layout(cell.main, if (cell.bold) mainB else main.apply { color = cell.color }, w, al)
                    main.color = FG
                    val s = if (cell.sub.isNotEmpty()) layout(cell.sub, sub, w, al) else null
                    m to s
                }
                val h = ls.maxOf { (m, s) -> m.height + (s?.height ?: 0) + if (s != null) 2 else 0 } + 12f
                return h to ls
            }
            // Titre gardé avec au moins la première ligne
            val first = rows.firstOrNull()?.let { rowHeight(it).first } ?: 0f
            if (!room(40f + first + 16f)) newPage()
            c!!.drawText(title, M, y + 13f, paint(13f, bold = true)); y += 19f
            c!!.drawLine(M, y, W - M, y, Paint().apply { color = FG; strokeWidth = 1.4f }); y += 8f
            if (note.isNotEmpty()) { val nl = layout(note, paint(9f, MUTED), W - 2 * M); c!!.save(); c!!.translate(M, y); nl.draw(c!!); c!!.restore(); y += nl.height + 8f }
            header()
            for (r in rows) {
                val (h, ls) = rowHeight(r)
                if (!room(h)) { newPage(); header() }
                var x = M
                ls.forEachIndexed { i, (m, s) ->
                    val cell = r[i]
                    if (cell.pill != 0) {
                        val pw = m.getLineWidth(0) + 10f
                        val px = if (cell.right) x + cols[i].second - 4f - pw else x + 2f
                        c!!.drawRoundRect(RectF(px, y + 4f, px + pw, y + 6f + m.height), 7f, 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = cell.pill })
                    }
                    c!!.save(); c!!.translate(x + 4f + if (cell.pill != 0 && !cell.right) 3f else 0f, y + 6f); m.draw(c!!)
                    if (s != null) { c!!.translate(0f, m.height + 2f); s.draw(c!!) }
                    c!!.restore()
                    x += cols[i].second
                }
                y += h
                c!!.drawLine(M, y, W - M, y, Paint().apply { color = LINE; strokeWidth = 0.6f })
            }
            y += 18f
        }

        // Échéances
        val items = st.items.map { it to st.due(it) }.sortedByDescending { it.second.r + if (it.second.lv < 0) -2f else 0f }
        val todo = items.count { it.second.lv == 2 }; val soon = items.count { it.second.lv == 1 }
        val note = when {
            todo > 0 -> "$todo entretien${if (todo > 1) "s" else ""} à faire" + if (soon > 0) ", $soon bientôt." else "."
            soon > 0 -> "$soon entretien${if (soon > 1) "s" else ""} à prévoir bientôt."
            else -> "Rien d'urgent."
        } + " Prochaine échéance au premier des deux termes (km ou date)."
        table("Échéances", note,
            listOf("Entretien" to 150f, "Intervalle" to 105f, "Dernier" to 85f, "Prochain" to 95f, "État" to 80f),
            items.map { (it, d) ->
                val (stateTxt, col, bg) = when (d.lv) {
                    2 -> Triple("À faire", BAD, Color.rgb(251, 227, 224)); 1 -> Triple("Bientôt", WARN, Color.rgb(251, 239, 217))
                    0 -> Triple("À jour", OK, Color.rgb(227, 243, 232)); else -> Triple("À renseigner", MUTED, SOFT)
                }
                listOf(
                    Cell(it.name, bold = true),
                    Cell(st.every(it)),
                    if (it.lastKm >= 0 && it.lastDate.isNotEmpty()) Cell("${Store.nf(it.lastKm)} km", Store.frDate(it.lastDate)) else Cell("—"),
                    if (d.lv < 0) Cell("—") else Cell(d.dueKm ?: d.dueDate, if (d.dueKm != null) d.dueDate else ""),
                    Cell(stateTxt, if (d.lv >= 0) d.txt.removePrefix("À faire · ") else "", col, pill = bg),
                )
            })

        // Historique
        if (log.isEmpty()) {
            if (!room(60f)) newPage()
            c!!.drawText("Historique", M, y + 13f, paint(13f, bold = true)); y += 22f
            c!!.drawText("Aucun relevé ni entretien enregistré pour l'instant.", M, y + 10f, paint(9.5f, MUTED)); y += 20f
        } else table("Historique", "",
            listOf("Date" to 70f, "Intervention" to 255f, "Kilométrage" to 100f, "Coût" to 90f),
            log.map { e ->
                listOf(Cell(Store.frDate(e.d)), Cell(e.name, e.note, bold = e.t == "ent"),
                    Cell("${Store.nf(e.km)} km", right = true), Cell(if (e.cost.isNotEmpty()) e.cost + " €" else "—", right = true))
            })

        if (!room(20f)) newPage()
        c!!.drawText("Généré par Clio Home ${BuildInfo.version}. Intervalles indicatifs : se référer au carnet d'entretien.", M, y + 8f, paint(8f, MUTED))
        page?.let { doc.finishPage(it) }
        val out = ByteArrayOutputStream()
        doc.writeTo(out); doc.close()
        return out.toByteArray()
    }
}
