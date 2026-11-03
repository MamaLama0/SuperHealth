import WidgetKit
import SwiftUI

@main
struct SuperHealthWidgets: WidgetBundle {
    @WidgetBundleBuilder
    var body: some Widget {
        CoreWidgets()
        SocialWidget(id: "league", title: "Classement", about: "Votre indice et le classement entre amis.")
        SocialWidget(id: "progress", title: "Progression", about: "Niveau, XP et activité de la semaine.")
        SocialWidget(id: "streak", title: "Séries", about: "La régularité de vos suivis.")
        SocialWidget(id: "quests", title: "Quêtes de la semaine", about: "Le prochain défi ou la récompense à récupérer.")
        SocialWidget(id: "friends", title: "Amis", about: "Vos amis et leur progression partagée.")
        SocialWidget(id: "social", title: "Vue sociale", about: "Profil, niveau, amis et badges.")
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
        DayWidget()
        WorkoutLiveActivity()
    }
}
