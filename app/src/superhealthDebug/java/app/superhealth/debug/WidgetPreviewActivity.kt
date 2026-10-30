package app.superhealth.debug

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import app.superhealth.CardSize
import app.superhealth.DashCard
import app.superhealth.MainActivity
import app.superhealth.ui.dashboard.DashPreview

/**
 * Version de test seulement : montre des cartes à une taille donnée sans rien écrire (disposition en mémoire, perdue à la
 * fermeture de l'app), utilisable sur un téléphone avec de vraies données.
 * `adb shell am start -n app.superhealth/app.superhealth.debug.WidgetPreviewActivity --es cards "STEPS,HEART:S,SLEEP" --es size M`
 * (taille par carte après « : », sinon `size` ; sans `cards`, toutes les cartes)
 */
class WidgetPreviewActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val default = intent.getStringExtra("size") ?: CardSize.M.name
        val names = intent.getStringExtra("cards")?.split(',')?.map { it.substringBefore(':') to it.substringAfter(':', default) }
            ?: DashCard.entries.map { it.name to default }
        DashPreview.cards = names.mapNotNull { (n, _) -> DashCard.entries.firstOrNull { it.name == n } }
        DashPreview.sizes = names.toMap()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
