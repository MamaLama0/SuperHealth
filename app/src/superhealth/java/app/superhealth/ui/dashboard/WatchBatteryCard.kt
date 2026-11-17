package app.superhealth.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.superhealth.CardSize
import app.superhealth.bridge.WatchStatus
import app.superhealth.ui.theme.Base
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr

/** Couleur de la batterie : rouge à 20 % et moins, jaune jusqu'à 40 %, verte au-delà ; grise montre déconnectée ou inconnue. */
internal fun batteryColor(level: Int?, connected: Boolean): Color = when {
    level == null || !connected -> Base.Grey
    level <= 20 -> Base.Red
    level <= 40 -> Base.Yellow
    else -> Base.Green
}

/** Carte « Batterie de la montre » : nom, connexion, jauge de batterie, charge, dernière connexion. */
@Composable
internal fun WMontre(sz: CardSize, icon: ImageVector, m: Modifier, onClick: () -> Unit) {
    val list by WatchStatus.state.collectAsState()
    val w = list.firstOrNull()
    val c = batteryColor(w?.battery, w?.connected == true)
    val f = (w?.battery ?: 0) / 100f
    val v = w?.battery?.toString() ?: "—"
    val name = w?.name?.takeIf { it.isNotBlank() } ?: tr("Montre")
    val state = w?.let { WatchStatus.stateText(it) } ?: tr("Aucune montre")
    val seen = w?.let { WatchStatus.lastSeenText(it) }
    val tone = Tone(c, c)
    @Composable fun Charge(size: Dp) { if (w?.charging == true) Icon(Icons.Rounded.Bolt, tr("En charge"), Modifier.size(size), tint = c) }
    when (sz) {
        CardSize.S -> RingTile(m, onClick, c, f) { RingCenter(if (w?.charging == true) Icons.Rounded.Bolt else icon, c, if (w?.battery != null) "$v %" else v, state) }
        CardSize.M -> MediumShell(m, onClick, icon, name, c, value = v, unit = "%", caption = seen ?: state) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                BatteryBar(f, c, Modifier.weight(1f), 10.dp)
                if (w?.charging == true) { Spacer(Modifier.width(6.dp)); Charge(18.dp) }
            }
        }
        CardSize.W -> Wide(m, onClick, icon, c, listOfNotNull(name, seen ?: state).joinToString(" · "), { V(v, "%", 20.sp) }, 120.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) { Charge(18.dp); Spacer(Modifier.width(4.dp)); BatteryBar(f, c, Modifier.width(96.dp), 10.dp) }
        }
        else -> Large(m, onClick, icon, tr("Batterie de la montre"), c, state, c) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                    DialT(f, tone, Modifier.size(112.dp), 10.dp)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Charge(16.dp)
                        Tq(if (w?.battery != null) "$v %" else v, 28.sp, weight = FontWeight.SemiBold, num = true)
                    }
                }
                Spacer(Modifier.width(18.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Tq(name, 17.sp, weight = FontWeight.SemiBold)
                    Tq(state, 13.sp, if (w?.connected == true) c else Mf.colors.textSecondary, FontWeight.SemiBold)
                    if (seen != null) Tq(seen, 13.sp, Mf.colors.textSecondary)
                    if (list.size > 1) Tq(list.drop(1).joinToString(" · ") { o -> o.name + (o.battery?.let { " $it %" } ?: "") }, 12.sp, Mf.colors.textSecondary)
                    if (w == null) Tq(tr("Associez une montre dans Appareils"), 12.sp, Mf.colors.textSecondary)
                }
            }
        }
    }
}

/** Batterie en barre arrondie. */
@Composable
private fun BatteryBar(f: Float, c: Color, modifier: Modifier, h: Dp) {
    val track = Mf.colors.chip
    Canvas(modifier.height(h)) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(track, cornerRadius = r)
        if (f > 0f) drawRoundRect(c, size = Size(maxOf(size.height, size.width * f.coerceAtMost(1f)), size.height), cornerRadius = r)
    }
}
