package app.superhealth.ui.cards

import app.superhealth.platform.*

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.superhealth.cards.CardStore
import app.superhealth.cards.PhotoSource
import app.superhealth.ui.tr
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Photo de carte : choix, détourage par Vision (segmentation de personne d'iOS, sur l'appareil), aperçu sur la carte.
 * Android détoure avec MobileSAM et des points à placer ; sur iPhone le détourage est automatique.
 */
@Composable
fun CutoutScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by CardStore.state.collectAsState()
    val snap by CardStore.snapshot.collectAsState()
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<ByteArray?>(null) }
    var preview by remember { mutableStateOf<ImageBitmap?>(null) }

    val picker = rememberImagePicker { jpeg ->
        if (jpeg == null || busy) return@rememberImagePicker
        busy = true; error = null
        scope.launch {
            try {
                // Redessinée à 2048 px au plus : orientation de l'appareil photo appliquée, détourage plus rapide.
                val png = cutoutPerson(downscaleJpeg(jpeg, 2048, 95))
                if (png == null) error = tr("Aucune personne reconnue sur cette photo. Choisis-en une où tu es bien visible.")
                result = png; preview = png?.let { decodeImage(it) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: "?"
            } finally { busy = false }
        }
    }
    CardScaffold("Ma photo de carte", { nav.popBackStack() }, Color(0xFF1FA67A)) {
        val shown = preview
        if (shown == null) {
            // La carte et le profil du Groupe se règlent séparément : par défaut la carte reprend la photo de profil.
            Text(tr("Photo affichée sur ta carte"), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(bottom = 6.dp)) {
                PhotoSource.entries.forEach { src ->
                    val enabled = src != PhotoSource.DETOUREE || state.cutout
                    ModeChip(tr(src.label), state.photo == src, if (enabled) Color(0xFF6FD3A0) else Color.White.copy(alpha = 0.3f)) { if (enabled) { CardStore.update(ctx) { it.copy(photo = src) }; CardPhoto.publish(ctx) } }
                }
            }
            AthleteCard(myFace(snap?.now), Modifier.fillMaxWidth(0.5f).align(Alignment.CenterHorizontally).padding(vertical = 12.dp), interactive = false)
            if (app.superhealth.ui.ProfilePhoto.bitmap.value == null && state.photo == PhotoSource.PROFIL)
                Text(tr("Pas encore de photo de profil : ajoute-la dans ton profil, ou détoure une photo pour la carte."), color = Gold, fontSize = 13.sp)
            Text(tr("Choisis une photo où tu es bien visible, de la tête au torse. Le détourage se fait sur ton téléphone. Seule la photo détourée est partagée : tous les membres du Groupe la voient sur ta carte."),
                color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
            GlowButton(tr(if (busy) "Analyse de la photo…" else "Choisir une photo"), Modifier.fillMaxWidth().padding(top = 20.dp), listOf(Color(0xFF1FA67A), Color(0xFF6FD3A0)), enabled = !busy) { picker() }
            if (busy) CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally).padding(top = 24.dp), color = Color(0xFF6FD3A0))
            if (state.cutout) Text(tr("Retirer ma photo de la carte"), color = Color(0xFFFF7A8A), fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 24.dp).clickable { CardStore.cutoutFile(ctx).delete(); CardStore.update(ctx) { it.copy(cutout = false, photo = PhotoSource.PROFIL) }; CardPhoto.load(ctx); CardPhoto.publish(ctx) })
        } else {
            AthleteCard(myFace(snap?.now, cutoutOverride = shown), Modifier.fillMaxWidth(0.55f).align(Alignment.CenterHorizontally).padding(vertical = 12.dp), interactive = false)
            Row(Modifier.padding(vertical = 10.dp).align(Alignment.CenterHorizontally), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ModeChip(tr("Une autre photo"), false, Color.White) { if (!busy) { preview = null; result = null; picker() } }
            }
            GlowButton(tr("Garder cette photo"), Modifier.fillMaxWidth().padding(top = 12.dp), listOf(Color(0xFF1FA67A), Color(0xFF6FD3A0)), enabled = !busy) {
                val png = result ?: return@GlowButton
                scope.launch {
                    runCatching { CardStore.cutoutFile(ctx).writeBytes(png) }.onFailure { error = it.message; return@launch }
                    CardStore.update(ctx) { it.copy(cutout = true, photo = PhotoSource.DETOUREE) }; CardPhoto.load(ctx); CardPhoto.publish(ctx); Rumble.reveal(ctx); nav.popBackStack()
                }
            }
        }
        error?.let { Text(tr("Erreur") + " : " + it, color = Color(0xFFFF7A8A), modifier = Modifier.padding(top = 16.dp)) }
        Text(tr("Détourage : Vision, sur l'iPhone."), color = Color.White.copy(alpha = 0.35f), fontSize = 11.sp, textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 28.dp, bottom = 24.dp))
    }
}

@Composable
private fun ModeChip(text: String, on: Boolean, color: Color, onClick: () -> Unit) = Text(text, color = if (on) Ink else color, fontWeight = FontWeight.Bold, fontSize = 13.sp,
    modifier = Modifier.clip(RoundedCornerShape(50)).background(if (on) color else Color.White.copy(alpha = 0.08f)).clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp))
