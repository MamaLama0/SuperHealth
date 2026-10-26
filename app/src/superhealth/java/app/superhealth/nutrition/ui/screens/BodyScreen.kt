package app.superhealth.nutrition.ui.screens

import app.superhealth.nutrition.ui.trf
import app.superhealth.nutrition.ui.tr
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.CompareArrows
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.content.FileProvider
import app.superhealth.SuperPrefs
import app.superhealth.nutrition.data.BodyMetricEntity
import app.superhealth.nutrition.data.ProgressPhotoEntity
import app.superhealth.nutrition.domain.BodyMetric
import app.superhealth.nutrition.domain.Habits
import app.superhealth.nutrition.domain.PhotoMilestones
import app.superhealth.nutrition.domain.PhotoPose
import app.superhealth.nutrition.domain.Sex
import app.superhealth.nutrition.ui.AppViewModel
import app.superhealth.nutrition.ui.components.ChipChoice
import app.superhealth.nutrition.ui.components.GroupCard
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.NumberField
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.components.RowDivider
import app.superhealth.nutrition.ui.components.SectionLabel
import app.superhealth.nutrition.ui.num
import app.superhealth.nutrition.ui.shortDate
import app.superhealth.nutrition.ui.toNum
import app.superhealth.nutrition.ui.weight
import app.superhealth.ui.charts.LineChart
import app.superhealth.ui.charts.Series
import app.superhealth.ui.theme.HealthHues
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.container
import app.superhealth.ui.theme.onContainer
import java.io.File
import java.time.LocalDate
import kotlin.math.abs
import kotlin.math.max
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Couleur de l'écran Corps (famille « corps » des cartes). */
private val BodyHue = HealthHues.Body

/** Paires gauche / droite affichées ensemble (une tuile « Biceps » plutôt que deux). */
private val pairs = listOf(
    BodyMetric.LEFT_BICEP to BodyMetric.RIGHT_BICEP, BodyMetric.LEFT_FOREARM to BodyMetric.RIGHT_FOREARM,
    BodyMetric.LEFT_WRIST to BodyMetric.RIGHT_WRIST, BodyMetric.LEFT_THIGH to BodyMetric.RIGHT_THIGH,
    BodyMetric.LEFT_CALF to BodyMetric.RIGHT_CALF, BodyMetric.LEFT_ANKLE to BodyMetric.RIGHT_ANKLE,
)
private fun pairLabel(m: BodyMetric) = when (m) {
    BodyMetric.LEFT_BICEP -> "Biceps"; BodyMetric.LEFT_FOREARM -> "Avant-bras"; BodyMetric.LEFT_WRIST -> "Poignets"
    BodyMetric.LEFT_THIGH -> "Cuisses"; BodyMetric.LEFT_CALF -> "Mollets"; BodyMetric.LEFT_ANKLE -> "Chevilles"; else -> m.label
}

/** Mesures proposées selon le sexe (poitrine ou buste, comme MacroFactor). */
internal fun bodyMetricsFor(sex: Sex) = BodyMetric.entries.filter { (it != BodyMetric.BUST || sex == Sex.FEMALE) && (it != BodyMetric.CHEST || sex == Sex.MALE) }

/** Photo enregistrée dans filesDir/photos : redressée (EXIF), réduite à 1600 px. */
internal fun savePhoto(ctx: Context, uri: Uri): String? = runCatching {
    val dir = File(ctx.filesDir, "photos").apply { mkdirs() }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    var sample = 1
    while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= 1600) sample *= 2
    var bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) }
        ?: return null
    // l'appareil photo enregistre souvent l'image couchée, avec l'orientation dans l'EXIF
    val rot = ctx.contentResolver.openInputStream(uri)?.use {
        when (android.media.ExifInterface(it).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
            android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> 0f
        }
    } ?: 0f
    if (rot != 0f) bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, Matrix().apply { postRotate(rot) }, true)
    val name = "p${System.currentTimeMillis()}.jpg"
    File(dir, name).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
    name
}.getOrNull()

/** Miniatures déjà décodées : la galerie ne clignote pas en défilant. */
private val thumbs = HashMap<String, ImageBitmap>()

