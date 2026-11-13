package fr.cliohome

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.AssetManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/** Unité de dessin : 1 = 1 pixel de la maquette 1280×720, multiplié par la taille d'interface choisie. */
object U {
    var f = 1f
    fun px(v: Number): Int = (v.toFloat() * f).roundToInt()
}

fun px(v: Number) = U.px(v)

/**
 * Polices. « Anguleuse » : Chakra Petch (fournie avec l'appli) en majuscules espacées pour les titres.
 * « Normale » : la police du système, sans majuscules forcées.
 */
object Fonts {
    var angular = true
    private val cache = HashMap<String, Typeface>()
    private var assets: AssetManager? = null

    fun init(a: AssetManager) { assets = a }

    private fun chakra(weight: Int): Typeface {
        val name = when { weight <= 350 -> "Light"; weight <= 450 -> "Regular"; weight <= 550 -> "Medium"; else -> "SemiBold" }
        return cache.getOrPut(name) {
            try { Typeface.createFromAsset(assets, "fonts/ChakraPetch-$name.ttf") } catch (e: Exception) { font(weight) }
        }
    }

    /** Police des titres, chiffres et boutons. */
    fun display(weight: Int): Typeface = if (angular) chakra(weight) else font(if (weight <= 350) 300 else weight + 100)
}

fun font(weight: Int): Typeface = when {
    weight <= 250 -> Typeface.create("sans-serif-thin", Typeface.NORMAL)
    weight <= 350 -> Typeface.create("sans-serif-light", Typeface.NORMAL)
    weight <= 450 -> Typeface.create("sans-serif", Typeface.NORMAL)
    weight <= 650 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
    else -> Typeface.create("sans-serif", Typeface.BOLD)
}

/** Palette jour / nuit. */
object Pal {
    var night = true
    var bg = 0; var fg = 0; var muted = 0; var dim = 0; var surface = 0; var surface2 = 0; var line = 0
    var ok = 0; var warn = 0; var bad = 0; var accent = 0; var onAccent = Color.WHITE; var light = 0; var glow = 0

    fun apply(st: Store, isNight: Boolean) {
        night = isNight
        accent = st.accent
        onAccent = if (lum(accent) > 0.6) 0xFF0B0C0E.toInt() else Color.WHITE
        if (isNight) {
            bg = 0xFF08090A.toInt(); fg = 0xFFE8EBEE.toInt(); muted = 0xFF7A8189.toInt(); dim = 0xFF454B52.toInt()
            surface = 0xFF191C20.toInt(); surface2 = 0xFF22262B.toInt(); line = 0xFF2A2F35.toInt()
            ok = 0xFF62D18E.toInt(); warn = 0xFFF0A43C.toInt(); bad = 0xFFFF4B3E.toInt()
            light = 0xFFF3F6F9.toInt(); glow = 0x55E1ECF7
        } else {
            bg = 0xFFEBEEF1.toInt(); fg = 0xFF0E1012.toInt(); muted = 0xFF5A6169.toInt(); dim = 0xFFB4BAC0.toInt()
            surface = 0xFFFFFFFF.toInt(); surface2 = 0xFFF1F3F5.toInt(); line = 0xFFC9D0D6.toInt()
            ok = 0xFF1C7E41.toInt(); warn = 0xFF9A5B00.toInt(); bad = 0xFFC2271A.toInt()
            light = 0xFF0E1012.toInt(); glow = 0x330E1012
        }
    }

    fun level(lv: Int) = when { lv >= 2 -> bad; lv == 1 -> warn; lv < 0 -> muted; else -> ok }

    fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(x: Int, y: Int) = (x + (y - x) * t).roundToInt().coerceIn(0, 255)
        return Color.rgb(ch(Color.red(a), Color.red(b)), ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b)))
    }

    fun lum(c: Int) = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0
}

fun rounded(color: Int, radius: Number, stroke: Int = 0, strokeW: Number = 0, dash: Boolean = false) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = px(radius).toFloat()
    if (stroke != 0) {
        val w = max(1, px(strokeW))
        if (dash) setStroke(w, stroke, px(8).toFloat(), px(6).toFloat()) else setStroke(w, stroke)
    }
}

