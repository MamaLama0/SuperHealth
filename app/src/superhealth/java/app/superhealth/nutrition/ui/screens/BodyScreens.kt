package app.superhealth.nutrition.ui.screens

import app.superhealth.nutrition.ui.trf
import app.superhealth.nutrition.ui.tr
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.LocalFireDepartment
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.superhealth.nutrition.data.BodyMetricEntity
import app.superhealth.nutrition.data.ProgressPhotoEntity
import app.superhealth.nutrition.domain.BodyMetric
import app.superhealth.nutrition.domain.Habits
import app.superhealth.nutrition.domain.PhotoPose
import app.superhealth.nutrition.domain.Sex
import app.superhealth.nutrition.ui.AppViewModel
import app.superhealth.nutrition.ui.components.ChipChoice
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.ui.charts.LineChart
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.NumberField
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.ui.charts.Series
import app.superhealth.nutrition.ui.num
import app.superhealth.nutrition.ui.shortDate
import app.superhealth.ui.theme.Mf
import app.superhealth.nutrition.ui.toNum
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import kotlin.math.max
import kotlin.math.roundToInt

// ----- Habitudes -----

/** Page Habitudes : séries de journalisation et de pesée, calendrier des 12 dernières semaines. */
@Composable
fun HabitsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val a by vm.analytics.collectAsState()
    var which by remember { mutableStateOf(0) }
    DetailScaffold(tr("Habitudes"), onBack) {
        val an = a ?: return@DetailScaffold
        val logged = an.days.filter { it.intakeKcal != null || it.fasted }.map { it.epochDay }.toSet()
        val partial = an.days.filter { it.partial }.map { it.epochDay }.toSet()
        val weighed = an.days.filter { it.weightKg != null }.map { it.epochDay }.toSet()
        val food = Habits.streak(logged, an.today)
        val weigh = Habits.streak(weighed, an.today)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StreakCard(tr("Journal"), food, Icons.Rounded.LocalFireDepartment, Modifier.weight(1f))
            StreakCard(tr("Pesées"), weigh, Icons.Rounded.MonitorWeight, Modifier.weight(1f))
        }
        SectionLabel(tr("Calendrier"))
        ChipChoice(listOf(0, 1), which, { if (it == 0) tr("Journal alimentaire") else tr("Pesées") }) { which = it }
        Spacer(Modifier.height(10.dp))
        MfCard {
            val days = if (which == 0) logged else weighed
            val on = Mf.colors.accent; val half = on.copy(alpha = 0.45f); val off = Mf.colors.chip
            HabitGrid(an.today, weeks = 12) { d ->
                when {
                    d > an.today -> Color.Transparent
                    which == 0 && d in partial && d in days -> half
                    d in days -> on
                    else -> off
                }
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Legend(Mf.colors.accent, if (which == 0) tr("Journalisé") else tr("Pesée"))
                if (which == 0) Legend(Mf.colors.accent.copy(alpha = 0.45f), tr("Incomplet"))
                Legend(Mf.colors.chip, tr("Rien"))
            }
        }
        Text(tr("Un jour compte s'il est journalisé ou marqué jeûne."),
            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(8.dp))
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StreakCard(title: String, s: Habits.Streak, icon: ImageVector, modifier: Modifier) {
    MfCard(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (s.current > 0) Color(0xFFFF8A3D) else Mf.colors.textTertiary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(6.dp))
            Text(title, style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary)
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text("${s.current}", style = MaterialTheme.typography.displaySmall)
            Text(tr(" j"), style = MaterialTheme.typography.bodyMedium, color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 6.dp))
        }
        Text(tr("Série actuelle"), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        Spacer(Modifier.height(6.dp))
        Text(trf("Record : %1\$s j · %2\$s/30 j", s.longest, s.done30), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Box(Modifier.size(10.dp).clip(RoundedCornerShape(3.dp)).background(color))
    Text(" ${label}   ", style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
}

/** Grille semaines × jours (lundi en haut), la semaine courante à droite. */
@Composable
private fun HabitGrid(today: Long, weeks: Int, color: (Long) -> Color) {
    val monday = today - LocalDate.ofEpochDay(today).dayOfWeek.value + 1
    val first = monday - (weeks - 1) * 7
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            (1..7).map { app.superhealth.nutrition.ui.dayLetter(it) }.forEach { Text(it, fontSize = 9.sp, color = Mf.colors.textTertiary, modifier = Modifier.height(16.dp)) }
        }
        (0 until weeks).forEach { w ->
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                (0 until 7).forEach { d ->
                    val day = first + w * 7 + d
                    Box(Modifier.size(16.dp).clip(RoundedCornerShape(4.dp)).background(color(day)))
                }
            }
        }
    }
}

