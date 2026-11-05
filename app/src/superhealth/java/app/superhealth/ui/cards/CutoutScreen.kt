package app.superhealth.ui.cards

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.superhealth.cards.CardStore
import app.superhealth.cards.Cutout
import app.superhealth.cards.PhotoSource
import app.superhealth.ui.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Photo de carte : choix, détourage MobileSAM, retouches par points, aperçu sur la carte. */
@Composable
fun CutoutScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by CardStore.state.collectAsState()
    val snap by CardStore.snapshot.collectAsState()
    var installed by remember { mutableStateOf(Cutout.installed(ctx)) }
    var downloading by remember { mutableStateOf<Float?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf<Cutout.Session?>(null) }
    val points = remember { mutableStateListOf<Cutout.Session.Point>() }
    var keepMode by remember { mutableStateOf(true) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }
    var result by remember { mutableStateOf<Bitmap?>(null) }
    DisposableEffect(Unit) { onDispose { session?.close() } }

    fun recompute() {
        val s = session ?: return
        if (points.none { it.keep }) { result = null; preview = null; return }
        if (busy) return
        val snapshot = points.toList()
        busy = true
        scope.launch {
            try {
                val out = withContext(Dispatchers.Default) { s.render(s.mask(snapshot)) }
                result = out; preview = out?.asImageBitmap()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "?"
            } finally { busy = false }
        }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null || busy) return@rememberLauncherForActivityResult
        busy = true; error = null
        scope.launch {
            runCatching {
                val bmp = withContext(Dispatchers.IO) {
                    if (Build.VERSION.SDK_INT >= 28) ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, info, _ ->
                        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val k = 2048f / maxOf(info.size.width, info.size.height); if (k < 1f) d.setTargetSize((info.size.width * k).toInt().coerceAtLeast(1), (info.size.height * k).toInt().coerceAtLeast(1))
                    } else {
                        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                        ctx.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
                        require(options.outWidth > 0 && options.outHeight > 0) { "Cannot decode photo" }
                        options.inSampleSize = 1
                        while (maxOf(options.outWidth, options.outHeight) / options.inSampleSize > 2048) options.inSampleSize *= 2
                        options.inJustDecodeBounds = false
                        ctx.contentResolver.openInputStream(uri).use { BitmapFactory.decodeStream(it, null, options) }
                    } ?: error("Cannot decode photo")
                }
                session?.close(); session = null
                val s = Cutout.Session.open(ctx, bmp)
                session = s
                points.clear()
                // Prompt automatique : le centre du cadre et le haut du torse, là où se trouve le sujet d'une photo de profil.
                points += Cutout.Session.Point(s.image.width * 0.5f, s.image.height * 0.38f, true)
                busy = false
                recompute()
            }.onFailure {
                busy = false
                if (it is CancellationException) throw it
                error = it.message ?: "?"
            }
        }
    }
    CardScaffold("Ma photo de carte", { nav.popBackStack() }, Color(0xFF1FA67A)) {
        when {
            !installed -> {
                Text(tr("Le détourage se fait sur ton téléphone : la photo ne quitte jamais l'appareil. Il faut d'abord télécharger le modèle MobileSAM (45 Mo, une seule fois)."),
                    color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp))
                downloading?.let { p ->
                    LinearProgressIndicator({ p }, Modifier.fillMaxWidth().padding(top = 20.dp), color = Color(0xFF6FD3A0), trackColor = Color.White.copy(alpha = 0.1f))
                    Text("${(p * 100).toInt()} %", color = Color.White, modifier = Modifier.padding(top = 6.dp))
                }
                GlowButton(tr("Télécharger le modèle"), Modifier.fillMaxWidth().padding(top = 20.dp), listOf(Color(0xFF1FA67A), Color(0xFF6FD3A0)), enabled = downloading == null) {
                    downloading = 0f; error = null
                    scope.launch {
                        runCatching { Cutout.download(ctx) { p -> downloading = p } }.onSuccess { installed = Cutout.installed(ctx) }.onFailure { error = it.message }
                        downloading = null
                    }
                }
            }
            session == null -> {
                // La carte et le profil du Groupe se règlent séparément : par défaut la carte reprend la photo de profil.
                Text(tr("Photo affichée sur ta carte"), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                    PhotoSource.entries.forEach { src ->
                        val enabled = src != PhotoSource.DETOUREE || state.cutout
                        ModeChip(tr(src.label), state.photo == src, if (enabled) Color(0xFF6FD3A0) else Color.White.copy(alpha = 0.3f)) { if (enabled) CardStore.update(ctx) { it.copy(photo = src) } }
                    }
                }
                AthleteCard(myFace(snap?.now), Modifier.fillMaxWidth(0.5f).align(Alignment.CenterHorizontally).padding(vertical = 12.dp), interactive = false)
                if (app.superhealth.ui.ProfilePhoto.bitmap.value == null && state.photo == PhotoSource.PROFIL)
                    Text(tr("Pas encore de photo de profil : ajoute-la dans ton profil, ou détoure une photo pour la carte."), color = Gold, fontSize = 13.sp)
                Text(tr("Choisis une photo où tu es bien visible, de la tête au torse. Un personnage dessiné marche aussi."), color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
                GlowButton(tr(if (busy) "Analyse de la photo…" else "Choisir une photo"), Modifier.fillMaxWidth().padding(top = 20.dp), listOf(Color(0xFF1FA67A), Color(0xFF6FD3A0)), enabled = !busy) {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
                if (busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp), color = Color(0xFF6FD3A0))
                if (state.cutout) Text(tr("Retirer ma photo de la carte"), color = Color(0xFFFF7A8A), fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 24.dp).clickable { CardStore.cutoutFile(ctx).delete(); CardStore.update(ctx) { it.copy(cutout = false, photo = PhotoSource.PROFIL) }; CardPhoto.load(ctx) })
            }
            else -> Editor(session!!, points, keepMode, busy, preview, onMode = { keepMode = it }, onTap = { p ->
                    if (!busy) {
                    // Un appui sur un point existant le retire ; sinon il ajoute un point « garder » ou « retirer ».
                    val near = points.indexOfFirst { kotlin.math.hypot(it.x - p.x, it.y - p.y) < session!!.image.width * 0.04f }
                    if (near >= 0) points.removeAt(near) else points += p.copy(keep = keepMode)
                    recompute()
                    }
                },
                onUndo = { if (!busy && points.isNotEmpty()) { points.removeAt(points.lastIndex); recompute() } },
                onClear = { if (!busy) { points.clear(); recompute() } },
                onAnother = { if (!busy) { session?.close(); session = null; preview = null; result = null } },
                card = { AthleteCard(myFace(snap?.now, cutoutOverride = preview), Modifier.fillMaxWidth(0.55f), interactive = false) },
                onSave = {
                    val r = result ?: return@Editor
                    scope.launch {
                        withContext(Dispatchers.IO) { CardStore.cutoutFile(ctx).outputStream().use { r.compress(Bitmap.CompressFormat.PNG, 100, it) } }
                        CardStore.update(ctx) { it.copy(cutout = true, photo = PhotoSource.DETOUREE) }; CardPhoto.load(ctx); Rumble.reveal(ctx); nav.popBackStack()
                    }
                })
        }
        error?.let { Text(tr("Erreur") + " : " + it, color = Color(0xFFFF7A8A), modifier = Modifier.padding(top = 16.dp)) }
        Text(tr("Modèle : MobileSAM (Apache-2.0), même approche que ReFra."), color = Color.White.copy(alpha = 0.35f), fontSize = 11.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 24.dp))
    }
}