fun spacer(size: Int) = GradientDrawable().apply { setColor(Color.TRANSPARENT); setSize(size, size) }

fun lp(w: Int = MATCH, h: Int = WRAP, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

/** Texte courant (police du système). */
fun Context.tv(text: CharSequence = "", size: Number = 17, color: Int = Pal.fg, weight: Int = 400, lines: Int = 0): TextView =
    TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size.toFloat() * U.f)
        setTextColor(color)
        typeface = font(weight)
        includeFontPadding = false
        if (lines > 0) { maxLines = lines; ellipsize = TextUtils.TruncateAt.END }
    }

/**
 * Texte « titre » : police anguleuse en majuscules espacées, ou police normale sans majuscules.
 * keepCaps : garde les majuscules même en police normale (petits libellés).
 */
fun Context.dtv(text: String, size: Number, color: Int = Pal.fg, weight: Int = 500, spacing: Float = .12f, lines: Int = 1, keepCaps: Boolean = false): TextView {
    val caps = Fonts.angular || keepCaps
    return tv(if (caps) text.uppercase(Locale.FRANCE) else text, size, color, weight, lines).apply {
        typeface = Fonts.display(weight)
        letterSpacing = if (Fonts.angular) spacing else if (keepCaps) .07f else 0f
    }
}

/** Petit libellé en majuscules (titres de widgets). */
fun Context.label(text: String, color: Int = Pal.muted) = dtv(text, 14, color, 500, .16f, keepCaps = true)

fun Context.col(gap: Number = 0, init: LinearLayout.() -> Unit = {}) = LinearLayout(this).apply {
    orientation = LinearLayout.VERTICAL
    if (gap.toFloat() > 0) { dividerDrawable = spacer(px(gap)); showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE }
    init()
}

fun Context.row(gap: Number = 0, init: LinearLayout.() -> Unit = {}) = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
    if (gap.toFloat() > 0) { dividerDrawable = spacer(px(gap)); showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE }
    init()
}

fun Context.icon(res: Int, size: Number = 22, color: Int = Pal.fg) = ImageView(this).apply {
    setImageResource(res)
    setColorFilter(color)
    scaleType = ImageView.ScaleType.FIT_CENTER
    layoutParams = LinearLayout.LayoutParams(px(size), px(size))
}

fun View.onTap(f: () -> Unit): View { isClickable = true; setOnClickListener { f() }; return this }

/** Panneau : fond uni, coins arrondis, fin contour. */
fun panelBg() = rounded(Pal.surface, 20, Pal.line, 1)

fun Context.card(gap: Number = 12, init: LinearLayout.() -> Unit = {}) = col(gap) {
    background = panelBg()
    setPadding(px(20), px(18), px(20), px(18))
    init()
}

enum class Btn { PLAIN, ACCENT, DANGER, DANGER_ARMED }

fun Context.btn(text: String, iconRes: Int = 0, style: Btn = Btn.PLAIN, height: Number = 48, size: Number = 16, onClick: () -> Unit): LinearLayout {
    val fgc = when (style) { Btn.ACCENT -> Pal.onAccent; Btn.DANGER -> Pal.bad; Btn.DANGER_ARMED -> Color.WHITE; Btn.PLAIN -> Pal.fg }
    return row(9) {
        gravity = Gravity.CENTER
        background = when (style) {
            Btn.ACCENT -> rounded(Pal.accent, 12)
            Btn.DANGER_ARMED -> rounded(Pal.bad, 12)
            else -> rounded(Pal.surface2, 12, Pal.line, 1)
        }
        setPadding(px(20), 0, px(20), 0)
        minimumHeight = px(height)
        if (iconRes != 0) addView(icon(iconRes, 20, fgc))
        addView(dtv(text, size, fgc, 600, .14f))
        onTap(onClick)
    }
}

