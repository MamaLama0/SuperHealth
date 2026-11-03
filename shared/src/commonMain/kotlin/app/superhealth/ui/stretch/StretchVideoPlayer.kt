package app.superhealth.ui.stretch

import app.superhealth.platform.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.superhealth.stretch.Stretch
import app.superhealth.ui.tr
import app.superhealth.ui.theme.Mf

/** AVPlayerViewController apporte lecture, recherche et plein écran avec les commandes accessibles iOS. */
@Composable
internal fun StretchVideo(id: String,modifier: Modifier,playing: Boolean=true,onPlayingChange: ((Boolean)->Unit)?=null,status: String?=null) {
    var localPlaying by remember(id) { mutableStateOf(playing) }
    val wanted=if(onPlayingChange==null)localPlaying else playing
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val url=remember(id) { Stretch.videoUrl(id) }
    Box(modifier.background(Color.Black)) {
        if(id.startsWith("custom-") || url.isBlank()) Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center) {
            Icon(Icons.Rounded.AccessibilityNew,tr("Exercice personnel"),Modifier.size(72.dp),tint=Mf.colors.accent)
        } else NativeVideo(url,wanted && lifecycle.isAtLeast(Lifecycle.State.RESUMED),onPlayingChange==null,Modifier.fillMaxSize())
        if(onPlayingChange!=null) IconButton(onClick={onPlayingChange(!wanted)},modifier=Modifier.align(Alignment.BottomStart)) {
            Icon(if(wanted)Icons.Rounded.Pause else Icons.Rounded.PlayArrow,tr(if(wanted)"Pause" else "Reprendre"),tint=Color.White)
        }
        status?.let { Text(it,color=Color.White,modifier=Modifier.align(Alignment.BottomEnd).background(Color.Black.copy(alpha=.6f)).padding(8.dp)) }
    }
}
