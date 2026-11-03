package app.superhealth.ui.stretch

import app.superhealth.platform.*

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import app.superhealth.nutrition.data.Net
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.stretch.Stretch
import app.superhealth.ui.SuperViewModel
import app.superhealth.ui.coach.askCoach
import app.superhealth.ui.coach.coachAvailable
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.onContainer
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Image du test, gardée dans le cache après la première lecture. Hors connexion : rien, les consignes et le niveau restent. */
@Composable
private fun NetImage(url: String, modifier: Modifier) {
    val ctx = LocalContext.current
    val bmp by produceState<ImageBitmap?>(null, url) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                val name = MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }
                val f = File(File(ctx.cacheDir, "mobility").apply { mkdirs() }, name)
                if (!f.isFile) { if (!Net.canSendSecret(url)) error("https"); val (status, data) = httpCall(url); check(status in 200..299 && data.size <= 8_000_000); f.writeBytes(data) }
                BitmapFactory.decodeFile(f.path)?.asImageBitmap()
            }.getOrNull()
        }
    }
    Box(modifier.clip(RoundedCornerShape(20.dp)).background(Mf.colors.chip), contentAlignment = Alignment.Center) {
        val b = bmp
        if (b != null) Image(b, null, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        else CircularProgressIndicator(Modifier.size(26.dp), color = StretchHue, strokeWidth = 2.5.dp)
    }
}

@Composable
private fun Bar(value: Int, average: Double?, modifier: Modifier) {
    Box(modifier.height(10.dp).clip(CircleShape).background(Mf.colors.chip)) {
        Box(Modifier.fillMaxWidth((value / 100f).coerceIn(0f, 1f)).height(10.dp).clip(CircleShape).background(StretchHue))
    }
}