/** Puce de choix (filtres, options). */
fun Context.chip(text: String, on: Boolean = false, f: () -> Unit) = dtv(text, 15, if (on) Pal.onAccent else Pal.fg, 500, .12f).apply {
    gravity = Gravity.CENTER
    background = if (on) rounded(Pal.accent, 10) else rounded(Pal.surface2, 10, Pal.line, 1)
    setPadding(px(16), 0, px(16), 0)
    minHeight = px(42)
    onTap(f)
}

/** Choix exclusif sous forme de puces. */
fun Context.seg(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) = row(8) {
    for ((value, text) in options) addView(chip(text, value == selected) { onPick(value) })
}

/** Pastille d'état : contour de la couleur du niveau. */
fun Context.pill(text: String, lv: Int) = tv(text, 14.5, Pal.level(lv), 500, 1).apply {
    typeface = Fonts.display(500); letterSpacing = if (Fonts.angular) .06f else 0f
    background = rounded(Color.TRANSPARENT, 4, Pal.level(lv), 1)
    setPadding(px(9), px(3), px(9), px(3))
}

fun Context.tag(text: String) = dtv(text, 11.5, Pal.muted, 500, .16f, keepCaps = true).apply {
    background = rounded(Color.TRANSPARENT, 3, Pal.dim, 1)
    setPadding(px(6), px(2), px(6), px(2))
}

fun Context.field(labelText: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT, hint: String = ""): Pair<LinearLayout, EditText> {
    val e = EditText(this).apply {
        setText(value)
        this.hint = hint
        inputType = type
        setSingleLine(true)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 19f * U.f)
        setTextColor(Pal.fg)
        setHintTextColor(Pal.muted)
        typeface = font(500)
        background = rounded(Pal.surface2, 12, Pal.line, 1)
        setPadding(px(14), 0, px(14), 0)
        minHeight = px(50)
    }
    val box = col(6) {
        addView(tv(labelText, 15, Pal.muted, 500, 1))
        addView(e, lp())
    }
    return box to e
}

/** Jauge en segments inclinés. */
class BarView(ctx: Context) : View(ctx) {
    var ratio = 0f; set(v) { field = v; invalidate() }
    var color = Pal.ok; set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    override fun onDraw(c: Canvas) {
        val h = height.toFloat(); if (h <= 0f || width <= 0) return
        val gap = max(2f, h * .3f)
        val pitch = max(px(11).toFloat(), h * 1.6f)
        val n = max(6, (width / pitch).toInt())
        val cw = (width - gap * (n - 1)) / n
        val skew = h * .45f
        val on = (ratio.coerceIn(0f, 1f) * n).roundToInt()
        val off = Pal.mix(Pal.surface, Pal.dim, if (Pal.night) .55f else .5f)
        for (i in 0 until n) {
            val x = i * (cw + gap)
            path.reset()
            path.moveTo(x + skew, 0f); path.lineTo(x + cw, 0f); path.lineTo(x + cw - skew, h); path.lineTo(x, h); path.close()
            p.color = if (i < on) color else off
            c.drawPath(path, p)
        }
    }
}

fun Context.bar(ratio: Float, color: Int, height: Number = 7) = BarView(this).apply {
    this.ratio = ratio; this.color = color
    layoutParams = LinearLayout.LayoutParams(MATCH, px(height))
}

/** Grand texte qui se réduit tout seul pour tenir dans sa place (chiffres des widgets). */
class BigText(ctx: Context) : View(ctx) {
    var text = ""; set(v) { if (field != v) { field = v; invalidate() } }
    var unit = ""; set(v) { field = v; invalidate() }
    var maxSize = 80f
    var color = Pal.fg; set(v) { field = v; invalidate() }
    var align = Gravity.START
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.display(300) }
    private val pu = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Fonts.display(500); letterSpacing = if (Fonts.angular) .14f else 0f }

    fun weight(w: Int) { p.typeface = Fonts.display(w); invalidate() }
    fun spacing(s: Float) { p.letterSpacing = s; invalidate() }

    override fun onMeasure(w: Int, h: Int) {
        val want = (maxSize * U.f * 1.05f).roundToInt()
        setMeasuredDimension(MeasureSpec.getSize(w), resolveSize(want, h))
    }

    override fun onDraw(c: Canvas) {
        var size = min(maxSize * U.f, height / 1.05f)
        pu.textSize = 20f * U.f
        val u = if (Fonts.angular) unit.uppercase(Locale.FRANCE) else unit
        val unitW = if (u.isEmpty()) 0f else pu.measureText(u) + px(8)
        p.textSize = size
        val tw = p.measureText(text)
        if (tw + unitW > width && tw > 0) { size *= max(0.3f, (width - unitW) / tw); p.textSize = size }
        p.color = color
        val total = p.measureText(text) + unitW
        val x = if (align == Gravity.CENTER) (width - total) / 2 else 0f
        val base = height - (height - size) / 2 - size * 0.12f
        c.drawText(text, x, base, p)
        if (u.isNotEmpty()) { pu.color = Pal.muted; c.drawText(u, x + p.measureText(text) + px(8), base, pu) }
    }
}

