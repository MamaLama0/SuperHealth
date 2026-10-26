import SwiftUI
import UIKit
import Shared

/// Hôte iOS : toute l'interface est celle de l'app Android, en Compose Multiplatform (module « shared »).
@main
struct SuperHealthApp: App {
    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all)
                .onOpenURL { url in
                    // superhealth://route (ex. superhealth://meds)
                    MainViewControllerKt.openRoute(route: url.host.map { $0 + url.path })
                }
        }
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
}
