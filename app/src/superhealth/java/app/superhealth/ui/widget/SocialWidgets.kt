package app.superhealth.ui.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
import app.superhealth.league.League
import app.superhealth.league.Progression
import app.superhealth.league.ProgressRepository
import app.superhealth.ui.dashboard.dashboardPriorityStreak
import app.superhealth.ui.dashboard.dashboardQuest
import app.superhealth.ui.dashboard.dashboardRank
import app.superhealth.ui.dashboard.dashboardScoreEntries
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import nodomain.freeyourgadget.gadgetbridge.R
import java.time.LocalDate

/** Les quatre cartes principales du social, sans réseau ni identité amie affichée sur l'accueil Android. */
abstract class SocialWidget(private val kind: Kind) : GlanceAppWidget() {
    enum class Kind { SCORE, PROGRESS, STREAK, QUEST, FRIENDS, OVERVIEW }
    override val sizeMode = SizeMode.Responsive(WK.ALL)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val enabled = SuperPrefs.settings.value.leagueEnabled
        val score = if (enabled) runCatching { League.compute(context) }.getOrNull() else null
        val summary = ProgressRepository.current.value?.summary
        val state = League.state.value
        val hue = when (kind) {
            Kind.SCORE -> Color(0xFFEAB544)
            Kind.PROGRESS -> Color(0xFF30A558)
            Kind.STREAK -> Color(0xFFFF9600)
            Kind.QUEST -> Color(0xFF9074DD)
            Kind.FRIENDS, Kind.OVERVIEW -> Color(0xFF4FA8E8)
        }
        val title = tr(when (kind) { Kind.SCORE -> "Classement"; Kind.PROGRESS -> "Progression"; Kind.STREAK -> "Mes séries"; Kind.QUEST -> "Quêtes"; Kind.FRIENDS -> "Amis"; Kind.OVERVIEW -> "Groupe" })
        val route = when (kind) { Kind.SCORE -> "league"; Kind.QUEST -> "quests"; Kind.FRIENDS -> "friends"; Kind.OVERVIEW -> "social"; else -> "progress" }
        val icon = when (kind) { Kind.SCORE -> R.drawable.ic_w_trophy; Kind.PROGRESS -> R.drawable.ic_w_bolt; Kind.STREAK -> R.drawable.ic_w_flame; Kind.QUEST -> R.drawable.ic_w_flag; Kind.FRIENDS -> R.drawable.ic_w_people; Kind.OVERVIEW -> R.drawable.ic_w_bolt }
        var place: Int? = null
        var participants = 0
        var value = "—"
        var unit: String? = null
        var caption = tr("Pas de données")
        var detail = tr("Ouvrir SuperHealth")
        var fraction: Float? = null
        var compactCaption: String? = null
        if (!enabled) caption = tr("Classement désactivé")
        else when (kind) {
            Kind.SCORE -> {
                val entries = dashboardScoreEntries(score, state.friends, System.currentTimeMillis() / 1000, "")
                val me = entries.firstOrNull { it.me }
                place = me?.let { dashboardRank(entries, it) }; participants = entries.size
                unit = "/ 1000"
                caption = if (me == null) tr("3 domaines requis") else if (entries.size == 1) tr("En solo")
                    else trf("Place %1\$s sur %2\$s", dashboardRank(entries, me), entries.size)
                if (me != null) { value = me.score.toString(); fraction = me.score / 1000f }
                val ahead = me?.let { own -> entries.lastOrNull { it.score > own.score } }
                detail = if (ahead != null && me != null) trf("À %1\$s points de la prochaine place", ahead.score - me.score)
                    else if (entries.size > 1) tr("En tête du Groupe · continuez !") else tr("Invitez votre premier rival")
                compactCaption = if (me != null && entries.size > 1) "#${dashboardRank(entries, me)} · " + tr("Groupe") else caption
            }
            Kind.PROGRESS -> summary?.let { p ->
                value = p.level.toString(); unit = tr("Niveau")
                val into = p.totalXp - Progression.threshold(p.level)
                val span = (Progression.threshold(p.level + 1) - Progression.threshold(p.level)).coerceAtLeast(1)
                fraction = (into.toFloat() / span).coerceIn(0f, 1f)
                caption = trf("%1\$s XP cette semaine", p.weekXp)
                compactCaption = trf("%1\$s XP / sem.", p.weekXp)
                detail = trf("%1\$s XP avant le niveau %2\$s", (span - into).coerceAtLeast(0), p.level + 1)
            }
            Kind.STREAK -> dashboardPriorityStreak(summary?.streaks.orEmpty())?.let { s ->
                value = s.current.toString() + if (s.capped) "+" else ""
                unit = tr(if (s.weekly) "sem." else "j")
                caption = Progression.Track.entries.firstOrNull { it.name == s.track }?.let { tr(it.label) } ?: ""
                detail = trf("Record : %1\$s", s.best.toString() + " " + unit)
                fraction = app.superhealth.ui.league.trackingFraction(s)
            }
            Kind.FRIENDS -> {
                value = state.friends.size.toString()
                caption = tr(if (state.friends.isEmpty()) "Ajouter un ami" else "Voir mes amis")
                detail = tr("Comparer nos progrès à notre rythme")
            }
            Kind.OVERVIEW -> {
                value = app.superhealth.cards.CardStore.state.value.let { (it.bonusPacks + it.eclats / it.price.coerceAtLeast(1)).toString() }; unit = tr("packs")
                val friends = trf(if (state.friends.size == 1) "%1\$s ami" else "%1\$s amis", state.friends.size)
                val badges = trf(if (state.earnedBadges.size == 1) "%1\$s badge" else "%1\$s badges", state.earnedBadges.size)
                caption = "$friends · $badges"
                compactCaption = friends
                val quests = state.questWeeks.firstOrNull { it.start == Progression.week(LocalDate.now().toEpochDay()) }?.quests.orEmpty()
                detail = if (quests.isEmpty()) tr("Configurer mes suivis") else trf("%1\$s / %2\$s accomplies", quests.count { it.complete }, quests.size)
                fraction = quests.takeIf { it.isNotEmpty() }?.let { it.count { q -> q.complete }.toFloat() / it.size }
            }
            Kind.QUEST -> {
                val week = state.questWeeks.firstOrNull { it.start == Progression.week(LocalDate.now().toEpochDay()) }
                val q = dashboardQuest(week?.quests.orEmpty())
                if (q == null) caption = tr("Configurer mes suivis")
                else {
                    value = "${q.progress}/${q.target}"
                    caption = tr(q.metric.title)
                    compactCaption = tr(q.metric.focus.label)
                    fraction = q.progress.toFloat() / q.target.coerceAtLeast(1)
                    detail = if (q.claimed) tr("Récompense récupérée") else if (q.complete) tr("Récompense à récupérer") else app.superhealth.ui.league.packLabel(app.superhealth.cards.CardStore.state.value.rewards.weekly.packs)
                }
            }
        }
        provideContent {
            if (kind == Kind.SCORE) ScoreWidgetContent(context, value, caption, detail, fraction, place, participants)
            else SocialContent(context, kind, route, title, icon, hue, value, unit, caption, compactCaption, detail, fraction)
        }
    }
}

