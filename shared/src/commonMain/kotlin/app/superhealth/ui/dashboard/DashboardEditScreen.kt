package app.superhealth.ui.dashboard

import app.superhealth.platform.*

import app.superhealth.available
import app.superhealth.feature
import app.superhealth.has
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import app.superhealth.sizes
import app.superhealth.sizeOf
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.foundation.border
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.DashCard
import app.superhealth.PlusAction
import app.superhealth.plusActions
import app.superhealth.SuperPrefs
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr

/**
 * Personnalisation, en un seul panneau : l'aperçu de la grille est l'outil. Toucher un bloc le sélectionne, une petite
 * barre dessous règle sa taille, le déplace ou le retire ; les cartes masquées s'ajoutent d'une touche (pastilles).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DashboardEditScreen(vm: SuperViewModel, nav: NavHostController) {
    val sh by vm.settings.collectAsState()
    val shown = sh.dashboard
    fun set(list: List<DashCard>) = SuperPrefs.update { it.copy(dashboard = list) }
    var selected by remember { mutableStateOf<DashCard?>(null) }
    var reorder by remember { mutableStateOf(false) }
    if (reorder) app.superhealth.training.ui.components.ReorderSheet(shown.map { it.name to null }, shown.map { null },
        onMove = { from, to -> set(with(app.superhealth.training.domain.ProgramEdit) { SuperPrefs.settings.value.dashboard.moved(from, to) }) },
        onDismiss = { reorder = false }, title = tr("Ordre des cartes"), names = shown.map { tr(it.label) })
    DetailScaffold(tr("Personnaliser"), onBack = { nav.popBackStack() }, action = {
        TextButton(onClick = { set(DashCard.defaults); selected = null }) { Text(tr("Par défaut"), color = Mf.colors.accent, maxLines = 1) }
    }) {
        SectionLabel(tr("Tableau de bord")) {
            if (shown.size > 1) Text(tr("Réorganiser"), Modifier.clickable { reorder = true }, color = Mf.colors.accent, style = MaterialTheme.typography.labelLarge)
        }
        if (shown.isEmpty()) Text(tr("Aucune carte affichée"), color = Mf.colors.textSecondary, modifier = Modifier.padding(4.dp))
        GridPreview(shown, sh, selected?.takeIf { it in shown }) { c -> selected = if (selected == c) null else c }
        selected?.takeIf { it in shown }?.let { c ->
            CardToolbar(c, sh, onMove = { d ->
                val i = shown.indexOf(c); val j = (i + d).coerceIn(0, shown.lastIndex)
                if (i != j) set(shown.toMutableList().apply { add(j, removeAt(i)) })
            }, onRemove = { set(shown - c); selected = null })
        }
        // réglages du contenu des deux cartes qui en ont, côte à côte
        Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ContentLink(tr("Constantes"), DashCard.VITALS, Modifier.weight(1f)) { nav.navigate("vitals/edit") }
            ContentLink(tr("Anneaux"), DashCard.RINGS, Modifier.weight(1f)) { nav.navigate("rings/edit") }
        }
        // cartes masquées : une pastille par carte, regroupées par partie ; une touche l'ajoute à la fin
        val sections = DashCard.Section.entries.map { section ->
            section to DashCard.entries.filter { it.section == section && it !in shown && it.available && (it.feature()?.let { f -> sh.has(f) } ?: true) }
        }.filter { it.second.isNotEmpty() }
        if (sections.isNotEmpty()) SectionLabel(tr("Ajouter"))
        sections.forEach { (section, hidden) ->
            Text(tr(section.label), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 6.dp, bottom = 6.dp))
            androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                hidden.forEach { c -> AddPill(c) { set(shown + c); selected = c } }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Barre de la carte sélectionnée : taille, déplacer avant / après, retirer. */
