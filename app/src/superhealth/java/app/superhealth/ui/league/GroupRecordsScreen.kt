package app.superhealth.ui.league

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.league.*
import app.superhealth.nutrition.ui.components.*
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.go
import app.superhealth.ui.tr
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun GroupRecordsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val history by GroupHistory.state.collectAsState()
    val friends by League.state.collectAsState()
    var days by remember { mutableStateOf(90) }
    var criterion by remember { mutableStateOf<String?>(null) }
    val posts = remember(history, friends, days) { GroupHistory.posts(ctx).filter { it.first.record?.achieved?.let { at -> at >= System.currentTimeMillis() - days.toLong() * 86400000 } == true } }
    val records = posts.mapNotNull { it.first.record }.distinctBy { it.exercise + ":" + it.metric }
    val chosen = records.firstOrNull { it.exercise + ":" + it.metric == criterion } ?: records.firstOrNull()
    DetailScaffold(tr("PR du Groupe"), { nav.popBackStack() }) {
        Text(tr("Uniquement les records partagés volontairement · même exercice et même mesure"), style = MaterialTheme.typography.bodySmall)
        ChipChoice(listOf(30, 90, 365), days, { "$it " + tr("jours") }) { days = it }
        if (records.isEmpty()) Text(tr("Aucun PR partagé sur cette période"))
        records.forEach { record ->
            FilterChip(selected = chosen?.let { it.exercise == record.exercise && it.metric == record.metric } == true,
                onClick = { criterion = record.exercise + ":" + record.metric }, label = { Text(record.name + " · " + tr(GroupRecords.label(record))) })
        }
        val rows = posts.filter { it.first.record?.let { r -> chosen != null && GroupRecords.comparable(r, chosen) } == true }
            .groupBy { it.second?.key ?: League.publicKey() }.values.map { person -> person.maxBy { it.first.record!!.value } }
            .sortedByDescending { it.first.record!!.value }
        rows.forEachIndexed { index, (post, friend) ->
            val record = post.record!!
            GroupSurface(onClick = { nav.go("group/post/${friend?.id ?: League.myId()}/${post.id}") }) {
                Text("${1 + rows.count { it.first.record!!.value > record.value }}. " + (friend?.shown ?: tr("Vous")), style = MaterialTheme.typography.titleMedium)
                Text(app.superhealth.nutrition.ui.num(record.value, 1) + " " + record.unit, style = MaterialTheme.typography.headlineSmall)
                Text(Instant.ofEpochMilli(record.achieved).atZone(ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("d MMM yyyy", app.superhealth.ui.Lang.locale)))
                if (post.media?.mime?.startsWith("video/") == true) Text(tr("Vidéo jointe à la publication"))
            }
        }
        PillButton(tr("Partager un PR"), Modifier.fillMaxWidth()) { nav.go("social/share/pr") }
    }
}
