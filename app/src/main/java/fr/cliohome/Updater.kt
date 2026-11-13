package fr.cliohome

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

/**
 * Mises à jour par Internet : GitHub construit l'APK à chaque modification et publie
 * ClioHome.apk + version.json dans la version « derniere-version » du dépôt.
 */
object Updater {
    private const val BASE = "https://github.com/ollski667/clio4/releases/download/derniere-version/"
    const val PAGE = "https://github.com/ollski667/clio4/releases/tag/derniere-version"

    class Info(val code: Int, val name: String)

    /** Dernière version trouvée en ligne (null = pas encore vérifié ou pas d'Internet). */
    var latest: Info? = null
    var lastCheck = 0L
    private val main = Handler(Looper.getMainLooper())

    fun installedCode(ctx: Context): Int = try {
        @Suppress("DEPRECATION")
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionCode
    } catch (e: Exception) { 0 }

    fun hasUpdate(ctx: Context) = (latest?.code ?: 0) > installedCode(ctx)

    /** Vérifie la version en ligne. done(null) = vérification impossible (pas d'Internet). */
    fun check(done: (Info?) -> Unit) {
        thread {
            val info = try {
                val c = URL(BASE + "version.json").openConnection() as HttpURLConnection
                c.connectTimeout = 8000; c.readTimeout = 10000; c.instanceFollowRedirects = true
                c.setRequestProperty("User-Agent", "ClioHome")
                if (c.responseCode == 200) {
                    val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                    Info(o.getInt("versionCode"), o.getString("versionName"))
                } else null
            } catch (e: Exception) { null }
            main.post { if (info != null) { latest = info; lastCheck = System.currentTimeMillis() }; done(info) }
        }
    }

    /** Android demande une fois d'autoriser Clio Home à installer des applis. */
    fun canInstall(ctx: Context) = Build.VERSION.SDK_INT < 26 || ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        try {
            ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + ctx.packageName)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: Exception) { ctx.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    /**
     * Télécharge l'APK puis ouvre l'écran d'installation d'Android.
     * status reçoit des messages à afficher ; en cas d'échec, on ouvre la page de téléchargement dans le navigateur.
     */
    fun downloadAndInstall(ctx: Context, status: (String) -> Unit) {
        val dm = ctx.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
        if (dm == null) { openInBrowser(ctx); status("Téléchargement dans le navigateur"); return }
        try {
            File(ctx.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "ClioHome.apk").delete()
            val req = DownloadManager.Request(Uri.parse(BASE + "ClioHome.apk"))
                .setTitle("Clio Home").setDescription("Mise à jour")
                .setMimeType("application/vnd.android.package-archive")
                .setDestinationInExternalFilesDir(ctx, Environment.DIRECTORY_DOWNLOADS, "ClioHome.apk")
            val id = dm.enqueue(req)
            status("Téléchargement de la mise à jour…")
            val rec = object : BroadcastReceiver() {
                override fun onReceive(c: Context, i: Intent) {
                    if (i.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1) != id) return
                    try { ctx.unregisterReceiver(this) } catch (e: Exception) {}
                    val uri = dm.getUriForDownloadedFile(id)
                    if (uri == null) { status("Téléchargement échoué : vérifiez la connexion"); return }
                    try {
                        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
                    } catch (e: Exception) { openInBrowser(ctx); status("Installation impossible ici : ouverture du navigateur") }
                }
            }
            @Suppress("UnspecifiedRegisterReceiverFlag")
            ctx.registerReceiver(rec, IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE))
        } catch (e: Exception) { openInBrowser(ctx); status("Téléchargement dans le navigateur") }
    }

    fun openInBrowser(ctx: Context) {
        try { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(BASE + "ClioHome.apk")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (e: Exception) {}
    }
}
