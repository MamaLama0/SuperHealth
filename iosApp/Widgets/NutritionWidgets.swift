import WidgetKit
import SwiftUI

extension Snapshot.NutritionWeek {
    static let preview: Snapshot.NutritionWeek = {
        let labels = ["J", "V", "S", "D", "L", "M", "M"]
        let energies: [Double?] = [2180, 2350, 2050, nil, 2420, 2210, 1040]
        var days: [Snapshot.NutritionDay] = []
        for i in 0..<7 {
            let grams: [Double]? = energies[i] == nil ? nil : [135.0 + Double(i), 72.0, 240.0]
            days.append(Snapshot.NutritionDay(label: labels[i], date: "\(i + 1)/11", energy: energies[i], target: 2280, macros: grams))
        }
        let macros = [Snapshot.Macro(label: "Protéines", value: 138, target: 140, color: "#E5574F"),
                      Snapshot.Macro(label: "Lipides", value: 72, target: 76, color: "#F9B500"),
                      Snapshot.Macro(label: "Glucides", value: 240, target: 260, color: "#3F7DE8")]
        return Snapshot.NutritionWeek(days: days, average: 2042, unit: "kcal", loggedDays: 6, caption: "Moy./jour suivi",
            macros: macros, title: "Calories sur 7 jours", macrosTitle: "Macros sur 7 jours")
    }()

}

private func nutritionNumber(_ value: Double?) -> String {
    guard let value, value.isFinite else { return "—" }
    return value.formatted(.number.precision(.fractionLength(0)).grouping(.never))
}

struct MacroWidgetView: View {
    @Environment(\.widgetFamily) private var environmentFamily
    var familyOverride: WidgetFamily? = nil
    var entry: Entry
    var weekly: Bool = false
    private var family: WidgetFamily { familyOverride ?? environmentFamily }
    private var week: Snapshot.NutritionWeek? { entry.snap?.nutritionWeek }
    private var hasData: Bool { !weekly || (week?.loggedDays ?? 0) > 0 }
    private var macros: [Snapshot.Macro] {
        if weekly, let values = week?.macros, !values.isEmpty { return values }
        return entry.snap?.calories?.macros ?? []
    }
    private var title: String { weekly ? (week?.macrosTitle ?? "") : (entry.snap?.label("macros") ?? "") }

    var body: some View {
        Group {
            if !macros.isEmpty {
                VStack(alignment: .leading, spacing: family == .systemSmall ? 4 : 6) {
                    HStack {
                        Image(systemName: "chart.pie.fill").foregroundStyle(Color(hex: macros[0].color))
                        Text(title).font(.caption.weight(.semibold)).lineLimit(1).minimumScaleFactor(0.75)
                        Spacer(minLength: 0)
                        if weekly { Text("\(week?.loggedDays ?? 0)/7").font(.caption2).foregroundStyle(.secondary) }
                    }
                    if family == .systemSmall {
                        ForEach(macros, id: \.label) { m in
                            VStack(spacing: 2) {
                                HStack {
                                    Text(String(m.label.prefix(1))).foregroundStyle(Color(hex: m.color)).fontWeight(.bold)
                                    Spacer(minLength: 2)
                                    Text(hasData ? "\(nutritionNumber(m.value))/\(nutritionNumber(m.target)) g" : "—")
                                        .monospacedDigit().minimumScaleFactor(0.7)
                                }.font(.caption)
                                ProgressView(value: fraction(m)).tint(Color(hex: m.color))
                            }
                        }
                    } else {
                        HStack(spacing: 12) {
                            ForEach(macros, id: \.label) { m in
                                VStack(spacing: 3) {
                                    Text(m.label).font(.caption).foregroundStyle(.secondary).lineLimit(1).minimumScaleFactor(0.7)
                                    ZStack {
                                        RingView(fraction: fraction(m), color: Color(hex: m.color), width: 6)
                                        Text(hasData ? nutritionNumber(m.value) : "—")
                                            .font(.system(size: family == .systemLarge ? 24 : 19, weight: .semibold, design: .rounded))
                                            .minimumScaleFactor(0.6).lineLimit(1)
                                    }.frame(height: family == .systemLarge ? 70 : 48)
                                    Text(hasData ? "/ \(nutritionNumber(m.target)) g" : "—")
                                        .font(.caption2).foregroundStyle(.secondary).lineLimit(1)
                                }.frame(maxWidth: .infinity)
                            }
                        }
                    }
                    if family == .systemLarge {
                        if weekly, let days = week?.days {
                            ForEach(Array(days.enumerated()), id: \.offset) { _, day in
                                HStack {
                                    Text(day.date).foregroundStyle(.secondary).frame(width: 42, alignment: .leading)
                                    ForEach(0..<3, id: \.self) { i in
                                        Text(day.macros.flatMap { i < $0.count ? $0[i] : nil }.map { nutritionNumber($0) + " g" } ?? "—")
                                            .foregroundStyle(Color(hex: macros[min(i, macros.count - 1)].color))
                                            .frame(maxWidth: .infinity)
                                    }
                                }.font(.caption2).monospacedDigit()
                            }
                        } else if let c = entry.snap?.calories {
                            Spacer(minLength: 0)
                            HStack {
                                Chip(symbol: "flame.fill", color: .caloriesHue)
                                Text("\(nutritionNumber(c.eaten)) / \(nutritionNumber(c.target)) \(c.unit)")
                                    .font(.title3.weight(.semibold)).lineLimit(1).minimumScaleFactor(0.65)
                            }
                            ProgressView(value: c.target > 0 ? min(max(c.eaten / c.target, 0), 1) : 0).tint(.caloriesHue)
                            Spacer(minLength: 0)
                        }
                    }
                    if weekly { Text(week?.caption ?? "").font(.caption2).foregroundStyle(.secondary).lineLimit(1) }
                }.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            } else { Empty() }
        }.widgetCard().open("macros")
    }
    private func fraction(_ m: Snapshot.Macro) -> Double { hasData && m.target > 0 ? min(max(m.value / m.target, 0), 1) : 0 }
}

