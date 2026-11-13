package fr.cliohome

import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

/**
 * Mini serveur web local : le téléphone, sur le même Wi-Fi, ouvre une page avec le récapitulatif,
 * l'historique (CSV), la sauvegarde et le diagnostic. Il ne tourne que tant que l'écran de partage est ouvert.
 */
object Share {
    private var server: ServerSocket? = null
    var port = 0; private set
    var requests = 0; private set
    var onRequest: (() -> Unit)? = null
    private val main = Handler(Looper.getMainLooper())

    val running get() = server != null

    /** Adresses IPv4 de l'autoradio sur les réseaux locaux (Wi-Fi, point d'accès). */
    fun addresses(): List<String> = try {
        NetworkInterface.getNetworkInterfaces().toList()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { ni -> ni.inetAddresses.toList().filterIsInstance<Inet4Address>().map { it.hostAddress ?: "" } }
            .filter { it.isNotEmpty() }
    } catch (e: Exception) { emptyList() }

    fun start(act: MainActivity) {
        if (server != null) return
        for (p in 8080..8090) {
            try { server = ServerSocket(p); port = p; break } catch (e: Exception) {}
        }
        val srv = server ?: return
        requests = 0
        thread(isDaemon = true) {
            while (!srv.isClosed) {
                val s = try { srv.accept() } catch (e: Exception) { break }
                thread(isDaemon = true) { serve(act, s) }
            }
        }
    }

    fun stop() { try { server?.close() } catch (e: Exception) {}; server = null; onRequest = null }

    private fun serve(act: MainActivity, s: Socket) {
        s.use { sock ->
            try {
                sock.soTimeout = 10_000
                val r = BufferedReader(InputStreamReader(sock.getInputStream()))
                val first = r.readLine() ?: return
                while (true) { val l = r.readLine() ?: break; if (l.isEmpty()) break }
                val path = first.split(" ").getOrNull(1)?.substringBefore('?') ?: "/"
                val day = SimpleDateFormat("yyyy-MM-dd", Locale.FRANCE).format(Date())
                val (type, name, body) = when (path) {
                    "/recap" -> Triple("text/html; charset=utf-8", null, Recap.html(act.st))
                    "/suivi.csv" -> Triple("text/csv; charset=utf-8", "suivi-clio-$day.csv", act.st.csv())
                    "/sauvegarde.json" -> Triple("application/json; charset=utf-8", "clio-home-sauvegarde-$day.json", act.st.exportJson(act.photoBytesPublic()))
                    "/diagnostic.txt" -> Triple("text/plain; charset=utf-8", null, Diag.report(act, act.sys))
                    "/" -> Triple("text/html; charset=utf-8", null, index(act.st))
                    else -> Triple("text/plain; charset=utf-8", null, "Page introuvable")
                }
                val bytes = body.toByteArray(Charsets.UTF_8)
                val head = StringBuilder("HTTP/1.1 ${if (path in setOf("/", "/recap", "/suivi.csv", "/sauvegarde.json", "/diagnostic.txt")) "200 OK" else "404 Not Found"}\r\n")
                    .append("Content-Type: $type\r\nContent-Length: ${bytes.size}\r\nCache-Control: no-store\r\nConnection: close\r\n")
                if (name != null) head.append("Content-Disposition: attachment; filename=\"$name\"\r\n")
                head.append("\r\n")
                val out = sock.getOutputStream()
                out.write(head.toString().toByteArray(Charsets.UTF_8)); out.write(bytes); out.flush()
                requests++
                main.post { onRequest?.invoke() }
            } catch (e: Exception) {}
        }
    }

    private fun index(st: Store) = """<!doctype html><html lang="fr"><head><meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1"><title>Clio Home</title>
<style>
body{margin:0;font:17px/1.5 -apple-system,system-ui,sans-serif;color:#111418;background:#f2f4f6;padding:24px 16px}
.w{max-width:520px;margin:0 auto}h1{font-size:24px;margin:0 0 4px}p{color:#5a6169;margin:0 0 20px}
a{display:block;background:#fff;border:1px solid #d5dae0;border-radius:14px;padding:16px 18px;margin-bottom:12px;color:#111418;text-decoration:none}
a b{display:block;font-size:18px}a span{color:#5a6169;font-size:15px}a.main{background:#0a84ff;border-color:#0a84ff;color:#fff}a.main span{color:#dbeaff}
</style></head><body><div class="w">
<h1>${st.carName} · ${st.plate}</h1><p>Données envoyées par Clio Home depuis l'autoradio.</p>
<a class="main" href="/recap"><b>Récapitulatif du suivi</b><span>Échéances et historique. Pour le garder : Partager → Imprimer → PDF, ou Enregistrer dans Fichiers.</span></a>
<a href="/suivi.csv"><b>Historique (tableur)</b><span>Fichier CSV pour Numbers ou Excel</span></a>
<a href="/sauvegarde.json"><b>Sauvegarde complète</b><span>Pour restaurer les données sur l'autoradio</span></a>
<a href="/diagnostic.txt"><b>Diagnostic</b><span>GPS et infos de la voiture, à envoyer pour le dépannage</span></a>
</div></body></html>"""