// ----- Mesures corporelles et photos -----

private fun savePhoto(ctx: Context, uri: Uri): String? = runCatching {
    val dir = File(ctx.filesDir, "photos").apply { mkdirs() }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
    val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        ?: return null
    val name = "p${System.currentTimeMillis()}.jpg"
    File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    name
}.getOrNull()

@Composable
private fun PhotoThumb(ctx: Context, file: String, modifier: Modifier) {
    val bmp = remember(file) {
        val f = File(File(ctx.filesDir, "photos"), file)
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = 4 })?.asImageBitmap()
    }
    if (bmp != null) Image(bmp, null, modifier, contentScale = ContentScale.Crop) else Box(modifier.background(Mf.colors.chip))
}

/** Mesures corporelles (BodyMetric) et photos de progression (front/side/back). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BodyScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val metrics by remember { vm.repo.body.metricsFlow() }.collectAsState(initial = emptyList())
    val photos by remember { vm.repo.body.photosFlow() }.collectAsState(initial = emptyList())
    val profile by vm.profile.collectAsState()
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<String?>(null) }
    var pose by remember { mutableStateOf(PhotoPose.FRONT) }
    var viewing by remember { mutableStateOf<ProgressPhotoEntity?>(null) }
    val today = LocalDate.now().toEpochDay()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            val name = withContext(Dispatchers.IO) { savePhoto(ctx, uri) } ?: return@launch
            vm.repo.body.insertPhoto(ProgressPhotoEntity(epochDay = today, pose = pose.code, file = name))
        }
    }
    val byMetric = metrics.groupBy { it.metric }
    val shown = BodyMetric.entries.filter { (it != BodyMetric.BUST || profile.sex == Sex.FEMALE) && (it != BodyMetric.CHEST || profile.sex == Sex.MALE) }

    DetailScaffold(tr("Mesures et photos"), onBack) {
        if (editing) {
            val values = remember { mutableStateMapOf<String, String>().apply {
                shown.forEach { m -> byMetric[m.code]?.lastOrNull { it.epochDay == today }?.let { put(m.code, num(it.value, 1)) } }
            } }
            MfCard {
                Text(trf("Mesures du %1\$s (cm)", (shortDate(today))), style = MaterialTheme.typography.titleMedium)
                Text(tr("Mesurez à jeun, au même moment de la journée, ruban horizontal et non serré."), style = MaterialTheme.typography.bodySmall,
                    color = Mf.colors.textSecondary)
                shown.chunked(2).forEach { pair ->
                    Row {
                        pair.forEachIndexed { i, m ->
                            NumberField(m.label.tr(), values[m.code] ?: "", { values[m.code] = it },
                                Modifier.weight(1f).padding(start = if (i == 1) 6.dp else 0.dp, end = if (i == 0) 6.dp else 0.dp), suffix = "cm")
                        }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            PillButton(tr("Enregistrer"), Modifier.fillMaxWidth()) {
                val list = values.mapNotNull { (k, v) -> v.toNum()?.takeIf { it > 0 }?.let { BodyMetricEntity(today, k, it) } }
                val cleared = values.filter { it.value.isBlank() }.keys
                vm.launch { body.upsertMetrics(list); cleared.forEach { body.deleteMetric(today, it) } }
                editing = false
            }
            PillButton(tr("Annuler"), Modifier.fillMaxWidth().padding(top = 8.dp), strong = false) { editing = false }
            return@DetailScaffold
        }
        PillButton(tr("Saisir les mesures du jour"), Modifier.fillMaxWidth(), icon = Icons.Rounded.Straighten) { editing = true }
        val waist = byMetric[BodyMetric.WAIST.code]?.lastOrNull()?.value
        val hips = byMetric[BodyMetric.HIPS.code]?.lastOrNull()?.value
        if (waist != null) {
            SectionLabel(tr("Rapports"))
            MfCard {
                Row {
                    Column(Modifier.weight(1f)) {
                        Text(num(Habits.waistToHeight(waist, profile.heightCm), 2), style = MaterialTheme.typography.headlineSmall)
                        Text(tr("Taille / hauteur"), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                    }
                    if (hips != null) Column(Modifier.weight(1f)) {
                        Text(num(Habits.waistToHip(waist, hips), 2), style = MaterialTheme.typography.headlineSmall)
                        Text(tr("Taille / hanches"), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                    }
                }
                Text(tr("Repère courant : un rapport taille / hauteur inférieur à 0,5."), style = MaterialTheme.typography.bodySmall,
                    color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
            }
        }
        val withData = shown.filter { byMetric[it.code] != null }
        SectionLabel(tr("Mesures"))
        if (withData.isEmpty()) MfCard { Text(tr("Aucune mesure pour l'instant."), color = Mf.colors.textSecondary) }
        else GroupCard {
            withData.forEachIndexed { i, m ->
                val list = byMetric.getValue(m.code)
                val last = list.last(); val first = list.first()
                if (i > 0) RowDivider()
                Column(Modifier.fillMaxWidth().clickable { expanded = if (expanded == m.code) null else m.code }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(m.label.tr(), style = MaterialTheme.typography.bodyMedium)
                            Text(shortDate(last.epochDay), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                        }
                        Text(trf("%1\$s cm", (num(last.value, 1))), style = MaterialTheme.typography.titleMedium)
                        if (list.size > 1) {
                            val dlt = last.value - first.value
                            Text("  ${if (dlt >= 0) "+" else "−"}${num(kotlin.math.abs(dlt), 1)}", style = MaterialTheme.typography.labelMedium,
                                color = Mf.colors.textSecondary, modifier = Modifier.width(52.dp))
                        }
                    }
                    if (expanded == m.code) LineChart(listOf(Series(list.map { it.epochDay to it.value }, Mf.colors.accent, dots = list.size < 3)),
                        Modifier.padding(top = 10.dp), height = 120.dp)
                }
            }
        }

        SectionLabel(tr("Photos de progression"))
        MfCard {
            ChipChoice(PhotoPose.entries, pose, { it.label.tr() }) { pose = it }
            Spacer(Modifier.height(8.dp))
            PillButton(trf("Ajouter une photo (%1\$s)", (pose.label.tr().lowercase())), Modifier.fillMaxWidth(), strong = false, icon = Icons.Rounded.AddAPhoto) {
                picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            }
            Text(tr("Photos privées, hors sauvegarde. Appui long pour supprimer."),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 6.dp))
        }
        photos.groupBy { it.epochDay }.forEach { (d, list) ->
            Text(shortDate(d), style = MaterialTheme.typography.labelLarge, color = Mf.colors.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                list.forEach { p ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PhotoThumb(ctx, p.file, Modifier.width(104.dp).aspectRatio(0.75f).clip(RoundedCornerShape(12.dp))
                            .combinedClickable(onClick = { viewing = p }, onLongClick = {
                                vm.launch { body.deletePhoto(p.id) }; File(File(ctx.filesDir, "photos"), p.file).delete()
                            }))
                        Text(PhotoPose.of(p.pose).label, style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
                    }
                }
            }
        }
        Spacer(Modifier.height(24.dp))
    }

    viewing?.let { p ->
        Dialog(onDismissRequest = { viewing = null }) {
            Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(Color.Black).clickable { viewing = null }) {
                val bmp = remember(p.file) { BitmapFactory.decodeFile(File(File(ctx.filesDir, "photos"), p.file).path)?.asImageBitmap() }
                if (bmp != null) Image(bmp, null, Modifier.fillMaxWidth(), contentScale = ContentScale.FillWidth)
            }
        }
    }
}
