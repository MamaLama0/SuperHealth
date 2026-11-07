package app.superhealth.ui.cards

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import app.superhealth.cards.*
import app.superhealth.league.League
import app.superhealth.ui.go
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal val Ink = Color(0xFF07080D)
internal val InkCard = Color(0xFF141724)
internal val Gold = Color(0xFFFFD36B)
internal val EclatColor = Color(0xFF8AE0FF)

/** Photo détourée de la carte, rechargée après chaque passage dans l'éditeur. */
object CardPhoto {
    val bitmap = MutableStateFlow<ImageBitmap?>(null)
    fun load(ctx: Context) {
        if (app.superhealth.ui.ProfilePhoto.bitmap.value == null) app.superhealth.ui.ProfilePhoto.load(ctx)
        val f = CardStore.cutoutFile(ctx)
        bitmap.value = if (f.exists()) runCatching { BitmapFactory.decodeFile(f.absolutePath)?.asImageBitmap() }.getOrNull() else null
    }
    /** Publie (ou retire) la photo détourée publique de la carte, en arrière-plan, quand le Groupe est actif. */
    fun publish(ctx: Context) {
        if (!app.superhealth.SuperPrefs.settings.value.leagueEnabled) return
        val appCtx = ctx.applicationContext
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching { app.superhealth.league.Faces.publishCardIfNeeded(appCtx) }.onFailure { android.util.Log.w("Faces", "card publish failed", it) }
        }
    }
}

/** Ma carte telle qu'elle s'affiche : photo choisie dans « Ma photo » (profil par défaut, détourée, ou aucune). */
@Composable
fun myFace(stats: AthleteStats?, background: Pull? = CardStore.background(), inForm: Boolean = false, cutoutOverride: ImageBitmap? = null): CardFace {
    val source by remember { CardStore.state }.collectAsState()
    val cutout by CardPhoto.bitmap.collectAsState()
    val profile = app.superhealth.ui.ProfilePhoto.bitmap.value
    val level = background?.let { source.level(it.card) } ?: 1
    return when {
        cutoutOverride != null -> CardFace(handle(), stats, background, cutoutOverride, inForm, PhotoMode.DETOUREE, level)
        source.photo == PhotoSource.DETOUREE && cutout != null -> CardFace(handle(), stats, background, cutout, inForm, PhotoMode.DETOUREE, level)
        source.photo == PhotoSource.AUCUNE -> CardFace(handle(), stats, background, null, inForm, level = level)
        else -> CardFace(handle(), stats, background, profile, inForm, PhotoMode.PORTRAIT, level)
    }
}

/**
 * Carte d'un ami : ses stats, son fond, son niveau et sa photo détourée publique quand il en a une ; sinon sa photo de
 * profil partagée (chiffrée), fondue en portrait.
 */
@Composable
fun friendFace(f: League.Friend): CardFace {
    val ctx = LocalContext.current
    val cutout = cardCutout(f.key)
    val photo by produceState<ImageBitmap?>(null, f.id, f.avatar) {
        val avatar = f.avatar?.takeIf { app.superhealth.league.ProfileAvatars.valid(it) } ?: return@produceState
        try {
            value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                BitmapFactory.decodeFile(app.superhealth.league.SocialPosts.downloadMedia(ctx, avatar, f).path)?.asImageBitmap()
            }
        } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { /* silhouette si la photo est indisponible */ }
    }
    val name = f.shown.lowercase().replace(" ", "")
    return if (cutout != null) CardFace(name, f.athlete, f.cardBg?.let(Pull::of), cutout, photoMode = PhotoMode.DETOUREE, level = f.cardLevel ?: 1)
        else CardFace(name, f.athlete, f.cardBg?.let(Pull::of), photo, photoMode = PhotoMode.PORTRAIT, level = f.cardLevel ?: 1)
}

/** Photo détourée publique de la carte de la personne de clé [key] (amie ou non), ou null. */
@Composable
fun cardCutout(key: String?): ImageBitmap? {
    val ctx = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, key) {
        if (key == null || !app.superhealth.SuperPrefs.settings.value.leagueEnabled) return@produceState
        value = runCatching { app.superhealth.league.Faces.cardBitmap(ctx, key)?.asImageBitmap() }.getOrNull()
    }
    return bitmap
}

