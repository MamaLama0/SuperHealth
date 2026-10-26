package app.superhealth.ui.settings

import app.superhealth.platform.*

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Extension
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RestaurantMenu
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import app.superhealth.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.SuperPrefs
import app.superhealth.modules.Modules
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.components.SettingRow
import app.superhealth.nutrition.ui.components.SwitchRow
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.Lang
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.go
import app.superhealth.ui.openTab
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.launch

/** Couleur et symbole d'un module (sa couleur d'index si elle est donnée). */
internal fun moduleHue(kind: String, color: String?): Color = color?.let { runCatching { parseColor(it) }.getOrNull() }
    ?: when (kind) { Modules.COACH -> Color(0xFF8E7CF0); Modules.MEALS -> Color(0xFFF2994A); Modules.MEDS -> Color(0xFF3FA7D6); else -> Color(0xFF6B9BF2) }

internal fun moduleIcon(kind: String): ImageVector = when (kind) {
    Modules.COACH -> Icons.Rounded.AutoAwesome
    Modules.MEALS -> Icons.Rounded.RestaurantMenu
    Modules.MEDS -> Icons.Rounded.Medication
    else -> Icons.Rounded.Extension
}

/** Ligne des réglages vers le catalogue de modules. */
@Composable
internal fun ModulesRow(nav: NavHostController) {
    val ctx = LocalContext.current
    val installed by Modules.installed.collectAsState()
    val catalog by Modules.catalog.collectAsState()
    val updates = remember(installed, catalog) { Modules.updates(ctx).size }
    SettingRow(tr("Plugins"), subtitle = when {
        updates > 0 -> trf(if (updates > 1) "%1\$s mises à jour disponibles" else "%1\$s mise à jour disponible", updates)
        installed.isEmpty() -> tr("Coach IA, repas, médication…")
        else -> installed.joinToString(" · ") { it.name }
    }, icon = Icons.Rounded.Extension, hue = app.superhealth.ui.theme.HealthHues.Sleep) { nav.go("modules") }
}

/**
 * Modules : catalogue des sources (officielle, puis celles ajoutées), installation animée, activation, mise à jour,
 * options propres à chaque module ; en bas, les sources non vérifiées.
 */
