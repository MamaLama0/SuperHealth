package app.superhealth.ui.hiking

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Map
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.documentfile.provider.DocumentFile
import app.superhealth.hiking.HikePoint
import app.superhealth.hiking.HikeRoute
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import kotlinx.coroutines.flow.MutableStateFlow
import nodomain.freeyourgadget.gadgetbridge.GBApplication
import nodomain.freeyourgadget.gadgetbridge.util.maps.MapsManager
import org.mapsforge.core.graphics.Style
import org.mapsforge.core.model.BoundingBox
import org.mapsforge.core.model.LatLong
import org.mapsforge.map.android.graphics.AndroidGraphicFactory
import org.mapsforge.map.android.view.MapView
import org.mapsforge.map.layer.overlay.Circle
import org.mapsforge.map.layer.overlay.Polyline

internal object RegionalMaps { val revision = MutableStateFlow(0) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun RegionalMapsPicker() {
    val ctx = LocalContext.current
    var message by remember { mutableStateOf<String?>(null) }
    var open by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) runCatching {
            require(DocumentFile.fromTreeUri(ctx,uri)?.listFiles()?.any { it.name?.endsWith(".map") == true } == true) { tr("Choisissez un dossier contenant une carte .map") }
            ctx.contentResolver.takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION)
            GBApplication.getPrefs().preferences.edit().putString(MapsManager.PREF_MAPS_FOLDER,uri.toString()).apply()
            RegionalMaps.revision.value++
            message = tr("Cartes régionales prêtes hors ligne")
        }.onFailure { message = it.message }
    }
    TextButton(onClick={open=true},contentPadding=PaddingValues(0.dp)) {
        Icon(Icons.Rounded.Map,null);Spacer(Modifier.width(8.dp));Text(tr("Cartes hors ligne"))
    }
    if(open) ModalBottomSheet(onDismissRequest={open=false},containerColor=Mf.colors.background) {
        Column(Modifier.fillMaxWidth().padding(20.dp).navigationBarsPadding(),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            Text(tr("Cartes hors ligne"),style=MaterialTheme.typography.titleLarge)
            Text(tr("Les parcours de la sélection sont prêts à consulter sans réseau. Pour un fond détaillé, ajoutez une carte régionale."),color=Mf.colors.textSecondary)
            Text(tr("Choisissez un dossier de cartes Mapsforge (.map), commun avec les cartes de la montre. Aucun serveur nécessaire."),style=MaterialTheme.typography.bodySmall,color=Mf.colors.textSecondary)
            OutlinedButton(onClick={picker.launch(null)},modifier=Modifier.fillMaxWidth()) { Text(tr("Choisir mon dossier de cartes")) }
            TextButton(onClick={ctx.startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse("https://download.mapsforge.org/maps/v5/")))}) { Text(tr("Trouver une carte régionale")) }
            message?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=Mf.colors.textSecondary) }
            TextButton(onClick={open=false}){Text(tr("Fermer"))}
        }
    }
}

@Composable
internal fun RegionalHikeMap(route:HikeRoute,modifier:Modifier,position:HikePoint?,onUnavailable:()->Unit) {
    val ctx=LocalContext.current
    val holder=remember(route.id,Mf.colors.dark,RegionalMaps.revision.collectAsState().value) {
        AndroidGraphicFactory.createInstance(ctx.applicationContext as android.app.Application)
        val view=MapView(ctx).apply { setClickable(true);setBuiltInZoomControls(true) }
        val manager=MapsManager(ctx,view)
        manager.loadMaps()
        val bounds=manager.mapBounds
        val center=route.points[route.points.size/2]
        val covered=manager.isMapLoaded && bounds!=null && bounds.contains(center.lat,center.lon)
        if(covered) {
            route.points.groupBy { it.segment }.values.forEach { segment ->
                val paint=AndroidGraphicFactory.INSTANCE.createPaint().apply { setColor(android.graphics.Color.rgb(40,140,105));setStrokeWidth(8f);setStyle(Style.STROKE) }
                view.layerManager.layers.add(Polyline(paint,AndroidGraphicFactory.INSTANCE).apply { setPoints(segment.map { LatLong(it.lat,it.lon) }) })
            }
            view.post { manager.setBoundingBox(BoundingBox(route.points.minOf{it.lat}-.001,route.points.minOf{it.lon}-.001,
                route.points.maxOf{it.lat}+.001,route.points.maxOf{it.lon}+.001)) }
        }
        val paint=AndroidGraphicFactory.INSTANCE.createPaint().apply { setColor(android.graphics.Color.rgb(50,140,235));setStyle(Style.FILL) }
        val dot=Circle(LatLong(center.lat,center.lon),8f,paint,null).apply { setVisible(false) }
        view.layerManager.layers.add(dot)
        Triple(view,manager,dot) to covered
    }
    DisposableEffect(holder) { onDispose { holder.first.second.onDestroy();holder.first.first.destroyAll() } }
    if(!holder.second) { LaunchedEffect(holder){onUnavailable()};return }
    AndroidView(factory={holder.first.first},modifier=modifier,update={
        holder.first.third.setVisible(position!=null)
        position?.let { p -> holder.first.third.setLatLong(LatLong(p.lat,p.lon)) }
        holder.first.third.requestRedraw()
    })
}
