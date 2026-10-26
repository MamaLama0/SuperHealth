package app.superhealth.training.ui.components

import app.superhealth.platform.*

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.superhealth.training.domain.WeightUnit
import app.superhealth.training.ui.KG_PER_LB
import app.superhealth.training.ui.tr
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.theme.WorkoutBlue
import kotlinx.coroutines.delay
import kotlin.math.abs
import kotlin.math.roundToInt

/** Apparition décalée (fondu + montée) des éléments d'une liste. */
@Composable
fun Staggered(index: Int, content: @Composable () -> Unit) {
    val a = remember { Animatable(0f) }
    LaunchedEffect(Unit) { delay(60L + index * 55L); a.animateTo(1f, tween(320, easing = FastOutSlowInEasing)) }
    Box(Modifier.graphicsLayer { alpha = a.value; translationY = (1 - a.value) * 40f }) { content() }
}

/** Grande tuile de choix animée (sélection : bordure, fond, rebond). */
@Composable
fun OptionTile(title: String, subtitle: String?, selected: Boolean, icon: ImageVector? = null, leading: (@Composable () -> Unit)? = null, onClick: () -> Unit) {
    // même tuile que le questionnaire d'accueil : carte neutre, bouton radio à droite
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clip(RoundedCornerShape(18.dp)).background(Mf.colors.card)
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) { leading(); Spacer(Modifier.width(14.dp)) }
        else if (icon != null) {
            Icon(icon, null, tint = if (selected) Mf.colors.accent else Mf.colors.textSecondary, modifier = Modifier.size(26.dp))
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(tr(title), style = MaterialTheme.typography.titleMedium)
            if (!subtitle.isNullOrBlank()) Text(tr(subtitle), style = MaterialTheme.typography.bodySmall, color = Mf.colors.textSecondary)
        }
        Spacer(Modifier.width(10.dp))
        androidx.compose.animation.Crossfade(selected, label = "check") { on ->
            if (on) Box(Modifier.size(22.dp).border(2.dp, Mf.colors.accent, CircleShape).padding(5.dp).clip(CircleShape).background(Mf.colors.accent))
            else Box(Modifier.size(22.dp).border(2.dp, Mf.colors.textSecondary, CircleShape))
        }
    }
}

/** Sélecteur à molette. */
@Composable
fun Wheel(values: List<String>, initial: Int, modifier: Modifier = Modifier, height: Int = 220, onIndex: (Int) -> Unit) {
    val itemH = 44.dp
    val state = rememberLazyListState(initialFirstVisibleItemIndex = initial)
    val pad = (height.dp - itemH) / 2
    LaunchedEffect(state) {
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }.collect { (i, off) ->
            val px = state.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 1
            onIndex((i + if (off > px / 2) 1 else 0).coerceIn(0, values.lastIndex))
        }
    }
    Box(modifier.fillMaxWidth().height(height.dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(itemH).clip(RoundedCornerShape(12.dp)).background(Mf.colors.card))
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = PaddingValues(vertical = pad),
            flingBehavior = rememberSnapFlingBehavior(state), horizontalAlignment = Alignment.CenterHorizontally) {
            items(values.size) { i ->
                val center by remember { derivedStateOf {
                    val info = state.layoutInfo
                    val item = info.visibleItemsInfo.firstOrNull { it.index == i } ?: return@derivedStateOf 3f
                    val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                    abs(item.offset + item.size / 2f - mid) / item.size
                } }
                Box(Modifier.height(itemH).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text(values[i], fontSize = 22.sp, fontWeight = if (center < 0.5f) FontWeight.Bold else FontWeight.Normal,
                        modifier = Modifier.graphicsLayer { alpha = (1f - center * 0.3f).coerceIn(0.2f, 1f); val s = (1f - center * 0.08f).coerceIn(0.75f, 1f); scaleX = s; scaleY = s })
                }
            }
        }
    }
}

/** Poids : molette entière + décimale, dans l'unité choisie. */
@Composable
fun WeightWheel(kg: Double, unit: WeightUnit, height: Int = 220, onKg: (Double) -> Unit) {
    val toU = if (unit == WeightUnit.LB) 1 / KG_PER_LB else 1.0
    val v = kg * toU
    val range = if (unit == WeightUnit.LB) (70..550) else (30..250)
    var whole by remember(unit) { mutableIntStateOf(v.toInt().coerceIn(range.first, range.last)) }
    var tenth by remember(unit) { mutableIntStateOf(((v - v.toInt()) * 10).roundToInt().coerceIn(0, 9)) }
    LaunchedEffect(whole, tenth, unit) { onKg((whole + tenth / 10.0) / toU) }
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Wheel(range.map { it.toString() }, whole - range.first, Modifier.weight(1.4f), height) { whole = range.first + it }
        Text(",", fontSize = 24.sp, modifier = Modifier.padding(horizontal = 4.dp))
        Wheel((0..9).map { it.toString() }, tenth, Modifier.weight(0.8f), height) { tenth = it }
        Text(unit.label.tr(), fontSize = 20.sp, modifier = Modifier.padding(start = 8.dp).width(36.dp))
    }
}