@Composable
fun ModulesScreen(vm: SuperViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val sh by vm.settings.collectAsState()
    val catalog by Modules.catalog.collectAsState()
    val installed by Modules.installed.collectAsState()
    val errors by Modules.sourceErrors.collectAsState()
    val loading by Modules.loading.collectAsState()
    val checked by Modules.lastRefresh.collectAsState()
    val jobs by Modules.jobs.collectAsState()
    LaunchedEffect(sh.moduleSources) { Modules.refresh() }
    val updates = remember(installed, catalog) { Modules.updates(ctx) }
    var adding by remember { mutableStateOf(false) }

    DetailScaffold(tr("Plugins"), onBack = { nav.popBackStack() }, action = {
        IconButton(onClick = { scope.launch { Modules.refresh() } }, enabled = !loading) { Icon(Icons.Rounded.Refresh, tr("Actualiser"), tint = Mf.colors.textSecondary) }
    }) {
        Text(tr("Ajoutez des fonctions à SuperHealth. Un plugin n'apporte que du contenu (images, textes, consignes), jamais de code."),
            style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
        Text(when {
            loading -> tr("Recherche de mises à jour…")
            checked > 0 -> trf("Vérifié à %1\$s", app.superhealth.ui.timeOf(checked))
            else -> ""
        }, style = MaterialTheme.typography.labelMedium, color = Mf.colors.textTertiary, modifier = Modifier.padding(horizontal = 4.dp))
        // plusieurs plugins à mettre à jour : un seul geste
        val pending = updates.filter { jobs[it.module.id] !is Modules.Job.Downloading && jobs[it.module.id] != Modules.Job.Installing }
        AnimatedVisibility(pending.size > 1, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            PillButton(trf("Tout mettre à jour (%1\$s)", pending.size), Modifier.fillMaxWidth().padding(top = 10.dp), icon = Icons.Rounded.Refresh) {
                scope.launch { pending.forEach { Modules.install(ctx, it) } }
            }
        }

        // installés d'abord (même hors ligne), puis ce que proposent les sources
        val ids = (installed.map { it.id } + catalog.map { it.module.id }).distinct()
        if (ids.isEmpty()) MfCard {
            // une réponse HTTP (404 : dépôt privé ou déplacé) n'est pas un problème de connexion
            val http = errors[Modules.OFFICIAL]?.takeIf { it.startsWith("HTTP") }
            Text(when {
                loading -> tr("Lecture du catalogue…")
                http != null -> trf("Catalogue indisponible (%1\$s).", http)
                else -> tr("Catalogue indisponible : vérifiez la connexion.")
            }, color = Mf.colors.textSecondary)
        }
        ids.forEach { id ->
            Spacer(Modifier.height(10.dp))
            ModuleCard(id, catalog.firstOrNull { it.module.id == id }, installed.firstOrNull { it.id == id }, nav)
        }

        app.superhealth.nutrition.ui.components.GroupHeader(tr("Options"), Modifier.padding(start = 0.dp))
        GroupCard {
            SwitchRow(tr("Mises à jour automatiques"), tr("Les plugins installés se mettent à jour seuls, une fois par jour"), sh.modulesAuto) { v ->
                SuperPrefs.update { it.copy(modulesAuto = v) }
            }
        }

        app.superhealth.nutrition.ui.components.GroupHeader(tr("Sources non vérifiées"), Modifier.padding(start = 0.dp))
        GroupCard {
            sh.moduleSources.forEachIndexed { i, src ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(src, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        errors[src]?.let { Text(trf("Erreur : %1\$s", it), style = MaterialTheme.typography.labelSmall, color = app.superhealth.ui.theme.Base.Red) }
                    }
                    IconButton(onClick = { SuperPrefs.update { s -> s.copy(moduleSources = s.moduleSources - src) } }) {
                        Icon(Icons.Rounded.Close, tr("Retirer"), tint = Mf.colors.textSecondary)
                    }
                }
            }
            if (sh.moduleSources.isNotEmpty()) RowDivider()
            SettingRow(tr("Ajouter une source"), subtitle = tr("Adresse d'un index.json (HTTPS)"), icon = Icons.Rounded.Add) { adding = true }
        }
        Text(tr("Une source non vérifiée peut proposer des images ou des textes trompeurs. Les archives sont toujours contrôlées (empreinte) et ne contiennent pas de code."),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp))
        Spacer(Modifier.height(24.dp))
    }

    if (adding) {
        // curseur après « https:// » pour qu'une adresse collée ou tapée le complète
        var field by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue("https://", androidx.compose.ui.text.TextRange(8))) }
        val url = field.text.trim()
        val ok = Modules.acceptable(url) && url.length > 10
        AlertDialog(
            onDismissRequest = { adding = false }, containerColor = Mf.colors.card,
            icon = { Icon(Icons.Rounded.Warning, null, tint = Color(0xFFF2A93B)) },
            title = { Text(tr("Source non vérifiée")) },
            text = {
                Column {
                    Text(tr("Ajoutez seulement une source en qui vous avez confiance."), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(field, { field = it }, singleLine = true, colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Mf.colors.accent, unfocusedBorderColor = Mf.colors.divider))
                }
            },
            confirmButton = {
                TextButton(enabled = ok, onClick = {
                    SuperPrefs.update { s -> if (url in s.moduleSources || url == Modules.OFFICIAL) s else s.copy(moduleSources = s.moduleSources + url) }
                    adding = false
                }) { Text(tr("Ajouter"), color = if (ok) Mf.colors.accent else Mf.colors.textTertiary) }
            },
            dismissButton = { TextButton(onClick = { adding = false }) { Text(tr("Annuler"), color = Mf.colors.text) } },
        )
    }
}

