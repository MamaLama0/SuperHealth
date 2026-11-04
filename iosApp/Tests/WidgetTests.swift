import XCTest
import SwiftUI
import WidgetKit

final class WidgetTests: XCTestCase {
    func testPreviousSnapshotRemainsDecodableWithoutSocialField() throws {
        let encoded = try JSONEncoder().encode(Snapshot.preview)
        var object = try XCTUnwrap(JSONSerialization.jsonObject(with: encoded) as? [String: Any])
        object.removeValue(forKey: "social")
        object.removeValue(forKey: "nutritionWeek")
        let previous = try JSONDecoder().decode(Snapshot.self, from: JSONSerialization.data(withJSONObject: object))
        XCTAssertNil(previous.social)
        XCTAssertNil(previous.nutritionWeek)
        XCTAssertEqual(previous.calories?.left, "1 240")
    }

    func testSocialCatalogueHasSixUniqueSafeDestinations() {
        let values = Snapshot.socialPreview
        XCTAssertEqual(values.count, 6)
        XCTAssertEqual(Set(values.map(\.id)).count, 6)
        let routes: Set<String> = ["league", "progress", "quests", "friends", "social"]
        XCTAssertTrue(values.allSatisfy { routes.contains($0.route) })
        XCTAssertTrue(values.allSatisfy { !$0.title.isEmpty && !$0.value.isEmpty })
    }

    @MainActor
    func testSocialWidgetsInEveryHomeScreenSizeAndAppearance() throws {
        let sizes: [(WidgetFamily, CGSize)] = [
            (.systemSmall, CGSize(width: 158, height: 158)),
            (.systemMedium, CGSize(width: 338, height: 158)),
            (.systemLarge, CGSize(width: 338, height: 354))
        ]
        for c in Snapshot.socialPreview {
            for (family, size) in sizes {
                for dark in [false, true] {
                    let view = SocialWidgetView(familyOverride: family, entry: Entry(date: Date(), snap: .preview), id: c.id)
                        .preferredColorScheme(dark ? .dark : .light)
                        .padding(16)
                        .frame(width: size.width, height: size.height)
                        .background(dark ? Color.black : Color.white)
                    let controller = UIHostingController(rootView: view)
                    let window = UIWindow(frame: CGRect(origin: .zero, size: size))
                    window.rootViewController = controller
                    window.makeKeyAndVisible()
                    controller.view.frame = window.bounds
                    controller.view.setNeedsLayout()
                    controller.view.layoutIfNeeded()
                    RunLoop.current.run(until: Date().addingTimeInterval(0.08))
                    let image = UIGraphicsImageRenderer(size: size).image { context in
                        controller.view.layer.render(in: context.cgContext)
                    }
                    XCTAssertGreaterThan(try XCTUnwrap(image.pngData()).count, 1000)
                    let attachment = XCTAttachment(image: image)
                    attachment.name = "\(c.id)-\(family)-\(dark ? "dark" : "light")"
                    attachment.lifetime = .keepAlways
                    add(attachment)
                    window.isHidden = true
                }
            }
        }
    }
    @MainActor
    func testNutritionVariantsInEveryHomeScreenSizeAndAppearance() throws {
        let sizes: [(WidgetFamily, CGSize)] = [
            (.systemSmall, CGSize(width: 158, height: 158)),
            (.systemMedium, CGSize(width: 338, height: 158)),
            (.systemLarge, CGSize(width: 338, height: 354))
        ]
        for kind in ["macros", "week-calories", "week-macros"] {
            for (family, size) in sizes {
                for dark in [false, true] {
                    let content: AnyView
                    let entry = Entry(date: Date(), snap: .preview)
                    if kind == "week-calories" { content = AnyView(WeeklyCaloriesView(familyOverride: family, entry: entry)) }
                    else { content = AnyView(MacroWidgetView(familyOverride: family, entry: entry, weekly: kind == "week-macros")) }
                    let view = content.preferredColorScheme(dark ? .dark : .light).padding(16)
                        .frame(width: size.width, height: size.height).background(dark ? Color.black : Color.white)
                    let controller = UIHostingController(rootView: view)
                    let window = UIWindow(frame: CGRect(origin: .zero, size: size))
                    window.rootViewController = controller
                    window.makeKeyAndVisible()
                    controller.view.frame = window.bounds
                    controller.view.setNeedsLayout()
                    controller.view.layoutIfNeeded()
                    RunLoop.current.run(until: Date().addingTimeInterval(0.08))
                    let image = UIGraphicsImageRenderer(size: size).image { context in
                        controller.view.layer.render(in: context.cgContext)
                    }
                    XCTAssertGreaterThan(try XCTUnwrap(image.pngData()).count, 1000)
                    let attachment = XCTAttachment(image: image)
                    attachment.name = "\(kind)-\(family)-\(dark ? "dark" : "light")"
                    attachment.lifetime = .keepAlways
                    add(attachment)
                    window.isHidden = true
                }
            }
        }
    }

}
