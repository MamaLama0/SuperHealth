import WidgetKit
import SwiftUI
import ActivityKit

// Widgets SuperHealth : ils lisent l'instantané écrit par l'app (Widgets.kt) dans le groupe d'apps partagé. Textes et
// nombres arrivent déjà traduits et formatés : les widgets montrent exactement ce que montre l'app.

let appGroup = "group.app.superhealth"

// MARK: - Données

struct Snapshot: Codable {
    struct Ring: Codable { var label: String; var value: Double; var goal: Double; var color: String; var text: String }
    struct Macro: Codable { var label: String; var value: Double; var target: Double; var color: String }
    struct Calories: Codable { var left: String; var leftLabel: String; var eaten: Double; var target: Double; var unit: String; var macros: [Macro] }
    struct Weight: Codable { var value: String; var unit: String; var delta: String?; var points: [Double] }
    struct Next: Codable { var title: String; var subtitle: String; var action: String; var route: String; var week: [Bool]; var dayLetters: [String]; var progress: Double? }
    struct Vital: Codable { var label: String; var value: String; var status: String }
    struct Vitals: Codable { var title: String; var summary: String; var ok: Bool; var items: [Vital] }

    var updatedMs: Int64
    var date: String
    var accent: String
    var labels: [String: String]
    var calories: Calories?
    var rings: [Ring]
    var next: Next?
    var weight: Weight?
    var vitals: Vitals?
    var steps: String?
    var sleep: String?

    func label(_ key: String) -> String { labels[key] ?? key }

    static func load() -> Snapshot? {
        guard let dir = FileManager.default.containerURL(forSecurityApplicationGroupIdentifier: appGroup),
              let data = try? Data(contentsOf: dir.appendingPathComponent("widget.json")) else { return nil }
        return try? JSONDecoder().decode(Snapshot.self, from: data)
    }

    static let preview = Snapshot(
        updatedMs: 0, date: "", accent: "#4285F4",
        labels: ["calories": "Calories", "next": "Prochaine séance", "weight": "Tendance du poids", "steps": "Pas", "sleep": "Sommeil", "rings": "Anneaux du jour"],
        calories: Calories(left: "1 240", leftLabel: "Restantes", eaten: 1040, target: 2280, unit: "kcal", macros: [
            Macro(label: "Protéines", value: 62, target: 140, color: "#E5574F"), Macro(label: "Lipides", value: 38, target: 76, color: "#F9B500"),
            Macro(label: "Glucides", value: 120, target: 260, color: "#3F7DE8")]),
        rings: [Ring(label: "Bouger", value: 412, goal: 600, color: "#FA114F", text: "412/600 kcal"),
                Ring(label: "M'entraîner", value: 24, goal: 30, color: "#92E82A", text: "24/30 min"),
                Ring(label: "Calories mangées", value: 1040, goal: 2280, color: "#14D8F0", text: "1040/2280 kcal")],
        next: Next(title: "Haut du corps A", subtitle: "6 exercices · ≈ 55 min", action: "Commencer", route: "training:startnext",
                   week: [true, false, true, false, false, false, false], dayLetters: ["L", "M", "M", "J", "V", "S", "D"], progress: nil),
        weight: Weight(value: "78,4", unit: "kg", delta: "−0,35 / sem.", points: [79.6, 79.4, 79.3, 79.1, 79.0, 78.8, 78.7, 78.6, 78.4]),
        vitals: Vitals(title: "Signes vitaux", summary: "Dans votre plage habituelle", ok: true, items: [
            Vital(label: "FC au repos", value: "54 bpm", status: "TYPICAL"), Vital(label: "SpO₂", value: "97 %", status: "TYPICAL")]),
        steps: "8 432", sleep: "7 h 32")
}

struct Entry: TimelineEntry {
    let date: Date
    let snap: Snapshot?
}

struct Provider: TimelineProvider {
    func placeholder(in context: Context) -> Entry { Entry(date: Date(), snap: .preview) }
    func getSnapshot(in context: Context, completion: @escaping (Entry) -> Void) {
        completion(Entry(date: Date(), snap: context.isPreview ? .preview : (Snapshot.load() ?? .preview)))
    }
    func getTimeline(in context: Context, completion: @escaping (Timeline<Entry>) -> Void) {
        // l'app recharge les widgets à chaque changement ; sinon relecture toutes les 30 minutes
        completion(Timeline(entries: [Entry(date: Date(), snap: Snapshot.load())], policy: .after(Date().addingTimeInterval(1800))))
    }
}