@Composable
private fun ModuleCard(id: String, entry: Modules.Entry?, inst: Modules.Installed?, nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val jobs by Modules.jobs.collectAsState()
    val job = jobs[id]
    val m = entry?.module
    val kind = m?.kind ?: inst?.kind ?: ""
    val hue = moduleHue(kind, m?.color)
    val name = (if (Lang.english) m?.nameEn else null) ?: m?.name ?: inst?.name ?: id
    val summary = (if (Lang.english) m?.summaryEn else null) ?: m?.summary ?: ""
    val update = inst?.let { Modules.update(it) }
    val compatible = m == null || Modules.compatible(ctx, m)
    val busy = job is Modules.Job.Downloading || job is Modules.Job.Installing
    var confirmRemove by remember { mutableStateOf(false) }
    fun install() { entry?.let { e -> scope.launch { Modules.install(ctx, e) } } }

    MfCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ModuleBadge(kind, hue, job, 56.dp) { Modules.jobs.value = Modules.jobs.value - id }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                val facts = listOfNotNull(
                    (inst?.version ?: m?.version)?.let { "v$it" },
                    m?.size?.takeIf { it > 0 && inst == null }?.let { if (it < 1_048_576) trf("%1\$s Ko", app.superhealth.ui.formatNumber(it / 1024.0))
                        else trf("%1\$s Mo", app.superhealth.ui.formatNumber(it / 1_048_576.0, 1)) },
                    if (inst != null && !inst.enabled) tr("désactivé") else null,
                    // installé : place occupée sur le téléphone plutôt que la taille de l'archive
                    inst?.let { remember(it.version) { Modules.diskSize(id) } }?.takeIf { it > 0 }?.let { b ->
                        if (b < 1_048_576) trf("%1\$s Ko", app.superhealth.ui.formatNumber(b / 1024.0)) else trf("%1\$s Mo", app.superhealth.ui.formatNumber(b / 1_048_576.0, 1))
                    },
                )
                Text(facts.joinToString(" · "), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, maxLines = 1)
                if ((entry != null && !entry.verified) || (inst != null && !inst.verified)) Text(tr("Source non vérifiée"),
                    style = MaterialTheme.typography.labelSmall, color = Color(0xFFF2A93B).onContainer(),
                    modifier = Modifier.padding(top = 4.dp).clip(CircleShape).background(Color(0xFFF2A93B).container()).padding(horizontal = 8.dp, vertical = 2.dp))
            }
            if (inst != null && !busy) app.superhealth.nutrition.ui.components.Toggle(inst.enabled, { Modules.setEnabled(ctx, id, it) })
        }
        if (summary.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            Text(summary, style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary)
        }
        (job as? Modules.Job.Failed)?.let {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(trf("Échec : %1\$s", it.message), style = MaterialTheme.typography.bodySmall, color = app.superhealth.ui.theme.Base.Red, modifier = Modifier.weight(1f))
                (update ?: entry)?.let { e -> TextButton(onClick = { scope.launch { Modules.install(ctx, e) } }) { Text(tr("Réessayer"), color = hue.onContainer()) } }
            }
        }
        // nouveautés de la version proposée
        val notes = update?.module?.let { (if (Lang.english) it.changelogEn else null) ?: it.changelog }
        if (notes != null && !busy) Column(Modifier.padding(top = 10.dp).fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(14.dp))
            .background(hue.container()).padding(12.dp)) {
            Text(trf("Nouveautés v%1\$s", update.module.version), style = MaterialTheme.typography.labelLarge, color = hue.onContainer())
            Text(notes, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 2.dp))
        }
        Spacer(Modifier.height(12.dp))
        when {
            busy -> {
                val f = (job as? Modules.Job.Downloading)?.fraction
                Text(if (f != null) trf("Téléchargement… %1\$s %%", (f * 100).toInt()) else tr("Installation…"), style = MaterialTheme.typography.labelLarge,
                    color = hue.onContainer())
            }
            inst == null && !compatible -> Text(tr("Ce plugin demande une version plus récente de SuperHealth."), style = MaterialTheme.typography.bodySmall,
                color = Mf.colors.textSecondary)
            inst == null && entry != null -> PillButton(tr("Installer"), Modifier.fillMaxWidth()) { install() }
            inst != null -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when {
                    update != null && Modules.compatible(ctx, update.module) ->
                        PillButton(trf("Mettre à jour (v%1\$s)", update.module.version), Modifier.weight(1f)) { scope.launch { Modules.install(ctx, update) } }
                    inst.enabled -> PillButton(tr("Ouvrir"), Modifier.weight(1f)) { openModule(kind, nav) }
                }
                PillButton(tr("Désinstaller"), Modifier.weight(1f), strong = false) { confirmRemove = true }
            }
        }
        // réglages propres au module, une fois installé et activé
        AnimatedVisibility(inst?.enabled == true && !busy, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            Column(Modifier.padding(top = 8.dp)) { ModuleOptions(kind, nav) }
        }
    }
    if (confirmRemove) AlertDialog(
        onDismissRequest = { confirmRemove = false }, containerColor = Mf.colors.card,
        title = { Text(trf("Désinstaller %1\$s ?", name)) },
        text = { Text(tr("Son contenu est supprimé du téléphone. Vos données (séances, journal…) ne sont pas touchées : tout revient en le réinstallant.")) },
        confirmButton = { TextButton(onClick = { confirmRemove = false; Modules.uninstall(ctx, id) }) { Text(tr("Désinstaller"), color = app.superhealth.ui.theme.Base.Red) } },
        dismissButton = { TextButton(onClick = { confirmRemove = false }) { Text(tr("Annuler"), color = Mf.colors.text) } },
    )
}

