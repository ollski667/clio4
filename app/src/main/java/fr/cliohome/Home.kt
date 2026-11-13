package fr.cliohome

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.max

/** Grille de l'accueil : colonnes fixes, widgets de 1 ou 2 cases, lignes qui se partagent la hauteur. */
class HomeGrid(ctx: Context, private val cols: Int) : ViewGroup(ctx) {
    private val gap = px(14)
    private val pos = mutableListOf<IntArray>() // col, row, span

    fun add(v: View, span: Int) { v.tag = span.coerceAtMost(cols); addView(v) }

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec); val h = MeasureSpec.getSize(hSpec)
        pos.clear()
        val used = mutableListOf<BooleanArray>()
        fun free(r: Int, c: Int, s: Int): Boolean {
            while (used.size <= r) used.add(BooleanArray(cols))
            return (c until c + s).all { !used[r][it] }
        }
        for (i in 0 until childCount) {
            val s = getChildAt(i).tag as Int
            var r = 0; var placed = false
            while (!placed) {
                for (c in 0..cols - s) if (free(r, c, s)) {
                    for (k in c until c + s) used[r][k] = true
                    pos.add(intArrayOf(c, r, s)); placed = true; break
                }
                if (!placed) r++
            }
        }
        val rows = max(1, used.size)
        val colW = (w - gap * (cols - 1)) / cols.toFloat()
        val rowH = (h - gap * (rows - 1)) / rows.toFloat()
        for (i in 0 until childCount) {
            val p = pos[i]
            val cw = (colW * p[2] + gap * (p[2] - 1)).toInt()
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(rowH.toInt(), MeasureSpec.EXACTLY))
        }
        setMeasuredDimension(w, h)
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val rows = max(1, (pos.maxOfOrNull { it[1] } ?: 0) + 1)
        val colW = (width - gap * (cols - 1)) / cols.toFloat()
        val rowH = (height - gap * (rows - 1)) / rows.toFloat()
        for (i in 0 until childCount) {
            val p = pos[i]; val v = getChildAt(i)
            val x = (p[0] * (colW + gap)).toInt(); val y = (p[1] * (rowH + gap)).toInt()
            v.layout(x, y, x + v.measuredWidth, y + v.measuredHeight)
        }
    }
}

val WIDGET_NAMES = mapOf(
    "car" to "Ma voiture", "speed" to "Vitesse", "temp" to "Extérieur", "maint" to "Entretien",
    "park" to "Stationnement", "fuel" to "Carburant", "volume" to "Volume", "clock" to "Heure", "conn" to "Connexions"
)

fun MainActivity.homeScreen(compact: Boolean): View {
    val grid = HomeGrid(this, if (compact) 2 else 4)
    for (id in st.layout) {
        val span = if (id in setOf("car", "maint", "clock")) 2 else 1
        grid.add(widgetFrame(id), span)
    }
    return col(14) {
        addView(grid, lp(MATCH, 0, 1f))
        if (edit) addView(editTray(), lp())
    }
}

private fun MainActivity.widgetFrame(id: String): View {
    val selected = edit && sel == id
    val card = widget(id).apply {
        background = when {
            selected -> rounded(Pal.mix(Pal.surface, Pal.accent, .12f), 20, Pal.accent, 2)
            edit -> rounded(Pal.surface, 20, Pal.mix(Pal.surface, Pal.muted, .6f), 2, dash = true)
            else -> panelBg()
        }
        if (edit) setPadding(paddingLeft, paddingTop, px(62), paddingBottom)
    }
    val frame = FrameLayout(this)
    frame.addView(card, FrameLayout.LayoutParams(MATCH, MATCH))
    if (edit) {
        val x = FrameLayout(this).apply {
            background = rounded(Pal.surface2, 999, Pal.line, 1)
            addView(icon(R.drawable.ic_x, 18, Pal.fg), FrameLayout.LayoutParams(px(18), px(18), Gravity.CENTER))
            onTap { st.layout = st.layout.apply { remove(id) }; st.hidden = st.hidden.apply { add(0, id) }; if (sel == id) sel = null; render() }
        }
        frame.addView(x, FrameLayout.LayoutParams(px(38), px(38), Gravity.TOP or Gravity.END).apply { topMargin = px(10); rightMargin = px(10) })
        frame.onTap {
            val s = sel
            when {
                s == null -> sel = id
                s == id -> sel = null
                else -> { val l = st.layout; val i = l.indexOf(s); val j = l.indexOf(id); l[i] = id; l[j] = s; st.layout = l; sel = null }
            }
            render()
        }
    } else {
        when (id) {
            "maint" -> frame.onTap { go("maint") }
            "fuel" -> frame.onTap { go("fuel") }
            "park" -> frame.onTap { go("park") }
            "temp" -> frame.onTap { sys.refreshWeather(true) { render() }; toast(if (sys.online()) "Météo actualisée" else "Hors ligne : météo indisponible") }
        }
        frame.setOnLongClickListener { edit = true; sel = null; render(); true }
    }
    return frame
}

