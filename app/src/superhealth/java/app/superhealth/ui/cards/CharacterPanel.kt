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
            val value = stats[attribute]
            val action = if (attribute == CharacterAttribute.FOR) Modifier.clickable(onClick = onForce) else Modifier
            Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(tint.copy(alpha = .12f)).then(action)
                .padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(if (attribute == CharacterAttribute.FOR) "FOR ↗" else attribute.name,
                    color = tint, style = MaterialTheme.typography.labelLarge)
                Text("$value / 20", color = Color.White, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                Text(tr(attribute.label), color = tint, style = MaterialTheme.typography.labelSmall)
                LinearProgressIndicator(progress = { ((value - 8) / 12f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = tint, trackColor = tint.copy(alpha = .12f))
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
                                Text(if (part.ratio == null) tr(if (part.required) "Donnée de musculation manquante · part réservée" else "Indisponible ou désactivé · non compté") else
                                    percent.format(part.ratio) + " × " + percent.format(part.weight / calculation.weight) +
                                        " = " + percent.format(part.ratio * part.weight / calculation.weight),
                                    color = Mf.colors.textSecondary, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        HorizontalDivider()
                        Text("8 + 12 × " + number.format(calculation.ratio) + " = " + number.format(8 + 12 * calculation.ratio) +
                            " → ${calculation.value}", style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            Text(tr("D’où viennent les scores ?"), style = MaterialTheme.typography.titleLarge)
            Text(tr("Force : records de charge et 1RM estimés 85 %, pratique récente 15 %. Le meilleur mouvement de référence pèse 60 % du score PR ; la moyenne des trois meilleurs, 40 %. Les variantes d’un même mouvement ne comptent qu’une fois."))
            Text(tr("Les PR de toutes les séances enregistrées sont conservés. Séries validées de 1 à 12 répétitions, hors échauffement ; RIR limité à 3 pour le calcul. Les repères tiennent compte du mouvement, du poids du corps et du profil. Une estimation de machine compte selon sa fiabilité."), color = Mf.colors.textSecondary)
            Text(tr("Dextérité : cardio 65 %, objectif de pas 20 %, esprit et mobilité 15 %. C’est une caractéristique de jeu, pas une mesure de coordination."))
            Text(tr("Cardio : niveau 30 %, fréquence et volume 20 %, pas 20 %, distance 15 %, allure 15 %. Le niveau utilise la VO₂max disponible, sinon les courses ou une estimation. Les activités récentes pèsent davantage : leur poids est divisé par deux après 21 jours."), color = Mf.colors.textSecondary)
            Text(tr("Constitution : volume moyen 70 %, régularité en salle 15 %, sommeil 10 %, nutrition 5 %. Les semaines sans séance enregistrée comptent dans la moyenne."))
            Text(tr("Volume sur les 365 derniers jours : 75 % pour le tonnage (charge externe × répétitions), 25 % pour les séries de travail validées. Repères de jeu : 200 × poids du corps en kg par semaine et 40 séries ; ce ne sont pas des objectifs d’entraînement. Chaque composante est plafonnée à 100 %."), color = Mf.colors.textSecondary)
            Text(tr("Historique court : moyenne sur la période observée, avec un minimum de 28 jours, signalée provisoire. La régularité compare les jours de musculation à l’objectif hebdomadaire sur la même période. Sans données de salle, sommeil et nutrition ne remplacent pas les parts de musculation."), color = Mf.colors.textSecondary)
            athlete?.training?.let { gym ->
                Text(tr("Période observée") + " : ${gym.coveredDays}/365 " + tr("jours"), style = MaterialTheme.typography.labelLarge)
                gym.evidence?.let { evidence ->
                    Text(tr("Volume hebdomadaire moyen") + " : " + number.format(evidence.weeklyKg) + " kg · " + number.format(evidence.weeklySets) + " " + tr("séries"))
                    Text(tr(if (evidence.bodyweightKnown) "Poids de corps utilisé" else "Poids de corps estimé · ajoutez une pesée") + " : " + number.format(evidence.bodyweightKg) + " kg", style = MaterialTheme.typography.bodySmall)
                    evidence.records.forEach { record ->
                        Text(record.name + " · " + number.format(record.loadKg) + " kg × ${record.reps} · " + tr("1RM estimé") + " " + number.format(record.estimatedMaxKg) + " kg", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text(tr("Suivis sur 28 jours : jours validés / 28 pour les suivis quotidiens ; jours avec séance validée / (4 × objectif hebdomadaire) pour la musculation. Chaque rapport est plafonné à 100 %. Un nouveau compte ne reçoit pas le maximum dès son premier jour."), color = Mf.colors.textSecondary)
            Text(tr("Les suivis facultatifs désactivés sont exclus. Les parts réservées aux PR et au volume restent présentes même si leurs données manquent : cocher des suivis ne suffit pas à obtenir une force ou une constitution maximale. Sans donnée : 8."), color = Mf.colors.textSecondary)
            if (!athlete?.provisional.isNullOrEmpty() || athlete?.training?.provisional == true) Text(tr("Certaines performances sont encore provisoires."), color = Mf.colors.accent)
            Text(tr("Modificateur : arrondi inférieur de (stat − 10) / 2. Les caractéristiques décrivent le personnage, pas un diagnostic de santé."), style = MaterialTheme.typography.bodySmall)
            Text(tr("Cartes : armes → Force ; bottes → Dextérité ; torses et protections → Constitution. Les malédictions retirent leur pourcentage à la caractéristique indiquée. Les types de dégâts restent indépendants."), style = MaterialTheme.typography.bodySmall)
            TextButton(onDismiss) { Text(tr("Compris")) }
        }
    }
}
