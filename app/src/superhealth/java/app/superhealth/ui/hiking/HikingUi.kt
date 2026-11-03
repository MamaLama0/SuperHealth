package app.superhealth.ui.hiking

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import app.superhealth.hiking.*
import app.superhealth.modules.Modules
import app.superhealth.nutrition.ui.components.MfCard
import app.superhealth.nutrition.ui.components.PillButton
import app.superhealth.nutrition.ui.screens.DetailScaffold
import app.superhealth.ui.go
import app.superhealth.ui.theme.Mf
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

private fun km(meters: Double) = String.format(app.superhealth.ui.Lang.locale,"%.1f km",meters/1000)
private fun duration(minutes: Int) = if (minutes < 60) "$minutes min" else "${minutes/60} h ${if(minutes%60>0) (minutes%60).toString().padStart(2,'0') else ""}"

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HikingSection(nav: NavHostController) {
    val ctx=LocalContext.current;val scope=rememberCoroutineScope()
    val keyboard=LocalSoftwareKeyboardController.current;val focus=LocalFocusManager.current
    val library by Hiking.library.collectAsState();val session by Hiking.session.collectAsState()
    val modules by Modules.installed.collectAsState()
    val catalogRevision by Hiking.catalogRevision.collectAsState()
    var tab by rememberSaveable { mutableStateOf("Explorer") }
    var query by rememberSaveable { mutableStateOf(Hiking.searchPlace.value) }
    var short by rememberSaveable { mutableStateOf(false) }
    var loop by rememberSaveable { mutableStateOf(false) }
    val result by Hiking.searchResults.collectAsState()
    var busy by remember { mutableStateOf(false) };var error by remember { mutableStateOf<String?>(null) }
    fun search() {
        if(query.trim().length<2 || busy) return
        keyboard?.hide();focus.clearFocus()
        scope.launch {
            busy=true;error=null
            try { Hiking.searchResults.value=Hiking.search(query);Hiking.searchPlace.value=query.trim() }
            catch(e:kotlinx.coroutines.CancellationException){throw e}
            catch(_:Exception){error=tr("Recherche indisponible. Vérifiez votre connexion et réessayez.")}
            finally{busy=false}
        }
    }
    val routes=remember(library,modules,catalogRevision,result,tab,short,loop) {
        val all=if(tab=="Explorer") result ?: Hiking.catalog().map { Hiking.route(it.id) ?: it } else Hiking.routes()
        all.filter { (tab!="Favoris" || it.id in library.favorites) && (tab!="Mes parcours" || library.routes.any { r -> r.id==it.id }) &&
            (!short || HikeGeometry.length(it.points)<10000) && (!loop || it.loop) }
    }
    val importer=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if(uri!=null) scope.launch {
        busy=true;error=null
        try { val route=Hiking.import(ctx,uri);nav.go("hiking/route/${route.id}") }
        catch(e:kotlinx.coroutines.CancellationException){throw e}
        catch(e:Exception){error=tr(e.message ?: "Import impossible. Réessayez.")}
        finally{busy=false}
    } }
    Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
        val hero by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null,modules) {
            value=withContext(Dispatchers.IO) { Modules.file(Modules.HIKING,"landscape.jpg")?.let { android.graphics.BitmapFactory.decodeFile(it.path)?.asImageBitmap() } }
        }
        Box(Modifier.fillMaxWidth().height(110.dp).clip(RoundedCornerShape(26.dp)).background(HikingHue)) {
            hero?.let { Image(it,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop,alignment=Alignment.TopCenter) }
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent,Color(0xDD102D22)))))
            Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
                Row(horizontalArrangement=Arrangement.spacedBy(6.dp),verticalAlignment=Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Landscape,null,Modifier.size(18.dp),tint=Color.White)
                    Text(tr("LE DEHORS VOUS ATTEND"),style=MaterialTheme.typography.labelSmall,color=Color.White)
                }
                Text(tr("Prenez le grand air"),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold,color=Color.White)
                Text(tr("Des sentiers à découvrir, des chemins à garder."),style=MaterialTheme.typography.bodySmall,color=Color.White.copy(alpha=.85f))
            }
        }
        if(session!=null) MfCard(onClick={nav.go("hiking/live")}) {
            Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Rounded.Explore,null,tint=HikingHue)
                Column(Modifier.weight(1f)){Text(tr("Retrouver ma randonnée"),fontWeight=FontWeight.Bold);Text(session?.route?.name ?: tr("Sortie libre"),color=Mf.colors.textSecondary)}
                Icon(Icons.Rounded.ChevronRight,null)
            }
        }
        Row(Modifier.horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            listOf("Explorer","Favoris","Mes parcours").forEach { label -> FilterChip(selected=tab==label,onClick={tab=label},label={Text(tr(label))}) }
        }
        if(tab=="Explorer") {
            OutlinedTextField(query,{query=it.take(160)},Modifier.fillMaxWidth(),label={Text(tr("Ville, région ou lieu"))},singleLine=true,
                leadingIcon={Icon(Icons.Rounded.TravelExplore,null)},shape=RoundedCornerShape(18.dp),
                trailingIcon={ IconButton(onClick=::search,enabled=query.trim().length>=2 && !busy) { Icon(Icons.Rounded.Search,tr("Rechercher en ligne")) } },
                keyboardOptions=androidx.compose.foundation.text.KeyboardOptions(imeAction=androidx.compose.ui.text.input.ImeAction.Search),
                keyboardActions=androidx.compose.foundation.text.KeyboardActions(onSearch={search()}))
            if(result!=null) Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                Text(trf("Résultats pour %1\$s",Hiking.searchPlace.value),Modifier.weight(1f),style=MaterialTheme.typography.labelLarge,color=Mf.colors.textSecondary)
                TextButton(onClick={Hiking.searchResults.value=null;Hiking.searchPlace.value="";error=null}){Text(tr("Revenir à la sélection"))}
            }
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically) {
            FilterChip(selected=short,onClick={short=!short},label={Text(tr("Moins de 10 km"))})
            FilterChip(selected=loop,onClick={loop=!loop},label={Text(tr("Boucle"))})
            Spacer(Modifier.weight(1f));RegionalMapsPicker(compact=true)
        }
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            PillButton(tr("Importer"),Modifier.weight(1f),strong=false,icon=Icons.Rounded.FileOpen) { importer.launch(arrayOf("application/gpx+xml","application/xml","text/xml","application/octet-stream")) }
            PillButton(tr("Sortie libre"),Modifier.weight(1f),strong=false,icon=Icons.Rounded.MyLocation) { nav.go("hiking/live") }
        }
        if(busy) LinearProgressIndicator(Modifier.fillMaxWidth(),color=HikingHue)
        error?.let{Text(it,color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)}
        Text(tr(if(tab=="Explorer" && result==null) "Une sélection pour s’évader" else tab)+" · ${routes.size}",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
        if(routes.isEmpty()) MfCard {
            Text(tr(if(result!=null) "Aucun parcours continu disponible ici. Essayez un lieu voisin." else "Vos chemins vous attendent"),style=MaterialTheme.typography.titleMedium)
            Text(tr("Enregistrez un itinéraire, ajoutez un favori ou importez une trace GPX : un fichier qui contient le chemin à suivre."),color=Mf.colors.textSecondary,style=MaterialTheme.typography.bodySmall)
            if(result!=null) TextButton(onClick={Hiking.searchResults.value=null;Hiking.searchPlace.value="";error=null}){Text(tr("Afficher les parcours hors ligne"))}
        }
        routes.forEachIndexed { index,route ->
            if(index==2) HikingSocial(nav)
            key(route.id) {
            RouteCard(route,route.id in library.favorites,route.features.isNotEmpty(),Hiking.route(route.id)!=null,onFavorite={
                scope.launch {
                    try { withContext(Dispatchers.IO) {
                        if(Hiking.route(route.id)==null) Hiking.save(ctx,route)
                        Hiking.favorite(ctx,route.id)
                    } } catch(e:kotlinx.coroutines.CancellationException){throw e}
                    catch(_:Exception){error=tr("Enregistrement impossible. Réessayez.")}
                }
            },onClick={
                scope.launch {
                    try {
                        if(Hiking.route(route.id)==null) withContext(Dispatchers.IO){Hiking.save(ctx,route)}
                        nav.go("hiking/route/${route.id}")
                    } catch(e:kotlinx.coroutines.CancellationException){throw e}
                    catch(_:Exception){error=tr("Enregistrement impossible. Réessayez.")}
                }
            })
        } }
        if(routes.size<3) HikingSocial(nav)
        if(library.completed.isNotEmpty()) {
            Text(tr("Dernières sorties"),style=MaterialTheme.typography.titleMedium)
            library.completed.take(5).forEach { log ->
                MfCard(onClick={nav.go("hiking/route/${log.route}")}) {
                    Text(Hiking.route(log.route)?.name ?: tr("Ma randonnée"),fontWeight=FontWeight.SemiBold)
                    Text("${java.time.Instant.ofEpochMilli(log.time).atZone(java.time.ZoneId.systemDefault()).toLocalDate()} · ${km(log.meters)} · ${duration((log.seconds/60).toInt())}",style=MaterialTheme.typography.bodySmall,color=Mf.colors.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun RouteCard(route:HikeRoute,favorite:Boolean,offline:Boolean,stored:Boolean,onFavorite:()->Unit,onClick:()->Unit) {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Mf.colors.card).clickable(onClick=onClick)) {
        Box { RoutePhoto(route,Modifier.fillMaxWidth().height(150.dp))
            FilledIconButton(onClick=onFavorite,modifier=Modifier.align(Alignment.TopEnd).padding(8.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=Mf.colors.card)) {
                Icon(if(favorite)Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,tr("Favoris"),tint=if(favorite)HikingHue else Mf.colors.text)
            }
        }
        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text(route.region,style=MaterialTheme.typography.labelSmall,color=Mf.colors.textSecondary)
            Text(route.name,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
            Text("${km(HikeGeometry.length(route.points))}  ·  ${duration(HikeGeometry.estimatedMinutes(route, Hiking.library.value.completed))} ${tr("estimées")}"+(if(route.loop) "  ·  ${tr("Boucle")}" else ""),style=MaterialTheme.typography.bodySmall,color=Mf.colors.textSecondary)
            DifficultyLabel(route)
            Text(tr(if(!stored) "À enregistrer pour le hors ligne" else if(offline) "Parcours et carte hors ligne" else "Tracé disponible hors ligne"),style=MaterialTheme.typography.labelSmall,color=HikingHue)
        }
    }
}

@Composable
private fun RoutePhoto(route:HikeRoute,modifier:Modifier) {
    val ctx=LocalContext.current
    val photo by produceState<androidx.compose.ui.graphics.ImageBitmap?>(null,route.id,route.imageUrl) {
        value=withContext(Dispatchers.IO) {
            try { HikePhotos.load(ctx,route)?.let { file ->
                val bounds=android.graphics.BitmapFactory.Options().apply{inJustDecodeBounds=true}
                android.graphics.BitmapFactory.decodeFile(file.path,bounds)
                val options=android.graphics.BitmapFactory.Options().apply{inSampleSize=(maxOf(bounds.outWidth,bounds.outHeight)/1200).coerceAtLeast(1)}
                android.graphics.BitmapFactory.decodeFile(file.path,options)?.asImageBitmap()
            } } catch(e:kotlinx.coroutines.CancellationException){throw e} catch(_:Exception){null}
        }
    }
    if(photo!=null) Image(photo!!,null,modifier.clip(RoundedCornerShape(24.dp)),contentScale=ContentScale.Crop)
    else HikeMap(route,modifier)
}

@Composable
private fun DifficultyLabel(route:HikeRoute) {
    if(route.difficulty.isBlank())return
    val (label,icon)=when(route.difficulty.lowercase(Locale.ROOT)) {
        "very_easy","très facile" -> "Très facile" to nodomain.freeyourgadget.gadgetbridge.R.drawable.hike_difficulty_easy
        "easy","facile" -> "Facile" to nodomain.freeyourgadget.gadgetbridge.R.drawable.hike_difficulty_easy
        "moderate","moyenne","moyen" -> "Modérée" to nodomain.freeyourgadget.gadgetbridge.R.drawable.hike_difficulty_moderate
        "difficult","hard","difficile" -> "Difficile" to nodomain.freeyourgadget.gadgetbridge.R.drawable.hike_difficulty_hard
        "strenuous","very_difficult","très difficile" -> "Très difficile" to nodomain.freeyourgadget.gadgetbridge.R.drawable.hike_difficulty_strenuous
        else -> route.difficulty to 0
    }
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(6.dp)) {
        if(icon!=0) Icon(painterResource(icon),null,Modifier.size(20.dp),tint=Color.Unspecified)
        else Icon(Icons.Rounded.Info,null,Modifier.size(20.dp),tint=Mf.colors.textSecondary)
        Text(tr(label),style=MaterialTheme.typography.labelMedium,color=Mf.colors.textSecondary)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HikeDetailScreen(nav:NavHostController,id:String) {
    val ctx=LocalContext.current;val scope=rememberCoroutineScope();val library by Hiking.library.collectAsState()
    val modules by Modules.installed.collectAsState();val session by Hiking.session.collectAsState()
    val catalogRevision by Hiking.catalogRevision.collectAsState()
    val route=remember(id,library,modules,catalogRevision){Hiking.route(id)}
    var busy by remember{mutableStateOf(false)};var error by remember{mutableStateOf<String?>(null)}
    var remove by remember{mutableStateOf(false)}
    var descriptionOpen by remember(id){mutableStateOf(false)}
    var directionsOpen by remember(id){mutableStateOf(false)}
    LaunchedEffect(id) {
        if(route!=null && route.provider.isNotBlank() && route.directions.isEmpty()) {
            busy=true
            try{Hiking.enrich(ctx,route)}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){error=tr("Les détails complémentaires sont indisponibles. Le tracé reste utilisable.")}finally{busy=false}
        }
    }
    val export=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")){uri -> if(uri!=null && route!=null) scope.launch{
        try{withContext(Dispatchers.IO){ctx.contentResolver.openOutputStream(uri)?.use{it.write(HikeGeometry.exportGpx(route).toByteArray())} ?: error("Fichier indisponible")}}
        catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){error=tr("Export impossible. Réessayez.")}
    } }
    DetailScaffold(route?.name ?: tr("Randonnée"),onBack={nav.popBackStack()}) {
        if(route==null){Text(tr("Parcours indisponible"));return@DetailScaffold}
        Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
            if(route.imageUrl.isNotBlank()) RoutePhoto(route,Modifier.fillMaxWidth().height(190.dp))
            Text(route.region,style=MaterialTheme.typography.labelLarge,color=HikingHue)
            DifficultyLabel(route)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Metric(tr("Distance"),km(HikeGeometry.length(route.points)))
                Metric(tr("Durée estimée"),duration(HikeGeometry.estimatedMinutes(route, Hiking.library.value.completed)))
                Metric(tr("Dénivelé +"),(route.ascent ?: HikeGeometry.ascent(route.points))?.let{"${it.toInt()} m"} ?: "—")
            }
            Text(tr(if(route.loop) "Boucle" else "Parcours linéaire"),color=Mf.colors.textSecondary)
            if(route.durationMinutes==null && route.ascent==null && HikeGeometry.ascent(route.points)==null) Text(tr("Durée indicative calculée sur la distance seule : l’altitude n’est pas fournie."),style=MaterialTheme.typography.bodySmall,color=Mf.colors.textSecondary)
            PillButton(tr(if(session!=null) "Retrouver ma randonnée" else "Suivre ce parcours"),Modifier.fillMaxWidth(),icon=Icons.Rounded.Explore) {
                if(session==null) Hiking.setSession(ctx,Hiking.Session(route=route,started=System.currentTimeMillis()))
                nav.go("hiking/live")
            }
            HikeMap(route,Modifier.fillMaxWidth().height(330.dp),interactive=true)
            Text(tr("Profil d’altitude"),style=MaterialTheme.typography.titleMedium)
            ElevationProfile(route)
            if(route.description.isNotBlank()) MfCard {
                Text(tr("À découvrir sur ce parcours"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                Text(route.description,style=MaterialTheme.typography.bodyMedium,maxLines=if(descriptionOpen)Int.MAX_VALUE else 6,overflow=TextOverflow.Ellipsis)
                TextButton(onClick={descriptionOpen=!descriptionOpen}){Text(tr(if(descriptionOpen)"Réduire" else "Lire la suite"))}
            }
            if(route.directions.isNotEmpty()) MfCard {
                Text(tr("Repères du parcours"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                route.directions.take(if(directionsOpen)route.directions.size else 3).forEachIndexed { index,text ->
                    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Text("${index+1}",style=MaterialTheme.typography.labelLarge,color=HikingHue)
                        Text(text,style=MaterialTheme.typography.bodyMedium,modifier=Modifier.weight(1f))
                    }
                }
                if(route.directions.size>3) TextButton(onClick={directionsOpen=!directionsOpen}){Text(tr(if(directionsOpen)"Réduire" else "Voir tous les repères"))}
            }
            if(route.surfaces.isNotEmpty()) MfCard {
                Text(tr("Terrain"),style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.Bold)
                route.surfaces.entries.sortedByDescending{it.value}.take(5).forEach { (kind,part) ->
                    val name=when(kind){"nature"->"Sol naturel";"asphalt"->"Asphalte";"gravel"->"Gravier";"paved"->"Revêtu";"alpine"->"Terrain alpin";else->"Autres terrains"}
                    Text("${tr(name)} · ${(part*100).toInt()} %",style=MaterialTheme.typography.bodySmall,color=Mf.colors.textSecondary)
                }
            }
            MfCard {
                Text(tr(if(route.features.isNotEmpty()) "Prêt hors ligne" else "Gardez aussi la carte"),fontWeight=FontWeight.Bold)
                Text(tr(if(route.features.isNotEmpty()) "Le parcours et une carte simplifiée des sentiers, routes, forêts et eaux sont sur ce téléphone." else "Le tracé est sur ce téléphone. Vous pouvez aussi enregistrer son fond simplifié ; vos cartes régionales restent disponibles."),style=MaterialTheme.typography.bodySmall,color=Mf.colors.textSecondary)
                TextButton(enabled=!busy,onClick={scope.launch{
                    busy=true;error=null
                    try{Hiking.downloadMap(ctx,route)}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(e:Exception){error=tr(e.message ?: "Téléchargement impossible. Réessayez.")}finally{busy=false}
                }}){Icon(Icons.Rounded.Download,null);Spacer(Modifier.width(8.dp));Text(tr(if(route.features.isEmpty()) "Télécharger la carte" else "Actualiser la carte"))}
            }
            if(busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            PillButton(tr(if(session!=null) "Retrouver ma randonnée" else "Suivre ce parcours"),Modifier.fillMaxWidth(),icon=Icons.Rounded.Explore) {
                if(session==null) Hiking.setSession(ctx,Hiking.Session(route=route,started=System.currentTimeMillis()))
                nav.go("hiking/live")
            }
            library.completed.firstOrNull { it.route==id }?.let { log ->
                if(app.superhealth.league.League.available()) PillButton(tr("Partager ma randonnée"),Modifier.fillMaxWidth(),strong=false,icon=Icons.Rounded.Share) {
                    app.superhealth.league.SocialPosts.draft.value=app.superhealth.league.SharedActivity.hikingDraft(route,log)
                    nav.go("social/compose")
                }
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick={scope.launch{try{withContext(Dispatchers.IO){Hiking.favorite(ctx,id)}}catch(e:kotlinx.coroutines.CancellationException){throw e}catch(_:Exception){error=tr("Enregistrement impossible. Réessayez.")}}},Modifier.weight(1f)){Icon(if(id in library.favorites)Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,null);Text(tr("Favoris"))}
                OutlinedButton(onClick={export.launch("${route.name.replace(Regex("[^\\p{L}\\p{N} _-]"),"_").take(80)}.gpx")},Modifier.weight(1f)){Icon(Icons.Rounded.FileUpload,null);Text(tr("Exporter GPX"))}
            }
            if(library.routes.any{it.id==id}) TextButton(onClick={remove=true},enabled=session?.route?.id!=id){Text(tr("Supprimer de mes parcours"),color=MaterialTheme.colorScheme.error)}
            Text(tr("Vérifiez les conditions locales et le balisage. La durée est indicative ; adaptez votre sortie au terrain et à votre expérience."),style=MaterialTheme.typography.bodySmall,color=Mf.colors.textSecondary)
        }
    }
    if(remove) AlertDialog(onDismissRequest={remove=false},title={Text(tr("Supprimer ce parcours ?"))},
        confirmButton={TextButton(onClick={Hiking.remove(ctx,id);remove=false;nav.popBackStack()}){Text(tr("Supprimer"))}},dismissButton={TextButton(onClick={remove=false}){Text(tr("Annuler"))}})
}

@Composable
private fun Metric(label:String,value:String){Column{Text(value,style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Text(label,style=MaterialTheme.typography.labelSmall,color=Mf.colors.textSecondary)}}

@Composable
fun HikeLiveScreen(nav:NavHostController) {
    val ctx=LocalContext.current
    val session by Hiking.session.collectAsState();val position by Hiking.location.collectAsState();val tracking by Hiking.tracking.collectAsState();val gpsError by Hiking.gpsError.collectAsState()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var confirm by remember { mutableStateOf(false) };var error by remember{mutableStateOf<String?>(null)}
    fun start() {
        if(Hiking.session.value==null) Hiking.setSession(ctx,Hiking.Session(started=System.currentTimeMillis()))
        try{HikeTrackingService.start(ctx)}catch(_:Exception){error=tr("Suivi GPS indisponible. Réessayez depuis cet écran.")}
    }
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()){ grants ->
        if(grants.values.any{it}) start() else error=tr("Autorisez la localisation pour enregistrer votre randonnée.")
    }
    LaunchedEffect(Unit){while(true){now=System.currentTimeMillis();kotlinx.coroutines.delay(1000)}}
    DetailScaffold(tr("Ma randonnée"),onBack={nav.popBackStack()}) {
        Column(verticalArrangement=Arrangement.spacedBy(16.dp)) {
            val current=session
            val map=current?.route ?: current?.points?.takeIf{it.size>1}?.let{HikeRoute("live",tr("Sortie libre"),points=it)}
            if(map!=null) HikeMap(map,Modifier.fillMaxWidth().height(360.dp),interactive=true,position=position,recorded=current?.points.orEmpty())
            else Box(Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(24.dp)).background(HikingHue.copy(alpha=.1f)),contentAlignment=Alignment.Center){Icon(Icons.Rounded.Landscape,null,Modifier.size(64.dp),tint=HikingHue)}
            Text(current?.route?.name ?: tr("Sortie libre"),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){
                Metric(tr("Distance"),km(HikeGeometry.length(current?.points.orEmpty())))
                val elapsed=current?.let{Hiking.elapsed(it,now)} ?: 0
                Metric(tr("Temps"),"%02d:%02d:%02d".format(Locale.ROOT,elapsed/3600,elapsed/60%60,elapsed%60))
                Metric(tr("Dénivelé +"),HikeGeometry.ascent(current?.points.orEmpty())?.let{"${it.toInt()} m"} ?: "—")
            }
            if(current?.route!=null && position!=null){
                var previous by remember(current.started, current.segment, current.route.id) { mutableStateOf<Double?>(null) }
                val projection = remember(position, current.started, current.segment, current.route.id) {
                    HikeGeometry.position(current.route.points, position!!, previous)
                }
                LaunchedEffect(projection) { previous = projection?.takeIf { it.offset <= 80 }?.progress }
                if(projection!=null){
                    LinearProgressIndicator(progress={(projection.progress/(projection.progress+projection.remaining).coerceAtLeast(1.0)).toFloat()},modifier=Modifier.fillMaxWidth(),color=HikingHue)
                    Text(trf("%1\$s restants · à %2\$s m du tracé",km(projection.remaining),projection.offset.toInt()),color=if(projection.offset>80)MaterialTheme.colorScheme.error else Mf.colors.textSecondary)
                    if(projection.offset>80) Text(tr("Vous vous éloignez du parcours"),color=MaterialTheme.colorScheme.error,fontWeight=FontWeight.Bold)
                }
            }
            gpsError?.let{Text(it,color=Mf.colors.textSecondary)}
            error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            if(!tracking) PillButton(tr(if(current!=null && current.points.isNotEmpty()) "Reprendre le suivi GPS" else "Démarrer le suivi GPS"),Modifier.fillMaxWidth(),icon=Icons.Rounded.PlayArrow){
                if(ContextCompat.checkSelfPermission(ctx,Manifest.permission.ACCESS_FINE_LOCATION)==PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(ctx,Manifest.permission.ACCESS_COARSE_LOCATION)==PackageManager.PERMISSION_GRANTED) start()
                else permission.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION))
            } else {
                PillButton(tr(if(current?.paused==true) "Reprendre" else "Pause"),Modifier.fillMaxWidth(),strong=false,icon=if(current?.paused==true)Icons.Rounded.PlayArrow else Icons.Rounded.Pause){
                    current?.let { s -> Hiking.setSession(ctx,if(s.paused) s.copy(paused=false,resumed=System.currentTimeMillis(),segment=s.segment+1) else s.copy(paused=true,seconds=Hiking.elapsed(s),resumed=System.currentTimeMillis())) }
                }
                Text(tr("Le GPS continue écran éteint. Une notification permet de revenir ici."),style=MaterialTheme.typography.bodySmall,color=Mf.colors.textSecondary)
            }
            if(current!=null) OutlinedButton(onClick={confirm=true},Modifier.fillMaxWidth()){Icon(Icons.Rounded.Stop,null);Text(tr("Terminer la randonnée"))}
        }
    }
    if(confirm) AlertDialog(onDismissRequest={confirm=false},title={Text(tr("Terminer la randonnée ?"))},text={Text(tr(if(session?.points.orEmpty().size>=2) "La sortie sera conservée sur ce téléphone. Vous pourrez exporter son tracé." else "Aucun tracé GPS n’a encore été enregistré. Terminer quittera cette sortie sans l’ajouter à l’historique."))},
        confirmButton={TextButton(onClick={
            val current=Hiking.session.value
            if(current!=null) Hiking.setSession(ctx,current.copy(seconds=Hiking.elapsed(current),paused=true))
            HikeTrackingService.stop(ctx)
            try{val saved=Hiking.finish(ctx);confirm=false;if(saved!=null){nav.popBackStack();nav.go("hiking/route/${saved.id}")}else nav.popBackStack()}
            catch(_:Exception){error=tr("Enregistrement impossible. Réessayez.");confirm=false}
        }){Text(tr("Terminer"))}},dismissButton={TextButton(onClick={confirm=false}){Text(tr("Continuer"))}})
}