// MARK: - Style (cartes du tableau de bord : fond neutre, pastille ronde, valeur dans la couleur de la mesure)

extension Color {
    init(hex: String) {
        let h = hex.trimmingCharacters(in: CharacterSet(charactersIn: "#"))
        let v = UInt64(h, radix: 16) ?? 0x4285F4
        self.init(red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255, blue: Double(v & 0xFF) / 255)
    }
    static let caloriesHue = Color(hex: "#F2803A")
    static let workoutHue = Color(hex: "#276EF1")
}

extension View {
    /// Fond du widget : celui du système (iOS 17) ou une couleur de carte.
    @ViewBuilder func widgetCard() -> some View {
        if #available(iOSApplicationExtension 17.0, *) {
            containerBackground(for: .widget) { Color(UIColor.secondarySystemBackground) }
        } else {
            padding().background(Color(UIColor.secondarySystemBackground))
        }
    }

    func open(_ route: String) -> some View { widgetURL(URL(string: "superhealth://open?route=" + route)) }
}

struct RingView: View {
    var fraction: Double
    var color: Color
    var width: CGFloat
    var body: some View {
        ZStack {
            Circle().stroke(color.opacity(0.22), lineWidth: width)
            Circle().trim(from: 0, to: min(max(fraction, 0), 1))
                .stroke(AngularGradient(colors: [color.opacity(0.75), color], center: .center), style: StrokeStyle(lineWidth: width, lineCap: .round))
                .rotationEffect(.degrees(-90))
        }
    }
}

/// Anneaux emboîtés, de l'extérieur vers l'intérieur.
struct NestedRings: View {
    var rings: [Snapshot.Ring]
    var size: CGFloat
    var body: some View {
        let n = CGFloat(max(rings.count, 1))
        let w = size * 0.28 / n
        ZStack {
            ForEach(Array(rings.enumerated()), id: \.offset) { i, r in
                RingView(fraction: r.goal > 0 ? r.value / r.goal : 0, color: Color(hex: r.color), width: w)
                    .padding(CGFloat(i) * (w * 1.2) + w / 2)
            }
        }
        .frame(width: size, height: size)
    }
}

struct Chip: View {
    var symbol: String
    var color: Color
    var body: some View {
        Image(systemName: symbol).font(.system(size: 12, weight: .semibold)).foregroundStyle(color)
            .frame(width: 24, height: 24).background(Circle().fill(color.opacity(0.16)))
    }
}

struct Empty: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Image(systemName: "heart.fill").foregroundStyle(.pink)
            Text("SuperHealth").font(.headline)
            Text("Ouvrez l'app pour commencer.").font(.caption).foregroundStyle(.secondary)
        }
    }
}

// MARK: - Calories restantes

struct CaloriesView: View {
    @Environment(\.widgetFamily) var family
    var entry: Entry
    var body: some View {
        Group {
            if let c = entry.snap?.calories {
                let f = c.target > 0 ? c.eaten / c.target : 0
                switch family {
                case .accessoryCircular:
                    Gauge(value: min(f, 1)) { Image(systemName: "flame.fill") } currentValueLabel: { Text(c.left).font(.system(size: 12, weight: .bold)) }
                        .gaugeStyle(.accessoryCircularCapacity)
                case .accessoryRectangular:
                    VStack(alignment: .leading) {
                        Text("\(c.left) \(c.unit)").font(.headline)
                        Text(c.leftLabel).font(.caption)
                        ProgressView(value: min(f, 1))
                    }
                case .accessoryInline:
                    Text("\(c.left) \(c.unit) · \(c.leftLabel)")
                case .systemMedium:
                    HStack(spacing: 16) {
                        ZStack {
                            RingView(fraction: f, color: .caloriesHue, width: 10)
                            VStack(spacing: 0) {
                                Text(c.left).font(.system(size: 22, weight: .bold)).minimumScaleFactor(0.6).foregroundStyle(Color.caloriesHue)
                                Text(c.leftLabel).font(.caption2).foregroundStyle(.secondary)
                            }
                        }.frame(width: 104, height: 104)
                        VStack(alignment: .leading, spacing: 8) {
                            ForEach(c.macros, id: \.label) { m in
                                VStack(alignment: .leading, spacing: 3) {
                                    HStack {
                                        Text(m.label).font(.caption).foregroundStyle(.secondary)
                                        Spacer()
                                        Text("\(Int(m.value)) / \(Int(m.target)) g").font(.caption.weight(.semibold))
                                    }
                                    ProgressView(value: m.target > 0 ? min(m.value / m.target, 1) : 0).tint(Color(hex: m.color))
                                }
                            }
                        }
                    }
                default:
                    VStack(alignment: .leading, spacing: 6) {
                        Chip(symbol: "flame.fill", color: .caloriesHue)
                        Spacer(minLength: 0)
                        Text(c.left).font(.system(size: 30, weight: .bold)).minimumScaleFactor(0.6).foregroundStyle(Color.caloriesHue)
                        Text("\(c.unit) · \(c.leftLabel)").font(.caption).foregroundStyle(.secondary)
                        ProgressView(value: min(f, 1)).tint(.caloriesHue)
                    }.frame(maxWidth: .infinity, alignment: .leading)
                }
            } else { Empty() }
        }
        .widgetCard()
        .open("log")
    }
}

