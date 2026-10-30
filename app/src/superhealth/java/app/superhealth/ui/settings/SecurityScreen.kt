package app.superhealth.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.ui.draw.clip
import app.superhealth.ui.theme.container
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.SuperPrefs
import app.superhealth.nutrition.ui.components.ChipChoice
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.GroupHeader
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.SwitchRow
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.security.AppLock
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr

/** Réglages > Sécurité : verrouillage par le système (empreinte, visage ou code du téléphone). */
@Composable
fun SecurityScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val activity = remember(ctx) {
        generateSequence(ctx) { (it as? android.content.ContextWrapper)?.baseContext }.firstNotNullOfOrNull { it as? android.app.Activity }
    }
    val sh by SuperPrefs.settings.collectAsState()
    val available = remember { AppLock.available(ctx) }
    var error by remember { mutableStateOf<String?>(null) }

    DetailScaffold(tr("Sécurité"), onBack = { nav.popBackStack() }) {
        GroupCard {
            SwitchRow(tr("Verrouiller SuperHealth"), tr("Empreinte, visage ou code du téléphone à l'ouverture"), sh.appLock) { on ->
                error = null
                when {
                    !on -> { SuperPrefs.update { it.copy(appLock = false) }; AppLock.disable() }
                    !available -> error = tr("Définissez d'abord un verrouillage d'écran dans les réglages du téléphone.")
                    // activer demande une première vérification : on ne peut pas s'enfermer dehors
                    activity != null -> AppLock.authenticate(activity) { ok -> if (ok) SuperPrefs.update { it.copy(appLock = true) } }
                }
            }
            if (sh.appLock) {
                RowDivider()
                Column(Modifier.padding(16.dp)) {
                    Text(tr("Redemander après"), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(10.dp))
                    ChipChoice(AppLock.DELAYS, sh.lockDelaySec.takeIf { it in AppLock.DELAYS } ?: 0, { delayLabel(it) }) { d ->
                        SuperPrefs.update { it.copy(lockDelaySec = d) }
                    }
                }
            }
        }
        error?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
        }
        Spacer(Modifier.height(12.dp))
        GroupCard {
            SwitchRow(tr("Masquer dans les apps récentes"), tr("Aperçu caché et captures d'écran bloquées"), sh.secureScreen) { on ->
                SuperPrefs.update { it.copy(secureScreen = on) }
            }
        }
        Text(tr("C'est le téléphone qui vérifie : SuperHealth ne voit ni votre empreinte ni votre code."),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp))
        Spacer(Modifier.height(24.dp))
    }
}

fun delayLabel(sec: Int): String = when (sec) {
    0 -> tr("Aussitôt")
    else -> "${sec / 60} min"
}

/** Écran posé sur l'app tant qu'elle est verrouillée. */
@Composable
fun LockScreen(onBack: () -> Unit, onUnlock: () -> Unit) {
    // retour : l'app passe en arrière-plan, sans toucher aux écrans cachés dessous
    androidx.activity.compose.BackHandler(onBack = onBack)
    Box(Modifier.fillMaxSize().background(Mf.colors.background)
        // rien ne passe aux écrans dessous
        .clickable(remember { MutableInteractionSource() }, null) {}, contentAlignment = Alignment.Center) {
        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(Modifier.size(96.dp).clip(CircleShape).background(Mf.colors.accent.container(1.4f)), contentAlignment = Alignment.Center) {
                androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.Lock, null, Modifier.size(44.dp), tint = Mf.colors.accent)
            }
            Spacer(Modifier.height(24.dp))
            Text(tr("SuperHealth est verrouillé"), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            Spacer(Modifier.height(6.dp))
            Text(tr("Empreinte, visage ou code du téléphone"), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary,
                textAlign = TextAlign.Center)
        }
        PillButton(tr("Déverrouiller"), Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(24.dp), onClick = onUnlock)
    }
}