private fun MainActivity.editTray() = row(10) {
    background = panelBg()
    setPadding(px(18), px(10), px(12), px(10))
    minimumHeight = px(64)
    addView(tv(if (sel != null) "Touchez le 2ᵉ widget" else "Touchez 2 widgets pour les échanger", 16, Pal.muted, 400, 1))
    for (h in st.hidden) addView(chip("+ " + (WIDGET_NAMES[h] ?: h)) {
        st.hidden = st.hidden.apply { remove(h) }; st.layout = st.layout.apply { add(h) }; render()
    })
    addView(View(context), lp(0, 1, 1f))
    addView(chip("Réinitialiser") {
        st.layout = mutableListOf("car", "speed", "temp", "maint", "park", "fuel"); st.hidden = mutableListOf("volume", "clock", "conn"); sel = null; render()
    })
}

private fun MainActivity.head(text: String, iconRes: Int = 0, right: View? = null) = row(9) {
    if (iconRes != 0) addView(icon(iconRes, 18, Pal.muted))
    addView(label(text))
    addView(View(context), lp(0, 1, 1f))
    if (right != null) addView(right)
}

private fun MainActivity.box(init: LinearLayout.() -> Unit) = col(8) {
    setPadding(px(22), px(20), px(22), px(20))
    init()
}

private fun Context.grow(v: View) = v.apply { layoutParams = lp(MATCH, 0, 1f) }

private fun MainActivity.kmText() = dtv(if (st.odo > 0) "${Store.nf(st.odo)} km" else "km à saisir", 16, if (st.odo > 0) Pal.fg else Pal.muted, 500, .06f, keepCaps = true)