@Composable
private fun ScoreWidgetContent(context: Context, value: String, caption: String, detail: String,
                               fraction: Float?, place: Int?, participants: Int) {
    val size = WK.size(LocalSize.current)
    val gold = Color(0xFFEAB544)
    WidgetCard(WK.open(context, "league"), gold, padding = if (size == WK.Size.SMALL) 5.dp else if (size == WK.Size.LARGE) 10.dp else 12.dp) {
        when (size) {
            WK.Size.SMALL -> {
                androidx.glance.Image(androidx.glance.ImageProvider(R.drawable.sh_league_trophy), null, GlanceModifier.size(17.dp))
                Text(place?.takeIf { participants > 1 }?.let { "#$it" } ?: value,
                    style = TextStyle(color = WK.on(gold), fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1)
            }
            WK.Size.STRIP -> Row(verticalAlignment = Alignment.CenterVertically) {
                androidx.glance.Image(androidx.glance.ImageProvider(R.drawable.sh_league_trophy), null, GlanceModifier.size(30.dp))
                Spacer(GlanceModifier.width(8.dp))
                Column(GlanceModifier.defaultWeight()) {
                    Text(tr("Groupe"), style = TextStyle(color = WK.text, fontSize = 12.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                    Text(caption, style = TextStyle(color = WK.sub, fontSize = 10.sp), maxLines = 1)
                }
                WidgetValue(value, null, gold, 20)
            }
            else -> {
                Text(tr("Groupe"), style = TextStyle(color = WK.sub, fontSize = 11.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                Spacer(GlanceModifier.height(5.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    androidx.glance.Image(androidx.glance.ImageProvider(R.drawable.sh_league_trophy), null,
                        GlanceModifier.size(if (size == WK.Size.LARGE) 42.dp else 32.dp))
                    Spacer(GlanceModifier.width(8.dp))
                    Column(GlanceModifier.defaultWeight()) {
                        Text(place?.takeIf { participants > 1 }?.let { "#$it" } ?: value,
                            style = TextStyle(color = WK.on(gold), fontSize = 24.sp, fontWeight = FontWeight.Bold), maxLines = 1)
                        Text(if (place != null && participants > 1) trf("%1\$s points", value) else tr("Score global"),
                            style = TextStyle(color = WK.sub, fontSize = 10.sp), maxLines = 1)
                    }
                }
                if (size == WK.Size.MEDIUM) {
                    Spacer(GlanceModifier.height(5.dp))
                    Text(caption, style = TextStyle(color = WK.text, fontSize = 10.sp), maxLines = 1)
                }
                if (fraction != null) {
                    Spacer(GlanceModifier.height(5.dp))
                    LinearProgressIndicator(fraction, GlanceModifier.fillMaxWidth().height(4.dp), color = WK.on(gold), backgroundColor = WK.track)
                }
                if (size == WK.Size.LARGE) {
                    Spacer(GlanceModifier.height(4.dp))
                    Text(detail, style = TextStyle(color = WK.sub, fontSize = 10.sp), maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun SocialContent(context: Context, kind: SocialWidget.Kind, route: String, title: String, icon: Int, hue: Color, value: String,
                          unit: String?, caption: String, compactCaption: String?, detail: String, fraction: Float?) {
    val size = WK.size(LocalSize.current)
    val compact = size == WK.Size.SMALL
    WidgetCard(WK.open(context, route), hue, padding = if (compact) 6.dp else 12.dp) {
        when (size) {
            WK.Size.SMALL -> {
                WidgetChip(icon, hue, 14.dp)
                Spacer(GlanceModifier.height(2.dp))
                Text(if (kind == SocialWidget.Kind.OVERVIEW) value.substringAfterLast(' ') else value, style = TextStyle(color = WK.on(hue), fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1)
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
            WK.Size.MEDIUM -> {
                WidgetHeader(icon, hue, tr(when (kind) {
                    SocialWidget.Kind.SCORE -> "Score"
                    SocialWidget.Kind.PROGRESS -> "Niveau"
                    SocialWidget.Kind.STREAK -> "Mes séries"
                    else -> title
                }))
                Spacer(GlanceModifier.height(4.dp))
                WidgetValue(value, if (kind == SocialWidget.Kind.PROGRESS) null else unit, hue, 22)
                Text(compactCaption ?: caption, style = TextStyle(color = WK.sub, fontSize = 11.sp), maxLines = 1)
                if (fraction != null) {
                    Spacer(GlanceModifier.height(6.dp))
                    LinearProgressIndicator(fraction.coerceIn(0f, 1f), GlanceModifier.fillMaxWidth().height(4.dp), color = WK.on(hue), backgroundColor = WK.track)
                }
            }
            WK.Size.LARGE -> {
                WidgetHeader(icon, hue, title)
                Spacer(GlanceModifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    WidgetValue(value, unit, hue, 24)
                    Spacer(GlanceModifier.width(12.dp))
                    Text(caption, modifier = GlanceModifier.defaultWeight(),
                        style = TextStyle(color = WK.sub, fontSize = 11.sp), maxLines = 2)
                }
                if (fraction != null) {
                    Spacer(GlanceModifier.height(6.dp))
                    LinearProgressIndicator(fraction.coerceIn(0f, 1f), GlanceModifier.fillMaxWidth().height(4.dp), color = WK.on(hue), backgroundColor = WK.track)
                }
                Spacer(GlanceModifier.height(4.dp))
                Text(detail, style = TextStyle(color = WK.text, fontSize = 11.sp), maxLines = 1)
            }
        }
    }
}

class LeagueWidget : SocialWidget(Kind.SCORE)
class ProgressWidget : SocialWidget(Kind.PROGRESS)
class StreakWidget : SocialWidget(Kind.STREAK)
class QuestWidget : SocialWidget(Kind.QUEST)
class FriendsWidget : SocialWidget(Kind.FRIENDS)
class SocialOverviewWidget : SocialWidget(Kind.OVERVIEW)
class LeagueWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = LeagueWidget() }
// Les niveaux ont laissé la place aux quêtes : un widget Progression déjà posé affiche les quêtes des cartes.
class ProgressWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = LegacyQuestWidget() }
// Séries et quêtes du Groupe ont laissé la place aux quêtes des cartes : les widgets déjà posés les affichent.
class StreakWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = LegacyQuestWidget() }
class QuestWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = LegacyQuestWidget() }

class FriendsWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = FriendsWidget() }
class SocialOverviewWidgetReceiver : GlanceAppWidgetReceiver() { override val glanceAppWidget: GlanceAppWidget = SocialOverviewWidget() }
