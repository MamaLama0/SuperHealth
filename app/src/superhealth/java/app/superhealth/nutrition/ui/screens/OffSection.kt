package app.superhealth.nutrition.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.superhealth.nutrition.data.OpenFoodFacts
import app.superhealth.nutrition.ui.tr
import app.superhealth.nutrition.ui.trf
import app.superhealth.ui.theme.Mf

/**
 * Fiche Open Food Facts d'un produit à code-barres : photo, Nutri-Score, NOVA, Green-Score, ingrédients, allergènes,
 * additifs et labels. Ne retarde jamais la fiche aliment : déjà là si le scan l'a rapportée, sinon chargée en arrière-plan
 * et ajoutée en bas quand elle arrive (seul le code-barres est envoyé).
 */
@Composable
fun OffSection(code: String) {
    val d by produceState(OpenFoodFacts.cachedDetails(code), code) { if (value == null) value = OpenFoodFacts.details(code) }
    val details = d ?: return
    OffDetails(details)
}

@Composable
private fun OffDetails(d: OpenFoodFacts.Details) {
    Text(tr("Open Food Facts"), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 20.dp, bottom = 8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        d.imageUrl?.let { url ->
            val bmp by produceState<android.graphics.Bitmap?>(null, url) { value = OpenFoodFacts.image(url) }
            Box(Modifier.size(84.dp).clip(RoundedCornerShape(16.dp)).background(Color.White), contentAlignment = Alignment.Center) {
                bmp?.let { Image(it.asImageBitmap(), null, Modifier.size(84.dp), contentScale = ContentScale.Fit) }
            }
            Spacer(Modifier.width(12.dp))
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            d.nutriScore?.let { ScoreRow(tr("Nutri-Score"), it.uppercase(), gradeColor(it)) }
            d.nova?.let { ScoreRow(tr("NOVA"), "$it", novaColor(it), novaLabel(it)) }
            d.greenScore?.let { ScoreRow(tr("Green-Score"), if (it == "a-plus") "A+" else it.uppercase(), gradeColor(it.removeSuffix("-plus"))) }
            d.quantity?.let { Text(it, style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary) }
        }
    }
    if (d.allergens.isNotEmpty()) {
        Text(tr("Allergènes"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 14.dp, bottom = 4.dp))
        Chips(d.allergens, Color(0xFFE5484D))
    }
    if (d.additives.isNotEmpty()) {
        Text(trf("Additifs (%1\$s)", d.additives.size), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        Chips(d.additives, Color(0xFFF5A524))
    }
    if (d.labels.isNotEmpty()) {
        Text(tr("Labels"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        Chips(d.labels, Color(0xFF2EA043))
    }
    d.ingredients?.let { text ->
        var open by remember { mutableStateOf(false) }
        Text(tr("Ingrédients"), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, maxLines = if (open) Int.MAX_VALUE else 3, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.clickable { open = !open })
    }
    d.origins?.let { Text(trf("Origine : %1\$s", it), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 8.dp)) }
    Text(trf("Code-barres %1\$s · données Open Food Facts (ODbL)", d.code), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textTertiary,
        modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun ScoreRow(title: String, grade: String, color: Color, sub: String? = null) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Text(grade, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
        Spacer(Modifier.width(8.dp))
        Column {
            Text(title, style = MaterialTheme.typography.labelLarge)
            sub?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary) }
        }
    }
}

@Composable
private fun Chips(items: List<String>, color: Color) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        items.forEach {
            Text(it, Modifier.clip(CircleShape).background(color.copy(alpha = 0.15f)).padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.labelMedium, color = Mf.colors.text, maxLines = 1)
        }
    }
}

private fun gradeColor(g: String) = when (g) {
    "a" -> Color(0xFF038141); "b" -> Color(0xFF85BB2F); "c" -> Color(0xFFE6A700); "d" -> Color(0xFFEE8100); else -> Color(0xFFE63E11)
}

private fun novaColor(n: Int) = when (n) { 1 -> Color(0xFF00AA00); 2 -> Color(0xFFE6A700); 3 -> Color(0xFFFF6600); else -> Color(0xFFE63E11) }

private fun novaLabel(n: Int) = when (n) {
    1 -> tr("Non transformé"); 2 -> tr("Ingrédient culinaire"); 3 -> tr("Transformé"); else -> tr("Ultra-transformé")
}
