package app.superhealth.ui.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.superhealth.cards.CardStore
import app.superhealth.cards.Catalog
import app.superhealth.cards.Tier
import app.superhealth.ui.tr
import app.superhealth.ui.trf

/**
 * Bouton « Cartes » de la barre du bas, sur le panneau social : un petit éventail aux couleurs du fond équipé, la
 * note de la carte et ce qui attend (packs à ouvrir, récompenses de quêtes). Il ouvre la carte Athlète, ses packs
 * et ses duels ; Retour ramène au fil.
 */
@Composable
internal fun CardsDock(onClick: () -> Unit) {
    val ctx = LocalContext.current
    val state by CardStore.state.collectAsState()
    val snap by CardStore.snapshot.collectAsState()
    LaunchedEffect(Unit) { CardStore.load(ctx) }
    val stats = snap?.now ?: state.lastStats
    val palette = (CardStore.background()?.card ?: Catalog.defaultFor(stats?.tier ?: Tier.BRONZE)).palette
    val claimable = remember(state.claimed, state.quests, snap) { runCatching { CardStore.quests().count { it.complete && !it.claimed } }.getOrDefault(0) }
    val waiting = state.packsAvailable + claimable
    val detail = when {
        state.packsAvailable > 0 -> trf(if (state.packsAvailable > 1) "%1\$s packs à ouvrir" else "%1\$s pack à ouvrir", state.packsAvailable)
        claimable > 0 -> trf(if (claimable > 1) "%1\$s récompenses à récupérer" else "%1\$s récompense à récupérer", claimable)
        else -> tr("Packs, duels, album")
    }
    val shape = RoundedCornerShape(26.dp)
    Row(Modifier.height(68.dp).shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.2f))
        .clip(shape).background(Brush.horizontalGradient(listOf(Color(0xFF171A2B), Color(0xFF232844))))
        .border(1.dp, Brush.linearGradient(listOf(palette.b.c().copy(alpha = 0.7f), Color.White.copy(alpha = 0.08f), Gold.copy(alpha = 0.45f))), shape)
        .clickable(role = Role.Button, onClickLabel = tr("Ouvrir mes cartes"), onClick = onClick).padding(start = 12.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        // Deux cartes en éventail : celle du dessous à la teinte du fond, celle du dessus porte la note.
        Box(Modifier.size(46.dp, 50.dp), contentAlignment = Alignment.Center) {
            Box(Modifier.size(27.dp, 38.dp).rotate(-13f).clip(RoundedCornerShape(6.dp)).background(Brush.verticalGradient(listOf(palette.c.c(), palette.a.c()))))
            Box(Modifier.padding(start = 12.dp).size(29.dp, 40.dp).rotate(7f).shadow(4.dp, RoundedCornerShape(6.dp)).clip(RoundedCornerShape(6.dp))
                .background(Brush.verticalGradient(listOf(palette.b.c(), palette.a.c()))).border(1.dp, Color.White.copy(alpha = 0.55f), RoundedCornerShape(6.dp)),
                contentAlignment = Alignment.Center) {
                Text(stats?.ovr?.toString() ?: "★", color = Color.White, fontWeight = FontWeight.Black, fontSize = 14.sp)
            }
        }
        Column(Modifier.width(132.dp)) {
            Text(tr("Mes cartes"), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp, maxLines = 1)
            Text(detail, color = if (waiting > 0) Gold else Color.White.copy(alpha = 0.62f), fontWeight = FontWeight.SemiBold, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (waiting > 0) Text("$waiting", color = Color(0xFF2A1B00), fontWeight = FontWeight.Black, fontSize = 12.sp,
            modifier = Modifier.clip(CircleShape).background(Gold).padding(horizontal = 7.dp, vertical = 2.dp))
        else Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = 0.55f))
    }
}
