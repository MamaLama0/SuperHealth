package app.superhealth.ui.hiking

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import app.superhealth.hiking.*
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import kotlin.math.*

internal val HikingHue = Color(0xFF328C70)

/** All features come from the local pack. Opening, zooming and following never trigger network calls. */
@Composable
internal fun HikeMap(route: HikeRoute, modifier: Modifier = Modifier, interactive: Boolean = false,
    position: HikePoint? = null, recorded: List<HikePoint> = emptyList()) {
    val revision by RegionalMaps.revision.collectAsState()
    var unavailable by remember(route.id,revision) { mutableStateOf(false) }
    val folder = nodomain.freeyourgadget.gadgetbridge.GBApplication.getPrefs().getString(
        nodomain.freeyourgadget.gadgetbridge.util.maps.MapsManager.PREF_MAPS_FOLDER, "")
    if (interactive && folder.isNotEmpty() && !unavailable) {
        Box(modifier.clip(RoundedCornerShape(24.dp))) {
            RegionalHikeMap(route,Modifier.fillMaxSize(),position,recorded) { unavailable = true }
            Text("© OpenStreetMap",Modifier.align(Alignment.BottomStart).background(Mf.colors.card).padding(6.dp),style=MaterialTheme.typography.labelSmall)
        }
        return
    }
    val dark = Mf.colors.dark
    val ground = if (dark) Color(0xFF192824) else Color(0xFFE8E8E8)
    val forest = if (dark) Color(0xFF284139) else Color(0xFFD6E5CE)
    val water = if (dark) Color(0xFF294D59) else Color(0xFFBCE0F0)
    val pathColor = if (dark) Color(0xFF91A69B) else Color(0xFF696969)
    val roadColor = if (dark) Color(0xFF718079) else Color.White
    val lineColor = if (dark) Color(0xFF83D7AE) else Color(0xFF37A480)
    val fit = remember(route.id, route.points.size) {
        val lat = (route.points.minOf { it.lat }+route.points.maxOf { it.lat })/2
        val lon = (route.points.minOf { it.lon }+route.points.maxOf { it.lon })/2
        val ratio = cos(Math.toRadians(lat)).coerceAtLeast(.05)
        doubleArrayOf(lat,lon,ratio,max(.001,(route.points.maxOf { it.lon }-route.points.minOf { it.lon })*ratio),max(.001,route.points.maxOf { it.lat }-route.points.minOf { it.lat }))
    }
    var zoom by remember(route.id) { mutableFloatStateOf(1f) }
    var pan by remember(route.id) { mutableStateOf(Offset.Zero) }
    BoxWithConstraints(modifier.clip(RoundedCornerShape(24.dp)).background(ground)) {
        val width = constraints.maxWidth.toFloat(); val height = constraints.maxHeight.toFloat()
        val scale = min(width/fit[3],height/fit[4])*.78
        fun screen(p: HikePoint) = Offset((width/2+(p.lon-fit[1])*fit[2]*scale*zoom+pan.x).toFloat(),
            (height/2-(p.lat-fit[0])*scale*zoom+pan.y).toFloat())
        Canvas(Modifier.fillMaxSize().then(if (interactive) Modifier.pointerInput(route.id,width,height) {
            detectTransformGestures { centroid, move, factor, _ ->
                val next = (zoom*factor).coerceIn(.6f,24f); val actual = next/zoom
                val relative = centroid-Offset(width/2,height/2)
                pan = (pan-relative)*actual+relative+move; zoom=next
            }
        } else Modifier)) {
            fun drawPoints(points: List<HikePoint>, color: Color, stroke: Float, fill: Boolean = false, dashed:Boolean=false) {
                if (points.size < 2) return
                val path=Path()
                points.forEachIndexed { i,p -> val pos=screen(p)
                    if (i==0 || points[i-1].segment != p.segment) path.moveTo(pos.x,pos.y) else path.lineTo(pos.x,pos.y) }
                if (fill) { path.close(); drawPath(path,color) } else drawPath(path,color,style=Stroke(stroke,cap=StrokeCap.Round,join=StrokeJoin.Round,
                    pathEffect=if(dashed)PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(),3.dp.toPx())) else null))
            }
            route.features.filter { it.kind in setOf("forest","water") }.forEach { f ->
                drawPoints(f.points,if (f.kind=="forest") forest else water,3.dp.toPx(),f.points.firstOrNull()==f.points.lastOrNull())
            }
            route.features.filter { it.kind in setOf("path","road") }.forEach { f -> drawPoints(f.points,if(f.kind=="road") roadColor else pathColor,if(f.kind=="road") 3.dp.toPx() else 1.2.dp.toPx(),dashed=f.kind=="path") }
            drawPoints(route.points,Color.White.copy(alpha=.85f),7.dp.toPx())
            drawPoints(route.points,lineColor,4.dp.toPx())
            if(recorded.size>1) drawPoints(recorded,Color(0xFF448FE8),3.dp.toPx())
            val start=screen(route.points.first());drawCircle(Color.White,8.dp.toPx(),start);drawCircle(lineColor,5.dp.toPx(),start)
            val end=screen(route.points.last()); if ((end-start).getDistance()>20.dp.toPx()) { drawCircle(Color.White,7.dp.toPx(),end);drawCircle(Color(0xFFE39E4C),4.dp.toPx(),end) }
            position?.let { p -> val point=screen(p);drawCircle(Color(0xFF448FE8).copy(alpha=.18f),18.dp.toPx(),point);drawCircle(Color.White,9.dp.toPx(),point);drawCircle(Color(0xFF448FE8),6.dp.toPx(),point) }
        }
        Text(tr(if (route.features.isEmpty()) "Tracé hors ligne · sans fond de carte" else "Carte simplifiée · © OpenStreetMap"),
            Modifier.align(Alignment.BottomStart).padding(8.dp).background(Mf.colors.card.copy(alpha=.9f),RoundedCornerShape(8.dp)).padding(6.dp),
            style=MaterialTheme.typography.labelSmall,color=Mf.colors.textSecondary)
        if(interactive) Column(Modifier.align(Alignment.TopEnd).padding(10.dp),verticalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf(Icons.Rounded.Add,Icons.Rounded.Remove,Icons.Rounded.CenterFocusStrong).forEachIndexed { i,icon ->
                FilledIconButton(onClick={ when(i) { 0 -> { zoom=(zoom*1.4f).coerceAtMost(24f);pan*=1.4f };1 -> { zoom=(zoom/1.4f).coerceAtLeast(.6f);pan/=1.4f };else -> { zoom=1f;pan=Offset.Zero } } },
                    colors=IconButtonDefaults.filledIconButtonColors(containerColor=Mf.colors.card,contentColor=Mf.colors.text)) {
                    Icon(icon,tr(listOf("Zoom avant","Zoom arrière","Recentrer")[i]))
                }
            }
        }
    }
}