struct CaloriesWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "calories", provider: Provider()) { CaloriesView(entry: $0) }
            .configurationDisplayName("Calories restantes")
            .description("Calories restantes du jour et macros.")
            .supportedFamilies([.systemSmall, .systemMedium, .accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}

// MARK: - Anneaux du jour

struct RingsWidgetView: View {
    @Environment(\.widgetFamily) var family
    var entry: Entry
    var body: some View {
        Group {
            if let rings = entry.snap?.rings, !rings.isEmpty {
                switch family {
                case .accessoryCircular:
                    NestedRings(rings: Array(rings.prefix(3)), size: 52)
                case .systemMedium:
                    HStack(spacing: 18) {
                        NestedRings(rings: rings, size: 120)
                        VStack(alignment: .leading, spacing: 8) {
                            ForEach(rings, id: \.label) { r in
                                VStack(alignment: .leading, spacing: 0) {
                                    Text(r.label).font(.caption).foregroundStyle(.secondary)
                                    Text(r.text).font(.system(size: 15, weight: .bold)).foregroundStyle(Color(hex: r.color))
                                }
                            }
                        }
                        Spacer(minLength: 0)
                    }
                default:
                    NestedRings(rings: rings, size: 132).frame(maxWidth: .infinity, maxHeight: .infinity)
                }
            } else { Empty() }
        }
        .widgetCard()
        .open("home")
    }
}

struct RingsWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "rings", provider: Provider()) { RingsWidgetView(entry: $0) }
            .configurationDisplayName("Anneaux du jour")
            .description("Bouger, M'entraîner, Me lever et calories mangées.")
            .supportedFamilies([.systemSmall, .systemMedium, .accessoryCircular])
    }
}

// MARK: - Prochaine séance

struct NextView: View {
    @Environment(\.widgetFamily) var family
    var entry: Entry
    var body: some View {
        Group {
            if let n = entry.snap?.next {
                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Chip(symbol: "dumbbell.fill", color: .workoutHue)
                        Text(entry.snap?.label(n.route == "training:session" ? "session" : "next") ?? "").font(.caption).foregroundStyle(.secondary)
                    }
                    Spacer(minLength: 0)
                    Text(n.title).font(.system(size: family == .systemSmall ? 16 : 18, weight: .bold)).foregroundStyle(Color.workoutHue).lineLimit(2)
                    Text(n.subtitle).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                    if let p = n.progress { ProgressView(value: p).tint(.workoutHue) }
                    if family != .systemSmall {
                        HStack(spacing: 5) {
                            ForEach(0..<min(n.week.count, n.dayLetters.count), id: \.self) { i in
                                Text(n.dayLetters[i]).font(.system(size: 9, weight: .bold))
                                    .foregroundStyle(n.week[i] ? Color.white : Color.secondary)
                                    .frame(width: 20, height: 20).background(Circle().fill(n.week[i] ? Color.workoutHue : Color.secondary.opacity(0.15)))
                            }
                            Spacer()
                            Text(n.action).font(.caption.weight(.bold)).foregroundStyle(.white)
                                .padding(.horizontal, 12).padding(.vertical, 6).background(Capsule().fill(Color.workoutHue))
                        }
                    }
                }.frame(maxWidth: .infinity, alignment: .leading)
                .open(n.route)
            } else { Empty() }
        }
        .widgetCard()
    }
}

struct NextWorkoutWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "next", provider: Provider()) { NextView(entry: $0) }
            .configurationDisplayName("Prochaine séance")
            .description("Jour du programme ou séance en cours, et semaine d'entraînement.")
            .supportedFamilies([.systemSmall, .systemMedium])
    }
}

// MARK: - Tendance du poids