@Composable
private fun CardToolbar(c: DashCard, sh: app.superhealth.SuperSettings, onMove: (Int) -> Unit, onRemove: () -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(18.dp)).background(Mf.colors.card)
        .padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(c.icon(), null, Modifier.size(18.dp), tint = c.hue())
            Spacer(Modifier.width(8.dp))
            Text(tr(c.label), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = { onMove(-1) }) { Icon(Icons.Rounded.KeyboardArrowUp, tr("Avant"), tint = Mf.colors.text) }
            IconButton(onClick = { onMove(1) }) { Icon(Icons.Rounded.KeyboardArrowDown, tr("Après"), tint = Mf.colors.text) }
            IconButton(onClick = onRemove) { Icon(Icons.Rounded.Close, tr("Retirer"), tint = Mf.colors.textSecondary) }
        }
        if (c.sizes.size > 1) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            c.sizes.forEach { z ->
                val on = sh.sizeOf(c) == z
                Text(z.title, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                    color = if (on) Mf.colors.onStrong else Mf.colors.text,
                    modifier = Modifier.clip(CircleShape).background(if (on) Mf.colors.strong else Mf.colors.chip)
                        .clickable { SuperPrefs.update { it.copy(dashSizes = it.dashSizes + (c.name to z.name)) } }
                        .padding(horizontal = 12.dp, vertical = 5.dp))
            }
        }
    }
}

/** Pastille d'une carte masquée : symbole, nom, « + ». */
@Composable
private fun AddPill(c: DashCard, onClick: () -> Unit) {
    Row(Modifier.clip(CircleShape).background(Mf.colors.card).clickable(onClick = onClick).padding(start = 10.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically) {
        Icon(c.icon(), null, Modifier.size(15.dp), tint = c.hue())
        Spacer(Modifier.width(6.dp))
        Text(tr(c.label), style = MaterialTheme.typography.labelMedium, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Rounded.Add, null, Modifier.size(14.dp), tint = Mf.colors.textTertiary)
    }
}

/** Lien vers le réglage du contenu d'une carte (constantes, anneaux), en petite tuile. */
@Composable
private fun ContentLink(title: String, c: DashCard, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)).background(Mf.colors.card).clickable(onClick = onClick)
        .padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(c.icon(), null, Modifier.size(18.dp), tint = c.hue())
        Spacer(Modifier.width(8.dp))
        Text(title, style = MaterialTheme.typography.labelLarge, maxLines = 1, modifier = Modifier.weight(1f))
        Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(18.dp).graphicsLayer(rotationZ = -90f), tint = Mf.colors.textTertiary)
    }
}