@Composable
internal fun ElevationProfile(route: HikeRoute) {
    val points = route.points
    val elevated = points.filter { it.ele != null }
    if (elevated.size < 2) { Text(tr("Altitude non fournie pour ce parcours"),style=MaterialTheme.typography.bodySmall,color=Mf.colors.textSecondary);return }
    val color = HikingHue
    val min = elevated.minOf { it.ele!! };val max=elevated.maxOf { it.ele!! }.coerceAtLeast(min+1)
    val distances=remember(points) { var d=0.0;points.mapIndexed { i,p -> if (i>0 && points[i-1].segment==p.segment) d+=HikeGeometry.distance(points[i-1],p);d } }
    Canvas(Modifier.fillMaxWidth().height(100.dp)) {
        val line=Path();var started=false
        points.forEachIndexed { i,p ->
            if(p.ele==null || (i>0 && p.segment!=points[i-1].segment)) started=false
            if(p.ele!=null) {
                val x=(distances[i]/distances.last().coerceAtLeast(1.0)*size.width).toFloat()
                val y=(size.height*(1-(p.ele-min)/(max-min))*.85+size.height*.05).toFloat()
                if(!started) line.moveTo(x,y) else line.lineTo(x,y)
                started=true
            }
        }
        drawPath(line,color,style=Stroke(2.dp.toPx(),cap=StrokeCap.Round))
    }
    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text("${min.roundToInt()} m",style=MaterialTheme.typography.labelSmall,color=Mf.colors.textSecondary)
        Text("${max.roundToInt()} m",style=MaterialTheme.typography.labelSmall,color=Mf.colors.textSecondary)
    }
}