/** Interrupteur on/off. */
class Toggle(ctx: Context, on: Boolean, private val changed: (Boolean) -> Unit) : View(ctx) {
    var on = on; private set
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    init { isClickable = true; setOnClickListener { this.on = !this.on; invalidate(); changed(this.on) } }
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(px(54), px(32))
    override fun onDraw(c: Canvas) {
        val h = height.toFloat()
        p.color = if (on) Pal.accent else Pal.surface2
        c.drawRoundRect(0f, 0f, width.toFloat(), h, h / 2, h / 2, p)
        p.color = if (on) Color.WHITE else Pal.muted
        val r = h / 2 - px(4)
        c.drawCircle(if (on) width - h / 2 else h / 2, h / 2, r, p)
    }
}

/**
 * Zone qui défile, avec deux gros boutons ▲ ▼ à droite.
 * Les boutons n'apparaissent que si le contenu dépasse.
 */
@SuppressLint("ViewConstructor")
class ScrollBox(ctx: Context, val content: View) : FrameLayout(ctx) {
    private val sv = ScrollView(ctx).apply {
        isVerticalScrollBarEnabled = false
        overScrollMode = OVER_SCROLL_NEVER
        isFillViewport = false
        addView(content, LayoutParams(MATCH, WRAP))
    }
    private val up = arrow(R.drawable.ic_up, -1)
    private val dn = arrow(R.drawable.ic_down, 1)
    private var padded = false

    init {
        addView(sv, LayoutParams(MATCH, MATCH))
        addView(up, LayoutParams(px(64), px(64), Gravity.TOP or Gravity.END).apply { topMargin = px(4); rightMargin = px(2) })
        addView(dn, LayoutParams(px(64), px(64), Gravity.BOTTOM or Gravity.END).apply { bottomMargin = px(4); rightMargin = px(2) })
        up.visibility = GONE; dn.visibility = GONE
        sv.viewTreeObserver.addOnScrollChangedListener { refresh() }
        sv.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> post { refresh() } }
    }

    private fun arrow(res: Int, dir: Int) = FrameLayout(context).apply {
        val ic = context.icon(res, 34, Pal.onAccent)
        addView(ic, LayoutParams(px(34), px(34), Gravity.CENTER))
        background = rounded(Pal.accent, 20)
        isClickable = true
        setOnClickListener { sv.scrollBy(0, dir * (sv.height * 0.8f).roundToInt()) }
    }

    private fun refresh() {
        val can = content.height > sv.height + 2
        if (can && !padded) { padded = true; sv.setPadding(0, 0, px(78), 0) }
        up.visibility = if (can) VISIBLE else GONE
        dn.visibility = if (can) VISIBLE else GONE
        if (!can) return
        val atTop = sv.scrollY <= 1
        val atBottom = sv.scrollY + sv.height >= content.height + sv.paddingBottom - 1
        style(up, !atTop); style(dn, !atBottom)
    }

    private fun style(b: FrameLayout, enabled: Boolean) {
        b.background = if (enabled) rounded(Pal.accent, 20) else rounded(Pal.surface2, 20, Pal.line, 1)
        (b.getChildAt(0) as ImageView).setColorFilter(if (enabled) Pal.onAccent else Pal.muted)
    }
}

fun Context.scroll(content: View) = ScrollBox(this, content)