fun MainActivity.widget(id: String): LinearLayout = when (id) {
    "car" -> carWidget()
    "speed" -> box {
        addView(head("Vitesse", R.drawable.ic_gauge))
        val big = BigText(context).apply { maxSize = 110f; unit = "km/h"; text = if (sys.speedKmh >= 0) "${sys.speedKmh}" else "—" }
        addView(grow(big))
        val g = bar(0f, Pal.accent, 12)
        addView(g)
        val sub = tv("", 15, Pal.muted, 400, 1)
        addView(sub)
        val upd = {
            big.text = if (sys.speedKmh >= 0) "${sys.speedKmh}" else "—"
            g.ratio = (sys.speedKmh.coerceAtLeast(0) / 130f)
            sub.text = if (sys.gpsFix) "" else "Recherche du signal GPS…"
            sub.visibility = if (sys.gpsFix) View.GONE else View.VISIBLE
        }
        upd(); ticks.add(upd)
    }
    "temp" -> box {
        addView(head("Extérieur", R.drawable.ic_thermo))
        val w = sys.weather()
        val outside = Can.outsideTemp ?: w?.temp
        addView(grow(BigText(context).apply { maxSize = 96f; text = if (outside != null) "$outside°" else "—" }))
        val online = sys.online()
        addView(col(4) {
            if (w != null && online) {
                addView(tv(w.text + " · ${w.min}° / ${w.max}°", 17, Pal.fg, 500, 1))
            } else if (w != null) {
                addView(tv("Météo de ${SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date(w.at))} · hors ligne", 15, Pal.muted, 400, 1))
            } else addView(tv("Prévisions météo : hors ligne", 15, Pal.muted, 400, 1))
            if (Can.outsideTemp == null && outside != null) addView(tv("Mesure météo (capteur à relier)", 13.5, Pal.muted, 400, 1))
        })
    }
    "maint" -> box {
        addView(head("Entretien", R.drawable.ic_wrench, kmText()))
        val list = st.items.map { it to st.due(it) }.sortedByDescending { it.second.r + if (it.second.lv < 0) -1f else 0f }.take(3)
        addView(grow(col {
            gravity = Gravity.CENTER_VERTICAL
            dividerDrawable = spacer(px(16)); showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE
            for ((it, d) in list) addView(col(8) {
                addView(row(10) {
                    addView(tv(it.name, 18, Pal.fg, 500, 1), lp(0, WRAP, 1f))
                    addView(pill(d.txt, d.lv))
                })
                addView(bar(d.r, Pal.level(d.lv), 7))
            })
        }))
    }
    "park" -> parkWidget()
    "fuel" -> box {
        val online = sys.online()
        addView(head(Sys.fuelName(st.fuelType), R.drawable.ic_fuel, if (online) null else tag("hors ligne")))
        val best = sys.stations().minByOrNull { it.price }
        if (best == null) {
            addView(grow(tv(if (online) "Recherche des stations proches… (GPS nécessaire)" else "Pas encore de prix : il faut Internet une première fois.", 17, Pal.muted).apply { gravity = Gravity.CENTER_VERTICAL }))
        } else {
            addView(grow(col(6) {
                gravity = Gravity.CENTER_VERTICAL
                addView(tv(if (online) "Le moins cher" else "Derniers prix connus", 17, Pal.muted, 400, 1))
                addView(BigText(context).apply { maxSize = 64f; text = euro(best.price, 3); unit = "€/L" }, lp(MATCH, px(68)))
                addView(tv("Plein ${st.tank} L : " + euro(best.price * st.tank, 2) + " €", 15, Pal.muted, 400, 1))
            }))
            addView(tv("${best.city} · ${km(best.km)}", 17, Pal.fg, 500, 1))
        }
    }
    "volume" -> box {
        addView(head("Volume", R.drawable.ic_vol))
        val big = BigText(context).apply { maxSize = 96f; text = "${sys.volume}"; unit = "/ ${sys.volumeMax}" }
        addView(grow(big))
        val b = bar(sys.volume / sys.volumeMax.coerceAtLeast(1).toFloat(), Pal.accent, 10)
        addView(row(10) {
            addView(roundBtn(R.drawable.ic_minus) { sys.volumeStep(-1) })
            addView(b, lp(0, px(10), 1f))
            addView(roundBtn(R.drawable.ic_plus) { sys.volumeStep(1) })
        })
        ticks.add { big.text = "${sys.volume}"; b.ratio = sys.volume / sys.volumeMax.coerceAtLeast(1).toFloat() }
    }
    "clock" -> box {
        addView(head("Aujourd'hui"))
        val t = BigText(context).apply { maxSize = 132f }
        val d = dtv("", 22, Pal.fg, 400, .1f)
        addView(grow(t)); addView(d)
        val upd = {
            t.text = SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date())
            val ds = SimpleDateFormat("EEEE d MMMM", Locale.FRANCE).format(Date()).replaceFirstChar { it.uppercase() }
            d.text = if (Fonts.angular) ds.uppercase(Locale.FRANCE) else ds
        }
        upd(); ticks.add(upd)
    }
    "conn" -> box {
        addView(head("Connexions"))
        addView(grow(col(16) {
            gravity = Gravity.CENTER_VERTICAL
            addView(row(10) { addView(icon(R.drawable.ic_bt, 26, Pal.accent)); addView(tv("Bluetooth", 18, Pal.fg, 500), lp(0, WRAP, 1f)); addView(tv(if (sys.bluetoothOn()) "activé" else "désactivé", 17, Pal.muted)) })
            addView(row(10) { addView(icon(R.drawable.ic_wifi, 26, Pal.accent)); addView(tv("Internet", 18, Pal.fg, 500), lp(0, WRAP, 1f)); addView(tv(if (sys.online()) "connecté" else "hors ligne", 17, Pal.muted)) })
        }))
        addView(tv("CarPlay sans fil via ZLink", 17, Pal.muted))
    }
    else -> box { addView(tv(id)) }
}

fun Context.roundBtn(res: Int, size: Number = 46, f: () -> Unit) = FrameLayout(this).apply {
    background = rounded(Pal.surface2, 12, Pal.line, 1)
    addView(icon(res, 22, Pal.fg), FrameLayout.LayoutParams(px(22), px(22), Gravity.CENTER))
    layoutParams = LinearLayout.LayoutParams(px(size), px(size))
    onTap(f)
}

fun euro(v: Double, dec: Int) = String.format(Locale.FRANCE, "%.${dec}f", v)
fun km(v: Float) = String.format(Locale.FRANCE, "%.1f km", v)

/** Plaque française : bande bleue « F », numéro, bande bleue. scale agrandit le tout. */
fun Context.plateView(plate: String, scale: Float = 1f) = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    background = rounded(Color.WHITE, 5 * scale, 0xFF111111.toInt(), 1.5 * scale)
    clipToOutline = true
    val blue = 0xFF1D4FB8.toInt()
    addView(tv("F", 10 * scale, Color.WHITE, 700).apply { gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL; setPadding(0, 0, 0, px(3 * scale)); setBackgroundColor(blue) }, LinearLayout.LayoutParams(px(16 * scale), MATCH))
    addView(tv(plate.uppercase(), 17 * scale, 0xFF111111.toInt(), 600, 1).apply {
        typeface = Fonts.display(600); letterSpacing = 0.08f; gravity = Gravity.CENTER; setPadding(px(9 * scale), 0, px(9 * scale), 0)
    }, LinearLayout.LayoutParams(WRAP, MATCH))
    addView(View(context).apply { setBackgroundColor(blue) }, LinearLayout.LayoutParams(px(12 * scale), MATCH))
    layoutParams = LinearLayout.LayoutParams(WRAP, px(28 * scale))
}

