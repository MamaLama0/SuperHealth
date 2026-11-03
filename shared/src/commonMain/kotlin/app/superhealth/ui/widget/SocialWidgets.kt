package app.superhealth.ui.widget

import app.superhealth.platform.*
import app.superhealth.league.*
import app.superhealth.ui.tr
import app.superhealth.ui.trf
import app.superhealth.ui.dashboard.dashboardQuest
import app.superhealth.ui.dashboard.dashboardScoreEntries
import kotlinx.serialization.Serializable

/** Instantané public local : aucune clé, URL de relais ou donnée de journal n'est copiée vers WidgetKit. */
@Serializable data class SocialRow(val label: String,val value: String)
@Serializable data class SocialWidgetCard(val id: String,val title: String,val value: String,val caption: String,val detail: String,
    val symbol: String,val color: String,val route: String,val fraction: Double?=null,val rows: List<SocialRow> = emptyList(),val chart: List<Int> = emptyList())

internal suspend fun socialWidgets(): List<SocialWidgetCard> {
    if(!League.available())return listOf(
        Triple("league","Classement","trophy.fill"),Triple("progress","Progression","bolt.fill"),Triple("streak","Séries","flame.fill"),
        Triple("quests","Quêtes","flag.fill"),Triple("friends","Amis","person.2.fill"),Triple("social","Social","person.crop.circle.fill")
    ).map { (id,title,icon)->SocialWidgetCard(id,tr(title),"—",tr("Activer le classement"),tr("Ouvrez SuperHealth pour configurer vos suivis."),icon,"#4FA8E8","league") }
    val result=runCatching { ProgressRepository.compute(appContext) }.getOrNull()
    val score=runCatching { League.compute(appContext) }.getOrNull()
    val state=League.state.value
    val summary=result?.summary
    val rankings=dashboardScoreEntries(score,state.friends,nowMs()/1000,tr("Moi"))
    val rank=rankings.indexOfFirst { it.me }.takeIf { it>=0 }?.plus(1)
    val streaks=summary?.streaks.orEmpty().sortedByDescending { it.current }
    val best=streaks.firstOrNull()
    val week=state.questWeeks.firstOrNull { it.start==Progression.week(LocalDate.now().toEpochDay()) }
    val quest=dashboardQuest(week?.quests.orEmpty())
    val into=summary?.let { it.totalXp-Progression.threshold(it.level) } ?: 0L
    val span=summary?.let { Progression.threshold(it.level+1)-Progression.threshold(it.level) } ?: 1L
    val friends=state.friends
    val profile=app.superhealth.ui.userName() ?: tr("Mon profil")
    fun track(t: String)=Progression.Track.entries.firstOrNull { it.name==t }?.label?.let(::tr).orEmpty()
    return listOf(
        SocialWidgetCard("league",tr("Classement"),score?.takeIf { it.ready }?.total?.toString() ?: "—",tr("Indice global de santé"),
            rank?.let { trf("%1\$s sur %2\$s",it,rankings.size) } ?: tr("Sept jours terminés"),"trophy.fill","#4FA8E8","league",
            rows=rankings.take(5).map { SocialRow(it.name,it.score.toString()) }),
        SocialWidgetCard("progress",tr("Progression"),summary?.let { trf("Niv. %1\$s",it.level) } ?: "—",trf("%1\$s XP cette semaine",summary?.weekXp ?: 0),
            trf("%1\$s XP avant le prochain niveau",(span-into).coerceAtLeast(0)),"bolt.fill","#9BD54B","progress",
            fraction=(into.toDouble()/span.coerceAtLeast(1)).coerceIn(0.0,1.0),chart=summary?.days?.map { it.xp }.orEmpty()),
        SocialWidgetCard("streak",tr("Séries"),best?.current?.toString() ?: "0",best?.let { track(it.track) } ?: tr("Choisir mes suivis"),
            best?.let { trf(if(it.weekly)"%1\$s semaines" else "%1\$s jours",it.current) } ?: tr("Un premier objectif simple pour découvrir votre rythme."),
            "flame.fill","#FF8C36","progress",rows=streaks.take(5).map { SocialRow(track(it.track),trf(if(it.weekly)"%1\$s sem." else "%1\$s j",it.current)) }),
        SocialWidgetCard("quests",tr("Quêtes"),quest?.let { "${it.progress} / ${it.target}" } ?: "—",quest?.let { tr(it.metric.title) } ?: tr("Choisir mes défis"),
            quest?.let { if(it.claimed)tr("Récompense récupérée")else if(it.complete)tr("Récompense à récupérer")else trf("+%1\$s XP",it.reward) } ?: tr("À votre rythme cette semaine"),
            "flag.fill","#B38CF7","quests",fraction=quest?.let { (it.progress.toDouble()/it.target.coerceAtLeast(1)).coerceIn(0.0,1.0) },
            rows=week?.quests?.map { SocialRow(tr(it.metric.title),"${it.progress}/${it.target}") }.orEmpty()),
        SocialWidgetCard("friends",tr("Amis"),friends.size.toString(),tr(if(friends.size==1)"1 ami" else "Mes amis"),
            tr("Code ou QR"),"person.2.fill","#4CBFAB","friends",rows=friends.take(5).map { SocialRow(it.shown,it.progress?.let { p->trf("Niv. %1\$s",p.level) } ?: "—") }),
        SocialWidgetCard("social",tr("Social"),summary?.let { trf("Niv. %1\$s",it.level) } ?: "—",profile,tr("Votre progression et vos amis"),
            "person.crop.circle.fill","#B38CF7","social",rows=listOf(SocialRow(tr("XP cette semaine"),(summary?.weekXp ?: 0).toString()),
                SocialRow(tr("Amis"),friends.size.toString()),SocialRow(tr("Badges"),state.earnedBadges.size.toString())),chart=summary?.days?.map { it.xp }.orEmpty())
    )
}
