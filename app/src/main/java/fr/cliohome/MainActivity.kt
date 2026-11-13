package fr.cliohome

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class MainActivity : Activity() {
    lateinit var st: Store
    lateinit var sys: Sys

    // État de l'écran
    var screen = "home"
    var edit = false
    var sel: String? = null
    var mTab = "due"
    var mSel: String? = null
    var logType = "km"
    var armed: String? = null

    /** Petites mises à jour (vitesse, minuteur…) appelées chaque seconde sans tout redessiner. */
    val ticks = mutableListOf<() -> Unit>()

    private val handler = Handler(Looper.getMainLooper())
    private var running = false
    private var tickCount = 0
    private var lastNight: Boolean? = null
    private var compact = false

    private lateinit var frame: FrameLayout
    private var toastView: TextView? = null
    private var banner: View? = null
    private var cpCountdown = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        st = Store(this)
        Fonts.init(assets)
        sys = Sys(this, st)
        sys.onGps = { if (screen == "home" && !edit) runTicks() }
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1)
        } else sys.startGps()
        immersive()
        render()
        // Ouverture automatique de CarPlay juste après le démarrage de l'autoradio
        if (st.autoCP == "boot" && SystemClock.elapsedRealtime() < 5 * 60_000L) startCarplayCountdown()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) sys.startGps()
    }

    /**
     * Plein écran : la barre Android (retour, accueil) reste cachée en haut,
     * un glissement vers le bas la fait apparaître, comme sur l'autoradio.
     */
    @Suppress("DEPRECATION")
    private fun immersive() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or View.SYSTEM_UI_FLAG_FULLSCREEN or
            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_LAYOUT_STABLE)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    override fun onResume() {
        super.onResume()
        running = true
        if (lastNight != isNight()) render()
        handler.post(tick)
        refreshOnline()
    }

    override fun onPause() {
        super.onPause()
        running = false
        handler.removeCallbacks(tick)
    }

    override fun onDestroy() {
        sys.stopGps()
        super.onDestroy()
    }

    /** Bouton Accueil de l'autoradio : on revient sur l'accueil. */
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (screen != "home" || edit) { screen = "home"; edit = false; sel = null; render() }
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when {
            edit -> { edit = false; sel = null; render() }
            screen == "settings" -> go("apps")
            screen != "home" -> go("home")
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        render()
    }

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            runTicks()
            tickCount++
            if (tickCount % 60 == 0 && lastNight != isNight()) render()
            if (tickCount % 300 == 0) refreshOnline()
            handler.postDelayed(this, 1000L)
        }
    }

    fun runTicks() { for (t in ticks) t() }

    private fun refreshOnline() {
        sys.refreshWeather { if (screen == "home") render() }
        sys.refreshFuel { if (screen == "home" || screen == "fuel") render() }
    }

    // ---------- Jour / nuit ----------
    fun isNight(): Boolean = when (st.themeMode) {
        "night" -> true
        "day" -> false
        else -> {
            val sysNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
            sysNight || darkOutside()
        }
    }

    /** Lever / coucher du soleil approximatifs en France, mois par mois (heure locale). */
    private fun darkOutside(): Boolean {
        val c = Calendar.getInstance()
        val h = c.get(Calendar.HOUR_OF_DAY) + c.get(Calendar.MINUTE) / 60f
        val rise = floatArrayOf(8.7f, 8.1f, 7.2f, 7.1f, 6.3f, 5.8f, 6.0f, 6.7f, 7.4f, 8.1f, 7.9f, 8.6f)
        val set = floatArrayOf(17.2f, 18.0f, 18.7f, 20.6f, 21.3f, 21.9f, 21.9f, 21.2f, 20.1f, 19.0f, 17.3f, 16.9f)
        val m = c.get(Calendar.MONTH)
        return h < rise[m] || h >= set[m] + 0.3f
    }

    // ---------- Construction de l'écran ----------
    fun go(s: String) { screen = s; edit = false; sel = null; armed = null; render() }

    fun render() {
        val night = isNight(); lastNight = night
        Pal.apply(st, night)
        Fonts.angular = st.font != "normal"
        // Taille réelle de l'écran : en plein écran, la barre Android ne prend pas de place
        val dm = android.util.DisplayMetrics().also { @Suppress("DEPRECATION") windowManager.defaultDisplay.getRealMetrics(it) }
        U.f = min(dm.widthPixels / 1280f, dm.heightPixels / 720f) * st.ui
        val logicalW = dm.widthPixels / U.f
        val logicalH = dm.heightPixels / U.f
        compact = logicalW < 1060
        ticks.clear()
        window.decorView.setBackgroundColor(Pal.bg)

        val root = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; setBackgroundColor(Pal.bg) }
        root.addView(sidebar(logicalH), LinearLayout.LayoutParams(px(if (compact) 112 else 132), MATCH))
        root.addView(View(this).apply { setBackgroundColor(Pal.line) }, LinearLayout.LayoutParams(max(1, px(1)), MATCH))

        frame = FrameLayout(this)
        val main = col(14) {
            setPadding(px(18), px(18), px(22), px(20))
            addView(statusBar(), lp(MATCH, px(44)))
            val content = when (screen) {
                "maint" -> maintScreen(compact)
                "fuel" -> fuelScreen()
                "park" -> parkScreen(compact)
                "apps" -> appsScreen(compact)
                "settings" -> settingsScreen(compact)
                else -> homeScreen(compact)
            }
            addView(content, lp(MATCH, 0, 1f))
        }
        frame.addView(main, FrameLayout.LayoutParams(MATCH, MATCH))
        toastView = null
        banner = null
        if (cpCountdown > 0) showBanner()
        root.addView(frame, LinearLayout.LayoutParams(0, MATCH, 1f))

        // Barre de lumière en haut de l'écran, avec un léger halo
        val screenBox = FrameLayout(this)
        screenBox.addView(root, FrameLayout.LayoutParams(MATCH, MATCH))
        screenBox.addView(View(this).apply {
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(Pal.glow, 0x00000000))
        }, FrameLayout.LayoutParams(MATCH, px(16)))
        screenBox.addView(View(this).apply { setBackgroundColor(Pal.light) }, FrameLayout.LayoutParams(MATCH, max(2, px(3))))
        setContentView(screenBox)
        runTicks()
    }

    private fun sidebar(logicalH: Float) = col(if (logicalH < 640) 3 else 6) {
        gravity = Gravity.CENTER_HORIZONTAL
        setBackgroundColor(Pal.bg)
        val small = logicalH < 640
        val iconOnly = logicalH < 540
        setPadding(0, px(if (small) 12 else 20), 0, px(if (small) 8 else 14))
        fun nav(id: String, text: String, res: Int, cp: Boolean = false, onClick: () -> Unit) {
            val on = !cp && (screen == id || (id == "apps" && screen == "settings"))
            val fg = if (cp) Pal.onAccent else if (on) Pal.accent else Pal.muted
            val h = if (cp) (if (iconOnly) 64 else if (small) 82 else 100) else (if (iconOnly) 56 else if (small) 68 else 82)
            val item = FrameLayout(context)
            item.addView(col(if (small) 4 else 7) {
                gravity = Gravity.CENTER
                if (cp) background = rounded(Pal.accent, 20)
                addView(icon(res, if (cp) (if (small) 36 else 42) else (if (small) 30 else 34), fg))
                if (!iconOnly) addView(dtv(text, if (cp) 15 else if (small) 13 else 14.5, fg, if (cp) 600 else 500, .14f).apply { gravity = Gravity.CENTER_HORIZONTAL })
            }, FrameLayout.LayoutParams(px(if (cp) 112 else 116), MATCH, Gravity.CENTER_HORIZONTAL))
            if (on) item.addView(View(context).apply { background = rounded(Pal.accent, 2) }, FrameLayout.LayoutParams(px(3), px(h - 32), Gravity.START or Gravity.CENTER_VERTICAL))
            item.onTap(onClick)
            addView(item, LinearLayout.LayoutParams(MATCH, px(h)).apply { if (cp) bottomMargin = px(10) })
        }
        nav("carplay", "CarPlay", R.drawable.ic_carplay, true) { openCarplay() }
        nav("home", "Accueil", R.drawable.ic_home) { go("home") }
        nav("maint", "Suivi", R.drawable.ic_wrench) { go("maint") }
        if (st.sideFuel) nav("fuel", "Carburant", R.drawable.ic_fuel) { go("fuel") }
        if (st.sidePark) nav("park", "Parking", R.drawable.ic_park) { go("park") }
        if (st.sideRadio) nav("radio", "Radio", R.drawable.ic_radio) { openRadio() }
        addView(View(context), lp(1, 0, 1f))
        nav("apps", "Applis", R.drawable.ic_grid) { go("apps") }
    }

    private val titles = mapOf("home" to "Accueil", "maint" to "Suivi", "fuel" to "Carburant", "park" to "Stationnement", "apps" to "Applications", "settings" to "Réglages")

    private fun statusBar() = row(22) {
        if (screen == "settings") addView(smallBtn("‹ Applis", 0) { go("apps") })
        addView(dtv(if (edit) "Modifier l'accueil" else titles[screen] ?: "", if (Fonts.angular) 22 else 23, Pal.fg, if (Fonts.angular) 500 else 600, .22f), lp(0, WRAP, 1f))
        if (screen == "home" && !edit) addView(smallBtn("Modifier", R.drawable.ic_sliders) { edit = true; sel = null; render() })
        if (edit) addView(smallBtn("Terminé", R.drawable.ic_check, true) { edit = false; sel = null; render() })
        addView(icon(R.drawable.ic_bt, 22, Pal.muted).apply { alpha = if (sys.bluetoothOn()) 1f else .35f })
        addView(icon(R.drawable.ic_wifi, 22, Pal.muted).apply { alpha = if (sys.online()) 1f else .35f })
        val vol = dtv("${sys.volume}", 17, Pal.muted, 500, .06f)
        addView(row(6) { addView(icon(R.drawable.ic_vol, 22, Pal.muted)); addView(vol) })
        addView(icon(if (Pal.night) R.drawable.ic_moon else R.drawable.ic_sun, 22, Pal.muted))
        val date = dtv("", 17, Pal.muted, 500, .06f)
        if (!compact) addView(date)
        val time = dtv("", 28, Pal.fg, 400, .06f)
        addView(time)
        val upd = {
            val now = Date()
            time.text = SimpleDateFormat("HH:mm", Locale.FRANCE).format(now)
            val d = SimpleDateFormat("EEE d MMM", Locale.FRANCE).format(now)
            date.text = if (Fonts.angular) d.uppercase(Locale.FRANCE) else d
            vol.text = "${sys.volume}"
        }
        upd(); ticks.add(upd)
    }

    fun smallBtn(text: String, res: Int, accent: Boolean = false, f: () -> Unit) = row(8) {
        background = if (accent) rounded(Pal.accent, 10) else rounded(Pal.surface2, 10, Pal.line, 1)
        setPadding(px(16), 0, px(16), 0)
        if (res != 0) addView(icon(res, 19, if (accent) Pal.onAccent else Pal.fg))
        addView(dtv(text, 15, if (accent) Pal.onAccent else Pal.fg, 500, .12f))
        layoutParams = lp(WRAP, px(42))
        onTap(f)
    }

    // ---------- Messages ----------
    private val hideToast = Runnable { toastView?.visibility = View.GONE }

    fun toast(msg: String) {
        val t = toastView ?: dtv("", 16, Pal.bg, 500, .1f).apply {
            background = rounded(Pal.fg, 14)
            setPadding(px(20), px(12), px(20), px(12))
            frame.addView(this, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL).apply { bottomMargin = px(28) })
            toastView = this
        }
        t.text = if (Fonts.angular) msg.uppercase(Locale.FRANCE) else msg; t.visibility = View.VISIBLE
        handler.removeCallbacks(hideToast); handler.postDelayed(hideToast, 2000L)
    }

    // ---------- CarPlay, radio ----------
    fun openCarplay() {
        stopCountdown()
        val app = sys.carplayApp()
        if (app == null || !sys.launch(app.pkg, app.cls)) {
            toast("ZLink introuvable : choisissez l'appli CarPlay dans Réglages")
            go("settings")
        }
    }

    fun openRadio() {
        val app = sys.radioApp()
        if (app == null || !sys.launch(app.pkg, app.cls)) { toast("Appli radio introuvable : choisissez-la dans Réglages"); go("settings") }
    }

    private val countdown = object : Runnable {
        override fun run() {
            cpCountdown--
            if (cpCountdown <= 0) { removeBanner(); openCarplay() } else { showBanner(); handler.postDelayed(this, 1000L) }
        }
    }

    private fun startCarplayCountdown() { cpCountdown = 5; showBanner(); handler.postDelayed(countdown, 1000L) }

    private fun stopCountdown() { cpCountdown = 0; handler.removeCallbacks(countdown); removeBanner() }

    private fun removeBanner() { banner?.let { frame.removeView(it) }; banner = null }

    private fun showBanner() {
        removeBanner()
        val b = row(14) {
            background = rounded(Pal.fg, 18)
            elevation = px(8).toFloat()
            setPadding(px(20), px(10), px(10), px(10))
            addView(icon(R.drawable.ic_carplay, 24, Pal.bg))
            addView(tv("CarPlay s'ouvre dans $cpCountdown s", 16, Pal.bg, 500, 1))
            addView(tv("Annuler", 15, Pal.bg, 600).apply {
                background = rounded(Pal.mix(Pal.fg, Pal.bg, .2f), 12); gravity = Gravity.CENTER
                setPadding(px(18), 0, px(18), 0); minHeight = px(42)
                onTap { stopCountdown(); toast("Ouverture de CarPlay annulée") }
            })
            addView(btn("Ouvrir", style = Btn.ACCENT, height = 42) { openCarplay() })
        }
        frame.addView(b, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = px(66) })
        banner = b
    }

    // ---------- Photo de la voiture et export ----------
    fun pickPhoto() {
        try {
            startActivityForResult(Intent.createChooser(Intent(Intent.ACTION_GET_CONTENT).setType("image/*"), "Photo de la voiture"), REQ_PHOTO)
        } catch (e: Exception) { toast("Aucune appli pour choisir une image") }
    }

    fun exportCsv() {
        val name = "suivi-clio-" + SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date()) + ".csv"
        try {
            startActivityForResult(Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("text/csv").putExtra(Intent.EXTRA_TITLE, name), REQ_CSV)
        } catch (e: Exception) {
            // Pas de sélecteur de fichiers : on enregistre dans le dossier de l'appli
            val dir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir
            val f = File(dir, name); f.writeText(st.csv())
            toast("Enregistré : " + f.path)
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri: Uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        if (requestCode == REQ_PHOTO) savePhoto(uri)
        if (requestCode == REQ_CSV) {
            try {
                contentResolver.openOutputStream(uri)?.use { it.write(st.csv().toByteArray(Charsets.UTF_8)) }
                toast("Fichier CSV enregistré")
            } catch (e: Exception) { toast("Impossible d'enregistrer le fichier") }
        }
    }

    /** Réduit la photo à 900 px pour économiser la mémoire. */
    private fun savePhoto(uri: Uri) {
        try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, o) }
            var sample = 1
            while (maxOf(o.outWidth, o.outHeight) / (sample * 2) >= 900) sample *= 2
            val bmp = contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return
            val k = min(1f, 900f / maxOf(bmp.width, bmp.height))
            val out = if (k < 1f) Bitmap.createScaledBitmap(bmp, (bmp.width * k).toInt(), (bmp.height * k).toInt(), true) else bmp
            File(filesDir, "car.jpg").outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, 82, it) }
            st.carShow = "photo"
            toast("Photo enregistrée"); render()
        } catch (e: Exception) { toast("Cette image ne peut pas être lue") }
    }

    companion object {
        const val REQ_PHOTO = 10
        const val REQ_CSV = 11
    }
}