/** Constantes : quelles mesures de la montre (ou de Health Connect) dans la carte principale, et dans quel ordre. */
@Composable
fun VitalsEditScreen(vm: SuperViewModel, nav: NavHostController) {
    val sh by vm.settings.collectAsState()
    val shown = sh.vitals
    fun set(list: List<String>) = SuperPrefs.update { it.copy(vitals = list) }
    fun label(n: String) = app.superhealth.health.data.Metric.entries.firstOrNull { it.name == n }?.label ?: n
    DetailScaffold(tr("Constantes"), onBack = { nav.popBackStack() }, action = {
        TextButton(onClick = { set(listOf("RESTING_HR", "RESPIRATORY_RATE", "BODY_ENERGY")) }) { Text(tr("Par défaut"), color = Mf.colors.accent, maxLines = 1) }
    }) {
        Text(tr("La première mesure est la grande bulle, les deux suivantes les petites. Les mesures sans source restent masquées."),
            style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
        app.superhealth.nutrition.ui.components.GroupHeader(tr("Affichées"), Modifier.padding(start = 0.dp))
        if (shown.isEmpty()) Text(tr("Aucune mesure"), color = Mf.colors.textSecondary, modifier = Modifier.padding(4.dp))
        else GroupCard {
            shown.forEachIndexed { i, n ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr(label(n)), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        val m = app.superhealth.health.data.Metric.entries.firstOrNull { it.name == n }
                        if (m != null && !app.superhealth.bridge.Bridge.hasSource(m))
                            Text(tr("Pas de source pour l'instant"), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1)
                    }
                    IconButton(onClick = { set(shown.toMutableList().apply { add(i - 1, removeAt(i)) }) }, enabled = i > 0) {
                        Icon(Icons.Rounded.KeyboardArrowUp, tr("Monter"), tint = if (i > 0) Mf.colors.text else Mf.colors.textTertiary)
                    }
                    IconButton(onClick = { set(shown.toMutableList().apply { add(i + 1, removeAt(i)) }) }, enabled = i < shown.lastIndex) {
                        Icon(Icons.Rounded.KeyboardArrowDown, tr("Descendre"), tint = if (i < shown.lastIndex) Mf.colors.text else Mf.colors.textTertiary)
                    }
                    app.superhealth.nutrition.ui.components.Toggle(true, { set(shown - n) }, Modifier.padding(end = 8.dp))
                }
            }
        }
        val hidden = app.superhealth.VITAL_CHOICES.filter { it !in shown }
        if (hidden.isNotEmpty()) {
            app.superhealth.nutrition.ui.components.GroupHeader(tr("Disponibles"), Modifier.padding(start = 0.dp))
            GroupCard {
                hidden.forEachIndexed { i, n ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(label(n)), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        app.superhealth.nutrition.ui.components.Toggle(false, { set(shown + n) })
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Raccourcis du bouton « + » : lesquels, dans quel ordre. Un seul choisi : le « + » l'ouvre directement. */
@Composable
fun PlusEditScreen(vm: SuperViewModel, nav: NavHostController) {
    val sh by vm.settings.collectAsState()
    val shown = sh.plusActions
    fun set(list: List<PlusAction>) = SuperPrefs.update { it.copy(plus = list.map { a -> a.name }) }
    DetailScaffold(tr("Bouton +"), onBack = { nav.popBackStack() }, action = {
        TextButton(onClick = { set(PlusAction.defaults) }) { Text(tr("Par défaut"), color = Mf.colors.accent, maxLines = 1) }
    }) {
        Text(tr("Raccourcis du bouton + et leur ordre. Un seul : le + l'ouvre directement."),
            style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
        // place du bouton : réglée dans Apparence, rappelée ici pour la trouver où l'on règle le +
        GroupCard {
            app.superhealth.nutrition.ui.components.SettingRow(tr("Position"), tr(when (sh.plusSide) { "LEFT" -> "Gauche"; "CENTER" -> "Milieu"; else -> "Droite" })) {
                nav.navigate("settings/look")
            }
        }
        // même geste que pour les cartes : interrupteur sur chaque ligne, ordre dans la feuille « Réorganiser »
        var reorder by remember { mutableStateOf(false) }
        if (reorder) app.superhealth.training.ui.components.ReorderSheet(shown.map { it.name to null }, shown.map { null },
            onMove = { from, to -> set(with(app.superhealth.training.domain.ProgramEdit) { SuperPrefs.settings.value.plusActions.moved(from, to) }) },
            onDismiss = { reorder = false }, title = tr("Ordre des raccourcis"), names = shown.map { tr(it.label) })
        SectionLabel(tr("Affichés")) {
            if (shown.size > 1) Text(tr("Réorganiser"), Modifier.clickable { reorder = true }, color = Mf.colors.accent, style = MaterialTheme.typography.labelLarge)
        }
        if (shown.isEmpty()) Text(tr("Aucun raccourci"), color = Mf.colors.textSecondary, modifier = Modifier.padding(4.dp))
        else GroupCard {
            shown.forEachIndexed { i, a ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(tr(a.label), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(tr(a.section.label), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1)
                    }
                    app.superhealth.nutrition.ui.components.Toggle(true, { set(shown - a) }, Modifier.padding(end = 8.dp))
                }
            }
        }
        DashCard.Section.entries.forEach { section ->
            val hidden = PlusAction.entries.filter { it.section == section && it !in shown && (it.feature()?.let { f -> sh.has(f) } ?: true) }
            if (hidden.isEmpty()) return@forEach
            app.superhealth.nutrition.ui.components.GroupHeader(tr(section.label), Modifier.padding(start = 0.dp))
            GroupCard {
                hidden.forEachIndexed { i, a ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(a.label), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        app.superhealth.nutrition.ui.components.Toggle(false, { set(shown + a) })
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Anneaux du jour : quelles mesures (1 à 4), de l'extérieur vers l'intérieur. */
@Composable
fun RingsEditScreen(vm: SuperViewModel, nav: NavHostController) {
    val sh by vm.settings.collectAsState()
    val shown = Rings.kinds(sh.rings)
    fun set(list: List<Rings.Kind>) = SuperPrefs.update { it.copy(rings = list.map { k -> k.name }) }
    DetailScaffold(tr("Anneaux du jour"), onBack = { nav.popBackStack() }, action = {
        TextButton(onClick = { set(Rings.defaults) }) { Text(tr("Par défaut"), color = Mf.colors.accent, maxLines = 1) }
    }) {
        Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
            ActivityRings(shown.map { Rings.Ring(it, 0.66, 1.0) }, 150.dp, track = Mf.colors.card)
        }
        Text(tr("De l'extérieur vers l'intérieur, jusqu'à 4 anneaux. Aussi dans le widget « Anneaux du jour »."),
            style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
        app.superhealth.nutrition.ui.components.GroupHeader(tr("Affichés"), Modifier.padding(start = 0.dp))
        GroupCard {
            shown.forEachIndexed { i, k ->
                if (i > 0) RowDivider()
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(12.dp).clip(CircleShape).background(k.color))
                    Spacer(Modifier.width(12.dp))
                    Text(tr(k.label), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                    IconButton(onClick = { set(shown.toMutableList().apply { add(i - 1, removeAt(i)) }) }, enabled = i > 0) {
                        Icon(Icons.Rounded.KeyboardArrowUp, tr("Monter"), tint = if (i > 0) Mf.colors.text else Mf.colors.textTertiary)
                    }
                    IconButton(onClick = { set(shown.toMutableList().apply { add(i + 1, removeAt(i)) }) }, enabled = i < shown.lastIndex) {
                        Icon(Icons.Rounded.KeyboardArrowDown, tr("Descendre"), tint = if (i < shown.lastIndex) Mf.colors.text else Mf.colors.textTertiary)
                    }
                    app.superhealth.nutrition.ui.components.Toggle(true, { set(shown - k) }, Modifier.padding(end = 8.dp), enabled = shown.size > 1)
                }
            }
        }
        val hidden = Rings.Kind.entries.filter { with(Rings) { it.available } } - shown.toSet()
        if (hidden.isNotEmpty()) {
            app.superhealth.nutrition.ui.components.GroupHeader(tr("Disponibles"), Modifier.padding(start = 0.dp))
            GroupCard {
                hidden.forEachIndexed { i, k ->
                    if (i > 0) RowDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(k.color))
                        Spacer(Modifier.width(12.dp))
                        Text(tr(k.label), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        app.superhealth.nutrition.ui.components.Toggle(false, { set(shown + k) }, enabled = shown.size < Rings.MAX)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}


/** Bloc de l'aperçu : toucher le sélectionne (sa barre de réglage s'ouvre dessous). */
@Composable
private fun Block(c: DashCard, z: app.superhealth.CardSize, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Row(modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
        .background(if (selected) c.hue().container(1.6f) else Mf.colors.chip)
        .then(if (selected) Modifier.border(1.5.dp, c.hue(), androidx.compose.foundation.shape.RoundedCornerShape(10.dp)) else Modifier)
        .clickable(onClick = onClick).padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(c.icon(), null, Modifier.size(16.dp), tint = c.hue())
        if (z.span > 1) {
            Spacer(Modifier.width(6.dp))
            Text(tr(c.label), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis,
                color = if (selected) c.hue().onContainer() else Mf.colors.text)
        }
    }
}

/** Aperçu de la grille (3 blocs par rangée), tel que le tableau de bord la range. */
@Composable
private fun GridPreview(cards: List<DashCard>, sh: app.superhealth.SuperSettings, selected: DashCard?, onSelect: (DashCard) -> Unit) {
    if (cards.isEmpty()) return
    Column(Modifier.fillMaxWidth().clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).background(Mf.colors.card).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        app.superhealth.packRows(cards) { sh.sizeOf(it).span }.forEach { row ->
            val used = row.sumOf { sh.sizeOf(it).span }
            val squares = row.all { sh.sizeOf(it) == app.superhealth.CardSize.S }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { c ->
                    val z = sh.sizeOf(c)
                    val w = if (used == app.superhealth.GRID || squares || z == app.superhealth.CardSize.S) z.span.toFloat()
                        else z.span + (app.superhealth.GRID - used).toFloat() / row.count { sh.sizeOf(it) != app.superhealth.CardSize.S }
                    Block(c, z, c == selected, Modifier.weight(w).height(if (z == app.superhealth.CardSize.W) 28.dp else 40.dp)) { onSelect(c) }
                }
                if (squares && used < app.superhealth.GRID) Spacer(Modifier.weight((app.superhealth.GRID - used).toFloat()))
            }
        }
        if (selected == null) Text(tr("Touchez une carte pour la régler."), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
    }
}
