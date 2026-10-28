package app.superhealth.ui.health

import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.DirectionsBike
import androidx.compose.material.icons.automirrored.rounded.DirectionsRun
import androidx.compose.material.icons.automirrored.rounded.DirectionsWalk
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Hiking
import androidx.compose.material.icons.rounded.Pool
import androidx.compose.material.icons.rounded.SelfImprovement
import androidx.compose.material.icons.rounded.SportsScore
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.bridge.Bridge
import app.superhealth.bridge.Watch
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.formatDuration
import app.superhealth.ui.localDate
import app.superhealth.ui.relativeDay
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.foundation.layout.height
import app.superhealth.nutrition.ui.components.SettingRow
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.History
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import app.superhealth.ui.trf
import kotlinx.coroutines.launch
import app.superhealth.ui.tr

fun Bridge.Kind.icon(): ImageVector = when (this) {
    Bridge.Kind.STRENGTH -> Icons.Rounded.FitnessCenter
    Bridge.Kind.RUN -> Icons.AutoMirrored.Rounded.DirectionsRun
    Bridge.Kind.WALK -> Icons.AutoMirrored.Rounded.DirectionsWalk
    Bridge.Kind.HIKE -> Icons.Rounded.Hiking
    Bridge.Kind.BIKE -> Icons.AutoMirrored.Rounded.DirectionsBike
    Bridge.Kind.SWIM -> Icons.Rounded.Pool
    Bridge.Kind.YOGA -> Icons.Rounded.SelfImprovement
    Bridge.Kind.OTHER -> Icons.Rounded.SportsScore
}

/** Ligne d'activité commune (musculation, montre, Health Connect), avec la FC moyenne de la montre quand elle existe. */
@Composable
fun ActivityRow(a: Bridge.Activity, horizontal: androidx.compose.ui.unit.Dp = 16.dp, chip: androidx.compose.ui.graphics.Color? = null, onClick: () -> Unit) {
    val hr by produceState<Int?>(null, a.key) { value = a.hc?.avgHr ?: runCatching { Bridge.averageHeartRate(a.start, a.end) }.getOrNull() }
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = horizontal, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        val tint = a.kind.hue()
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(chip ?: tint.container()), contentAlignment = Alignment.Center) {
            Icon(a.kind.icon(), null, Modifier.size(22.dp), tint = tint)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tr(a.title), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(relativeDay(localDate(a.start)), a.distanceM?.takeIf { it >= 100 }?.let { km(it) + " km" },
                formatDuration(a.minutes.toDouble()), a.run?.app ?: tr(a.source.label)).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        hr?.let {
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Rounded.Favorite, null, Modifier.size(14.dp), tint = HealthHues.Heart)
            Text(" $it", style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

fun openActivity(ctx: Context, nav: NavHostController, a: Bridge.Activity) {
    when {
        a.gymId != null -> nav.navigate("workoutdetail/${a.gymId}")
        a.watch != null -> nav.navigate("h/workout/${a.watch.id}")
        a.run != null -> nav.navigate("h/workout/${a.run.id}")
        a.hc != null -> nav.navigate("h/workout/${app.superhealth.bridge.HcWorkouts.idOf(a.hc)}")
        else -> Toast.makeText(ctx, app.superhealth.ui.trf("Activité enregistrée par %1\$s", a.hc?.app ?: "Health Connect"), Toast.LENGTH_SHORT).show()
    }
}

/** Onglet Activités, sous la musculation : course, marche, randonnée… de la montre et de Health Connect. */
@Composable
fun ColumnScope.OtherActivities(vm: SuperViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val refresh by vm.refresh.collectAsState()
    val sh by vm.settings.collectAsState()
    val devices by vm.devices.collectAsState()
    val imported by app.superhealth.bridge.RunImport.runs.collectAsState()
    val list by produceState(emptyList<Bridge.Activity>(), refresh, sh.watchModule, imported) {
        // Les dernières, quel que soit leur âge (historique rapatrié de la montre, pause hivernale…).
        value = runCatching { Bridge.activities(0L) }.getOrDefault(emptyList())
            .filter { it.source != Bridge.Source.GYM && it.kind != Bridge.Kind.STRENGTH } // la musculation a sa propre section
    }
    SectionLabel(tr("Cardio et plein air")) {
        if (list.isNotEmpty()) Text(tr("Tout voir"), Modifier.clickable { nav.navigate("h/activities") }, color = Mf.colors.accent,
            style = MaterialTheme.typography.labelLarge)
    }
    if (list.isEmpty()) MfCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.Watch, null, Modifier.size(28.dp), tint = Mf.colors.accent)
            Spacer(Modifier.width(12.dp))
            Text(when {
                sh.watchModule && devices.isNotEmpty() -> tr("Lancez une activité sur la montre : elle apparaîtra ici après synchronisation.")
                sh.healthConnect -> tr("Les activités de vos autres apps (Health Connect) apparaîtront ici.")
                else -> tr("Associez une montre ou Health Connect pour retrouver vos activités ici.")
            }, style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary)
        }
    } else GroupCard {
        list.take(5).forEachIndexed { i, a ->
            if (i > 0) RowDivider()
            ActivityRow(a) { openActivity(ctx, nav, a) }
        }
    }
    if (sh.watchModule && devices.isNotEmpty()) PerformanceEntry(nav)
    WatchHistoryCard(vm)
    ImportActivitiesCard(vm, nav)
}

