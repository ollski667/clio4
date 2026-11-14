package fr.cliohome

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout

/**
 * Notes de version, affichées une fois après chaque mise à jour.
 * À compléter à chaque nouvelle version (code = versionCode de app/build.gradle.kts).
 */
object Notes {
    class Release(val code: Int, val name: String, val news: List<String>, val fixes: List<String> = emptyList())

    val RELEASES = listOf(
        Release(12, "0.9",
            news = listOf(
                "Cette fenêtre : après chaque mise à jour, Clio Home montre les nouveautés et les correctifs.",
                "Réglages → À propos → Nouveautés pour la revoir.",
            )),
        Release(11, "0.8",
            news = listOf(
                "Récapitulatif en PDF : téléchargé directement sur le téléphone, ou enregistré sur clé USB.",
                "Réglages → Température : capteur de la voiture activable (désactivé par défaut, météo à la place).",
            ),
            fixes = listOf(
                "Le volume se règle de nouveau avec les commandes au volant.",
                "Icônes Wi-Fi et Bluetooth mises à jour en direct, barrées quand c'est coupé.",
                "Carburant hors ligne : le badge ne dépasse plus du cadre.",
            )),
        Release(10, "0.7.1",
            fixes = listOf("Le récapitulatif n'est plus coupé entre deux pages à l'impression."),
            news = emptyList()),
        Release(9, "0.7",
            news = listOf(
                "Envoyer au téléphone par Wi-Fi : QR code, récapitulatif, historique, sauvegarde et diagnostic.",
                "Récapitulatif du suivi : échéances, historique, dépenses.",
            )),
        Release(8, "0.6.1",
            news = listOf("Sauvegarde et restauration de toutes les données sur clé USB (Réglages → Système)."),
            fixes = listOf("Le bouton « Je me gare » n'est plus coupé.")),
        Release(7, "0.6",
            news = listOf("Mises à jour par Internet : plus besoin de clé USB (Réglages → Système).")),
    )

    /** Notes des versions installées depuis la dernière vue (au plus les 4 plus récentes). */
    fun since(lastSeen: Int, installed: Int) = RELEASES.filter { it.code in (lastSeen + 1)..installed }.take(4)
}

/** Fenêtre « Quoi de neuf » par-dessus l'écran. releases vide = rien à afficher. */
fun MainActivity.whatsNewOverlay(releases: List<Notes.Release>, onClose: () -> Unit): View {
    val shade = FrameLayout(this).apply {
        setBackgroundColor(Color.argb(170, 0, 0, 0))
        isClickable = true // bloque les touches sur l'écran derrière
    }
    val box = card(14) {
        addView(row(12) {
            addView(icon(R.drawable.ic_check, 26, Pal.accent))
            addView(dtv(if (releases.size == 1) "Nouveautés de la version ${releases[0].name}" else "Nouveautés depuis votre dernière visite", 22, Pal.fg, 600, .08f), lp(0, WRAP, 1f))
        })
        addView(scroll(col(16) {
            for (r in releases) addView(col(8) {
                if (releases.size > 1) addView(label("Version ${r.name}", Pal.accent))
                if (r.news.isNotEmpty()) {
                    addView(tv("Nouveautés", 17, Pal.fg, 600))
                    r.news.forEach { addView(bullet(it, Pal.accent)) }
                }
                if (r.fixes.isNotEmpty()) {
                    addView(tv("Correctifs", 17, Pal.fg, 600))
                    r.fixes.forEach { addView(bullet(it, Pal.ok)) }
                }
            })
        }), lp(MATCH, 0, 1f))
        addView(row { addView(View(context), lp(0, 1, 1f)); addView(btn("C'est parti", R.drawable.ic_check, Btn.ACCENT) { onClose() }) })
    }
    shade.addView(box, FrameLayout.LayoutParams(px(760), px(520), Gravity.CENTER))
    return shade
}

private fun MainActivity.bullet(text: String, color: Int) = row(12) {
    gravity = Gravity.TOP
    addView(View(context).apply { background = rounded(color, 4) }, LinearLayout.LayoutParams(px(8), px(8)).apply { topMargin = px(8) })
    addView(tv(text, 17, Pal.fg).apply { setLineSpacing(0f, 1.15f) }, lp(0, WRAP, 1f))
}
