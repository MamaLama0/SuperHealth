import WidgetKit
import SwiftUI

@main
struct SuperHealthWidgets: WidgetBundle {
    @WidgetBundleBuilder
    var body: some Widget {
        CoreWidgets().body
        NutritionWidgets().body
        LeagueWidget()
        ProgressWidget()
        StreakWidget()
        QuestsWidget()
        FriendsWidget()
        SocialOverviewWidget()
    }
}

struct CoreWidgets: WidgetBundle {
    var body: some Widget {
        CaloriesWidget()
        RingsWidget()
        NextWorkoutWidget()
        WeightWidget()
        VitalsWidget()
        SleepWidget()
        StepsWidget()
        DayWidget()
        WorkoutLiveActivity()
    }
}