/** Rapatrie les activités gardées en mémoire par une montre Garmin (après une réinitialisation ou un nouvel appairage). */
@Composable
fun ColumnScope.WatchHistoryCard(vm: SuperViewModel) {
    val ctx = LocalContext.current
    val devices by vm.devices.collectAsState()
    val garmin = devices.filter { app.superhealth.bridge.Watch.supportsHistory(it.device) }
    if (garmin.isEmpty()) return
    val busy = garmin.any { it.busy }
    // Progression de la récupération (compteurs du moteur Garmin), relue chaque seconde pendant la synchro.
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    if (busy) LaunchedEffect(Unit) {
        while (true) {
            progress = if (nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.GarminSupport.historyRunning) {
                val total = nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.GarminSupport.historyTotal
                (total - nodomain.freeyourgadget.gadgetbridge.service.devices.garmin.GarminSupport.historyRemaining).coerceAtLeast(0) to total
            } else null
            kotlinx.coroutines.delay(1000)
        }
    } else progress = null
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(app.superhealth.ui.theme.CardRadius)).background(Mf.colors.card)
        .clickable(enabled = !busy) {
            val ready = garmin.filter { it.connected }
            if (ready.isEmpty()) {
                garmin.forEach { vm.fetchHistory(it) } // reconnexion d'abord
                Toast.makeText(ctx, tr("Montre déconnectée : reconnexion en cours, réessayez dans un instant."), Toast.LENGTH_LONG).show()
            } else {
                ready.forEach { vm.fetchHistory(it) }
                Toast.makeText(ctx, tr("Récupération lancée : les activités apparaissent au fil du téléchargement."), Toast.LENGTH_LONG).show()
            }
        }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(HealthHues.Fitness.container()), contentAlignment = Alignment.Center) {
            if (busy) androidx.compose.material3.CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp, color = HealthHues.Fitness)
            else Icon(Icons.Rounded.History, null, Modifier.size(22.dp), tint = HealthHues.Fitness)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tr("Récupérer l'historique de la montre"), style = MaterialTheme.typography.bodyLarge)
            val p = progress
            Text(when {
                p != null && p.second > 0 -> trf("Récupération : %1\$s / %2\$s activités", p.first, p.second)
                p != null -> tr("Recherche des activités sur la montre…")
                busy -> tr("Synchronisation en cours…")
                else -> tr("Activités encore stockées sur la montre, même déjà synchronisées")
            }, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
            if (p != null && p.second > 0) androidx.compose.material3.LinearProgressIndicator(progress = { p.first.toFloat() / p.second },
                Modifier.fillMaxWidth().padding(top = 6.dp), color = HealthHues.Fitness, trackColor = HealthHues.Fitness.container())
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Mf.colors.textTertiary)
    }
}

/** Accès aux performances (records, récupération, charge, volume). */
@Composable
fun ColumnScope.PerformanceEntry(nav: NavHostController) {
    Spacer(Modifier.height(10.dp))
    val hue = HealthHues.Mind
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(app.superhealth.ui.theme.CardRadius))
        .background(androidx.compose.ui.graphics.Brush.horizontalGradient(listOf(hue.container(1.4f), HealthHues.Fitness.container(1.1f))))
        .clickable { nav.navigate("h/performance") }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(Mf.colors.card), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.EmojiEvents, null, Modifier.size(22.dp), tint = hue)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tr("Performances"), style = MaterialTheme.typography.bodyLarge, fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold)
            Text(tr("Records, récupération, charge et volume"), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Mf.colors.textTertiary)
    }
}

