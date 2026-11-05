package app.superhealth.ui.widget

import app.superhealth.nutrition.widget.KcalWidgetReceiver
import app.superhealth.nutrition.widget.TodayWidgetReceiver
import app.superhealth.nutrition.widget.WeightWidgetReceiver
import app.superhealth.training.widget.NextWorkoutWidgetReceiver
import nodomain.freeyourgadget.gadgetbridge.R

/** Source unique du catalogue dans l'app et du banc d'essai des vrais widgets Android. */
object WidgetCatalog {
    data class Entry(val key: String, val section: String, val receiver: Class<*>, val name: Int, val description: Int)
    val entries = listOf(
        Entry("league", "Social", LeagueWidgetReceiver::class.java, R.string.widget_league_name, R.string.widget_league_desc),
        Entry("friends", "Social", FriendsWidgetReceiver::class.java, R.string.widget_friends_name, R.string.widget_friends_desc),
        Entry("social", "Social", SocialOverviewWidgetReceiver::class.java, R.string.widget_social_name, R.string.widget_social_desc),
        Entry("cardpacks", "Social", CardPacksWidgetReceiver::class.java, R.string.widget_cardpacks_name, R.string.widget_cardpacks_desc),
        Entry("cardquests", "Social", CardQuestsWidgetReceiver::class.java, R.string.widget_cardquests_name, R.string.widget_cardquests_desc),
        Entry("athletecard", "Social", AthleteCardWidgetReceiver::class.java, R.string.widget_athletecard_name, R.string.widget_athletecard_desc),
        Entry("day", "Santé", DayWidgetReceiver::class.java, R.string.widget_day_name, R.string.widget_day_desc),
        Entry("rings", "Santé", RingsWidgetReceiver::class.java, R.string.widget_rings_name, R.string.widget_rings_desc),
        Entry("ringsfree", "Santé", RingsFreeWidgetReceiver::class.java, R.string.widget_ringsfree_name, R.string.widget_ringsfree_desc),
        Entry("steps", "Santé", StepsWidgetReceiver::class.java, R.string.widget_steps_name, R.string.widget_steps_desc),
        Entry("sleep", "Santé", SleepWidgetReceiver::class.java, R.string.widget_sleep_name, R.string.widget_sleep_desc),
        Entry("vitals", "Santé", VitalsWidgetReceiver::class.java, R.string.widget_vitals_name, R.string.widget_vitals_desc),
        Entry("hydration", "Santé", HydrationWidgetReceiver::class.java, R.string.widget_hydration_name, R.string.widget_hydration_desc),
        Entry("insight", "Santé", InsightWidgetReceiver::class.java, R.string.widget_insight_name, R.string.widget_insight_desc),
        Entry("mind", "Santé", MindWidgetReceiver::class.java, R.string.widget_mind_name, R.string.widget_mind_desc),
        Entry("overview", "Santé", OverviewWidgetReceiver::class.java, R.string.widget_overview_name, R.string.widget_overview_desc),
        Entry("chargeeffort", "Santé", ChargeEffortWidgetReceiver::class.java, R.string.widget_chargeeffort_name, R.string.widget_chargeeffort_desc),
        Entry("today", "Nutrition", TodayWidgetReceiver::class.java, R.string.widget_today_name, R.string.widget_today_desc),
        Entry("kcal", "Nutrition", KcalWidgetReceiver::class.java, R.string.widget_kcal_name, R.string.widget_kcal_desc),
        Entry("macros", "Nutrition", app.superhealth.nutrition.widget.MacrosWidgetReceiver::class.java, R.string.widget_macros_name, R.string.widget_macros_desc),
        Entry("weekcalories", "Nutrition", app.superhealth.nutrition.widget.WeeklyCaloriesWidgetReceiver::class.java, R.string.widget_weekcalories_name, R.string.widget_weekcalories_desc),
        Entry("weekmacros", "Nutrition", app.superhealth.nutrition.widget.WeeklyMacrosWidgetReceiver::class.java, R.string.widget_weekmacros_name, R.string.widget_weekmacros_desc),
        Entry("weight", "Nutrition", WeightWidgetReceiver::class.java, R.string.widget_weight_name, R.string.widget_weight_desc),
        Entry("next", "Musculation", NextWorkoutWidgetReceiver::class.java, R.string.widget_next_name, R.string.widget_next_desc),
    )
}