struct Sparkline: View {
    var points: [Double]
    var color: Color
    var body: some View {
        GeometryReader { g in
            let lo = points.min() ?? 0, hi = points.max() ?? 1
            let span = max(hi - lo, 0.0001)
            Path { p in
                for (i, v) in points.enumerated() {
                    let x = g.size.width * CGFloat(i) / CGFloat(max(points.count - 1, 1))
                    let y = g.size.height * (1 - CGFloat((v - lo) / span))
                    if i == 0 { p.move(to: CGPoint(x: x, y: y)) } else { p.addLine(to: CGPoint(x: x, y: y)) }
                }
            }.stroke(color, style: StrokeStyle(lineWidth: 2.5, lineCap: .round, lineJoin: .round))
        }
    }
}

struct WeightView: View {
    @Environment(\.widgetFamily) var family
    var entry: Entry
    var body: some View {
        let hue = Color(hex: "#3F7DE8")
        Group {
            if let w = entry.snap?.weight {
                VStack(alignment: .leading, spacing: 4) {
                    HStack { Chip(symbol: "scalemass.fill", color: hue); Text(entry.snap?.label("weight") ?? "").font(.caption).foregroundStyle(.secondary).lineLimit(1) }
                    Spacer(minLength: 0)
                    HStack(alignment: .firstTextBaseline, spacing: 3) {
                        Text(w.value).font(.system(size: 28, weight: .bold)).foregroundStyle(hue)
                        Text(w.unit).font(.caption).foregroundStyle(.secondary)
                    }
                    if let d = w.delta { Text(d).font(.caption).foregroundStyle(.secondary) }
                    if w.points.count >= 2 { Sparkline(points: w.points, color: hue).frame(height: family == .systemSmall ? 26 : 40) }
                }.frame(maxWidth: .infinity, alignment: .leading)
            } else { Empty() }
        }
        .widgetCard()
        .open("weight")
    }
}

struct WeightWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "weight", provider: Provider()) { WeightView(entry: $0) }
            .configurationDisplayName("Tendance du poids")
            .description("Poids de tendance et évolution sur la semaine.")
            .supportedFamilies([.systemSmall, .systemMedium])
    }
}

// MARK: - Signes vitaux de la nuit

struct VitalsView: View {
    @Environment(\.widgetFamily) var family
    var entry: Entry
    var body: some View {
        Group {
            if let v = entry.snap?.vitals {
                let hue = v.ok ? Color(hex: "#1F9E85") : Color(hex: "#D9931F")
                VStack(alignment: .leading, spacing: 6) {
                    HStack { Chip(symbol: "moon.stars.fill", color: Color(hex: "#7B61D9")); Text(v.title).font(.caption).foregroundStyle(.secondary) }
                    HStack(spacing: 4) {
                        Image(systemName: v.ok ? "checkmark.circle.fill" : "exclamationmark.circle.fill").foregroundStyle(hue)
                        Text(v.summary).font(.system(size: 13, weight: .semibold)).foregroundStyle(hue).lineLimit(2)
                    }
                    if family != .systemSmall {
                        ForEach(Array(v.items.prefix(3)), id: \.label) { i in
                            HStack {
                                Text(i.label).font(.caption).foregroundStyle(.secondary).lineLimit(1)
                                Spacer()
                                Text(i.value).font(.caption.weight(.semibold))
                                    .foregroundStyle(i.status == "TYPICAL" ? Color.primary : Color(hex: "#D9931F"))
                            }
                        }
                    }
                    Spacer(minLength: 0)
                }.frame(maxWidth: .infinity, alignment: .leading)
            } else { Empty() }
        }
        .widgetCard()
        .open("h/apple")
    }
}

struct VitalsWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "vitals", provider: Provider()) { VitalsView(entry: $0) }
            .configurationDisplayName("Signes vitaux de la nuit")
            .description("Dernière nuit comparée à votre plage habituelle (Apple Watch).")
            .supportedFamilies([.systemSmall, .systemMedium])
    }
}

// MARK: - Ma journée (vue d'ensemble)

