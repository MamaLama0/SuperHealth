package app.superhealth.ui.league

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.superhealth.ui.theme.Mf
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import app.rive.*
import app.superhealth.nutrition.ui.components.AssetImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private object StreakFile {
    @Volatile private var bytes: ByteArray? = null
    @Synchronized fun load(context: android.content.Context): ByteArray {
        bytes?.let { return it }
        app.rive.runtime.kotlin.core.Rive.init(context.applicationContext)
        return context.assets.open("game/streak.riv").use { it.readBytes() }.also { bytes = it }
    }
}

/** Fichier local : aucune requête, 30 images/s maximum, arrêt hors écran et en arrière-plan. */
@Composable
internal fun StreakAnimation(count: Int, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycle by LocalLifecycleOwner.current.lifecycle.currentStateFlow.collectAsState()
    val view = LocalView.current
    val motion = remember(context) { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f }
    if (!motion) { StaticStreak(count, modifier); return }
    val bytes by produceState<ByteArray?>(null) {
        value = withContext(Dispatchers.IO) {
            runCatching { StreakFile.load(context) }.onFailure { Log.w("StreakArt", "Cannot load Rive", it) }.getOrNull()
        }
    }
    val error = remember { mutableStateOf<Throwable?>(null) }
    val data = bytes
    if (data == null) { Box(modifier); return }
    val worker = rememberRiveWorkerOrNull(error)
    if (worker == null) { if (error.value != null) StaticStreak(count, modifier) else Box(modifier); return }
    val modelSource = remember { ViewModelSource.Named("UserStreakVM").defaultInstance() }
    when (val file = rememberRiveFile(remember(data) { RiveFileSource.Bytes(data) }, worker)) {
        is Result.Success -> {
            when (val model = rememberViewModelInstanceResult(file.value, modelSource)) {
                is Result.Success -> {
                    val artboard = rememberArtboardResult(file.value, "streak")
                    LaunchedEffect(model.value, count) {
                        model.value.setColor("dark", streakColor(count).toArgb())
                        model.value.setColor("light", Color.White.toArgb())
                        model.value.setNumber("streak", count.coerceIn(0, 999).toFloat())
                    }
                    var visible by remember { mutableStateOf(true) }
                    if (artboard !is Result.Success) { Box(modifier); return }
                    val machine = rememberStateMachineResult(artboard.value, "State Machine 1")
                    if (machine !is Result.Success) { Box(modifier); return }
                    val active = visible && lifecycle.isAtLeast(Lifecycle.State.STARTED)
                    Box(modifier.onGloballyPositioned {
                        val r = it.boundsInWindow()
                        visible = !r.isEmpty && r.bottom > 0 && r.top < view.height && r.right > 0 && r.left < view.width
                    }) {
                        Rive(file.value, Modifier.matchParentSize().graphicsLayer(scaleX = 1.4f, scaleY = 1.4f), playing = active,
                            artboard = artboard.value, stateMachine = machine.value,
                            viewModelInstance = model.value, frameRate = RiveFrameRate.Capped(30f),
                            pointerInputMode = RivePointerInputMode.Observe)
                    }
                }
                else -> Box(modifier)
            }
        }
        else -> Box(modifier)
    }
}

@Composable
private fun StaticStreak(count: Int, modifier: Modifier) {
    Box(modifier, contentAlignment = Alignment.Center) {
        AssetImage("game/flame.png", Modifier.matchParentSize())
        Text(count.toString(), Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
            .background(Mf.colors.card, CircleShape).padding(horizontal = 8.dp, vertical = 1.dp),
            style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

internal fun streakColor(count: Int): Color = when {
    count >= 100 -> Color(0xFF9B6DFF)
    count >= 30 -> Color(0xFF39B9F8)
    count >= 14 -> Color(0xFFEF5261)
    count >= 7 -> Color(0xFFFFB628)
    else -> Color(0xFFFF7C25)
}
