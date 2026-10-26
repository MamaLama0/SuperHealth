package app.superhealth.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.DashCard
import app.superhealth.SuperPrefs
import app.superhealth.nutrition.data.Analytics
import app.superhealth.nutrition.domain.BodyMetric
import app.superhealth.nutrition.domain.PhotoMilestones
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.formatNumber
import app.superhealth.ui.go
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr
import app.superhealth.ui.trf

/**
 * Grande carte « Mesures et photos » : la dernière photo, les mesures suivies et, quand un palier de l'objectif est
 * franchi, la proposition de photo (la même que dans « À faire »).
 */
@Composable
internal fun BodyCard(c: DashCard, vm: SuperViewModel, an: Analytics, modifier: Modifier, nav: NavHostController) {
    val ctx = LocalContext.current
    val metrics by remember { vm.nutrition.repo.body.metricsFlow() }.collectAsState(initial = emptyList())
    val photos by remember { vm.nutrition.repo.body.photosFlow() }.collectAsState(initial = emptyList())
    val sh by SuperPrefs.settings.collectAsState()
    val due = PhotoMilestones.due(an, photos)
    val hue = c.ink()
    Tile(c, tr("Mesures et photos"), modifier, trailing = PhotoMilestones.progressAt(an)?.let { trf("%1\$s %% de l'objectif", (it * 100).toInt()) },
        onClick = { nav.go("body") }) {
        due?.let { m ->
            Row(Modifier.fillMaxWidth().padding(bottom = 10.dp).clip(RoundedCornerShape(14.dp)).background(hue.container(1.2f)).padding(10.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.AddAPhoto, null, Modifier.size(18.dp), tint = hue.onContainer())
                Spacer(Modifier.width(8.dp))
                Text(if (m == 0) tr("Photo de départ") else trf("%1\$s %% de votre objectif : photo", m), style = MaterialTheme.typography.labelLarge,
                    color = hue.onContainer())
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            photos.firstOrNull()?.let { p ->
                app.superhealth.nutrition.ui.screens.PhotoThumb(ctx, p.file, Modifier.width(72.dp).aspectRatio(0.75f).clip(RoundedCornerShape(14.dp)))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                val byMetric = metrics.groupBy { it.metric }
                val shown = sh.bodyMetrics.mapNotNull { BodyMetric.of(it) }.filter { byMetric[it.code] != null }.take(4)
                if (shown.isEmpty()) Text(tr("Ajoutez vos mesures : tour de taille, bras, cuisses…"), style = MaterialTheme.typography.bodySmall,
                    color = Mf.colors.textSecondary)
                shown.forEach { m ->
                    val l = byMetric.getValue(m.code)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(m.label), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(formatNumber(l.last().value, 1) + " cm", style = MaterialTheme.typography.titleSmall, color = hue.onContainer())
                        if (l.size > 1) {
                            val d = l.last().value - l.first().value
                            Text("  " + (if (d > 0) "+" else if (d < 0) "−" else "") + formatNumber(kotlin.math.abs(d), 1),
                                style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
                        }
                    }
                }
            }
        }
    }
}