struct WeeklyCaloriesView: View {
    @Environment(\.widgetFamily) private var environmentFamily
    var familyOverride: WidgetFamily? = nil
    var entry: Entry
    private var family: WidgetFamily { familyOverride ?? environmentFamily }

    var body: some View {
        Group {
            if let week = entry.snap?.nutritionWeek {
                VStack(alignment: .leading, spacing: 7) {
                    HStack {
                        Image(systemName: "chart.bar.fill").foregroundStyle(Color.caloriesHue)
                        Text(week.title).font(.caption.weight(.semibold)).lineLimit(1).minimumScaleFactor(0.7)
                        Spacer(minLength: 0)
                        Text("\(week.loggedDays)/7").font(.caption2).foregroundStyle(.secondary)
                    }
                    if family == .systemMedium {
                        HStack(spacing: 16) {
                            metric(week).frame(maxWidth: 110, alignment: .leading)
                            chart(week).frame(maxWidth: .infinity).frame(height: 84)
                        }
                    } else {
                        metric(week)
                        chart(week).frame(maxHeight: .infinity)
                    }
                }.frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            } else { Empty() }
        }.widgetCard().open("nutrition")
    }
    private func metric(_ week: Snapshot.NutritionWeek) -> some View {
        VStack(alignment: .leading, spacing: 1) {
            Text(nutritionNumber(week.average)).font(.system(size: family == .systemLarge ? 38 : 28, weight: .bold, design: .rounded))
                .foregroundStyle(Color.caloriesHue).minimumScaleFactor(0.6).lineLimit(1)
            Text(week.unit + " · " + week.caption).font(.caption2).foregroundStyle(.secondary).lineLimit(2)
        }
    }
    private func chart(_ week: Snapshot.NutritionWeek) -> some View {
        GeometryReader { geometry in
            let maximum = max(week.days.compactMap(\.energy).max() ?? 0, 1)
            let chartHeight = max(geometry.size.height - (family == .systemLarge ? 30 : 16), 4)
            HStack(alignment: .bottom, spacing: family == .systemSmall ? 3 : 6) {
                ForEach(Array(week.days.enumerated()), id: \.offset) { _, day in
                    VStack(spacing: 3) {
                        ZStack(alignment: .bottom) {
                            RoundedRectangle(cornerRadius: 4).fill(Color.secondary.opacity(0.12))
                            if let energy = day.energy {
                                RoundedRectangle(cornerRadius: 4).fill(Color.caloriesHue)
                                    .frame(height: max(2, chartHeight * min(max(energy / maximum, 0), 1)))
                            }
                        }.frame(height: chartHeight)
                        Text(day.label).font(.system(size: 9)).foregroundStyle(.secondary)
                        if family == .systemLarge { Text(nutritionNumber(day.energy)).font(.system(size: 10)).foregroundStyle(.secondary).lineLimit(1).minimumScaleFactor(0.6) }
                    }.frame(maxWidth: .infinity)
                }
            }
        }
    }
}

struct MacrosWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "nutrition.macros", provider: Provider()) { MacroWidgetView(entry: $0) }
            .configurationDisplayName("Macros du jour").description("Protéines, lipides et glucides face aux objectifs du jour.")
            .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}
struct WeeklyCaloriesWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "nutrition.week.calories", provider: Provider()) { WeeklyCaloriesView(entry: $0) }
            .configurationDisplayName("Calories sur 7 jours").description("Calories quotidiennes et moyenne sur les jours renseignés.")
            .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}
struct WeeklyMacrosWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "nutrition.week.macros", provider: Provider()) { MacroWidgetView(entry: $0, weekly: true) }
            .configurationDisplayName("Macros sur 7 jours").description("Moyennes des macros et objectifs sur les jours renseignés.")
            .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
    }
}
struct NutritionWidgets: WidgetBundle {
    var body: some Widget {
        MacrosWidget()
        WeeklyCaloriesWidget()
        WeeklyMacrosWidget()
    }
}
