package app.superhealth.nutrition.ui.screens

import app.superhealth.platform.*

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.superhealth.nutrition.data.FoodEntryEntity
import app.superhealth.nutrition.data.Micros
import app.superhealth.nutrition.domain.Settings as NutritionSettings
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.ProgressBar
import app.superhealth.nutrition.ui.num
import app.superhealth.nutrition.ui.tr
import app.superhealth.nutrition.ui.trf
import app.superhealth.ui.theme.Mf

/**
 * Nutriments du jour, dans le journal : quatre repères d'un coup d'œil (fibres, sucres, sodium, caféine ou saturés), la
 * couverture des vitamines et minéraux ; touché, le détail complet par famille avec l'apport conseillé ou le plafond.
 * Les valeurs viennent des aliments (base suisse, Open Food Facts) : la ligne du bas dit combien d'aliments en ont.
 */
@Composable
fun DayNutrients(entries: List<FoodEntryEntity>, s: NutritionSettings, modifier: Modifier = Modifier, nav: androidx.navigation.NavHostController? = null, onMore: () -> Unit) {
    if (entries.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(false) }
    val totals = remember(entries) {
        Micros.sum(entries.map { it.microsJson }).toMutableMap().also { m -> m["fiber"] = entries.sumOf { it.fiber } }
    }
    val withData = remember(entries) { entries.count { !it.microsJson.isNullOrBlank() } }
    fun target(d: Micros.Def) = s.microTargets[d.code] ?: Micros.reference(d)
    MfCard(modifier.animateContentSize(), onClick = { open = !open }, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Nutriments"), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.KeyboardArrowDown, null, Modifier.size(20.dp).graphicsLayer(rotationZ = if (open) 180f else 0f), tint = Mf.colors.textTertiary)
        }
        Spacer(Modifier.height(8.dp))
        // les quatre repères : la caféine dès qu'il y en a, sinon les graisses saturées
        val keys = listOf("fiber", "sugar", "sodium", if ((totals["caffeine"] ?: 0.0) > 0) "caffeine" else "sat")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            keys.forEach { code ->
                val d = Micros.byCode[code]!!
                KeyStat(d, totals[code] ?: 0.0, target(d), Modifier.weight(1f))
            }
        }
        // couverture : moyenne des apports conseillés atteints (chaque nutriment plafonné à 100 %), sur ceux qui ont une référence
        val groups = listOf("Vitamines", "Minéraux").map { cat -> cat to Micros.defs.filter { it.category == cat && target(it) != null } }
        if (groups.any { (_, l) -> l.any { (totals[it.code] ?: 0.0) > 0 } }) {
            Spacer(Modifier.height(10.dp))
            groups.forEach { (cat, l) ->
                val cover = l.map { ((totals[it.code] ?: 0.0) / target(it)!!).coerceAtMost(1.0) }.average()
                Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(tr(cat), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.width(76.dp))
                    ProgressBar(cover.toFloat(), Mf.colors.accent, Modifier.weight(1f), height = 6.dp)
                    Text(" ${(cover * 100).toInt()} %", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(48.dp))
                }
            }
        }
        if (open) {
            Micros.defs.filter { (totals[it.code] ?: 0.0) > 0 || it.code in s.microTargets }.groupBy { it.category }.forEach { (cat, list) ->
                Text(tr(cat), style = MaterialTheme.typography.labelLarge, color = Mf.colors.accent, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
                list.forEach { d -> NutrientRow(d, totals[d.code] ?: 0.0, target(d)) }
            }
            Text(trf("Données pour %1\$s aliment(s) sur %2\$s.", withData, entries.size), style = MaterialTheme.typography.bodySmall,
                color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 12.dp))
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(tr("Moyennes, sources et cibles"), style = MaterialTheme.typography.labelLarge, color = Mf.colors.accent,
                    modifier = Modifier.weight(1f).clip(RoundedCornerShape(8.dp)).clickable(onClick = onMore).padding(vertical = 4.dp))
                if (nav != null) app.superhealth.ui.coach.CoachChip(tr("Analyser"),
                    tr("Analyse les nutriments de ma journée (vitamines, minéraux, fibres, sodium, caféine) : qu'est-ce qui manque ou dépasse, et quels aliments simples ajouter ?"), nav)
            }
        }
    }
}

/** Repère : valeur, unité, et une barre vers l'apport conseillé (ou le plafond, qui vire à l'alerte une fois dépassé). */
@Composable
private fun KeyStat(d: Micros.Def, v: Double, target: Double?, modifier: Modifier) {
    val goal = target ?: d.limit
    val over = d.limit != null && v > d.limit
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(Mf.colors.chip).padding(horizontal = 8.dp, vertical = 8.dp)) {
        Text(tr(d.label.substringBefore(" (")), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(num(v, if (v < 10) 1 else 0), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1,
                color = if (over) Danger else Mf.colors.text)
            Text(" ${d.unit}", style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary, maxLines = 1)
        }
        if (goal != null) {
            Spacer(Modifier.height(4.dp))
            ProgressBar((v / goal).toFloat(), if (over) Danger else Mf.colors.accent, height = 3.dp, track = Mf.colors.card)
        }
    }
}

@Composable
private fun NutrientRow(d: Micros.Def, v: Double, target: Double?) {
    val over = d.limit != null && v > d.limit
    Column(Modifier.fillMaxWidth().padding(vertical = 5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(tr(d.label), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${num(v, if (v < 10) 1 else 0)} ${d.unit}" + (target?.let { " / ${num(it, if (it < 10) 1 else 0)}" }
                ?: d.limit?.let { " · " + trf("max %1\$s", num(it, 0)) } ?: ""),
                style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, color = if (over) Danger else Mf.colors.text)
        }
        (target ?: d.limit)?.let { g ->
            Spacer(Modifier.height(3.dp))
            ProgressBar((v / g).toFloat(), when { over -> Danger; target != null && v >= g -> Color(0xFF2EA043); else -> Mf.colors.accent }, height = 4.dp)
        }
    }
}

private val Danger = Color(0xFFE5484D)