/**
 * Carte d'un ami en grand, avant tout combat : on la regarde (un appui la retourne), on compare stat par stat à la sienne,
 * puis « Défier » lance le duel d'un clic de plus. [onTrade] apparaît quand l'ami accepte les échanges.
 */
@Composable
internal fun FriendCardViewer(face: CardFace, note: String, onClose: () -> Unit, onTrade: (() -> Unit)? = null, account: String? = null, onDuel: (() -> Unit)?) {
    val mine = CardStore.snapshot.collectAsState().value?.now
    androidx.compose.ui.window.Dialog(onClose, androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false)) {
        Box(Modifier.fillMaxSize().background(Ink.copy(alpha = 0.96f)).clickable(remember { androidx.compose.foundation.interaction.MutableInteractionSource() }, null, onClick = onClose)) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("@" + face.name, color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp, maxLines = 1)
                        Text(note, color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp, maxLines = 1)
                    }
                    IconButton(onClose) { Icon(Icons.Rounded.Close, tr("Fermer"), tint = Color.White) }
                }
                // La carte avale ses propres appuis (retournement) : seul le fond referme.
                AthleteCard(face, Modifier.padding(top = 12.dp).fillMaxWidth(0.82f), interactive = true, animate = true)
                Text(tr("Touchez la carte pour la retourner"), color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp))
                val theirs = face.stats
                if (theirs != null && mine != null) Column(Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(InkCard).padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row { Text(tr("Toi"), color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("@" + face.name, color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                    Stat.entries.forEach { stat ->
                        val a = mine[stat]; val b = theirs[stat]
                        if (a != null || b != null) Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(a?.toString() ?: "–", color = if ((a ?: 0) > (b ?: 0)) Color(0xFF6FD3A0) else Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp, modifier = Modifier.width(36.dp))
                            Text(stat.code + " · " + tr(stat.label), color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                            Text(b?.toString() ?: "–", color = if ((b ?: 0) > (a ?: 0)) Color(0xFFFF8A80) else Color.White, fontWeight = FontWeight.Black, fontSize = 17.sp, textAlign = TextAlign.End, modifier = Modifier.width(36.dp))
                        }
                    }
                    Text(tr("Hors bonus de fond, ajoutés pendant le duel."), color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                }
                if (onDuel != null) GlowButton("⚔ " + tr("Défier"), Modifier.fillMaxWidth().padding(top = 16.dp), listOf(Color(0xFFE53935), Color(0xFFFF8A1F)), enabled = mine?.ovr10 != null, onClick = onDuel)
                if (onDuel != null && mine?.ovr10 == null) Text(tr("Ta carte doit d'abord être évaluée (quelques séances)."), color = Gold, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
                if (onTrade != null) TextButton(onTrade, Modifier.padding(top = 4.dp)) { Text(tr("Proposer un échange"), color = Color.White) }
                // Identifiant de compte des cartes : sert à lui offrir des paquets depuis le serveur (rewards.json, « accounts »).
                if (account != null) {
                    val clip = androidx.compose.ui.platform.LocalClipboardManager.current
                    var copied by remember { mutableStateOf(false) }
                    Text(if (copied) tr("Identifiant copié") else tr("ID cadeau") + " : " + account.take(6) + "…" + account.takeLast(4),
                        color = Color.White.copy(alpha = 0.35f), fontSize = 11.sp,
                        modifier = Modifier.padding(top = 10.dp).clickable { clip.setText(androidx.compose.ui.text.AnnotatedString(account)); copied = true })
                }
            }
        }
    }
}

internal fun handle(): String = (app.superhealth.ui.userName() ?: "moi").lowercase().replace(Regex("\\s+"), "")
internal val isTestBuild get() = nodomain.freeyourgadget.gadgetbridge.BuildConfig.APPLICATION_ID != "app.superhealth"

/** Fond sombre commun aux écrans de cartes, teinté par le fond choisi. */
@Composable
fun CardScaffold(title: String, onBack: () -> Unit, tint: Color = Color(0xFF2C4CFF), action: @Composable RowScope.() -> Unit = {}, scroll: Boolean = true,
                 backdrop: (@Composable BoxScope.() -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Ink).drawBehind {
        if (backdrop == null) drawRect(Brush.radialGradient(listOf(tint.copy(alpha = 0.45f), Color.Transparent), Offset(size.width * 0.5f, size.height * 0.18f), size.width * 1.1f))
    }) {
        backdrop?.invoke(this)
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, tr("Retour"), tint = Color.White) }
                Text(tr(title), color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp, modifier = Modifier.weight(1f))
                action()
            }
            Column(Modifier.fillMaxSize().then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier).navigationBarsPadding().padding(horizontal = 16.dp),
                content = content)
        }
    }
}