@Composable
internal fun PhotoThumb(ctx: Context, file: String, modifier: Modifier, sample: Int = 4) {
    val key = "$file@$sample"
    val bmp = remember(key) {
        thumbs[key] ?: BitmapFactory.decodeFile(File(File(ctx.filesDir, "photos"), file).path, BitmapFactory.Options().apply { inSampleSize = sample })
            ?.asImageBitmap()?.also { thumbs[key] = it }
    }
    if (bmp != null) Image(bmp, null, modifier, contentScale = ContentScale.Crop) else Box(modifier.background(Mf.colors.chip))
}

/**
 * Corps : photos de progression (comparaison avant / après, paliers de l'objectif) et mesures (celles choisies, saisie en
 * feuille, tendance de chacune, comparaison de toutes les mesures entre la première et la dernière date), comme l'écran
 * « Body Metrics » de MacroFactor.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BodyScreen(vm: AppViewModel, onBack: () -> Unit) {
    val ctx = LocalContext.current
    val metrics by remember { vm.repo.body.metricsFlow() }.collectAsState(initial = emptyList())
    val photos by remember { vm.repo.body.photosFlow() }.collectAsState(initial = emptyList())
    val an by vm.analytics.collectAsState()
    val profile by vm.profile.collectAsState()
    val sh by SuperPrefs.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var pose by remember { mutableStateOf(PhotoPose.FRONT) }
    var viewing by remember { mutableStateOf<ProgressPhotoEntity?>(null) }
    var deleting by remember { mutableStateOf<ProgressPhotoEntity?>(null) }
    var editing by remember { mutableStateOf(false) }
    var choosing by remember { mutableStateOf(false) }
    var expanded by remember { mutableStateOf<String?>(null) }
    val today = LocalDate.now().toEpochDay()
    val due = an?.let { PhotoMilestones.due(it, photos) }

    fun added(name: String?) {
        if (name == null) return
        scope.launch {
            vm.repo.body.insertPhoto(ProgressPhotoEntity(epochDay = today, pose = pose.code, file = name))
            // photo prise : le palier atteint est fait
            an?.let { a -> PhotoMilestones.progressAt(a)?.let { p -> PhotoMilestones.markDone(a, PhotoMilestones.STEPS.last { it <= p * 100 + 1e-6 }) }
                ?: PhotoMilestones.markDone(a, 0) }
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { added(withContext(Dispatchers.IO) { savePhoto(ctx, uri) }) }
    }
    val shot = remember { File(File(ctx.cacheDir, "camera").apply { mkdirs() }, "shot.jpg") }
    val shotUri = remember { FileProvider.getUriForFile(ctx, ctx.packageName + ".files", shot) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) scope.launch { added(withContext(Dispatchers.IO) { savePhoto(ctx, shotUri).also { shot.delete() } }) }
    }
    fun takePhoto() = runCatching { camera.launch(shotUri) }.onFailure { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }

    val byMetric = metrics.groupBy { it.metric }
    val available = bodyMetricsFor(profile.sex)
    val tracked = available.filter { it.code in sh.bodyMetrics }

    DetailScaffold(tr("Mesures et photos"), onBack) {
        // ---- palier de l'objectif : photo proposée ----
        AnimatedVisibility(due != null, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
            val m = due ?: 0
            Column(Modifier.fillMaxWidth().padding(bottom = 12.dp).clip(RoundedCornerShape(24.dp)).background(BodyHue.container(1.3f)).padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(40.dp).clip(CircleShape).background(BodyHue), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.AddAPhoto, null, Modifier.size(22.dp), tint = Color.White)
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (m == 0) tr("Photo de départ") else trf("%1\$s %% de votre objectif", m), style = MaterialTheme.typography.titleMedium,
                            color = BodyHue.onContainer())
                        Text(if (m == 0) tr("Le point de comparaison des prochains paliers.") else tr("Un palier franchi : le bon moment pour comparer."),
                            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PillButton(tr("Prendre la photo"), Modifier.weight(1f), icon = Icons.Rounded.PhotoCamera) { pose = PhotoPose.FRONT; takePhoto() }
                    PillButton(tr("Plus tard"), Modifier.weight(0.7f), strong = false) { an?.let { PhotoMilestones.markDone(it, m) } }
                }
            }
        }

        // ---- photos ----
        SectionLabel(tr("Photos de progression"))
        val ofPose = photos.filter { it.pose == pose.code }.sortedBy { it.epochDay }
        MfCard {
            ChipChoice(PhotoPose.entries, pose, { it.label.tr() }) { pose = it }
            if (ofPose.size >= 2) {
                Spacer(Modifier.height(12.dp))
                Compare(ctx, ofPose, an) { viewing = it }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PillButton(tr("Photo"), Modifier.weight(1f), strong = false, icon = Icons.Rounded.PhotoCamera) { takePhoto() }
                PillButton(tr("Galerie"), Modifier.weight(1f), strong = false, icon = Icons.Rounded.PhotoLibrary) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
            Text(tr("Même lumière, même tenue, même distance : la comparaison n'en sera que plus juste. Photos privées, hors sauvegarde."),
                style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary, modifier = Modifier.padding(top = 8.dp))
        }
        photos.groupBy { it.epochDay }.forEach { (d, list) ->
            val pct = an?.let { PhotoMilestones.progressAt(it, d) }
            Text(shortDate(d) + (pct?.let { " · " + trf("%1\$s %% de l'objectif", (it * 100).toInt()) } ?: ""), style = MaterialTheme.typography.labelLarge,
                color = Mf.colors.textSecondary, modifier = Modifier.padding(start = 4.dp, top = 12.dp, bottom = 6.dp))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                list.forEach { p ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        PhotoThumb(ctx, p.file, Modifier.width(96.dp).aspectRatio(0.75f).clip(RoundedCornerShape(14.dp))
                            .combinedClickable(onClick = { viewing = p }, onLongClick = { deleting = p }))
                        Text(PhotoPose.of(p.pose).label.tr(), style = MaterialTheme.typography.labelSmall, color = Mf.colors.textSecondary)
                    }
                }
            }
        }

        // ---- mesures ----
        SectionLabel(tr("Mesures"))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PillButton(tr("Saisir"), Modifier.weight(1f), icon = Icons.Rounded.Edit) { editing = true }
            PillButton(tr("Choisir"), Modifier.weight(1f), strong = false, icon = Icons.Rounded.Tune) { choosing = true }
        }
        Spacer(Modifier.height(10.dp))
        // tuiles : une par mesure suivie, gauche et droite ensemble
        val tiles = buildList {
            tracked.forEach { m ->
                val pair = pairs.firstOrNull { it.first == m || it.second == m }
                when {
                    pair == null -> add(listOf(m))
                    m == pair.first || pair.first !in tracked -> add(listOfNotNull(pair.first.takeIf { it in tracked }, pair.second.takeIf { it in tracked }))
                }
            }
        }
        if (tiles.isEmpty()) MfCard { Text(tr("Aucune mesure suivie : touchez « Choisir »."), color = Mf.colors.textSecondary) }
        tiles.chunked(2).forEach { row ->
            Row(Modifier.padding(bottom = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { ms ->
                    val key = ms.first().code
                    MetricTile(ms, byMetric, expanded == key, Modifier.weight(1f)) { expanded = if (expanded == key) null else key }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
            // tendance de la tuile ouverte, sous sa rangée
            row.firstOrNull { it.first().code == expanded }?.let { ms ->
                MfCard(Modifier.padding(bottom = 8.dp)) {
                    Text(tr(if (ms.size > 1) pairLabel(ms.first()) else ms.first().label), style = MaterialTheme.typography.titleSmall)
                    val series = ms.mapIndexedNotNull { i, m -> byMetric[m.code]?.let { l ->
                        Series(l.map { it.epochDay to it.value }, if (i == 0) BodyHue else BodyHue.copy(alpha = 0.5f), dots = l.size < 3) } }
                    if (series.any { it.points.size >= 2 }) LineChart(series, Modifier.padding(top = 8.dp), height = 120.dp)
                    else Text(tr("Une deuxième mesure trace la tendance."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary,
                        modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        // ---- comparer toutes les mesures (première et dernière date) ----
        val days = metrics.filter { it.metric in sh.bodyMetrics }.map { it.epochDay }.distinct().sorted()
        if (days.size >= 2) {
            SectionLabel(tr("Comparer"))
            GroupCard {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Text("", Modifier.weight(1.4f))
                    Text(shortDate(days.first()), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, textAlign = TextAlign.End)
                    Text(shortDate(days.last()), Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, textAlign = TextAlign.End)
                    Text("Δ", Modifier.weight(0.8f), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary, textAlign = TextAlign.End)
                }
                tracked.forEach { m ->
                    val l = byMetric[m.code] ?: return@forEach
                    val first = l.firstOrNull { it.epochDay >= days.first() } ?: return@forEach
                    val last = l.last()
                    RowDivider()
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(m.label.tr(), Modifier.weight(1.4f), style = MaterialTheme.typography.bodyMedium)
                        Text(num(first.value, 1), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
                        Text(num(last.value, 1), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
                        val d = last.value - first.value
                        Text((if (d > 0) "+" else if (d < 0) "−" else "") + num(abs(d), 1), Modifier.weight(0.8f), style = MaterialTheme.typography.labelLarge,
                            color = BodyHue.onContainer(), textAlign = TextAlign.End)
                    }
                }
            }
        }

        // ---- rapports ----
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
        Spacer(Modifier.height(24.dp))
    }

    if (editing) MeasureSheet(tracked.ifEmpty { available }, byMetric, today, onDismiss = { editing = false }) { list, cleared ->
        vm.launch { body.upsertMetrics(list); cleared.forEach { body.deleteMetric(today, it) } }
        editing = false
    }
    if (choosing) ChooseSheet(available, sh.bodyMetrics) { choosing = false }
    deleting?.let { p ->
        AlertDialog(onDismissRequest = { deleting = null }, containerColor = Mf.colors.card,
            title = { Text(tr("Supprimer cette photo ?")) },
            confirmButton = { TextButton(onClick = {
                vm.launch { body.deletePhoto(p.id) }; File(File(ctx.filesDir, "photos"), p.file).delete(); deleting = null
            }) { Text(tr("Supprimer"), color = app.superhealth.ui.theme.Base.Red) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(tr("Annuler"), color = Mf.colors.text) } })
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

/**
 * Avant / après : la première photo de la pose et la dernière, côte à côte, avec la date, le poids tendance et l'avancée
 * vers l'objectif ; toucher une date en choisit une autre.
 */