/** Écran principal d'un plugin. */
private fun openModule(kind: String, nav: NavHostController) {
    when (kind) {
        Modules.COACH -> nav.go("coach")
        Modules.MEALS -> nav.openTab("log")
        Modules.MEDS -> nav.go("meds")
    }
}

@Composable
private fun ModuleOptions(kind: String, nav: NavHostController) {
    when (kind) {
        Modules.COACH -> {
            RowDivider()
            SettingRow(tr("Fournisseur IA"), subtitle = tr("Clé et modèle, partagés avec la nutrition")) { nav.go("aisettings") }
        }
        Modules.MEALS -> app.superhealth.ui.meals.MealPlannerOptions(nav)
    }
}

/**
 * Pastille d'un module. Pendant le téléchargement, elle se remplit comme un liquide (vague douce dont le niveau suit la
 * progression) dans un anneau qui avance ; une fois installée, une coche apparaît d'un léger rebond puis laisse la place
 * au symbole. Aucune animation en boucle hors téléchargement.
 */
@Composable
internal fun ModuleBadge(kind: String, hue: Color, job: Modules.Job?, size: Dp, onSettled: () -> Unit = {}) {
    val target = when (job) { is Modules.Job.Downloading -> job.fraction; Modules.Job.Installing, Modules.Job.Done -> 1f; else -> 0f }
    val level by animateFloatAsState(target, tween(900), label = "level")
    val active = job is Modules.Job.Downloading || job is Modules.Job.Installing
    val phase = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) while (true) { phase.snapTo(0f); phase.animateTo(1f, tween(1400, easing = LinearEasing)) }
    }
    val check = remember { Animatable(0f) }
    LaunchedEffect(job) {
        if (job == Modules.Job.Done) {
            check.snapTo(0f)
            check.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow))
            kotlinx.coroutines.delay(1400)
            check.animateTo(0f, tween(400))
            onSettled()
        }
    }
    val iconScale by animateFloatAsState(if (active) 0.82f else 1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "icon")
    val soft = hue.container()
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val d = this.size.minDimension
            val st = d * 0.07f
            val r = d / 2 - st * 1.6f
            val c = Offset(d / 2, d / 2)
            drawCircle(soft, r, c)
            if (level > 0f && (active || check.value > 0f)) {
                // liquide : surface en vague, niveau = progression
                val top = c.y + r - 2 * r * level
                val amp = r * 0.09f * (if (active) 1f else 0f)
                val wave = Path().apply {
                    moveTo(c.x - r, top)
                    var x = -r
                    while (x <= r) {
                        val y = top + amp * sin((x / r * 1.6f + phase.value * 2f) * PI).toFloat()
                        lineTo(c.x + x, y); x += r / 12
                    }
                    lineTo(c.x + r, c.y + r); lineTo(c.x - r, c.y + r); close()
                }
                clipPath(Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }) { drawPath(wave, hue.copy(alpha = 0.45f)) }
            }
            if (active || check.value > 0f) {
                drawArc(hue.copy(alpha = 0.18f), 0f, 360f, false, Offset(st / 2, st / 2), Size(d - st, d - st), style = Stroke(st))
                drawArc(hue, -90f, 360f * level, false, Offset(st / 2, st / 2), Size(d - st, d - st), style = Stroke(st, cap = StrokeCap.Round))
            }
            if (check.value > 0f) drawCircle(hue, r * check.value.coerceAtMost(1.05f), c)
        }
        val showCheck = check.value > 0.3f
        Icon(if (showCheck) Icons.Rounded.Check else moduleIcon(kind), null, Modifier.size(size * 0.42f).scale(if (showCheck) check.value else iconScale),
            tint = if (showCheck) Color.White else hue)
    }
}