    fun qr(text: String, size: Int): Bitmap {
        val m = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, mapOf(EncodeHintType.MARGIN to 1))
        val px = IntArray(size * size) { if (m.get(it % size, it / size)) Color.BLACK else Color.WHITE }
        return Bitmap.createBitmap(px, size, size, Bitmap.Config.RGB_565)
    }
}

/** Écran « Envoyer au téléphone ». */
fun MainActivity.shareScreen(compact: Boolean): View {
    Share.start(this)
    val ips = Share.addresses()
    val url = ips.firstOrNull()?.let { "http://$it:${Share.port}" }
    val status = tv("", 16, Pal.muted)
    fun upd() { status.text = if (Share.requests == 0) "En attente du téléphone…" else "Téléphone connecté · ${Share.requests} page${if (Share.requests > 1) "s" else ""} envoyée${if (Share.requests > 1) "s" else ""}" }
    upd(); Share.onRequest = { upd() }

    val left = card(14) {
        addView(label("Envoyer au téléphone"))
        addView(scroll(col(14) {
            listOf(
                "L'iPhone et l'autoradio doivent être sur le même Wi-Fi : partage de connexion de l'iPhone, ou CarPlay sans fil.",
                "Ouvrez l'appareil photo de l'iPhone et visez le QR code.",
                "Touchez le lien : Safari ouvre une page avec le récapitulatif du suivi, l'historique, la sauvegarde et le diagnostic.",
                "Pour garder le récapitulatif : Partager → Imprimer → PDF, ou Enregistrer dans Fichiers.",
            ).forEachIndexed { i, t ->
                addView(row(12) {
                    gravity = Gravity.TOP
                    addView(dtv("${i + 1}", 18, Pal.accent, 600, 0f), LinearLayout.LayoutParams(px(22), WRAP))
                    addView(tv(t, 17, Pal.fg).apply { setLineSpacing(0f, 1.2f) }, lp(0, WRAP, 1f))
                })
            }
            addView(tv("La page n'existe que tant que cet écran est ouvert. Rien ne passe par Internet.", 15, Pal.muted))
            addView(row { addView(btn("Récapitulatif sur clé USB", R.drawable.ic_download) { saveRecap() }) })
        }), lp(MATCH, 0, 1f))
    }
    val right = card(14) {
        gravity = Gravity.CENTER_HORIZONTAL
        if (url == null) {
            addView(tv("Pas de réseau Wi-Fi. Activez le partage de connexion de l'iPhone, ou connectez CarPlay sans fil, puis revenez sur cet écran.", 18, Pal.warn).apply { gravity = Gravity.CENTER }, lp(MATCH, 0, 1f))
        } else {
            addView(View(context), lp(1, 0, 1f))
            addView(ImageView(context).apply {
                setImageBitmap(Share.qr(url, 400))
                background = rounded(Color.WHITE, 14); setPadding(px(10), px(10), px(10), px(10))
            }, LinearLayout.LayoutParams(px(250), px(250)))
            addView(dtv(url.removePrefix("http://"), 22, Pal.fg, 500, .04f, keepCaps = false).apply { gravity = Gravity.CENTER })
            if (ips.size > 1) addView(tv("Autres adresses : " + ips.drop(1).joinToString(", ") { "$it:${Share.port}" }, 14, Pal.muted).apply { gravity = Gravity.CENTER })
            addView(status.apply { gravity = Gravity.CENTER })
            addView(View(context), lp(1, 0, 1f))
        }
    }
    return if (compact) col(14) { addView(right, lp(MATCH, 0, 1f)); addView(left, lp(MATCH, 0, 1f)) } else row(14) {
        gravity = Gravity.TOP
        addView(left, LinearLayout.LayoutParams(0, MATCH, 1.2f)); addView(right, LinearLayout.LayoutParams(0, MATCH, 1f))
    }
}
