package app.superhealth.ui.cards

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.superhealth.league.Progression
import androidx.compose.ui.Alignment
import app.superhealth.ui.theme.Mf
import java.text.NumberFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import androidx.compose.foundation.clickable
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
internal fun CharacterAttributes(stats: CharacterStats, modifier: Modifier = Modifier, onForce: () -> Unit) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        CharacterAttribute.entries.forEach { attribute ->
            val tint = when (attribute) {
                CharacterAttribute.FOR -> Color(0xFFFFBA9E)
                CharacterAttribute.DEX -> Color(0xFF9EDBD2)
                CharacterAttribute.CON -> Color(0xFFC6B8F2)
            }
            val value = stats.score(attribute)
            val action = if (attribute == CharacterAttribute.FOR) Modifier.clickable(onClick = onForce) else Modifier
            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(tint.copy(alpha = .12f)).then(action)
                .padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (attribute == CharacterAttribute.FOR) "FOR ↗" else attribute.name,
                    color = tint, style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(value.toString(), Modifier.alignByBaseline(), color = Color.White,
                        style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                    Text("/100", Modifier.alignByBaseline(), color = tint, style = MaterialTheme.typography.labelSmall)
                }
                Text(tr(attribute.label), color = tint, style = MaterialTheme.typography.labelSmall)
                LinearProgressIndicator(progress = { value / 100f }, modifier = Modifier.fillMaxWidth(), color = tint, trackColor = tint.copy(alpha = .12f))
                if (stats.bonus(attribute) != 0) Text((if (stats.bonus(attribute) > 0) "+" else "") + java.text.NumberFormat.getNumberInstance().format(stats.bonus(attribute) / 100.0) + "%", color = tint, style = MaterialTheme.typography.labelSmall)
            }
        }
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
            Text(tr("Le score sur 100 combine les performances et les suivis. Les bonus des cartes s’appliquent en combat."),
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
                            Text("${calculation.score} / 100", color = Mf.colors.accent, style = MaterialTheme.typography.headlineSmall)
                        }
                        calculation.parts.forEach { part ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(tr(part.label), style = MaterialTheme.typography.labelLarge)
                                Text(if (part.ratio == null) tr(if (part.required) "Donnée manquante · part réservée" else "Indisponible ou désactivé · non compté") else
                                    percent.format(part.ratio) + " × " + percent.format(part.weight / calculation.weight) +
                                        " = " + percent.format(part.ratio * part.weight / calculation.weight),
                                    color = Mf.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
            Text(tr("D’où viennent les scores ?"), style = MaterialTheme.typography.titleLarge)
            Text(tr("Force : moyenne des pectoraux, du dos, des bras et épaules, et des jambes. Chaque groupe pèse 25 %. Un groupe non évalué contribue zéro."))
            Text(tr("La charte rattache chaque exercice à un mouvement de référence. Les charges de référence montent de 10 %. Les muscles secondaires et les exercices dont la correspondance est incertaine pèsent moins."), color = Mf.colors.textSecondary)
            Text(tr("Dextérité : cardio 65 %, pas 20 %, esprit et mobilité 15 %. Les valeurs cardio intermédiaires progressent plus graduellement."))
            Text(tr("Cardio : niveau 30 %, fréquence et volume 20 %, pas 20 %, distance 15 %, allure 15 %. Le niveau utilise la VO₂max disponible, sinon les courses ou une estimation. Les activités récentes pèsent davantage : leur poids est divisé par deux après 21 jours."), color = Mf.colors.textSecondary)
            Text(tr("Constitution : volume moyen 45 %, régularité en salle 15 %, sommeil 25 %, nutrition 15 %. Les semaines sans séance comptent dans la moyenne."))
            Text(tr("Repères de jeu du volume sur un an : 75 % pour le tonnage et 25 % pour les séries. La référence maximale correspond à 250 fois le poids du corps en kg et à 50 séries par semaine."), color = Mf.colors.textSecondary)
            Text(tr("Historique court : moyenne sur la période observée, avec un minimum de 28 jours, signalée provisoire. La régularité compare les jours de musculation à l’objectif hebdomadaire sur la même période. Sans données de salle, sommeil et nutrition ne remplacent pas les parts de musculation."), color = Mf.colors.textSecondary)
            athlete?.training?.let { gym ->
                Text(tr("Période observée") + " : ${gym.coveredDays}/365 " + tr("jours"), style = MaterialTheme.typography.labelLarge)
                gym.evidence?.let { evidence ->
                    Text(tr("Volume hebdomadaire moyen") + " : " + number.format(evidence.weeklyKg) + " kg · " + number.format(evidence.weeklySets) + " " + tr("séries"))
                    Text(tr(if (evidence.bodyweightKnown) "Poids de corps utilisé" else "Poids de corps estimé · ajoutez une pesée") + " : " + number.format(evidence.bodyweightKg) + " kg", style = MaterialTheme.typography.bodySmall)

                }
            }
            Text(tr("Suivis sur 28 jours : jours validés / 28 pour les suivis quotidiens ; jours avec séance validée / (4 × objectif hebdomadaire) pour la musculation. Chaque rapport est plafonné à 100 %. Un nouveau compte ne reçoit pas le maximum dès son premier jour."), color = Mf.colors.textSecondary)
            Text(tr("Les suivis facultatifs désactivés sont exclus. Les parts réservées aux groupes musculaires, au cardio et à la musculation restent à zéro si leurs données manquent."), color = Mf.colors.textSecondary)
            if (!athlete?.provisional.isNullOrEmpty() || athlete?.training?.provisional == true) Text(tr("Certaines performances sont encore provisoires."), color = Mf.colors.accent)
            Text(tr("Cartes : armes → Force ; bottes → Dextérité ; torses et protections → Constitution. Les malédictions retirent leur pourcentage à la caractéristique indiquée. Les types de dégâts restent indépendants."), style = MaterialTheme.typography.bodySmall)
            TextButton(onDismiss) { Text(tr("Compris")) }
        }
    }
}
