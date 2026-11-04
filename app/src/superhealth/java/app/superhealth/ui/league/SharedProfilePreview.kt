package app.superhealth.ui.league

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import app.superhealth.league.*
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.tr
import app.superhealth.ui.trf

/** Preview uses the signed, bounded card that the relay would receive. */
@Composable
fun SharedProfilePreview(nav: NavHostController) {
    val ctx = LocalContext.current
    val state by League.state.collectAsState()
    var card by remember { mutableStateOf<League.Card?>(null) }
    var error by remember { mutableStateOf(false) }
    LaunchedEffect(state.shareIndex, state.sharePillars, state.shareActivities, state.shareProgress, state.shareProfilePhoto,
        state.profileTitle, state.profileBadges, state.profileAvatar) {
        card = null; error = false
        try { card = League.parse(League.myCard(ctx, detailed = true, includeRelayKey = false)); error = card == null }
        catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { error = true }
    }
    DetailScaffold(tr("Voir mon profil comme un ami"), { nav.popBackStack() }) {
        Text(tr("Aperçu de la carte actuellement partagée. Vos publications possèdent leur propre aperçu."))
        val shared = card
        if (shared == null) {
            if (error) Text(tr("Aperçu indisponible. Rouvrez cet écran pour réessayer.")) else LinearProgressIndicator()
        } else {
            Text(shared.n, style = MaterialTheme.typography.headlineSmall)
            ProfileDecor(shared.profileTitle, shared.profileBadges)
            Text(tr(if (shared.avatar != null) "Photo de profil partagée" else "Photo de profil masquée ou indisponible"))
            val index = shared.unified
            val total = if (index != null) index.total else shared.s.takeIf { shared.ready }
            Text(total?.let { trf("Score partagé · %1\$s / 1000", it) } ?: tr(if (state.shareIndex) "Indice indisponible" else "Indice masqué"))
            val parts = index?.parts ?: shared.p
            if (parts.isEmpty()) Text(tr("Composantes de l'indice masquées"))
            else League.Pillar.entries.forEachIndexed { i, pillar ->
                Text(tr(pillar.label) + " · " + (parts.getOrNull(i)?.let { "$it %" } ?: "—"))
            }
            Text(shared.progress?.let { trf("Niveau partagé · %1\$s", it.level) } ?: tr("Progression masquée ou indisponible"))
            if (shared.metrics.isEmpty()) Text(tr("Statistiques sportives masquées ou indisponibles"))
            else shared.metrics.forEach { Text(it.label + " · " + app.superhealth.nutrition.ui.num(it.value, 1) + " " + it.unit) }
        }
    }
}