@Composable
private fun Editor(session: Cutout.Session, points: List<Cutout.Session.Point>, keepMode: Boolean, busy: Boolean, preview: ImageBitmap?, onMode: (Boolean) -> Unit,
                   onTap: (Cutout.Session.Point) -> Unit, onUndo: () -> Unit, onClear: () -> Unit, onAnother: () -> Unit, card: @Composable () -> Unit, onSave: () -> Unit) {
    val image = remember(session) { session.image.asImageBitmap() }
    var box by remember { mutableStateOf(IntSize.Zero) }
    Text(tr("Touche la photo pour ajouter ce qu'il faut garder ou retirer. Touche un point pour l'enlever."), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
    Row(Modifier.padding(vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ModeChip(tr("＋ Garder"), keepMode, Color(0xFF6FD3A0)) { onMode(true) }
        ModeChip(tr("－ Retirer"), !keepMode, Color(0xFFFF7A8A)) { onMode(false) }
        ModeChip(tr("Annuler"), false, Color.White) { onUndo() }
        ModeChip(tr("Tout effacer"), false, Color.White) { onClear() }
    }
    Box(Modifier.fillMaxWidth().aspectRatio(session.image.width / session.image.height.toFloat()).clip(RoundedCornerShape(16.dp)).background(Color.Black)
        .onSizeChanged { box = it }.pointerInput(session) {
            detectTapGestures { o -> val k = session.image.width / size.width.toFloat(); onTap(Cutout.Session.Point(o.x * k, o.y * k, true)) }
        }) {
        Image(image, null, Modifier.fillMaxSize(), contentScale = ContentScale.FillBounds, alpha = if (preview != null) 0.35f else 1f)
        Canvas(Modifier.fillMaxSize()) {
            val k = size.width / session.image.width
            points.forEach { p ->
                val c = if (p.keep) Color(0xFF6FD3A0) else Color(0xFFFF7A8A)
                drawCircle(c, 9.dp.toPx(), Offset(p.x * k, p.y * k), style = Stroke(3.dp.toPx())); drawCircle(c.copy(alpha = 0.5f), 4.dp.toPx(), Offset(p.x * k, p.y * k))
            }
        }
        if (busy) CircularProgressIndicator(Modifier.align(Alignment.Center), color = Color.White)
    }
    Box(Modifier.fillMaxWidth().padding(top = 16.dp), contentAlignment = Alignment.Center) { card() }
    GlowButton(tr("Utiliser sur ma carte"), Modifier.fillMaxWidth().padding(top = 16.dp), listOf(Color(0xFF1FA67A), Color(0xFF6FD3A0)), enabled = preview != null && !busy, onClick = onSave)
    Text(tr("Choisir une autre photo"), color = Color.White.copy(alpha = 0.7f), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 14.dp).clickable(onClick = onAnother))
}

@Composable
private fun ModeChip(text: String, on: Boolean, color: Color, onClick: () -> Unit) = Text(text, color = if (on) Ink else color, fontWeight = FontWeight.Bold, fontSize = 13.sp,
    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) color else Color.White.copy(alpha = 0.08f)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp))