@Composable
internal fun Wallet(state: CardStore.State, modifier: Modifier = Modifier) {
    Row(modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WalletChip(Icons.Rounded.Diamond, "${state.eclats} " + tr("éclats"), EclatColor)
        if (state.packsAvailable > 0) WalletChip(Icons.Rounded.CardGiftcard, "${state.packsAvailable} " + tr(if (state.packsAvailable > 1) "packs offerts" else "pack offert"), Color(0xFF9AA8FF))
        WalletChip(Icons.Rounded.AutoAwesome, if (state.nextGold) tr("Prochain pack doré") else tr("Doré dans") + " ${state.untilGold}", Gold)
    }
}

@Composable
internal fun WalletChip(icon: ImageVector, text: String, color: Color) {
    Row(Modifier.clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f)).border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(50))
        .padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(15.dp), tint = color)
        Text(text, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 5.dp))
    }
}

/** Gros bouton lumineux des écrans de cartes. */
@Composable
internal fun GlowButton(text: String, modifier: Modifier = Modifier, colors: List<Color> = listOf(Color(0xFFFF5E7A), Color(0xFFFF9A3C)), enabled: Boolean = true, badge: String? = null, onClick: () -> Unit) {
    val pulse by rememberInfiniteTransition(label = "glow").animateFloat(0.35f, 0.8f, infiniteRepeatable(tween(1200), RepeatMode.Reverse), label = "g")
    Box(modifier.height(54.dp).drawBehind {
        if (enabled) drawRoundRect(colors.first().copy(alpha = pulse * 0.6f), topLeft = Offset(-6f, -6f), size = androidx.compose.ui.geometry.Size(size.width + 12f, size.height + 12f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(size.height))
    }.clip(RoundedCornerShape(50)).background(if (enabled) Brush.horizontalGradient(colors) else Brush.horizontalGradient(listOf(Color(0xFF2A2D38), Color(0xFF2A2D38))))
        .clickable(enabled = enabled, onClick = onClick), contentAlignment = Alignment.Center) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text, color = if (enabled) Color.White else Color.White.copy(alpha = 0.4f), fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            if (badge != null) Text(badge, color = colors.first(), fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp).clip(CircleShape).background(Color.White).padding(horizontal = 7.dp, vertical = 2.dp))
        }
    }
}

@Composable
internal fun Tile(icon: ImageVector, title: String, subtitle: String?, modifier: Modifier = Modifier, accent: Color = Color.White, badge: Int = 0,
                  panel: Color = InkCard, fg: Color = Color.White, onClick: () -> Unit) {
    Column(modifier.clip(RoundedCornerShape(20.dp)).background(panel).border(1.dp, fg.copy(alpha = 0.07f), RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(22.dp), tint = accent)
            Spacer(Modifier.weight(1f))
            if (badge > 0) Text("$badge", color = Color.White, fontWeight = FontWeight.Black, fontSize = 12.sp, modifier = Modifier.clip(CircleShape).background(Color(0xFFFF4F7B)).padding(horizontal = 7.dp, vertical = 1.dp))
        }
        Text(tr(title), color = fg, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 10.dp))
        if (subtitle != null) Text(subtitle, color = fg.copy(alpha = 0.55f), fontSize = 12.sp)
    }
}

@Composable
internal fun SectionTitle(text: String) = Text(tr(text).uppercase(), color = Color.White.copy(alpha = 0.5f), fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.4.sp,
    modifier = Modifier.padding(top = 22.dp, bottom = 8.dp))