struct DayView: View {
    var entry: Entry
    var body: some View {
        Group {
            if let s = entry.snap {
                VStack(alignment: .leading, spacing: 10) {
                    HStack(spacing: 14) {
                        if !s.rings.isEmpty { NestedRings(rings: s.rings, size: 84) }
                        VStack(alignment: .leading, spacing: 6) {
                            if let c = s.calories {
                                HStack(alignment: .firstTextBaseline, spacing: 3) {
                                    Text(c.left).font(.system(size: 22, weight: .bold)).foregroundStyle(Color.caloriesHue)
                                    Text("\(c.unit) · \(c.leftLabel)").font(.caption).foregroundStyle(.secondary)
                                }
                            }
                            HStack(spacing: 14) {
                                if let st = s.steps { Label(st, systemImage: "figure.walk").font(.caption.weight(.semibold)) }
                                if let sl = s.sleep { Label(sl, systemImage: "bed.double.fill").font(.caption.weight(.semibold)) }
                            }
                            if let w = s.weight { Label("\(w.value) \(w.unit)", systemImage: "scalemass.fill").font(.caption.weight(.semibold)) }
                        }
                        Spacer(minLength: 0)
                    }
                    if let n = s.next {
                        HStack {
                            Image(systemName: "dumbbell.fill").foregroundStyle(Color.workoutHue)
                            VStack(alignment: .leading, spacing: 0) {
                                Text(n.title).font(.subheadline.weight(.semibold)).lineLimit(1)
                                Text(n.subtitle).font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                            }
                            Spacer()
                            Text(n.action).font(.caption.weight(.bold)).foregroundStyle(.white)
                                .padding(.horizontal, 10).padding(.vertical, 5).background(Capsule().fill(Color.workoutHue))
                        }
                    }
                }
            } else { Empty() }
        }
        .widgetCard()
        .open("home")
    }
}

struct DayWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "day", provider: Provider()) { DayView(entry: $0) }
            .configurationDisplayName("Ma journée")
            .description("Anneaux, calories, pas, sommeil, poids et prochaine séance.")
            .supportedFamilies([.systemMedium, .systemLarge])
    }
}

// MARK: - Séance en cours (Live Activity : écran verrouillé et Dynamic Island)

struct WorkoutLiveActivity: Widget {
    var body: some WidgetConfiguration {
        ActivityConfiguration(for: WorkoutAttributes.self) { ctx in
            let s = ctx.state
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Image(systemName: "dumbbell.fill").foregroundStyle(Color.workoutHue)
                    Text(s.title).font(.headline).lineLimit(1)
                    Spacer()
                    ClockText(state: s).font(.system(size: 24, weight: .bold).monospacedDigit())
                        .foregroundStyle(s.restEnd != nil ? Color.orange : Color.primary)
                }
                Text(s.line).font(.subheadline).lineLimit(1)
                HStack {
                    if !s.load.isEmpty { Text(s.load).font(.subheadline.weight(.semibold)) }
                    Spacer()
                    Text("\(s.done)/\(s.total)").font(.caption).foregroundStyle(.secondary)
                }
                ProgressView(value: Double(s.done), total: Double(max(s.total, 1))).tint(.workoutHue)
            }
            .padding()
            .widgetURL(URL(string: "superhealth://open?route=training:session"))
        } dynamicIsland: { ctx in
            let s = ctx.state
            return DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    Label("\(s.done)/\(s.total)", systemImage: "dumbbell.fill").foregroundStyle(Color.workoutHue)
                }
                DynamicIslandExpandedRegion(.trailing) {
                    ClockText(state: s).font(.title3.monospacedDigit().weight(.bold)).foregroundStyle(s.restEnd != nil ? Color.orange : Color.primary)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading) {
                        Text(s.line).font(.subheadline).lineLimit(1)
                        if !s.load.isEmpty { Text(s.load).font(.caption).foregroundStyle(.secondary) }
                    }
                }
            } compactLeading: {
                Image(systemName: s.restEnd != nil ? "timer" : "dumbbell.fill").foregroundStyle(s.restEnd != nil ? Color.orange : Color.workoutHue)
            } compactTrailing: {
                ClockText(state: s).monospacedDigit().frame(maxWidth: 52)
            } minimal: {
                Image(systemName: "dumbbell.fill").foregroundStyle(Color.workoutHue)
            }
            .widgetURL(URL(string: "superhealth://open?route=training:session"))
        }
    }
}

/// Compte à rebours du repos, sinon chronomètre de la séance (le système l'anime sans réveiller l'app).
struct ClockText: View {
    var state: SessionPayload
    var body: some View {
        if state.paused {
            Text(state.pausedLabel)
        } else if let end = state.restEnd, end > Date() {
            Text(timerInterval: Date()...end, countsDown: true)
        } else {
            Text(timerInterval: state.start...Date.distantFuture, countsDown: false)
        }
    }
}

// MARK: - Ensemble

@main
struct SuperHealthWidgets: WidgetBundle {
    @WidgetBundleBuilder
    var body: some Widget {
        CaloriesWidget()
        RingsWidget()
        NextWorkoutWidget()
        WeightWidget()
        VitalsWidget()
        DayWidget()
        WorkoutLiveActivity()
    }
}
