import SwiftUI
import UIKit
import WidgetKit
import ActivityKit
import Shared

// Les noms Kotlin exportés (URL, Date, Context…) peuvent masquer ceux de Foundation et SwiftUI : ils sont qualifiés ici.

/// Hôte iOS : toute l'interface est celle de l'app Android, en Compose Multiplatform (module « shared »).
@main
struct SuperHealthApp: App {
    @UIApplicationDelegateAdaptor(AppDelegate.self) var appDelegate

    init() {
        // source Apple Santé et tâches de fond avant tout affichage ; ponts vers les API Swift (widgets, Live Activity)
        MainViewControllerKt.launch()
        NativeBridge.shared.hooks = SwiftHooks()
    }

    var body: some Scene {
        WindowGroup {
            ComposeView()
                .ignoresSafeArea(.all)
                .onOpenURL { url in MainViewControllerKt.openRoute(route: Self.route(of: url)) }
        }
    }

    /// superhealth://open?route=log, ou superhealth://log
    static func route(of url: Foundation.URL) -> String? {
        if let r = Foundation.URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems?.first(where: { $0.name == "route" })?.value { return r }
        guard let host = url.host else { return nil }
        return host + url.path
    }
}

struct ComposeView: UIViewControllerRepresentable {
    func makeUIViewController(context: Self.Context) -> UIViewController {
        MainViewControllerKt.MainViewController()
    }

    func updateUIViewController(_ uiViewController: UIViewController, context: Self.Context) {}
}

/// Fonctions que le code Kotlin demande à Swift (WidgetKit et ActivityKit n'existent qu'en Swift).
final class SwiftHooks: NSObject, NativeHooks {
    func reloadWidgets() {
        WidgetCenter.shared.reloadAllTimelines()
    }

    func updateWorkoutActivity(json: String?) {
        if #available(iOS 16.2, *) { WorkoutActivityController.shared.update(json: json) }
    }
}

@available(iOS 16.2, *)
final class WorkoutActivityController {
    static let shared = WorkoutActivityController()
    private var activity: ActivityKit.Activity<WorkoutAttributes>?
    private var last: SessionPayload?

    func update(json: String?) {
        guard let json, let data = json.data(using: .utf8), let state = try? JSONDecoder().decode(SessionPayload.self, from: data) else {
            end(); return
        }
        if state == last { return }
        last = state
        let content = ActivityKit.ActivityContent(state: state, staleDate: nil)
        if let a = activity ?? ActivityKit.Activity<WorkoutAttributes>.activities.first {
            activity = a
            Task { await a.update(content) }
        } else if ActivityKit.ActivityAuthorizationInfo().areActivitiesEnabled {
            activity = try? ActivityKit.Activity<WorkoutAttributes>.request(attributes: WorkoutAttributes(name: state.title), content: content)
        }
    }

    private func end() {
        last = nil
        let all = ActivityKit.Activity<WorkoutAttributes>.activities
        activity = nil
        Task { for a in all { await a.end(nil, dismissalPolicy: .immediate) } }
    }
}

/// Raccourcis de l'icône (appui long) : la scène reçoit l'action, à froid comme à chaud.
final class AppDelegate: NSObject, UIApplicationDelegate {
    func application(_ application: UIApplication, configurationForConnecting connectingSceneSession: UISceneSession,
                     options: UIScene.ConnectionOptions) -> UISceneConfiguration {
        let config = UISceneConfiguration(name: nil, sessionRole: connectingSceneSession.role)
        config.delegateClass = SceneDelegate.self
        return config
    }
}

final class SceneDelegate: NSObject, UIWindowSceneDelegate {
    func scene(_ scene: UIScene, willConnectTo session: UISceneSession, options connectionOptions: UIScene.ConnectionOptions) {
        if let item = connectionOptions.shortcutItem { MainViewControllerKt.openRoute(route: item.type) }
    }

    func windowScene(_ windowScene: UIWindowScene, performActionFor shortcutItem: UIApplicationShortcutItem,
                     completionHandler: @escaping (Bool) -> Void) {
        MainViewControllerKt.openRoute(route: shortcutItem.type)
        completionHandler(true)
    }
}