private fun MainActivity.carWidget() = box {
    addView(row(12) {
        addView(label("Ma Clio"))
        addView(plateView(st.plate))
        addView(View(context), lp(0, 1, 1f))
        addView(kmText())
    })
    val photo = File(filesDir, "car.jpg")
    val body: View = when {
        st.carShow == "name" -> col(12) {
            gravity = Gravity.CENTER_VERTICAL
            addView(BigText(context).apply {
                maxSize = 96f; text = if (Fonts.angular) st.carName.uppercase(Locale.FRANCE) else st.carName
                if (Fonts.angular) spacing(.14f) else weight(600)
            }, lp(MATCH, px(96)))
            addView(dtv(st.carSub, 17, Pal.muted, 500, .2f))
        }
        st.carShow == "plate" -> FrameLayout(this@carWidget).apply {
            addView(plateView(st.plate, 2.4f), FrameLayout.LayoutParams(WRAP, px(28 * 2.4f), Gravity.CENTER))
        }
        photo.exists() -> ImageView(this@carWidget).apply {
            scaleType = ImageView.ScaleType.CENTER_CROP
            setImageBitmap(BitmapFactory.decodeFile(photo.path))
            background = rounded(Pal.surface2, 14); clipToOutline = true
            if (!edit) onTap { pickPhoto() }
        }
        else -> col(10) {
            gravity = Gravity.CENTER
            background = rounded(Color.TRANSPARENT, 14, Pal.dim, 2, dash = true)
            addView(ImageView(context).apply { setImageResource(R.drawable.ic_plus); setColorFilter(Pal.muted) }, LinearLayout.LayoutParams(px(36), px(36)))
            addView(tv("Touchez pour ajouter une photo de votre voiture", 17, Pal.muted, 500).apply { gravity = Gravity.CENTER })
            if (!edit) onTap { pickPhoto() }
        }
    }
    addView(grow(body))
}

private fun MainActivity.parkWidget() = box {
    val p = parkInfo()
    if (p == null) {
        addView(head("Stationnement", R.drawable.ic_park))
        addView(grow(tv("Notez l'heure d'arrivée et la durée autorisée.", 17, Pal.muted).apply { gravity = Gravity.CENTER_VERTICAL }))
        addView(btn("Je me gare", R.drawable.ic_park, Btn.ACCENT) { if (!edit) parkNow() })
    } else {
        addView(head("Garé à ${p.at}", R.drawable.ic_park))
        val big = BigText(context).apply { maxSize = 60f; text = p.big; color = if (p.lv > 0) Pal.level(p.lv) else Pal.fg }
        val unit = tv(p.unit + if (p.end.isNotEmpty()) " · jusqu'à ${p.end}" else "", 17, Pal.muted, 400, 1)
        addView(grow(col(6) { gravity = Gravity.CENTER_VERTICAL; addView(big, lp(MATCH, px(64))); addView(unit) }))
        val b = if (p.limit > 0) bar(p.r, Pal.level(p.lv), 12).also { addView(it) } else null
        ticks.add {
            parkInfo()?.let { q -> big.text = q.big; big.color = if (q.lv > 0) Pal.level(q.lv) else Pal.fg; unit.text = q.unit + if (q.end.isNotEmpty()) " · jusqu'à ${q.end}" else ""; b?.ratio = q.r; b?.color = Pal.level(q.lv) }
        }
    }
}

class ParkInfo(val at: String, val end: String, val limit: Int, val big: String, val unit: String, val lv: Int, val r: Float)

fun MainActivity.parkInfo(): ParkInfo? {
    val at = st.parkAt; if (at == 0L) return null
    val now = System.currentTimeMillis()
    val hm = SimpleDateFormat("HH:mm", Locale.FRANCE)
    fun dur(m: Int) = if (m >= 60) "${m / 60} h ${String.format(Locale.FRANCE, "%02d", m % 60)}" else "$m min"
    val lim = st.parkLimit
    if (lim <= 0) return ParkInfo(hm.format(Date(at)), "", 0, dur(max(0, ((now - at) / 60000).toInt())), "écoulées", 0, 0f)
    val end = at + lim * 60_000L
    val rem = ceil((end - now) / 60000.0).toInt()
    val lv = if (rem <= 0) 2 else if (rem <= 10) 1 else 0
    return ParkInfo(hm.format(Date(at)), hm.format(Date(end)), lim, if (rem <= 0) "+" + dur(-rem) else dur(rem),
        if (rem <= 0) "de dépassement" else "restantes", lv, ((now - at) / (lim * 60_000f)).coerceIn(0f, 1f))
}

fun MainActivity.parkNow() {
    st.parkAt = System.currentTimeMillis()
    toast("Garé à " + SimpleDateFormat("HH:mm", Locale.FRANCE).format(Date()))
    render()
}