/** « Ma carte » : la carte Athlète, ce qui la fait monter, et l'accès aux packs, à l'album et aux duels. */
@Composable
fun CardsHome(nav: NavHostController) {
    val ctx = LocalContext.current
    val state by CardStore.state.collectAsState()
    val snap by CardStore.snapshot.collectAsState()
    val photo by CardPhoto.bitmap.collectAsState()
    val league by League.state.collectAsState()
    LaunchedEffect(Unit) { CardStore.load(ctx); CardPhoto.load(ctx); runCatching { CardStore.refresh(ctx) } }
    val bg = CardStore.background()
    val stats = snap?.now
    val tint = (bg?.card ?: Catalog.defaultFor(stats?.tier ?: Tier.BRONZE)).palette.b.c()
    val claimable = remember(state.claimed, snap) { runCatching { CardStore.quests().count { it.complete && !it.claimed } }.getOrDefault(0) }
    CardScaffold("Ma carte", { nav.popBackStack() }, tint, action = {
        // Partager ma carte : son fond, son niveau et la note globale, rien d'autre.
        if (bg != null) IconButton({ CardShare.open(bg, state.level(bg.card), CardShare.Event.ATHLETE, stats?.ovr); nav.go("social/compose") }) {
            Icon(Icons.Rounded.Share, tr("Partager"), tint = Color.White.copy(alpha = 0.7f))
        }
        IconButton({ nav.go("league/settings") }) { Icon(Icons.Rounded.Settings, tr("Réglages"), tint = Color.White.copy(alpha = 0.7f)) }
    }) {
        Wallet(state, Modifier.padding(top = 4.dp))
        val float by rememberInfiniteTransition(label = "float").animateFloat(-5f, 5f, infiniteRepeatable(tween(2600, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "f")
        AthleteCard(myFace(stats, bg, snap?.inForm == true),
            Modifier.padding(top = 18.dp).fillMaxWidth(0.82f).align(Alignment.CenterHorizontally).graphicsLayer { translationY = float * density })
        if (stats?.ovr10 == null) Text(tr("Ta carte s'évalue après quelques séances : deux familles de muscles suffisent pour une note."),
            color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 14.dp))
        bg?.let { BackgroundBonus(it, state.level(it.card)) }
        stats?.ovr10?.let { o -> NextLevel(o, stats) }
        GlowButton(tr("Ouvrir un pack"), Modifier.fillMaxWidth().padding(top = 18.dp), badge = state.packsAvailable.takeIf { it > 0 }?.toString()) { nav.go("cards/packs") }
        Row(Modifier.fillMaxWidth().padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val owned = Catalog.cards.count { state.owns(it) }
            Tile(Icons.Rounded.CollectionsBookmark, "Album", "$owned/${Catalog.cards.size}", Modifier.weight(1f), Color(0xFF9AA8FF)) { nav.go("cards/album") }
            Tile(Icons.Rounded.Bolt, "Duels", tr("Carte contre carte"), Modifier.weight(1f), Color(0xFFFF5E7A)) { nav.go("cards/duel") }
        }
        Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Tile(Icons.Rounded.Flag, "Quêtes", tr("Gagne des éclats"), Modifier.weight(1f), Gold, claimable) { nav.go("cards/quests") }
            Tile(Icons.Rounded.ContentCut, "Ma photo", tr(state.photo.label), Modifier.weight(1f), Color(0xFF6FD3A0)) { nav.go("cards/cutout") }
        }
        // 3.8 : vitrine, les fonds préférés mis en avant
        val shelf = remember(state.showcase, state.owned) { CardStore.showcase() }
        if (shelf.isNotEmpty()) {
            SectionTitle("Ma vitrine")
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                shelf.forEach { p -> CollectibleCard(p.card, p.finish, Modifier.weight(1f).clickable { nav.go("cards/album") }, live = p.card.rarity >= Rarity.RARE, level = state.level(p.card)) }
                repeat(CardStore.SHOWCASE_MAX - shelf.size) { Spacer(Modifier.weight(1f)) }
            }
        }
        val friends = league.friends.filter { it.athlete != null }
        var viewing by remember { mutableStateOf<League.Friend?>(null) }
        viewing?.let { f -> FriendCardViewer(friendFace(f), tr("Ami"), { viewing = null }, f.cardTrade?.let { { viewing = null; nav.go("cards/trades") } }, account = f.cardTrade) { viewing = null; nav.go("cards/duel?friend=${f.id}") } }
        if (friends.isNotEmpty()) {
            SectionTitle("Cartes de mes amis")
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                friends.forEach { f ->
                    AthleteCard(friendFace(f), Modifier.width(130.dp).clickable { viewing = f },
                        interactive = false, animate = false)
                }
            }
        }
        if (isTestBuild) {
            SectionTitle("Build de test")
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(InkCard).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(tr("Mode démo"), color = Color.White, fontWeight = FontWeight.Bold)
                    Text(tr("Carte d'exemple, rival fictif, +10 packs, +500 éclats, 1er pack doré, sans serveur"), color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp)
                }
                Switch(state.demo, { on -> CardStore.setDemo(ctx, on); CardStore.snapshot.value = null })
            }
            LaunchedEffect(state.demo) { runCatching { CardStore.refresh(ctx) } }
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun NextLevel(ovr10: Int, stats: AthleteStats) {
    val next = ovr10 / 10 + 1
    val progress = (ovr10 % 10) / 10f
    val anim by animateFloatAsState(progress, tween(1200, easing = FastOutSlowInEasing), label = "p")
    val tier = stats.tier
    Column(Modifier.fillMaxWidth().padding(top = 18.dp)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text("%.1f".format(ovr10 / 10.0).replace('.', ',') + " → $next", color = Color.White, fontWeight = FontWeight.Black, fontSize = 22.sp, modifier = Modifier.weight(1f))
            tier.next?.let { Text(tr(it.label) + " " + tr("à") + " ${it.min}", color = tierColors(it)[1], fontWeight = FontWeight.ExtraBold, fontSize = 15.sp) }
        }
        Box(Modifier.padding(vertical = 8.dp).fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f))) {
            Box(Modifier.fillMaxWidth(anim.coerceAtLeast(0.03f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Color(0xFF4FA8FF), Color(0xFF8AE0FF)))))
        }
        val gap = (next * 10 - ovr10) / 10.0
        val best = stats.mostProfitable
        Text(tr("Encore") + " " + "%.1f".format(gap).replace('.', ',') + " " + tr("point pour") + " $next." + (best?.let { " " + tr("Le plus rentable") + " : " + tr(it.mine) + " (${stats[it]})." } ?: ""),
            color = Color.White.copy(alpha = 0.75f), fontSize = 14.sp)
    }
}

