package app.superhealth.training.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowDown
import androidx.compose.material.icons.rounded.KeyboardDoubleArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.superhealth.training.domain.Exercise
import app.superhealth.training.ui.tr
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.WorkoutBlue
import kotlin.math.roundToInt

/**
 * Réorganiser les exercices : glisser la poignée, ou envoyer en haut / en bas d'une touche. Les supersets sont signalés
 * par leur lettre ; un déplacement qui sépare un groupe le recalcule (voir Supersets.normalize).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReorderSheet(items: List<Pair<String, Exercise?>>, groups: List<Int?>, onMove: (from: Int, to: Int) -> Unit, onDismiss: () -> Unit) {
    val rowH = 64.dp
    val rowPx = with(LocalDensity.current) { rowH.toPx() }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var offset by remember { mutableFloatStateOf(0f) }
    val letters = groups.filterNotNull().distinct().mapIndexed { n, g -> g to ('A' + n) }.toMap()
    val target = dragging?.let { (it + (offset / rowPx).roundToInt()).coerceIn(0, items.lastIndex) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true), containerColor = Mf.colors.background) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 16.dp).navigationBarsPadding()) {
            Text(tr("Réorganiser les exercices"), style = MaterialTheme.typography.titleLarge)
            Text(tr("Glissez la poignée, ou envoyez en haut / en bas."), style = MaterialTheme.typography.bodySmall,
                color = Mf.colors.textSecondary, modifier = Modifier.padding(bottom = 10.dp))
            Column(Modifier.verticalScroll(rememberScrollState(), enabled = dragging == null)) {
                items.forEachIndexed { i, (key, e) ->
                    androidx.compose.runtime.key(key) {
                        // décalage des autres lignes pendant le glissement : elles laissent la place à la ligne déplacée
                        val shift = when {
                            dragging == null || target == null || i == dragging -> 0f
                            i in (dragging!! + 1)..target -> -rowPx
                            i in target until dragging!! -> rowPx
                            else -> 0f
                        }
                        val animShift by animateFloatAsState(shift, label = "shift")
                        val isDragged = i == dragging
                        Row(Modifier.fillMaxWidth().height(rowH).padding(vertical = 3.dp)
                            .graphicsLayer { translationY = if (isDragged) offset else animShift }
                            .then(if (isDragged) Modifier.shadow(8.dp, RoundedCornerShape(16.dp)) else Modifier)
                            .clip(RoundedCornerShape(16.dp)).background(if (isDragged) Mf.colors.elevated else Mf.colors.card)
                            .padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) {
                                val g = groups.getOrNull(i)
                                if (g != null) Text("${letters[g]}", Modifier.clip(CircleShape).background(WorkoutBlue).padding(horizontal = 7.dp, vertical = 2.dp),
                                    color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                else Text("${i + 1}", color = Mf.colors.textSecondary, style = MaterialTheme.typography.labelLarge)
                            }
                            Spacer(Modifier.width(6.dp))
                            ExerciseThumb(e, 40.dp, 10.dp)
                            Spacer(Modifier.width(10.dp))
                            Text(e?.name ?: "?", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            IconButton(onClick = { if (i > 0) onMove(i, 0) }, enabled = i > 0) {
                                Icon(Icons.Rounded.KeyboardDoubleArrowUp, tr("En haut"), tint = if (i > 0) Mf.colors.textSecondary else Mf.colors.divider)
                            }
                            IconButton(onClick = { if (i < items.lastIndex) onMove(i, items.lastIndex) }, enabled = i < items.lastIndex) {
                                Icon(Icons.Rounded.KeyboardDoubleArrowDown, tr("En bas"), tint = if (i < items.lastIndex) Mf.colors.textSecondary else Mf.colors.divider)
                            }
                            Icon(Icons.Rounded.DragHandle, tr("Déplacer"), tint = Mf.colors.text, modifier = Modifier.size(40.dp).padding(8.dp)
                                .pointerInput(i, items.size) {
                                    detectDragGestures(
                                        onDragStart = { dragging = i; offset = 0f },
                                        onDragEnd = {
                                            val from = dragging; val to = target
                                            dragging = null; offset = 0f
                                            if (from != null && to != null && from != to) onMove(from, to)
                                        },
                                        onDragCancel = { dragging = null; offset = 0f },
                                    ) { change, amount -> change.consume(); offset += amount.y }
                                })
                        }
                    }
                }
            }
        }
    }
}
