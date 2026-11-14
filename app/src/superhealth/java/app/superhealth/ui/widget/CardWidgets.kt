package app.superhealth.ui.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.LinearProgressIndicator
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.provideContent
import androidx.glance.layout.*
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.superhealth.SuperPrefs
import app.superhealth.cards.Bonus
import app.superhealth.cards.CardStore
import app.superhealth.cards.Tier
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import nodomain.freeyourgadget.gadgetbridge.R

/**
 * Widgets des cartes Athlète : packs à ouvrir et compteur du pack doré, quêtes du jour, et ta carte (note, fond
 * équipé, bonus). Tout vient de l'état local reflété du serveur : aucun appel réseau depuis l'écran d'accueil.
 */
abstract class CardWidget(private val kind: Kind) : GlanceAppWidget() {
    enum class Kind { PACKS, QUESTS, CARD }
    override val sizeMode = SizeMode.Responsive(WK.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        CardStore.load(context)
        val st = CardStore.state.value
        val enabled = SuperPrefs.settings.value.leagueEnabled
        val gold = Color(0xFFE0A82E)
        var hue = when (kind) { Kind.PACKS -> Color(st.booster.element.mid.toInt()); Kind.QUESTS -> gold; Kind.CARD -> Color(0xFF7C5CFF) }
        val icon = when (kind) { Kind.PACKS -> R.drawable.ic_w_cards; Kind.QUESTS -> R.drawable.ic_w_flag; Kind.CARD -> R.drawable.ic_w_lift }
        val route = when (kind) { Kind.PACKS -> "cards/packs"; Kind.QUESTS -> "cards/quests"; Kind.CARD -> "social" }
        val title = tr(when (kind) { Kind.PACKS -> "Packs"; Kind.QUESTS -> "Quêtes des cartes"; Kind.CARD -> "Carte Athlète" })
        var value = "—"; var unit: String? = null; var caption = tr("Ouvrir SuperHealth"); var detail = ""; var fraction: Float? = null
        if (!enabled) caption = tr("Fonctions sociales désactivées")
        else when (kind) {
            Kind.PACKS -> {
                val packs = st.bonusPacks + st.eclats / st.price.coerceAtLeast(1)
                value = packs.toString(); unit = tr(if (packs > 1) "packs" else "pack")
                if (st.nextGold) hue = gold
                caption = if (st.nextGold) tr("Le prochain pack est doré !") else trf("Pack doré dans %1\$s", st.untilGold)
                detail = trf("%1\$s or", st.eclats)
                fraction = (st.eclats % st.price.coerceAtLeast(1)) / st.price.coerceAtLeast(1).toFloat()
            }
            Kind.QUESTS -> {
                val quests = runCatching { CardStore.quests() }.getOrDefault(emptyList())
                val claimable = quests.count { it.complete && !it.claimed }
                val next = quests.firstOrNull { !it.complete }
                value = "${quests.count { it.complete }}/${quests.size}"
                caption = when { claimable > 0 -> trf(if (claimable > 1) "%1\$s récompenses à récupérer" else "%1\$s récompense à récupérer", claimable)
                    next != null -> tr(next.title); else -> tr("Toutes les quêtes sont faites") }
                detail = next?.let { "${it.progress}/${it.target} · " + CardStore.prize(it) } ?: ""
                fraction = quests.takeIf { it.isNotEmpty() }?.let { it.count { q -> q.complete }.toFloat() / it.size }
            }
            Kind.CARD -> {
                val ovr = CardStore.snapshot.value?.now?.ovr ?: st.lastOvr10.takeIf { it > 0 }?.let { (it + 5) / 10 }
                val bg = CardStore.background()
                if (bg != null) hue = Color(bg.card.element.mid.toInt())
                value = ovr?.toString() ?: "—"; unit = ovr?.let { tr(Tier.of(it).label) }
                caption = bg?.let { it.card.name + " · " + tr("Niv.") + " ${st.level(it.card)}" } ?: tr("Choisis un fond dans l'album")
                detail = bg?.let { Bonus.label(it, st.level(it.card)) } ?: ""
            }
        }
        if (enabled && kind == Kind.QUESTS) {
            val quests = runCatching { CardStore.quests() }.getOrDefault(emptyList())
            val ring = WK.ring(quests.takeIf { it.isNotEmpty() }?.let { it.count { q -> q.complete }.toFloat() / it.size } ?: 0f, QuestGold, WK.px(context, 96.dp), 0.1f)
            return provideContent { QuestBoard(context, quests, ring) }
        }
        if (enabled && kind == Kind.CARD) {
            val art = runCatching { CardArt.render(context, st, 540, 756) }.getOrNull()
            if (art != null) return provideContent { CardPortrait(context, art, st) }
        }
        provideContent { CardContent(context, route, title, icon, hue, value, unit, caption, detail, fraction) }
    }
}