@Composable
private fun Compare(ctx: Context, photos: List<ProgressPhotoEntity>, an: app.superhealth.nutrition.data.Analytics?, onOpen: (ProgressPhotoEntity) -> Unit) {
    var left by remember(photos.size) { mutableStateOf(photos.first()) }
    var right by remember(photos.size) { mutableStateOf(photos.last()) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(left to { p: ProgressPhotoEntity -> left = p }, right to { p: ProgressPhotoEntity -> right = p }).forEach { (p, set) ->
            Column(Modifier.weight(1f)) {
                PhotoThumb(ctx, p.file, Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(16.dp)).clickable { onOpen(p) }, sample = 2)
                var menu by remember { mutableStateOf(false) }
                Box {
                    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { menu = true }.padding(vertical = 6.dp, horizontal = 2.dp)) {
                        Text(shortDate(p.epochDay), style = MaterialTheme.typography.labelLarge)
                        val w = an?.trend?.lastOrNull { it.epochDay <= p.epochDay }?.trend
                        val pct = an?.let { PhotoMilestones.progressAt(it, p.epochDay) }
                        Text(listOfNotNull(w?.let { an.settings.weight(it, 1) }, pct?.let { trf("%1\$s %%", (it * 100).toInt()) }).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
                    }
                    androidx.compose.material3.DropdownMenu(menu, { menu = false }, containerColor = Mf.colors.card) {
                        photos.forEach { o -> androidx.compose.material3.DropdownMenuItem({ Text(shortDate(o.epochDay)) }, onClick = { set(o); menu = false }) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricTile(ms: List<BodyMetric>, byMetric: Map<String, List<BodyMetricEntity>>, open: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val first = ms.first()
    Column(modifier.clip(RoundedCornerShape(18.dp)).background(if (open) BodyHue.container(1.2f) else Mf.colors.card)
        .border(if (open) 1.5.dp else 0.dp, if (open) BodyHue else Color.Transparent, RoundedCornerShape(18.dp))
        .clickable(onClick = onClick).padding(12.dp)) {
        Text(tr(if (ms.size > 1) pairLabel(first) else first.label), style = MaterialTheme.typography.labelMedium, color = Mf.colors.textSecondary)
        val values = ms.map { byMetric[it.code] }
        if (values.all { it == null }) { Text("—", style = MaterialTheme.typography.titleLarge, color = Mf.colors.textTertiary); return@Column }
        Text(values.joinToString(" · ") { l -> l?.last()?.value?.let { num(it, 1) } ?: "—" } + " cm", style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold, maxLines = 1)
        // écart depuis la première mesure (de la première de la paire)
        values.firstOrNull { it != null && it.size > 1 }?.let { l ->
            val d = l.last().value - l.first().value
            Text((if (d > 0) "+" else if (d < 0) "−" else "") + trf("%1\$s cm depuis le %2\$s", num(abs(d), 1), shortDate(l.first().epochDay)),
                style = MaterialTheme.typography.labelSmall, color = BodyHue.onContainer(), maxLines = 1)
        }
    }
}

/** Saisie des mesures du jour (feuille), les mesures suivies seulement. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MeasureSheet(shown: List<BodyMetric>, byMetric: Map<String, List<BodyMetricEntity>>, today: Long, onDismiss: () -> Unit,
                         onSave: (List<BodyMetricEntity>, Set<String>) -> Unit) {
    val values = remember { mutableStateMapOf<String, String>().apply {
        shown.forEach { m -> byMetric[m.code]?.lastOrNull { it.epochDay == today }?.let { put(m.code, num(it.value, 1)) } }
    } }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Mf.colors.card) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding().imePadding().verticalScroll(rememberScrollState())) {
            Text(trf("Mesures du %1\$s", shortDate(today)), style = MaterialTheme.typography.titleLarge)
            Text(tr("À jeun, au même moment de la journée, ruban horizontal et non serré."), style = MaterialTheme.typography.bodySmall,
                color = Mf.colors.textSecondary)
            shown.chunked(2).forEach { pair ->
                Row {
                    pair.forEachIndexed { i, m ->
                        // dernière valeur connue en repère
                        val last = byMetric[m.code]?.lastOrNull { it.epochDay < today }?.value
                        NumberField(m.label.tr() + (last?.let { "  (" + num(it, 1) + ")" } ?: ""), values[m.code] ?: "", { values[m.code] = it },
                            Modifier.weight(1f).padding(start = if (i == 1) 6.dp else 0.dp, end = if (i == 0) 6.dp else 0.dp), suffix = "cm")
                    }
                    if (pair.size == 1) Spacer(Modifier.weight(1f))
                }
            }
            PillButton(tr("Enregistrer"), Modifier.fillMaxWidth().padding(top = 12.dp)) {
                onSave(values.mapNotNull { (k, v) -> v.toNum()?.takeIf { it > 0 }?.let { BodyMetricEntity(today, k, it) } }, values.filter { it.value.isBlank() }.keys)
            }
        }
    }
}

/** Mesures suivies (comme le choix des données visibles de MacroFactor). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChooseSheet(available: List<BodyMetric>, chosen: List<String>, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = Mf.colors.card) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 16.dp).navigationBarsPadding().verticalScroll(rememberScrollState())) {
            Text(tr("Mesures suivies"), style = MaterialTheme.typography.titleLarge)
            Text(tr("Seules celles-ci sont proposées à la saisie et sur l'écran."), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary,
                modifier = Modifier.padding(bottom = 8.dp))
            available.forEach { m ->
                val on = m.code in chosen
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable {
                    SuperPrefs.update { s -> s.copy(bodyMetrics = if (on) s.bodyMetrics - m.code else s.bodyMetrics + m.code) }
                }.padding(vertical = 10.dp, horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Straighten, null, Modifier.size(20.dp), tint = if (on) BodyHue else Mf.colors.textTertiary)
                    Spacer(Modifier.width(12.dp))
                    Text(m.label.tr(), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                    app.superhealth.nutrition.ui.components.Toggle(on, { SuperPrefs.update { s -> s.copy(bodyMetrics = if (on) s.bodyMetrics - m.code else s.bodyMetrics + m.code) } })
                }
            }
        }
    }
}
