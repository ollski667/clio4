package fr.cliohome

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
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

/** Palette jour / nuit. Les couleurs sont opaques (mélangées au fond) pour un dessin plus rapide. */
object Pal {
    var night = false
    var bg = 0; var fg = 0; var muted = 0; var surface = 0; var surface2 = 0; var line = 0; var side = 0
    var ok = 0; var warn = 0; var bad = 0; var accent = 0; var onAccent = Color.WHITE

    fun apply(st: Store, isNight: Boolean) {
        night = isNight
        accent = st.accent
        onAccent = if (lum(accent) > 0.6) 0xFF101317.toInt() else Color.WHITE
        if (isNight) {
            bg = st.bgNight; fg = 0xFFF2F4F7.toInt(); muted = 0xFF8B929C.toInt()
            surface = mix(bg, Color.WHITE, .065f); surface2 = mix(bg, Color.WHITE, .11f)
            line = mix(bg, Color.WHITE, .06f); side = mix(bg, Color.BLACK, .25f)
            ok = 0xFF30D158.toInt(); warn = 0xFFFF9F0A.toInt(); bad = 0xFFFF453A.toInt()
        } else {
            bg = st.bgDay; fg = 0xFF101317.toInt(); muted = 0xFF5F6670.toInt()
            surface = mix(bg, Color.WHITE, .82f); surface2 = mix(bg, 0xFF101317.toInt(), .07f)
            line = mix(bg, 0xFF101317.toInt(), .07f); side = mix(bg, Color.WHITE, .45f)
            ok = 0xFF1E9E48.toInt(); warn = 0xFFC77700.toInt(); bad = 0xFFD92D20.toInt()
        }
    }

    fun level(lv: Int) = when { lv >= 2 -> bad; lv == 1 -> warn; lv < 0 -> muted; else -> ok }

    fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(x: Int, y: Int) = (x + (y - x) * t).roundToInt().coerceIn(0, 255)
        return Color.rgb(ch(Color.red(a), Color.red(b)), ch(Color.green(a), Color.green(b)), ch(Color.blue(a), Color.blue(b)))
    }

    fun lum(c: Int) = (0.299 * Color.red(c) + 0.587 * Color.green(c) + 0.114 * Color.blue(c)) / 255.0
}

fun font(weight: Int): Typeface = when {
    weight <= 250 -> Typeface.create("sans-serif-thin", Typeface.NORMAL)
    weight <= 350 -> Typeface.create("sans-serif-light", Typeface.NORMAL)
    weight <= 450 -> Typeface.create("sans-serif", Typeface.NORMAL)
    weight <= 650 -> Typeface.create("sans-serif-medium", Typeface.NORMAL)
    else -> Typeface.create("sans-serif", Typeface.BOLD)
}

fun rounded(color: Int, radius: Number, stroke: Int = 0, strokeW: Number = 0, dash: Boolean = false) = GradientDrawable().apply {
    setColor(color)
    cornerRadius = px(radius).toFloat()
    if (stroke != 0) {
        if (dash) setStroke(px(strokeW), stroke, px(8).toFloat(), px(6).toFloat()) else setStroke(px(strokeW), stroke)
    }
}

fun spacer(size: Int) = GradientDrawable().apply { setColor(Color.TRANSPARENT); setSize(size, size) }

fun lp(w: Int = MATCH, h: Int = WRAP, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)

fun Context.tv(text: CharSequence = "", size: Number = 16, color: Int = Pal.fg, weight: Int = 400, lines: Int = 0): TextView =
    TextView(this).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size.toFloat() * U.f)
        setTextColor(color)
        typeface = font(weight)
        includeFontPadding = false
        if (lines > 0) { maxLines = lines; ellipsize = TextUtils.TruncateAt.END }
    }

/** Petit libellé en majuscules, comme les titres de widgets de la maquette. */
fun Context.label(text: String, color: Int = Pal.muted) = tv(text.uppercase(), 12.5, color, 600, 1).apply { letterSpacing = 0.07f }

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

/** Carte : fond légèrement détaché, coins arrondis. */
fun Context.card(gap: Number = 12, init: LinearLayout.() -> Unit = {}) = col(gap) {
    background = rounded(Pal.surface, 22, Pal.line, 1)
    setPadding(px(20), px(18), px(20), px(18))
    init()
}

enum class Btn { PLAIN, ACCENT, DANGER, DANGER_ARMED }

fun Context.btn(text: String, iconRes: Int = 0, style: Btn = Btn.PLAIN, height: Number = 46, size: Number = 15, onClick: () -> Unit): LinearLayout {
    val (bgc, fgc) = when (style) {
        Btn.ACCENT -> Pal.accent to Pal.onAccent
        Btn.DANGER -> Pal.surface2 to Pal.bad
        Btn.DANGER_ARMED -> Pal.bad to Color.WHITE
        Btn.PLAIN -> Pal.surface2 to Pal.fg
    }
    return row(8) {
        gravity = Gravity.CENTER
        background = rounded(bgc, 12)
        setPadding(px(18), 0, px(18), 0)
        minimumHeight = px(height)
        if (iconRes != 0) addView(icon(iconRes, 20, fgc))
        addView(tv(text, size, fgc, 600, 1))
        onTap(onClick)
    }
}

