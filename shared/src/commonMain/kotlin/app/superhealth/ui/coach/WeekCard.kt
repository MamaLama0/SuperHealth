package app.superhealth.ui.coach

import app.superhealth.platform.*

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import app.superhealth.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.CardSize
import app.superhealth.DashCard
import app.superhealth.coach.Week
import app.superhealth.nutrition.ui.energyValue
import app.superhealth.nutrition.ui.weightValue
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.dashboard.CompactBubble
import app.superhealth.ui.dashboard.CompactData
import app.superhealth.ui.dashboard.CompactMedium
import app.superhealth.ui.dashboard.CompactStrip
import app.superhealth.ui.dashboard.Tile
import app.superhealth.ui.formatNumber
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import app.superhealth.platform.LocalDate
import kotlin.math.abs

/**
 * Carte « Ma semaine » (plugin Coach) : les 7 derniers jours complets comptés par l'app, et en grand une phrase du coach.
 * Petit, moyen et bandeau : la variation du poids tendance, l'anneau = jours journalisés sur 7. Toucher : le coach analyse
 * la semaine.
 */
@Composable
fun CoachWeekCard(c: DashCard, size: CardSize, vm: SuperViewModel, modifier: Modifier, nav: NavHostController) {
    val ctx = LocalContext.current
    val s by vm.nutrition.settings.collectAsState()
    val stats by Week.stats.collectAsState()
    val note by Week.note.collectAsState()
    val end = LocalDate.now().minusDays(1).toEpochDay()
    // recalcul à chaque affichage (données locales, rapide) ; la phrase du coach : une fois par semaine glissante
    LaunchedEffect(end) {
        Week.load(ctx)
        val st = runCatching { Week.compute(ctx) }.getOrNull() ?: return@LaunchedEffect
        val last = Week.note.value
        if (size == CardSize.L && (last == null || st.end - last.end >= 7)) runCatching { Week.makeNote(ctx, st) }
    }
    val ask: () -> Unit = { nav.askCoach(tr("Fais le bilan de ma semaine")) }
    val st = stats
    val unitW = tr(s.weightUnit.label)
    fun delta(kg: Double) = (if (kg > 0) "+" else if (kg < 0) "−" else "") + formatNumber(abs(s.weightValue(kg)), 1)
    val d = CompactData(st?.trendDelta?.let(::delta) ?: "—", unitW, tr("7 jours"), st?.let { it.loggedDays / 7f }, onClick = ask)
    when (size) {
        CardSize.S -> CompactBubble(c, d, modifier)
        CardSize.M -> CompactMedium(c, d, modifier)
        CardSize.W -> CompactStrip(c, d, modifier)
        CardSize.L -> Tile(c, tr("Ma semaine"), modifier, trailing = st?.let { trf("%1\$s/7 jours notés", it.loggedDays) }, onClick = ask) {
            if (st == null) { Text(tr("Calcul…"), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary); return@Tile }
            val kcal = tr(s.energyUnit.label)
            val cells = listOfNotNull(
                st.kcalAvg?.let { Triple(tr("Calories / jour"), formatNumber(s.energyValue(it)), trf("cible %1\$s %2\$s", formatNumber(s.energyValue(st.kcalTarget ?: 0.0)), kcal)) },
                st.proteinAvg?.let { Triple(tr("Protéines / jour"), formatNumber(it) + " g", trf("cible %1\$s g", formatNumber(st.proteinTarget ?: 0.0))) },
                st.trendDelta?.let { Triple(tr("Poids tendance"), delta(it) + " " + unitW, tr("sur 7 jours")) },
                Triple(tr("Séances"), st.sessions.toString(), if (st.volumeKg > 0) trf("%1\$s t soulevées", formatNumber(st.volumeKg / 1000, 1)) else
                    trf("%1\$s min de cardio", st.cardioMin)),
                st.sleepH?.let { Triple(tr("Sommeil"), formatNumber(it, 1) + " h", tr("par nuit")) },
                st.steps?.let { Triple(tr("Pas"), formatNumber(it), tr("par jour")) },
            )
            cells.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { (label, value, sub) ->
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary)
                            Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, color = CoachHue.onContainer())
                            Text(sub, style = MaterialTheme.typography.bodySmall, color = Mf.colors.textTertiary)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            note?.takeIf { it.end == st.end || st.end - it.end < 7 }?.let { n ->
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(CoachHue.container()).padding(10.dp)) {
                    CoachAvatar(24.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(rich(n.text), style = MaterialTheme.typography.bodyMedium, color = CoachHue.onContainer())
                }
            }
        }
    }
}
