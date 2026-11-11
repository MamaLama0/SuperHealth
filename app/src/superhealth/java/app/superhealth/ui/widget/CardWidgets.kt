package app.superhealth.ui.widget

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
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
import app.superhealth.cards.CardStore
import app.superhealth.cards.ArsenalGame
import app.superhealth.cards.EquipmentStore
import app.superhealth.league.ProgressRepository
import java.time.LocalDate
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import nodomain.freeyourgadget.gadgetbridge.R

/**
 * Widgets des cartes : paquet du jour, quêtes et personnage. Ils lisent l’état local sans lancer de synchronisation.
 */
abstract class CardWidget(private val kind: Kind) : GlanceAppWidget() {
    enum class Kind { PACKS, QUESTS, CARD }
    override val sizeMode = SizeMode.Responsive(WK.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        CardStore.load(context)
        val st = CardStore.state.value
        val enabled = SuperPrefs.settings.value.leagueEnabled
        val equipment = if (enabled) {
            runCatching { EquipmentStore.load(context) }
            EquipmentStore.state.value
        } else null
        val gold = Color(0xFFE0A82E)
        var hue = when (kind) { Kind.PACKS -> Color(0xFF638CE7); Kind.QUESTS -> gold; Kind.CARD -> Color(0xFF7C5CFF) }
        val icon = when (kind) { Kind.PACKS -> R.drawable.ic_w_cards; Kind.QUESTS -> R.drawable.ic_w_flag; Kind.CARD -> R.drawable.ic_w_lift }
        var route = when (kind) { Kind.PACKS -> "cards/packs"; Kind.QUESTS -> "cards/quests"; Kind.CARD -> "social" }
        val title = tr(when (kind) { Kind.PACKS -> "Packs"; Kind.QUESTS -> "Quêtes des cartes"; Kind.CARD -> "Mon personnage" })
        var value = "—"; var unit: String? = null; var caption = tr("Ouvrir SuperHealth"); var detail = ""; var fraction: Float? = null
        if (!enabled) caption = tr("Fonctions sociales désactivées")
        else when (kind) {
            Kind.PACKS -> {
                val day = LocalDate.now().toEpochDay()
                val progress = ProgressRepository.current.value?.takeIf { it.summary.throughDay == day }
                val quest = progress?.let { ArsenalGame.quests(it, ProgressRepository.selectedTracks(SuperPrefs.settings.value)) }
                val claimed = equipment?.day?.let { it >= day } == true
                val ready = quest?.canOpenDaily(equipment?.day) == true
                val bonus = (equipment?.freePacks ?: 0) + (equipment?.packCredits?.size ?: 0)
                value = if (ready || bonus > 0 || equipment?.pending != null || equipment?.rewardPending != null) (bonus + if (ready || equipment?.pending != null) 1 else 0).coerceAtLeast(1).toString() else if (claimed || progress != null) "0" else "—"
                unit = tr("pack")
                if (equipment?.goldNext == true) hue = gold
                caption = tr(when {
                    equipment?.pending != null -> "Reprendre le tirage"
                    bonus > 0 || equipment?.rewardPending != null -> "Paquets à récupérer"
                    claimed -> "Pack du jour ouvert"
                    ready -> "Pack du jour prêt"
                    else -> "Vérifier les suivis du jour"
                })
                fraction = quest?.rank?.div(3f)
            }
            Kind.QUESTS -> {
                val day = LocalDate.now().toEpochDay()
                val quest = ProgressRepository.current.value?.takeIf { it.summary.throughDay == day }?.let {
                    ArsenalGame.quests(it, ProgressRepository.selectedTracks(SuperPrefs.settings.value))
                }
                val rank = quest?.rank ?: 0
                val ready = quest?.canOpenDaily(equipment?.day) == true || (equipment?.freePacks ?: 0) > 0 ||
                    equipment?.packCredits.orEmpty().isNotEmpty() || equipment?.rewardPending != null
                if (ready) route = "cards/packs"
                value = if (ready) tr("À ouvrir") else if (quest == null) "—" else "$rank/3"
                caption = if (ready) tr("Paquet à récupérer") else if (quest == null) tr("Actualiser les suivis dans l’app") else tr(listOf("Premier sceau", "Grand sceau", "Sceau héroïque", "Tous les sceaux du jour")[rank])
                fraction = rank / 3f
                detail = quest?.let { "${it.weekCount}/6 " + tr("jours cette semaine") }.orEmpty()
            }
            Kind.CARD -> {
                val stats = CardStore.snapshot.value?.now ?: st.lastStats
                val character = ProgressRepository.current.value?.let { app.superhealth.cards.CharacterStats.from(it,
                    ProgressRepository.selectedTracks(SuperPrefs.settings.value), stats) } ?: stats?.character
                value = character?.scoreLine ?: "—"
                unit = "FOR · DEX · CON"
                caption = trf("%1\$s / 4 pièces équipées", equipment?.equipped?.size ?: 0)
            }
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
