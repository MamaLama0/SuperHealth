package app.superhealth.ui.cards

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.superhealth.league.Progression
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import app.superhealth.ui.theme.Mf
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.superhealth.cards.*
import app.superhealth.ui.tr

@Composable
internal fun CharacterAttributes(stats: CharacterStats, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CharacterAttribute.entries.forEach { attribute ->
            val tint = when (attribute) {
                CharacterAttribute.FOR -> Color(0xFFFFBA9E)
                CharacterAttribute.DEX -> Color(0xFF9EDBD2)
                CharacterAttribute.CON -> Color(0xFFC6B8F2)
            }
            val value = stats[attribute]
            val bonus = CharacterStats.modifier(value)
            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(tint.copy(alpha = .12f))
                .padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(attribute.name, color = tint, style = MaterialTheme.typography.labelLarge)
                Text(value.toString(), color = Color.White, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Black)
                Text(tr(attribute.label), color = tint, style = MaterialTheme.typography.labelSmall)
                if (stats.bonus(attribute) != 0) Text((if (stats.bonus(attribute) > 0) "+" else "") + java.text.NumberFormat.getNumberInstance().format(stats.bonus(attribute) / 100.0) + "%", color = tint, style = MaterialTheme.typography.labelSmall)
                Text(tr("Mod.") + " " + (if (bonus >= 0) "+" else "") + bonus, color = Color(0xFFCBD4DF), style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}

@Composable
internal fun StancePicker(stance: CombatStance, enabled: Boolean = true, onSelect: (CombatStance) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CombatStance.entries.forEach { item ->
                FilterChip(selected = stance == item, onClick = { onSelect(item) }, enabled = enabled,
                    label = { Text(tr(item.title)) }, modifier = Modifier.weight(1f),
                    colors = FilterChipDefaults.filterChipColors(labelColor = Color(0xFFCDD9E5), selectedLabelColor = Color(0xFF17232E),
                        selectedContainerColor = Color(0xFFE8C584)))
            }
        }
        Text(tr(stance.detail), color = Color(0xFFB6C5D4), style = MaterialTheme.typography.bodySmall)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CharacterRules(progress: Progression.Result?, selected: Set<Progression.Track>, athlete: AthleteStats?, onDismiss: () -> Unit) {
    val calculations = CharacterStats.breakdown(progress, selected, athlete)
    val percent = remember { NumberFormat.getPercentInstance().apply { maximumFractionDigits = 1 } }
    val number = remember { NumberFormat.getNumberInstance().apply { maximumFractionDigits = 4 } }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Mf.colors.background) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(.88f).verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text(tr("Comment sont calculées mes stats ?"), style = MaterialTheme.typography.headlineSmall)
            Text(tr("8 + 12 × le score pondéré, arrondi à l’entier le plus proche. Les bonus des cartes s’appliquent ensuite en pourcentage."),
                color = Mf.colors.textSecondary)
            athlete?.computedAt?.takeIf { it > 0 }?.let { date ->
                Text(tr("Performances actualisées le") + " " + DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
                    .format(Instant.ofEpochMilli(date).atZone(ZoneId.systemDefault())), style = MaterialTheme.typography.labelSmall)
            }
            calculations.forEach { calculation ->
                Surface(color = Mf.colors.card, shape = RoundedCornerShape(20.dp)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(tr(calculation.attribute.label), Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                            Text("${calculation.value} / 20", color = Mf.colors.accent, style = MaterialTheme.typography.headlineSmall)
                        }
                        calculation.parts.forEach { part ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(tr(part.label), style = MaterialTheme.typography.labelLarge)
                                Text(if (part.ratio == null) tr("Indisponible ou désactivé · non compté") else
                                    percent.format(part.ratio) + " × " + percent.format(part.weight / calculation.weight) +
                                        " = " + percent.format(part.ratio * part.weight / calculation.weight),
                                    color = Mf.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        HorizontalDivider()
                        Text("8 + 12 × " + number.format(calculation.ratio) + " = " + number.format(8 + 12 * calculation.ratio) +
                            " → ${calculation.value}", style = MaterialTheme.typography.bodyMedium)
                        Text(tr("Mod.") + " " + CharacterStats.modifier(calculation.value), color = Mf.colors.accent)
                    }
                }
            }
            Text(tr("D’où viennent les scores ?"), style = MaterialTheme.typography.titleLarge)
            Text(tr("Force : moyenne des quatre familles musculaires disponibles (pectoraux, dos, bras-épaules, jambes), poids nominal 80 %. Objectif de musculation tenu sur 28 jours, poids 20 %."))
            Text(tr("Performances musculaires : force estimée à partir des séries, rapportée aux barèmes et au poids du corps. Les trois meilleurs exercices de chaque muscle sont pondérés ; les muscles secondaires comptent moins. Le meilleur niveau de l’année, le volume sur six semaines et la progression récente participent au score. Une longue absence réduit progressivement la note."), color = Mf.colors.textSecondary)
            Text(tr("Dextérité : cardio 65 %, objectif de pas 20 %, esprit et mobilité 15 %. C’est une caractéristique de jeu, pas une mesure de coordination."))
            Text(tr("Cardio : niveau 30 %, fréquence et volume 20 %, pas 20 %, distance 15 %, allure 15 %. Le niveau utilise la VO₂max disponible, sinon les courses ou une estimation. Les activités récentes pèsent davantage : leur poids est divisé par deux après 21 jours."), color = Mf.colors.textSecondary)
            Text(tr("Constitution : objectif de sommeil 45 %, nutrition 25 %, régularité 30 %."))
            Text(tr("Régularité : jusqu’à 26 semaines terminées, les récentes comptent davantage. 60 % pour les séances rapportées à l’objectif, 40 % pour les jours suivis ; cinq jours suivis suffisent pour une semaine complète. Le résultat devient un score de 30 à 99."), color = Mf.colors.textSecondary)
            Text(tr("Suivis sur 28 jours : jours validés / 28 pour les suivis quotidiens ; jours avec séance validée / (4 × objectif hebdomadaire) pour la musculation. Chaque rapport est plafonné à 100 %. Un nouveau compte ne reçoit pas le maximum dès son premier jour."), color = Mf.colors.textSecondary)
            Text(tr("Les données indisponibles et les suivis désactivés sont exclus ; les poids restants sont redistribués. Sans donnée : 8. Les valeurs provisoires restent signalées et peuvent évoluer avec de nouvelles séances."), color = Mf.colors.textSecondary)
            if (!athlete?.provisional.isNullOrEmpty()) Text(tr("Certaines performances sont encore provisoires."), color = Mf.colors.accent)
            Text(tr("Modificateur : arrondi inférieur de (stat − 10) / 2. Les caractéristiques décrivent le personnage, pas un diagnostic de santé."), style = MaterialTheme.typography.bodySmall)
            Text(tr("Cartes : armes → Force ; bottes → Dextérité ; torses et protections → Constitution. Les malédictions retirent leur pourcentage à la caractéristique indiquée. Les types de dégâts restent indépendants."), style = MaterialTheme.typography.bodySmall)
            TextButton(onDismiss) { Text(tr("Compris")) }
        }
    }
}
