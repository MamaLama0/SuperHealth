package app.superhealth.ui.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.superhealth.Feature
import app.superhealth.SuperPrefs
import app.superhealth.SuperSettings
import app.superhealth.has
import app.superhealth.ui.tr
import kotlin.math.roundToInt

private data class TourTarget(val owner: Any, val bounds: Rect, val action: () -> Unit)
private val targets = mutableStateMapOf<String, TourTarget>()

/** Measures the real control, so highlights follow text size, handedness and screen dimensions. */
internal fun Modifier.tourTarget(id: String, action: () -> Unit): Modifier = composed {
    val settings by SuperPrefs.settings.collectAsState()
    if (!settings.appTourPending) return@composed this
    val owner = remember { Any() }
    val latest by rememberUpdatedState(action)
    DisposableEffect(id) { onDispose { if (targets[id]?.owner === owner) targets.remove(id) } }
    onGloballyPositioned { coordinates ->
        val bounds = coordinates.boundsInRoot()
        if (bounds.width > 0 && bounds.height > 0 && targets[id]?.let { it.owner === owner && it.bounds == bounds } != true)
            targets[id] = TourTarget(owner, bounds) { latest() }
    }
}

private data class TourStep(val target: String?, val title: String, val text: String, val route: String)

/** Interactive tour: real controls open real screens, and customization remains freely editable. */
@Composable
internal fun AppTour(settings: SuperSettings, onPage: (String) -> Unit, onDone: () -> Unit) {
    val steps = remember(settings.features, settings.leagueEnabled) { buildList {
        add(TourStep("customize", "Un accueil à votre image", "Appuyez sur la zone éclairée pour choisir les cartes de votre accueil.", "dash/edit"))
        add(TourStep(null, "Choisissez vos cartes", "Touchez une carte pour la retirer ou la redimensionner. Les pastilles de la section Ajouter permettent d’en choisir d’autres. Vos choix sont enregistrés.", "home"))
        if (settings.has(Feature.NUTRITION)) add(TourStep("tab:log", "Ouvrez votre journal", "Appuyez sur Journal. Vous y retrouvez vos repas ; le bouton + sert à en ajouter, et Planning prépare les prochains jours.", "log"))
        if (settings.has(Feature.TRAINING) || settings.has(Feature.HEALTH)) add(TourStep("tab:activities", "Découvrez vos activités", "Appuyez sur Activités pour retrouver vos séances et changer de discipline. Une séance en cours peut être reprise.", "activities"))
        if (settings.leagueEnabled) add(TourStep(null, "Bienvenue dans le Groupe",
            "Depuis le tableau de bord, glissez vers la droite pour ouvrir le Groupe. Vous y trouverez les paquets, les quêtes et le fil de vos amis. Glissez vers la gauche pour revenir.",
            "feed"))
        else add(TourStep(null, "Le plugin Groupe", "Installez Groupe dans Plugins pour débloquer les paquets, les quêtes et les duels entre amis.", "modules"))
        add(TourStep("settings", "Retrouvez vos réglages", "Appuyez sur votre profil pour ouvrir les réglages : fonctions, données du coach et sauvegardes.", "settings"))
        add(TourStep(null, "À vous de jouer", "Vous pouvez explorer les réglages et configurer vos sauvegardes. Cette visite reste disponible dans Outils. Votre accueil garde les cartes choisies.", "home"))
    } }
    var step by rememberSaveable { mutableIntStateOf(0) }
    val item = steps[step.coerceIn(0, steps.lastIndex)]
    LaunchedEffect(Unit) { onPage(if (step == 1) "dash/edit" else if (step == steps.lastIndex) "settings" else "home") }
    val target = item.target?.let { targets[it] }
    val density = LocalDensity.current
    val last = step == steps.lastIndex
    fun next() { if (last) onDone() else step++ }
    fun activate() { if (target != null) target.action() else onPage(item.route); next() }
    androidx.activity.compose.BackHandler { onDone() }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (item.target != null && target != null) {
            val rect = target.bounds
            Canvas(Modifier.fillMaxSize().graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }.clickable(onClick = {})) {
                drawRect(Color.Black.copy(alpha = .64f))
                drawRoundRect(Color.Transparent, rect.topLeft, rect.size, CornerRadius(18.dp.toPx()), blendMode = BlendMode.Clear)
                drawRoundRect(Color.White, rect.topLeft, rect.size, CornerRadius(18.dp.toPx()), style = Stroke(3.dp.toPx()))
            }
            Box(Modifier.offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                .size(with(density) { rect.width.toDp() }, with(density) { rect.height.toDp() })
                .semantics { contentDescription = tr(item.title) }
                .clickable(role = Role.Button, onClickLabel = tr(item.title), onClick = ::activate))
        }
        val belowTarget = target != null && target.bounds.center.y < with(density) { maxHeight.toPx() } / 2
        Surface(Modifier.align(if (item.target == null || belowTarget) Alignment.BottomCenter else Alignment.TopCenter)
            .statusBarsPadding().navigationBarsPadding().padding(horizontal = 18.dp, vertical = 18.dp).fillMaxWidth(),
            shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp), shadowElevation = 8.dp) {
            Column(Modifier.heightIn(max = 250.dp).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${step + 1} / ${steps.size} · " + tr(item.title), style = MaterialTheme.typography.titleMedium)
                Text(tr(item.text), style = MaterialTheme.typography.bodyMedium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onDone) { Text(tr("Passer le tour")) }
                    if (item.target == null) TextButton({ if (!last) onPage(item.route.takeIf { it == "feed" || it == "modules" } ?: "home"); next() }) {
                        Text(tr(if (last) "Terminer" else "Continuer la visite"))
                    } else if (target == null) TextButton(::activate) { Text(tr("Ouvrir")) }
                }
            }
        }
    }
}