/** Test de mobilité : un test par écran (par côté s'il le faut), on glisse jusqu'à l'image qui ressemble à sa position. */
@Composable
fun MobilityTestScreen(vm: SuperViewModel, nav: NavHostController) {
    val ctx = LocalContext.current
    val tests = remember { Stretch.mobility().tests }
    val results by Stretch.results.collectAsState()
    val stages = remember(tests) { tests.flatMap { t -> if (t.uni) listOf(t to "left", t to "right") else listOf(t to "both") } }
    var running by rememberSaveable { mutableStateOf(false) }
    var stage by rememberSaveable { mutableIntStateOf(0) }
    val answers = remember { mutableStateOf(mapOf<String, Int>()) }

    if (tests.isEmpty()) {
        DetailScaffold(tr("Test de mobilité"), onBack = { nav.popBackStack() }) {
            MfCard { Text(tr("Mettez à jour le plugin Étirements dans Réglages > Plugins."), color = Mf.colors.textSecondary) }
        }
        return
    }
    if (running) {
        val (t, side) = stages[stage.coerceIn(0, stages.lastIndex)]
        val key = "${t.id}:$side"
        val images = t.images(side)
        val count = minOf(images.size, t.pts.size).coerceAtLeast(1)
        val level = answers.value[key] ?: 0
        fun set(v: Int) { answers.value = answers.value + (key to v.coerceIn(0, count - 1)) }
        DetailScaffold(trf("Test %1\$s sur %2\$s", stage + 1, stages.size), onBack = { if (stage > 0) stage-- else running = false }) {
            Text(t.anatomy, style = MaterialTheme.typography.titleLarge)
            if (side != "both") Text(tr(if (side == "left") "Côté gauche" else "Côté droit"), style = MaterialTheme.typography.labelLarge, color = StretchHue.onContainer())
            Spacer(Modifier.height(10.dp))
            images.getOrNull(level)?.let { NetImage(Stretch.imageUrl(it), Modifier.fillMaxWidth().aspectRatio(1.3f)) }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(40.dp).clip(CircleShape).background(Mf.colors.chip).clickable { set(level - 1) }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Remove, tr("Moins"), tint = Mf.colors.text) }
                Slider(level.toFloat(), { set(it.toInt()) }, Modifier.weight(1f).padding(horizontal = 8.dp), valueRange = 0f..(count - 1).coerceAtLeast(1).toFloat(), steps = (count - 2).coerceAtLeast(0),
                    colors = SliderDefaults.colors(thumbColor = StretchHue, activeTrackColor = StretchHue))
                Box(Modifier.size(40.dp).clip(CircleShape).background(Mf.colors.chip).clickable { set(level + 1) }, contentAlignment = Alignment.Center) { Icon(Icons.Rounded.Add, tr("Plus"), tint = Mf.colors.text) }
            }
            Spacer(Modifier.height(6.dp))
            t.steps.forEachIndexed { i, s ->
                Row(Modifier.padding(vertical = 2.dp)) {
                    Text("${i + 1}", style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary, modifier = Modifier.width(20.dp))
                    Text(s, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Spacer(Modifier.height(12.dp))
            PillButton(tr(if (stage < stages.lastIndex) "Suivant" else "Voir mon score"), Modifier.fillMaxWidth()) {
                if (stage < stages.lastIndex) stage++
                else {
                    val levels = tests.associate { x -> x.id to (if (x.uni) listOf("left", "right") else listOf("both")).map { answers.value["${x.id}:$it"] ?: 0 } }
                    Stretch.saveResult(ctx, Stretch.MResult(System.currentTimeMillis(), levels))
                    running = false; stage = 0; answers.value = emptyMap()
                }
            }
            Spacer(Modifier.height(24.dp))
        }
        return
    }

    DetailScaffold(tr("Test de mobilité"), onBack = { nav.popBackStack() }) {
        val last = results.lastOrNull()
        if (last == null) MfCard {
            Text(tr("Faites chaque position, puis choisissez l'image qui vous ressemble le plus. Un mur et un peu de place suffisent."), style = MaterialTheme.typography.bodyMedium)
        } else {
            val profile = vm.nutrition.profile.collectAsState().value
            val avg = remember(profile) { Stretch.average(LocalDate.now().year - profile.birthYear, profile.sex == app.superhealth.nutrition.domain.Sex.MALE) }
            val score = Stretch.globalScore(last) ?: 0
            MfCard {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text("$score", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = StretchHue.onContainer())
                    Text(" / 100", style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
                    Spacer(Modifier.weight(1f))
                    avg?.let { Text(trf("Moyenne %1\$s", it[0].toInt()), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 6.dp)) }
                }
                Spacer(Modifier.height(8.dp))
                Stretch.FOCI.forEachIndexed { i, f ->
                    val v = Stretch.focusScore(f, last) ?: return@forEachIndexed
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(Stretch.focusLabel(f)), style = MaterialTheme.typography.bodySmall, maxLines = 1, modifier = Modifier.width(140.dp))
                        Bar(v, avg?.getOrNull(i + 1), Modifier.weight(1f))
                        Text("$v", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 8.dp).width(28.dp))
                        Text(avg?.getOrNull(i + 1)?.toInt()?.let { "/$it" } ?: "", style = MaterialTheme.typography.labelMedium, color = Mf.colors.textTertiary, modifier = Modifier.width(30.dp))
                    }
                }
            }
            Text(tr("Niveaux et points de GOWOD. Score global reconstitué : GOWOD le calcule sur son serveur."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textTertiary, modifier = Modifier.padding(start = 4.dp, top = 8.dp))
            if (results.size > 1) {
                SectionLabel(tr("Progression"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    results.takeLast(6).forEach { r ->
                        Column(Modifier.weight(1f).clip(RoundedCornerShape(14.dp)).background(Mf.colors.card).padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("${Stretch.globalScore(r) ?: 0}", style = MaterialTheme.typography.titleSmall)
                            Text(app.superhealth.platform.Instant.ofEpochMilli(r.at).atZone(app.superhealth.platform.ZoneId.systemDefault()).toLocalDate().let { "%02d/%02d".format(it.dayOfMonth, it.monthValue) },
                                style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
                        }
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
            PillButton(tr("Routine sur mes points faibles"), Modifier.fillMaxWidth(), strong = false) {
                preset.value = Stretch.weakGroups(last); nav.popBackStack()
            }
            if (coachAvailable()) {
                Spacer(Modifier.height(8.dp))
                PillButton(tr("Demander au coach"), Modifier.fillMaxWidth(), strong = false, icon = Icons.Rounded.AutoAwesome) {
                    val detail = Stretch.FOCI.joinToString { f -> "$f ${Stretch.focusScore(f, last) ?: "?"}" }
                    nav.askCoach(trf("Mon test de mobilité donne %1\$s/100 (%2\$s). Que travailler en priorité vu mes séances, et avec quelle routine ?", score, detail))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        PillButton(tr(if (last == null) "Commencer le test" else "Refaire le test"), Modifier.fillMaxWidth()) { stage = 0; answers.value = emptyMap(); running = true }
        Spacer(Modifier.height(24.dp))
    }
}