@androidx.compose.runtime.Composable
private fun CardContent(context: Context, route: String, title: String, icon: Int, hue: Color, value: String, unit: String?, caption: String, detail: String, fraction: Float?) {
    val size = WK.size(LocalSize.current)
    WidgetCard(WK.open(context, route), hue, padding = if (size == WK.Size.SMALL) 6.dp else 12.dp) {
        when (size) {
            WK.Size.SMALL -> {
                WidgetChip(icon, hue, 14.dp)
                Spacer(GlanceModifier.height(2.dp))
                Text(value, style = TextStyle(color = WK.on(hue), fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
            WK.Size.STRIP -> Row(verticalAlignment = Alignment.CenterVertically) {
                WidgetChip(icon, hue, 24.dp)
                Spacer(GlanceModifier.width(8.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(title, style = TextStyle(color = WK.text, fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                    Text(caption, style = TextStyle(color = WK.sub, fontSize = 10.sp), maxLines = 1)
                }
                Spacer(GlanceModifier.width(6.dp))
                WidgetValue(value, unit, hue, 18)
            }
            else -> {
                WidgetHeader(icon, hue, title)
                Spacer(GlanceModifier.height(4.dp))
                WidgetValue(value, unit, hue, if (size == WK.Size.LARGE) 24 else 22)
                Text(caption, style = TextStyle(color = WK.sub, fontSize = 11.sp), maxLines = if (size == WK.Size.LARGE) 2 else 1)
                if (fraction != null) {
                    Spacer(GlanceModifier.height(6.dp))
                    LinearProgressIndicator(fraction.coerceIn(0f, 1f), GlanceModifier.fillMaxWidth().height(4.dp), color = WK.on(hue), backgroundColor = WK.track)
                }
                if (size == WK.Size.LARGE && detail.isNotEmpty()) {
                    Spacer(GlanceModifier.height(4.dp))
                    Text(detail, style = TextStyle(color = WK.text, fontSize = 11.sp), maxLines = 1)
                }
            }
        }
    }
}

private val QuestInk = Color(0xFF15110A)
private val QuestGold = Color(0xFFFFC94A)

/**
 * Tableau des quêtes : fond nuit et or, une ligne par quête avec sa jauge, sa difficulté et son or ; une quête
 * accomplie montre sa récompense à récupérer. Toucher ouvre les quêtes.
 */
@androidx.compose.runtime.Composable
private fun QuestBoard(context: Context, quests: List<CardStore.Quest>, ring: android.graphics.Bitmap) {
    val size = WK.size(LocalSize.current)
    val ready = quests.count { it.complete && !it.claimed }
    val done = quests.count { it.claimed }
    val shown = quests.sortedWith(compareBy<CardStore.Quest> { it.claimed }.thenByDescending { it.complete && !it.claimed }.thenBy { it.weekly })
    Box(GlanceModifier.fillMaxSize().cornerRadius(WK.RADIUS.dp).background(androidx.glance.ImageProvider(CardArt.questBackdrop(square = size == WK.Size.SMALL || size == WK.Size.MEDIUM)))
        .clickable(WK.open(context, "cards/quests")).padding(if (size == WK.Size.SMALL) 4.dp else 12.dp)) {
        when (size) {
            // petit : anneau doré des quêtes faites, drapeau et compteur au centre ; une récompense à prendre passe en pastille
            WK.Size.SMALL -> Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Box(GlanceModifier.size(50.dp), contentAlignment = Alignment.Center) {
                    androidx.glance.Image(androidx.glance.ImageProvider(ring), null, GlanceModifier.size(50.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        androidx.glance.Image(androidx.glance.ImageProvider(R.drawable.ic_w_flag), null, GlanceModifier.size(12.dp),
                            colorFilter = androidx.glance.ColorFilter.tint(WK.c(QuestGold)))
                        Text("${quests.count { it.complete }}/${quests.size}", style = TextStyle(color = WK.c(Color.White), fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    }
                }
                if (ready > 0) Box(GlanceModifier.fillMaxSize(), contentAlignment = Alignment.TopEnd) {
                    Box(GlanceModifier.size(16.dp).cornerRadius(8.dp).background(QuestGold), contentAlignment = Alignment.Center) {
                        Text("$ready", style = TextStyle(color = WK.c(QuestInk), fontSize = 10.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    }
                }
            }
            WK.Size.STRIP -> Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
                Text("⚑", style = TextStyle(color = WK.c(QuestGold), fontSize = 22.sp))
                Spacer(GlanceModifier.width(10.dp))
                val q = shown.firstOrNull()
                Column(GlanceModifier.defaultWeight()) {
                    Text(q?.let { tr(it.title) } ?: tr("Tes quêtes arrivent"), style = TextStyle(color = WK.c(Color.White), fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Spacer(GlanceModifier.height(4.dp))
                    if (q != null) LinearProgressIndicator(q.progress / q.target.coerceAtLeast(1).toFloat(), GlanceModifier.fillMaxWidth().height(5.dp), color = WK.c(QuestGold), backgroundColor = WK.c(Color(0x33FFFFFF)))
                }
                Spacer(GlanceModifier.width(10.dp))
                RewardPill(if (ready > 0) "$ready ✦" else "$done/${quests.size}", ready > 0)
            }
            else -> Column(GlanceModifier.fillMaxSize()) {
                Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("⚑ " + tr("QUÊTES"), style = TextStyle(color = WK.c(QuestGold), fontSize = 13.sp, fontWeight = FontWeight.Bold), modifier = GlanceModifier.defaultWeight())
                    RewardPill(if (ready > 0) trf("%1\$s à prendre", ready) else "$done/${quests.size}", ready > 0)
                }
                Spacer(GlanceModifier.height(6.dp))
                shown.take(if (size == WK.Size.LARGE) 3 else 2).forEach { q ->
                    QuestLine(q, compact = size == WK.Size.MEDIUM)
                    Spacer(GlanceModifier.height(6.dp))
                }
                if (quests.isEmpty()) Text(tr("Lance une séance : tes quêtes du jour arrivent."), style = TextStyle(color = WK.c(Color(0xCCFFFFFF)), fontSize = 11.sp))
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun RewardPill(text: String, hot: Boolean) = Box(GlanceModifier.cornerRadius(50.dp).background(if (hot) QuestGold else Color(0x26FFFFFF)).padding(horizontal = 8.dp, vertical = 3.dp)) {
    Text(text, style = TextStyle(color = WK.c(if (hot) QuestInk else Color.White), fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
}

@androidx.compose.runtime.Composable
private fun QuestLine(q: CardStore.Quest, compact: Boolean) {
    val hue = Color(q.difficulty.color.toInt())
    Column(GlanceModifier.fillMaxWidth()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(if (q.claimed) "✓" else if (q.complete) "★" else "•", style = TextStyle(color = WK.c(if (q.claimed) Color(0xFF6FD3A0) else hue), fontSize = 12.sp, fontWeight = FontWeight.Bold))
            Spacer(GlanceModifier.width(5.dp))
            Text(tr(q.title), style = TextStyle(color = WK.c(if (q.claimed) Color(0x80FFFFFF) else Color.White), fontSize = if (compact) 10.sp else 11.sp, fontWeight = FontWeight.Medium),
                maxLines = 1, modifier = GlanceModifier.defaultWeight())
            if (!compact) Text(if (q.complete && !q.claimed) CardStore.prize(q, short = true) else "${q.progress}/${q.target}",
                style = TextStyle(color = WK.c(if (q.complete && !q.claimed) QuestGold else Color(0xB3FFFFFF)), fontSize = 11.sp, fontWeight = FontWeight.Bold))
        }
        Spacer(GlanceModifier.height(3.dp))
        LinearProgressIndicator((q.progress / q.target.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f), GlanceModifier.fillMaxWidth().height(4.dp),
            color = WK.c(if (q.claimed) Color(0xFF6FD3A0) else hue), backgroundColor = WK.c(Color(0x26FFFFFF)))
    }
}

/** La carte Athlète en grand : l'image dessinée par [CardArt], posée telle quelle (le widget suit son format). */
@androidx.compose.runtime.Composable
private fun CardPortrait(context: Context, art: android.graphics.Bitmap, st: CardStore.State) {
    val size = WK.size(LocalSize.current)
    Box(GlanceModifier.fillMaxSize().clickable(WK.open(context, "social")), contentAlignment = Alignment.Center) {
        if (size == WK.Size.STRIP || size == WK.Size.LARGE) Row(GlanceModifier.fillMaxSize().cornerRadius(WK.RADIUS.dp).background(androidx.glance.ImageProvider(CardArt.questBackdrop())).padding(8.dp),
            verticalAlignment = Alignment.CenterVertically) {
            androidx.glance.Image(androidx.glance.ImageProvider(art), tr("Carte Athlète"), GlanceModifier.fillMaxHeight().width(if (size == WK.Size.LARGE) 82.dp else 34.dp), contentScale = androidx.glance.layout.ContentScale.Fit)
            Spacer(GlanceModifier.width(10.dp))
            val bg = CardStore.background()
            Column(GlanceModifier.defaultWeight()) {
                Text("@" + (app.superhealth.ui.userName() ?: "moi").lowercase().replace(" ", ""), style = TextStyle(color = WK.c(Color.White), fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                Text(bg?.let { it.card.name + " · " + tr("Niv.") + " ${st.level(it.card)}" } ?: tr("Choisis un fond dans l'album"), style = TextStyle(color = WK.c(Color(0xB3FFFFFF)), fontSize = 11.sp), maxLines = 1)
                if (size == WK.Size.LARGE) {
                    Spacer(GlanceModifier.height(4.dp))
                    bg?.let { Text(Bonus.label(it, st.level(it.card)), style = TextStyle(color = WK.c(Color(it.card.element.mid.toInt())), fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1) }
                    Spacer(GlanceModifier.height(4.dp))
                    Text(if (st.bonusPacks > 0) trf("%1\$s packs à ouvrir", st.bonusPacks) else trf("%1\$s or", st.eclats), style = TextStyle(color = WK.c(QuestGold), fontSize = 11.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                }
            }
        } else androidx.glance.Image(androidx.glance.ImageProvider(art), tr("Carte Athlète"), GlanceModifier.fillMaxSize(), contentScale = androidx.glance.layout.ContentScale.Fit)
    }
}

class CardPacksWidget : CardWidget(Kind.PACKS)
class CardQuestsWidget : CardWidget(Kind.QUESTS)
class AthleteCardWidget : CardWidget(Kind.CARD)
class CardPacksWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = CardPacksWidget() }
class CardQuestsWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = CardQuestsWidget() }
/** Anciens widgets « Mes séries » et « Quêtes » (retirés du catalogue) : ceux déjà posés affichent les quêtes des cartes. */
class LegacyQuestWidget : CardWidget(Kind.QUESTS)
class AthleteCardWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = AthleteCardWidget() }

object CardWidgets {
    /** Rafraîchit les widgets des cartes après une synchronisation, une ouverture ou une quête récupérée. */
    suspend fun refresh(ctx: Context) {
        val mgr = android.appwidget.AppWidgetManager.getInstance(ctx)
        val glance = androidx.glance.appwidget.GlanceAppWidgetManager(ctx)
        listOf(CardPacksWidgetReceiver::class.java to CardPacksWidget(), CardQuestsWidgetReceiver::class.java to CardQuestsWidget(),
            AthleteCardWidgetReceiver::class.java to AthleteCardWidget(), StreakWidgetReceiver::class.java to LegacyQuestWidget(), ProgressWidgetReceiver::class.java to LegacyQuestWidget(),
            QuestWidgetReceiver::class.java to LegacyQuestWidget()).forEach { (receiver, widget) ->
            mgr.getAppWidgetIds(android.content.ComponentName(ctx, receiver)).forEach { id -> runCatching { widget.update(ctx, glance.getGlanceIdBy(id)) } }
        }
    }
}
