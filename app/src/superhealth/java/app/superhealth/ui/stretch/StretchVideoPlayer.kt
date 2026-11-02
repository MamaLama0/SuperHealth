package app.superhealth.ui.stretch

import app.superhealth.ui.theme.Mf

import android.content.Context
import android.view.accessibility.AccessibilityManager
import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import app.superhealth.stretch.Stretch
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlinx.coroutines.delay
import nodomain.freeyourgadget.gadgetbridge.R

/** One silent, looping player for the current exercise. Decoder and network buffer are released off-screen. */
@OptIn(UnstableApi::class)
@Composable
internal fun StretchVideo(
    id: String,
    modifier: Modifier,
    playing: Boolean = true,
    onPlayingChange: ((Boolean) -> Unit)? = null,
    status: String? = null,
) {
    if (id.startsWith("custom-")) {
        Box(modifier.background(Mf.colors.card), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AccessibilityNew, app.superhealth.ui.tr("Exercice personnel"), Modifier.size(72.dp), tint = Mf.colors.accent)
        }
        return
    }
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val lifecycleState by owner.lifecycle.currentStateFlow.collectAsState()
    val foreground = lifecycleState.isAtLeast(Lifecycle.State.RESUMED)
    var localPlaying by rememberSaveable(id) { mutableStateOf(playing) }
    val wanted = if (onPlayingChange == null) localPlaying else playing
    val wantedNow by rememberUpdatedState(wanted)
    val activeNow by rememberUpdatedState(foreground)
    var position by rememberSaveable(id) { mutableLongStateOf(0L) }
    var duration by remember(id) { mutableLongStateOf(0L) }
    var fullscreen by rememberSaveable { mutableStateOf(false) }
    var speed by rememberSaveable { mutableFloatStateOf(1f) }
    var showControls by remember(id) { mutableStateOf(true) }
    var showInstructions by rememberSaveable(id) { mutableStateOf(false) }
    var ready by remember(id) { mutableStateOf(false) }
    var firstFrame by remember(id) { mutableStateOf(false) }
    var buffering by remember(id) { mutableStateOf(true) }
    var failed by remember(id) { mutableStateOf(false) }
    var retryGeneration by remember(id) { mutableIntStateOf(0) }
    var player by remember(id) { mutableStateOf<ExoPlayer?>(null) }
    var drag by remember(id) { mutableStateOf<Float?>(null) }
    val accessibility = remember(ctx.applicationContext) {
        ctx.applicationContext.getSystemService(Context.ACCESSIBILITY_SERVICE) as? AccessibilityManager
    }
    var touchExploration by remember(accessibility) { mutableStateOf(accessibility?.isTouchExplorationEnabled == true) }
    DisposableEffect(accessibility, owner) {
        val listener = AccessibilityManager.TouchExplorationStateChangeListener { touchExploration = it }
        var registered = false
        fun register() {
            touchExploration = accessibility?.isTouchExplorationEnabled == true
            if (!registered && accessibility != null) {
                accessibility.addTouchExplorationStateChangeListener(listener)
                registered = true
            }
        }
        fun unregister() {
            if (registered) accessibility?.removeTouchExplorationStateChangeListener(listener)
            registered = false
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> register()
                Lifecycle.Event.ON_STOP -> unregister()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) register()
        onDispose { owner.lifecycle.removeObserver(observer); unregister() }
    }
    val url = remember(id) { Stretch.videoUrl(id) }
    val title = remember(id) { Stretch.exercise(id)?.name.orEmpty() }
    val speedNow by rememberUpdatedState(speed)

    DisposableEffect(id, owner, retryGeneration) {
        fun create() {
            if (player != null) return
            ready = false; firstFrame = false; buffering = true; failed = false
            var created: ExoPlayer? = null
            try {
                val next = ExoPlayer.Builder(ctx.applicationContext)
                    .setLoadControl(DefaultLoadControl.Builder().setBufferDurationsMs(5_000, 15_000, 1_000, 1_500).setTargetBufferBytes(8 * 1024 * 1024).build())
                    .build()
                created = next
                next.apply {
                    volume = 0f
                    repeatMode = Player.REPEAT_MODE_ONE
                    setPlaybackSpeed(speedNow)
                    addListener(object : Player.Listener {
                        override fun onPlaybackStateChanged(state: Int) {
                            buffering = state == Player.STATE_BUFFERING
                            ready = state == Player.STATE_READY
                            duration = next.duration.coerceAtLeast(0L)
                        }
                        override fun onRenderedFirstFrame() { firstFrame = true }
                        override fun onPlayerError(error: PlaybackException) { failed = true; buffering = false; ready = false }
                    })
                    setMediaItem(MediaItem.fromUri(url), position)
                    playWhenReady = wantedNow && activeNow
                    prepare()
                }
                player = next
            } catch (error: Exception) {
                // Factory/URI errors can be synchronous, before Player.Listener receives an error.
                runCatching { created?.release() }
                player = null
                ready = false; firstFrame = false; buffering = false; failed = true
                android.util.Log.w("StretchVideo", "Cannot prepare exercise video", error)
            }
        }
        fun release() {
            player?.let { p -> position = p.currentPosition.coerceAtLeast(0L); p.release() }
            player = null
            ready = false; firstFrame = false; buffering = false
        }
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> create()
                Lifecycle.Event.ON_STOP -> release()
                else -> Unit
            }
        }
        owner.lifecycle.addObserver(observer)
        if (owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) create()
        onDispose { owner.lifecycle.removeObserver(observer); release() }
    }
    LaunchedEffect(player, wanted, foreground, speed) {
        player?.let { it.playWhenReady = wanted && foreground; it.setPlaybackSpeed(speed) }
    }
    LaunchedEffect(player, ready, wanted, foreground) {
        val p = player ?: return@LaunchedEffect
        position = p.currentPosition.coerceAtLeast(0L)
        while (ready && wanted && foreground) { delay(500); position = p.currentPosition.coerceAtLeast(0L) }
    }
    LaunchedEffect(showControls, wanted, touchExploration, showInstructions, drag) {
        if (showControls && wanted && !touchExploration && !showInstructions && drag == null) { delay(3500); showControls = false }
    }
    fun playPause() {
        showControls = true
        onPlayingChange?.invoke(!wanted) ?: run { localPlaying = !wanted }
    }
    fun seek(value: Long) {
        val target = value.coerceIn(0L, duration.coerceAtLeast(0L))
        player?.seekTo(target); position = target; showControls = true
    }

    @Composable
    fun Surface(large: Boolean) {
        val p = player
        val controls = showControls || !wanted || failed || touchExploration
        Box(Modifier.fillMaxSize().background(Color.Black).clickable { showControls = !showControls }, contentAlignment = Alignment.Center) {
            if (p != null) AndroidView(factory = { c ->
                (LayoutInflater.from(c).inflate(R.layout.stretch_video_player, null, false) as PlayerView).apply { this.player = p }
            }, modifier = Modifier.fillMaxSize(), onReset = null, onRelease = { it.player = null }, update = { it.player = p })
            if (!firstFrame) StretchThumb(id, Modifier.fillMaxSize())
            if (buffering && !failed && p != null) CircularProgressIndicator(color = StretchHue, modifier = Modifier.size(34.dp))
            if (failed) Column(Modifier.background(Color.Black.copy(alpha = .8f)).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(tr("Vidéo indisponible. Vérifiez la connexion."), color = Color.White, style = MaterialTheme.typography.bodySmall)
                TextButton(onClick = {
                    // Recreate through the same guarded path, including after construction failed.
                    retryGeneration++
                }, enabled = foreground) { Text(tr("Réessayer"), color = Color.White) }
            }
            if (large) Row(Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().background(Color.Black.copy(alpha = .65f)).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { fullscreen = false }) { Icon(Icons.Rounded.Close, tr("Quitter le plein écran"), tint = Color.White) }
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color.White, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    status?.let { Text(it, color = Color.White, style = MaterialTheme.typography.labelMedium) }
                }
                TextButton(onClick = { showInstructions = !showInstructions; showControls = true }) { Text(tr("Consignes"), color = Color.White) }
            }
            if (large && showInstructions) Box(Modifier.align(Alignment.Center).fillMaxWidth().padding(horizontal = 20.dp).heightIn(max = 260.dp).background(Color.Black.copy(alpha = .88f))) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(16.dp)) {
                    val exercise = Stretch.exercise(id)
                    exercise?.feel?.let { Text(it, color = Color.White, modifier = Modifier.padding(bottom = 8.dp), style = MaterialTheme.typography.bodyMedium) }
                    exercise?.steps?.forEachIndexed { index, text -> Text("${index + 1}. $text", color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 4.dp)) }
                }
            }
            if (controls && !failed) Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().background(Color.Black.copy(alpha = .72f)).then(if (large) Modifier.navigationBarsPadding() else Modifier).padding(horizontal = 8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { seek(position - 10_000) }, enabled = ready) { Icon(Icons.Rounded.Replay10, tr("Reculer de 10 secondes"), tint = Color.White) }
                    IconButton(onClick = ::playPause) { Icon(if (wanted) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, tr(if (wanted) "Pause" else "Reprendre"), tint = Color.White) }
                    IconButton(onClick = { seek(position + 10_000) }, enabled = ready) { Icon(Icons.Rounded.Forward10, tr("Avancer de 10 secondes"), tint = Color.White) }
                    Text("${videoTime(position)} / ${videoTime(duration)}", color = Color.White, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = { speed = when (speed) { .5f -> 1f; 1f -> 1.5f; else -> .5f }; showControls = true }, modifier = Modifier.semantics { contentDescription = trf("Vitesse de lecture : %1\$s", speed) }) {
                        Text("${speed}×", color = Color.White)
                    }
                    IconButton(onClick = { fullscreen = !large; showControls = true }) { Icon(if (large) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen, tr(if (large) "Quitter le plein écran" else "Plein écran"), tint = Color.White) }
                }
                val seekLabel = tr("Position de lecture")
                Slider(value = drag ?: position.toFloat().coerceIn(0f, duration.coerceAtLeast(1).toFloat()), onValueChange = { drag = it },
                    onValueChangeFinished = { drag?.let { seek(it.toLong()) }; drag = null }, valueRange = 0f..duration.coerceAtLeast(1).toFloat(), enabled = ready && duration > 0,
                    colors = SliderDefaults.colors(thumbColor = Color.White, activeTrackColor = StretchHue, inactiveTrackColor = Color.White.copy(alpha = .3f)), modifier = Modifier.height(28.dp).semantics { contentDescription = seekLabel })
            }
        }
    }

    Box(modifier.background(Color.Black)) {
        if (!fullscreen) Surface(false) else StretchThumb(id, Modifier.fillMaxSize())
    }
    if (fullscreen) Dialog(onDismissRequest = { fullscreen = false }, properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false, dismissOnClickOutside = false)) {
        val window = (LocalView.current.parent as? DialogWindowProvider)?.window
        DisposableEffect(window) {
            window?.let { WindowCompat.getInsetsController(it, it.decorView).hide(WindowInsetsCompat.Type.systemBars()) }
            onDispose { }
        }
        BackHandler { fullscreen = false }
        Surface(true)
    }
}

private fun videoTime(ms: Long): String = "%d:%02d".format(ms.coerceAtLeast(0) / 60_000, ms.coerceAtLeast(0) / 1000 % 60)