/** Import d'activités passées : fichiers GPX, TCX, FIT ou export ZIP des apps de running. */
@Composable
fun ColumnScope.ImportActivitiesCard(vm: SuperViewModel, nav: NavHostController) {
    val importer = rememberFitImport(vm, nav)
    Spacer(Modifier.height(10.dp))
    Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(app.superhealth.ui.theme.CardRadius)).background(Mf.colors.card)
        .clickable { importer() }.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).clip(RoundedCornerShape(14.dp)).background(HealthHues.Fitness.container()), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.FileOpen, null, Modifier.size(22.dp), tint = HealthHues.Fitness)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(tr("Importer des activités"), style = MaterialTheme.typography.bodyLarge)
            Text(tr("Strava, Garmin, Polar, Suunto, Coros, Nike… (GPX, TCX, FIT ou ZIP)"), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null, tint = Mf.colors.textTertiary)
    }
}

/**
 * Sélecteur de fichiers + fenêtre de progression puis de résultat. Renvoie l'action qui ouvre le sélecteur.
 * Après l'import, « Voir » ouvre l'historique des activités.
 */
@Composable
fun rememberFitImport(vm: SuperViewModel, nav: NavHostController): () -> Unit {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var progress by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var result by remember { mutableStateOf<Result<app.superhealth.bridge.RunImport.ImportResult>?>(null) }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        progress = 0 to 0
        scope.launch {
            result = runCatching { app.superhealth.bridge.RunImport.import(ctx, uris) { done, total -> progress = done to total } }
            progress = null
            app.superhealth.health.data.HealthRepository.invalidate(); vm.bump()
        }
    }
    progress?.let { (done, total) ->
        AlertDialog(onDismissRequest = {}, containerColor = Mf.colors.card, confirmButton = {},
            title = { Text(tr("Import en cours…")) },
            text = {
                Column {
                    if (total > 0) {
                        LinearProgressIndicator(progress = { done.toFloat() / total }, Modifier.fillMaxWidth(), color = HealthHues.Fitness,
                            trackColor = HealthHues.Fitness.container())
                        Spacer(Modifier.height(8.dp))
                        Text(trf("%1\$s sur %2\$s fichiers", done, total), color = Mf.colors.textSecondary)
                    } else LinearProgressIndicator(Modifier.fillMaxWidth(), color = HealthHues.Fitness, trackColor = HealthHues.Fitness.container())
                }
            })
    }
    result?.let { r ->
        val res = r.getOrNull()
        AlertDialog(onDismissRequest = { result = null }, containerColor = Mf.colors.card,
            title = { Text(tr(if (res != null && res.imported > 0) "Activités importées" else "Import terminé")) },
            text = {
                Text(r.fold({
                    listOfNotNull(
                        trf("%1\$s activité(s) importée(s)", it.imported),
                        it.skipped.takeIf { n -> n > 0 }?.let { n -> trf("%1\$s déjà présente(s)", n) },
                        it.failed.takeIf { n -> n > 0 }?.let { n -> trf("%1\$s fichier(s) illisible(s)", n) },
                    ).joinToString("\n")
                }, { e -> trf("Erreur : %1\$s", e.message) }))
            },
            confirmButton = {
                if (res != null && res.imported > 0) TextButton(onClick = { result = null; nav.navigate("h/activities") }) { Text(tr("Voir"), color = Mf.colors.accent) }
                else TextButton(onClick = { result = null }) { Text(tr("OK"), color = Mf.colors.accent) }
            },
            dismissButton = { if (res != null && res.imported > 0) TextButton(onClick = { result = null }) { Text(tr("Fermer"), color = Mf.colors.text) } })
    }
    return { pick.launch(arrayOf("*/*")) }
}

/** Historique complet de toutes les activités, par mois. */
@Composable
fun ActivitiesHistoryScreen(vm: SuperViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val refresh by vm.refresh.collectAsState()
    val imported by app.superhealth.bridge.RunImport.runs.collectAsState()
    val list by produceState(emptyList<Bridge.Activity>(), refresh, imported) {
        value = runCatching { Bridge.activities(0L) }.getOrDefault(emptyList())
    }
    val import = rememberFitImport(vm, nav)
    DetailScaffold(tr("Toutes les activités"), onBack = { nav.popBackStack() }, action = {
        IconButton(onClick = import) { Icon(Icons.Rounded.FileOpen, tr("Importer des activités")) }
    }) {
        if (list.isEmpty()) MfCard { Text(tr("Aucune activité pour l'instant."), color = Mf.colors.textSecondary) }
        list.groupBy { localDate(it.start).withDayOfMonth(1) }.forEach { (month, items) ->
            SectionLabel(month.format(java.time.format.DateTimeFormatter.ofPattern("LLLL yyyy", app.superhealth.ui.Lang.locale))
                .replaceFirstChar { it.uppercase(app.superhealth.ui.Lang.locale) })
            GroupCard {
                items.forEachIndexed { i, a ->
                    if (i > 0) RowDivider()
                    ActivityRow(a) { openActivity(ctx, nav, a) }
                }
            }
        }
    }
}