/** Groupe de boutons exclusifs (segmented control). */
fun Context.seg(options: List<Pair<String, String>>, selected: String, onPick: (String) -> Unit) = row(2) {
    background = rounded(Pal.surface2, 12)
    setPadding(px(3), px(3), px(3), px(3))
    for ((value, text) in options) {
        val on = value == selected
        addView(tv(text, 14, if (on) Pal.onAccent else Pal.muted, 500, 1).apply {
            gravity = Gravity.CENTER
            setPadding(px(14), 0, px(14), 0)
            minHeight = px(38)
            if (on) background = rounded(Pal.accent, 10)
            onTap { onPick(value) }
        })
    }
}

fun Context.pill(text: String, lv: Int) = tv(text, 13, Pal.level(lv), 600, 1).apply {
    background = rounded(Pal.mix(Pal.surface, Pal.level(lv), if (Pal.night) .18f else .14f), 999)
    setPadding(px(10), px(4), px(10), px(4))
}

fun Context.tag(text: String) = tv(text.uppercase(), 10.5, Pal.muted, 600, 1).apply {
    letterSpacing = 0.05f
    background = rounded(Pal.surface, 6, Pal.line, 1)
    setPadding(px(6), px(2), px(6), px(2))
}

fun Context.field(labelText: String, value: String, type: Int = InputType.TYPE_CLASS_TEXT, hint: String = ""): Pair<LinearLayout, EditText> {
    val e = EditText(this).apply {
        setText(value)
        this.hint = hint
        inputType = type
        setSingleLine(true)
        setTextSize(TypedValue.COMPLEX_UNIT_PX, 17f * U.f)
        setTextColor(Pal.fg)
        setHintTextColor(Pal.muted)
        typeface = font(500)
        background = rounded(Pal.surface2, 12, Pal.line, 1)
        setPadding(px(14), 0, px(14), 0)
        minHeight = px(46)
    }
    val box = col(6) {
        addView(tv(labelText, 13, Pal.muted, 500, 1))
        addView(e, lp())
    }
    return box to e
}

/** Barre de progression simple. */
class BarView(ctx: Context) : View(ctx) {
    var ratio = 0f; set(v) { field = v; invalidate() }
    var color = Pal.ok; set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    override fun onDraw(c: Canvas) {
        val h = height.toFloat()
        p.color = Pal.surface2; r.set(0f, 0f, width.toFloat(), h); c.drawRoundRect(r, h / 2, h / 2, p)
        p.color = color; r.set(0f, 0f, width * ratio.coerceIn(0f, 1f), h); c.drawRoundRect(r, h / 2, h / 2, p)
    }
}

fun Context.bar(ratio: Float, color: Int, height: Number = 6) = BarView(this).apply {
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
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font(300) }
    private val pu = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = font(500) }
    fun weight(w: Int) { p.typeface = font(w); invalidate() }

    override fun onMeasure(w: Int, h: Int) {
        val want = (maxSize * U.f * 1.05f).roundToInt()
        setMeasuredDimension(MeasureSpec.getSize(w), resolveSize(want, h))
    }

    override fun onDraw(c: Canvas) {
        var size = min(maxSize * U.f, height / 1.05f)
        pu.textSize = 20f * U.f
        val unitW = if (unit.isEmpty()) 0f else pu.measureText(unit) + px(6)
        p.textSize = size
        val tw = p.measureText(text)
        if (tw + unitW > width && tw > 0) { size *= max(0.3f, (width - unitW) / tw); p.textSize = size }
        p.color = color
        val total = p.measureText(text) + unitW
        val x = if (align == Gravity.CENTER) (width - total) / 2 else 0f
        val base = height - (height - size) / 2 - size * 0.12f
        c.drawText(text, x, base, p)
        if (unit.isNotEmpty()) { pu.color = Pal.muted; c.drawText(unit, x + p.measureText(text) + px(6), base, pu) }
    }
}

/** Interrupteur on/off. */
class Toggle(ctx: Context, on: Boolean, private val changed: (Boolean) -> Unit) : View(ctx) {
    var on = on; private set
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    init { isClickable = true; setOnClickListener { this.on = !this.on; invalidate(); changed(this.on) } }
    override fun onMeasure(w: Int, h: Int) = setMeasuredDimension(px(52), px(32))
    override fun onDraw(c: Canvas) {
        val h = height.toFloat()
        p.color = if (on) Pal.accent else Pal.surface2
        c.drawRoundRect(0f, 0f, width.toFloat(), h, h / 2, h / 2, p)
        p.color = Color.WHITE
        val r = h / 2 - px(3)
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
        elevation = px(4).toFloat()
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
        b.background = rounded(if (enabled) Pal.accent else Pal.surface2, 20)
        b.elevation = if (enabled) px(4).toFloat() else 0f
        (b.getChildAt(0) as ImageView).setColorFilter(if (enabled) Pal.onAccent else Pal.muted)
    }
}

fun Context.scroll(content: View) = ScrollBox(this, content)
