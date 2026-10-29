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

/** Personnalisation : quelles cartes, dans quel ordre. Les cartes « montre » s'affichent dès qu'une source existe. */
@Composable
fun DashboardEditScreen(vm: SuperViewModel, nav: NavHostController) {
    val sh by vm.settings.collectAsState()
    val shown = sh.dashboard
    fun set(list: List<DashCard>) = SuperPrefs.update { it.copy(dashboard = list) }
    DetailScaffold(tr("Personnaliser"), onBack = { nav.popBackStack() }, action = {
        TextButton(onClick = { set(DashCard.defaults) }) { Text(tr("Par défaut"), color = Mf.colors.accent, maxLines = 1) }
    }) {
        Text(tr("Choisissez vos cartes, leur taille et leur ordre."), style = MaterialTheme.typography.bodyMedium,
            color = Mf.colors.textSecondary, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))

        GridPreview(shown, sh)
        // Accès direct au choix des constantes de la carte principale.
        GroupCard {
            Row(Modifier.fillMaxWidth().clickable { nav.navigate("vitals/edit") }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Constantes affichées"), style = MaterialTheme.typography.bodyLarge)
                    Text(sh.vitals.joinToString(" · ") { n -> tr(app.superhealth.health.data.Metric.entries.firstOrNull { it.name == n }?.label ?: n) },
                        style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.graphicsLayer(rotationZ = -90f), tint = Mf.colors.textTertiary)
            }
        }
        GroupCard {
            Row(Modifier.fillMaxWidth().clickable { nav.navigate("rings/edit") }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Anneaux du jour"), style = MaterialTheme.typography.bodyLarge)
                    Text(app.superhealth.ui.dashboard.Rings.kinds(sh.rings).joinToString(" · ") { tr(it.label) },
                        style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.graphicsLayer(rotationZ = -90f), tint = Mf.colors.textTertiary)
            }
        }
        var reorder by remember { mutableStateOf(false) }
        if (reorder) app.superhealth.training.ui.components.ReorderSheet(shown.map { it.name to null }, shown.map { null },
            onMove = { from, to -> set(with(app.superhealth.training.domain.ProgramEdit) { SuperPrefs.settings.value.dashboard.moved(from, to) }) },
            onDismiss = { reorder = false }, title = tr("Ordre des cartes"), names = shown.map { tr(it.label) })
        SectionLabel(tr("Affichées")) {
            if (shown.size > 1) Text(tr("Réorganiser"), Modifier.clickable { reorder = true }, color = Mf.colors.accent, style = MaterialTheme.typography.labelLarge)
        }
        if (shown.isEmpty()) Text(tr("Aucune carte affichée"), color = Mf.colors.textSecondary, modifier = Modifier.padding(4.dp))
        else GroupCard {
            shown.forEachIndexed { i, c ->
                if (i > 0) RowDivider(56.dp)
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    CardGlyph(c)
                    Column(Modifier.weight(1f)) {
                        Text(tr(c.label), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        // taille : bulle, demi-largeur, pleine largeur (celles que la carte accepte)
                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                        if (c.sizes.size > 1) androidx.compose.foundation.layout.FlowRow(Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            c.sizes.forEach { z ->
                                val on = sh.sizeOf(c) == z
                                Text(z.title, style = MaterialTheme.typography.labelMedium, maxLines = 1,
                                    color = if (on) Mf.colors.onStrong else Mf.colors.text,
                                    modifier = Modifier.clip(CircleShape).background(if (on) Mf.colors.strong else Mf.colors.chip)
                                        .clickable { SuperPrefs.update { it.copy(dashSizes = it.dashSizes + (c.name to z.name)) } }
                                        .padding(horizontal = 10.dp, vertical = 4.dp))
                            }
                        } else Text(tr(c.section.label), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1)
                    }
                    app.superhealth.nutrition.ui.components.Toggle(true, { set(shown - c) }, Modifier.padding(end = 8.dp))
                }
            }
        }

        DashCard.Section.entries.forEach { section ->
            val hidden = DashCard.entries.filter { it.section == section && it !in shown && it.available && (it.feature()?.let { f -> sh.has(f) } ?: true) }
            if (hidden.isEmpty()) return@forEach
            app.superhealth.nutrition.ui.components.GroupHeader(tr(section.label), Modifier.padding(start = 0.dp))
            GroupCard {
                hidden.forEachIndexed { i, c ->
                    if (i > 0) RowDivider(56.dp)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        CardGlyph(c)
                        Column(Modifier.weight(1f)) {
                            Text(tr(c.label), style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (c.watch) Text(tr("Montre ou Apple Santé"), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, maxLines = 1)
                        }
                        app.superhealth.nutrition.ui.components.Toggle(false, { set(shown + c) })
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
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


/** Bloc de l'aperçu : toucher passe à la taille suivante que la carte accepte. */
@Composable
private fun Block(c: DashCard, z: app.superhealth.CardSize, modifier: Modifier) {
    Row(modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(10.dp)).background(Mf.colors.chip)
        .clickable(enabled = c.sizes.size > 1) {
            val next = c.sizes[(c.sizes.indexOf(z) + 1) % c.sizes.size]
            SuperPrefs.update { it.copy(dashSizes = it.dashSizes + (c.name to next.name)) }
        }.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(c.icon(), null, Modifier.size(16.dp), tint = c.hue())
        if (z.span > 1) {
            Spacer(Modifier.width(6.dp))
            Text(tr(c.label), style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

/** Icône de la carte dans un carré arrondi de sa couleur, comme les listes de réglages. */
@Composable
private fun CardGlyph(c: DashCard) {
    Box(Modifier.padding(end = 14.dp).size(26.dp), contentAlignment = Alignment.Center) {
        Icon(c.icon(), null, Modifier.size(21.dp), tint = c.hue())
    }
}

/** Aperçu de la grille (3 blocs par rangée), tel que le tableau de bord la range ; toucher un bloc change sa taille. */
@Composable
private fun GridPreview(cards: List<DashCard>, sh: app.superhealth.SuperSettings) {
    if (cards.isEmpty()) return
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp).clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp)).background(Mf.colors.card).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)) {
        app.superhealth.packRows(cards) { sh.sizeOf(it).span }.forEach { row ->
            val used = row.sumOf { sh.sizeOf(it).span }
            val squares = row.all { sh.sizeOf(it) == app.superhealth.CardSize.S }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { c ->
                    val z = sh.sizeOf(c)
                    val w = if (used == app.superhealth.GRID || squares || z == app.superhealth.CardSize.S) z.span.toFloat()
                        else z.span + (app.superhealth.GRID - used).toFloat() / row.count { sh.sizeOf(it) != app.superhealth.CardSize.S }
                    Block(c, z, Modifier.weight(w).height(if (z == app.superhealth.CardSize.W) 26.dp else 40.dp))
                }
                if (squares && used < app.superhealth.GRID) Spacer(Modifier.weight((app.superhealth.GRID - used).toFloat()))
            }
        }
        Text(tr("Touchez un bloc pour changer sa taille ; appui long sur une carte du tableau de bord pour la régler."),
            style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
    }
}
