import SwiftUI

/// Hôte de rendu isolé : les tests WidgetKit n'ont pas besoin de reconstruire le moteur Kotlin.
@main
struct WidgetTestHost: App {
    var body: some Scene {
        WindowGroup { Color.clear }
    }
}
