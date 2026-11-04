package app.superhealth.ui.league

import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.superhealth.league.SocialPosts
import app.superhealth.social.SpaceAttachment
import app.superhealth.ui.tr
import kotlinx.coroutines.launch
import java.io.File

@Composable
internal fun CircleMediaPicker(selected: (SpaceAttachment) -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch {
            busy = true; error = false
            try { selected(SocialPosts.prepareCircleMedia(ctx, uri)) }
            catch (e: kotlinx.coroutines.CancellationException) { throw e }
            catch (_: Exception) { error = true }
            finally { busy = false }
        }
    }
    TextButton(enabled = !busy, onClick = { picker.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)) }) { Text(tr("Photo ou vidéo")) }
    if (busy) LinearProgressIndicator()
    if (error) Text(tr("Média illisible ou trop volumineux."), color = MaterialTheme.colorScheme.error)
}

@Composable
internal fun CircleMediaView(media: SpaceAttachment) {
    val ctx = LocalContext.current
    var requested by remember(media.id) { mutableStateOf(false) }
    var error by remember { mutableStateOf(false) }
    val file by produceState<File?>(null, media.id, requested) {
        value=null
        if (requested) try {
            value = SocialPosts.downloadMedia(ctx, SocialPosts.Media(media.id, media.key, media.mime, media.hash, media.bytes), null, media.endpoint)
        } catch (e: kotlinx.coroutines.CancellationException) { throw e }
        catch (_: Exception) { error = true }
    }
    val loaded = file
    if (loaded == null) TextButton(onClick = { error = false; requested = !requested }) { Text(tr(if (error) "Réessayer" else "Afficher le média")) }
    else if (media.mime.startsWith("video/")) {
        var player by remember { mutableStateOf<VideoView?>(null) }
        DisposableEffect(Unit) { onDispose { player?.stopPlayback() } }
        AndroidView(factory = { context -> VideoView(context).apply {
            player = this
            setMediaController(android.widget.MediaController(context).also { it.setAnchorView(this) })
            setVideoPath(loaded.path)
        } }, modifier = Modifier.fillMaxWidth().height(240.dp))
    } else {
        val image = remember(loaded.path) { android.graphics.BitmapFactory.decodeFile(loaded.path)?.asImageBitmap() }
        image?.let { Image(it, tr("Photo partagée"), Modifier.fillMaxWidth().heightIn(max = 400.dp)) }
    }
}