@Composable
internal fun LinkRow(icon: ImageVector, title: String, value: String?, fg: Color = Color.White, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(20.dp), tint = fg.copy(alpha = 0.7f))
        Text(tr(title), color = fg, fontSize = 15.sp, modifier = Modifier.padding(start = 14.dp).weight(1f))
        if (value != null) Text(value, color = fg.copy(alpha = 0.5f), fontSize = 13.sp)
        Icon(Icons.Rounded.ChevronRight, null, tint = fg.copy(alpha = 0.3f))
    }
}

@Composable
fun CardQuestsScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    val state by CardStore.state.collectAsState()
    val league by app.superhealth.league.League.state.collectAsState()
    LaunchedEffect(Unit) { runCatching { app.superhealth.league.ProgressRepository.compute(ctx) } }
    val quests = remember(state.claimed, state.quests, league.questWeeks) { runCatching { CardStore.quests() }.getOrDefault(emptyList()) }
    val haptics = app.superhealth.ui.rememberHaptics()
    val scope = rememberCoroutineScope()
    var error by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) { runCatching { CardStore.refresh(ctx) } }
    CardScaffold("Quêtes", { nav.popBackStack() }, Gold) {
        Wallet(state)
        Text(tr("Chaque jour, des quêtes qui rapportent des éclats. Chaque semaine, trois quêtes de domaines différents qui offrent des packs : en simple, en groupe ou en rapidité contre tes amis."),
            color = Color.White.copy(alpha = 0.65f), fontSize = 13.sp, modifier = Modifier.padding(top = 12.dp))
        error?.let { Text(it, color = Color(0xFFFF8A80), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
        for (group in listOf("Aujourd'hui" to false, "Cette semaine" to true)) {
            SectionTitle(group.first)
            quests.filter { it.weekly == group.second }.sortedByDescending { it.challenge != null }.forEach { q -> QuestRow(q) { scope.launch {
                runCatching { CardStore.claim(ctx, q) }.onSuccess { error = null; haptics.confirm(); Rumble.reveal(ctx) }.onFailure { error = it.message }
            } } }
        }
        // La quête de la semaine se règle sur les domaines choisis (musculation, nutrition, course, quotidien…).
        Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(InkCard).clickable { nav.go("quests") }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(tr("Détail de mes quêtes de la semaine"), color = Color.White, modifier = Modifier.weight(1f))
            Icon(Icons.Rounded.ChevronRight, null, tint = Color.White.copy(alpha = 0.4f))
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** Mode d'une quête de la semaine : simple (la sienne), groupe (finie par tous) ou rapidité (le premier gagne). */
@Composable
internal fun ModePill(mode: CardStore.Mode) {
    val (icon, color) = when (mode) { CardStore.Mode.SIMPLE -> "◆" to Color(0xFF8AE0FF); CardStore.Mode.GROUPE -> "◎" to Color(0xFF6FD3A0); CardStore.Mode.RAPIDITE -> "⚡" to Color(0xFFFF8A65) }
    Text("$icon " + tr(mode.label).uppercase(), color = color, fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp))
}

/** Récompense d'une quête : un pack offert (pastille dorée, en forme de mini pack) ou des éclats. */
@Composable
internal fun RewardChip(q: CardStore.Quest, dim: Boolean = q.claimed) {
    val alpha = if (dim) 0.4f else 1f
    if (q.packs > 0) Row(Modifier.clip(RoundedCornerShape(50)).background(Brush.horizontalGradient(listOf(Gold.copy(alpha = 0.22f * alpha), Color(0xFFFF8A1F).copy(alpha = 0.18f * alpha))))
        .border(1.dp, Gold.copy(alpha = 0.55f * alpha), RoundedCornerShape(50)).padding(start = 5.dp, end = 10.dp, top = 3.dp, bottom = 3.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(width = 11.dp, height = 15.dp).clip(RoundedCornerShape(3.dp)).background(Brush.verticalGradient(listOf(Gold, Color(0xFFFF8A1F)))).border(1.dp, Color.White.copy(alpha = 0.6f), RoundedCornerShape(3.dp)))
        Text(CardStore.prize(q), color = Gold.copy(alpha = alpha), fontWeight = FontWeight.ExtraBold, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
    } else Text(CardStore.prize(q), color = EclatColor.copy(alpha = alpha), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
}

@Composable
private fun DifficultyPill(d: CardStore.Difficulty) = Text(tr(d.label).uppercase(), color = d.color.c(), fontWeight = FontWeight.Black, fontSize = 10.sp, letterSpacing = 1.sp,
    modifier = Modifier.clip(RoundedCornerShape(50)).background(d.color.c().copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 3.dp))

@Composable
private fun QuestRow(q: CardStore.Quest, onClaim: () -> Unit) {
    val color = q.difficulty.color.c()
    Column(Modifier.padding(bottom = 10.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(InkCard)
        .border(1.dp, if (q.complete && !q.claimed) color.copy(alpha = 0.7f) else Color.Transparent, RoundedCornerShape(18.dp)).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            DifficultyPill(q.difficulty)
            if (q.weekly) ModePill(q.mode)
            Spacer(Modifier.weight(1f))
            RewardChip(q)
        }
        Text(tr(q.title), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp, modifier = Modifier.padding(top = 8.dp))
        // Défi entre amis : qui l'a reçue, et ce qu'il faut pour le bonus.
        q.note?.let { Text(tr(it), color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp)) }
        Row(Modifier.padding(top = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.1f))) {
                Box(Modifier.fillMaxWidth((q.progress.toFloat() / q.target).coerceIn(0.02f, 1f)).fillMaxHeight().clip(RoundedCornerShape(50)).background(color))
            }
            Text("${q.progress}/${q.target}", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, modifier = Modifier.padding(horizontal = 10.dp))
            when {
                q.claimed -> Icon(Icons.Rounded.CheckCircle, null, tint = color)
                q.complete -> Text(tr("Récupérer"), color = Ink, fontWeight = FontWeight.Black, fontSize = 13.sp,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(color).clickable(onClick = onClaim).padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

/** Fond équipé : biome, niveau et bonus, avec la progression vers le niveau suivant. */
@Composable
private fun BackgroundBonus(bg: Pull, level: Int) {
    val card = bg.card
    val st = CardStore.state.collectAsState().value
    Row(Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp)).background(InkCard).padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        card.elements.forEach { ElementChip(it, 3.2.dp, Modifier.padding(end = 6.dp)) }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(card.name + " · " + tr("Niv.") + " $level", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(Bonus.label(bg, level) + " " + tr("en duel"), color = card.element.mid.c(), fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        }
        st.fuseCost(card)?.let { need -> Text("${st.spare(card).coerceAtMost(need)}/$need " + tr("doublons"), color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp) }
            ?: Text(tr("Niveau max"), color = Gold, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
fun PackRatesScreen(nav: NavHostController) {
    CardScaffold("Taux des packs", { nav.popBackStack() }) {
        Text(tr("Trois cartes par pack, et une quatrième une fois sur quatre. Chaque booster contient les 36 fonds de son biome et les duos qui le croisent. Une carte peut être un duo (1 chance sur 200, deux stats renforcées) et, en plus, brillante, animée ou chromatique : une épique chromatique duo existe. Pas de boutique : tout se gagne à l'entraînement ou s'échange entre amis."),
            color = Color.White.copy(alpha = 0.7f), fontSize = 13.sp)
        SectionTitle("Charte des cartes")
        EffectChart()
        for ((title, table) in listOf("1ʳᵉ carte" to PackEngine.slot1, "2ᵉ carte" to PackEngine.slot2, "3ᵉ carte" to PackEngine.slot3, "4ᵉ carte (1 pack sur 4)" to PackEngine.slot4)) {
            SectionTitle(title)
            Column(Modifier.clip(RoundedCornerShape(18.dp)).background(InkCard).padding(vertical = 4.dp)) {
                table.forEach { (r, w) ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text(r.symbol + "  " + tr(r.label), color = r.color.c(), fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                        Text("%.2f %%".format(w).replace('.', ',').replace(",00 ", " "), color = Color.White)
                    }
                }
            }
        }
        SectionTitle("Pack doré et Prisme")
        Column(Modifier.clip(RoundedCornerShape(18.dp)).background(InkCard).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(trf("Un pack sur %1\$s est doré : chances de rare, d'épique, de duo, d'animée, de chromatique et de Prisme multipliées par 3.", PackEngine.GOLD_EVERY), color = Color.White)
            Text(tr("Pack Prisme : 3 rares et 1 épique, 1 pack sur 1 000 (3 sur 1 000 en doré)."), color = Color.White)
            Text(tr("Effets par carte (chromatique / animée / brillante) :") + "\n" + PackEngine.EFFECTS.entries.joinToString("\n") { (r, e) ->
                r.symbol + " " + tr(r.label) + " : " + listOf(e.first, e.second, e.third).joinToString(" / ") { "%.1f %%".format(java.util.Locale.FRANCE, it).replace(",0 ", " ") } } +
                "\n" + tr("Duo : 0,5 % à toute rareté, toujours en finition normale. Chromatique et animée triplées en doré."), color = Color.White)
        }
        SectionTitle("Doublons et bonus")
        Column(Modifier.clip(RoundedCornerShape(18.dp)).background(InkCard).padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(tr("Fusionne tes doublons pour renforcer un fond : 2 exemplaires pour +10 % d'effet, 4 pour +20 %, 16 pour +30 %, 256 pour +40 %, contre 20, 40, 80 puis 160 éclats. Un doublon peut aussi s'échanger avec un ami, contre une autre carte de même rareté ou contre des éclats."), color = Color.White)
            Text(tr("Le fond équipé renforce la stat de son biome en duel :") + " " + Rarity.entries.joinToString(" · ") { it.symbol + " " + (it.bonus / 10.0).toString().replace(".0", "").replace('.', ',') + " %" } +
                tr(", × 1,1 en brillante, × 1,25 en animée, × 1,5 en chromatique. Chaque fusion ajoute 10 % à ce bonus. Un duo renforce ses deux stats. Régularité et cardio ne se boostent pas. La note globale ne change pas."), color = Color.White)
            Text(Element.entries.joinToString("\n") { tr(it.label) + " → " + it.stat.code + " · " + tr(it.tagline) }, color = Color.White.copy(alpha = 0.75f), fontSize = 13.sp)
        }
        Spacer(Modifier.height(32.dp))
    }
}

internal val Dp.half get() = this / 2
