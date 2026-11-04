import WidgetKit
import SwiftUI

/// Six cartes, une destination chacune. Les tailles moyennes donnent du contexte, les grandes développent les suivis.
struct SocialWidgetView: View {
    @Environment(\.widgetFamily) private var environmentFamily
    var familyOverride: WidgetFamily? = nil
    private var family: WidgetFamily { familyOverride ?? environmentFamily }
    var entry: Entry
    var id: String
    var card: Snapshot.SocialCard? { entry.snap?.social?.first { $0.id == id } }

    var body: some View {
        Group {
            if let c = card {
                content(c)
            } else {
                Empty()
            }
        }
        .widgetCard()
        .open(card?.route ?? id)
    }

    @ViewBuilder
    private func content(_ c: Snapshot.SocialCard) -> some View {
        let hue = Color(hex: c.color)
        VStack(alignment: .leading, spacing: family == .systemSmall ? 7 : 8) {
            HStack(spacing: 7) {
                Chip(symbol: c.symbol, color: hue)
                Text(c.title).font(.caption.weight(.semibold)).foregroundStyle(.secondary).lineLimit(1)
                Spacer(minLength: 0)
                if family != .systemSmall {
                    Image(systemName: "chevron.right").font(.caption2).foregroundStyle(.tertiary)
                }
            }
            if family == .systemMedium {
                HStack(alignment: .center, spacing: 18) {
                    metric(c, hue: hue)
                    if !c.chart.isEmpty && c.rows.isEmpty { bars(c.chart, hue: hue).frame(maxWidth: 130).frame(height: 65) }
                    else if !c.rows.isEmpty { rows(Array(c.rows.prefix(2)), hue: hue).frame(maxWidth: .infinity) }
                }
            } else {
                if family == .systemSmall { Spacer(minLength: 0) }
                metric(c, hue: hue)
            }
            if let fraction = c.fraction {
                ProgressView(value: min(max(fraction, 0), 1)).tint(hue).accessibilityLabel(c.detail)
            }
            if family == .systemLarge {
                if !c.chart.isEmpty && c.rows.isEmpty { bars(c.chart, hue: hue).frame(height: 65) }
                if !c.rows.isEmpty { rows(Array(c.rows.prefix(5)), hue: hue) }
                Spacer(minLength: 0)
            }
            if family != .systemSmall {
                Text(c.detail).font(.caption).foregroundStyle(.secondary).lineLimit(2)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private func metric(_ c: Snapshot.SocialCard, hue: Color) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(c.value).font(.system(size: family == .systemSmall ? 29 : 34, weight: .bold, design: .rounded))
                .foregroundStyle(Color.primary).minimumScaleFactor(0.65).lineLimit(1)
            Text(c.caption).font(.caption).foregroundStyle(.secondary).lineLimit(family == .systemSmall ? 2 : 3)
        }.frame(maxWidth: .infinity, alignment: .leading)
    }

    private func rows(_ values: [Snapshot.SocialRow], hue: Color) -> some View {
        VStack(spacing: 9) {
            ForEach(Array(values.enumerated()), id: \.offset) { _, row in
                HStack(spacing: 8) {
                    Text(row.label).foregroundStyle(.secondary).lineLimit(1)
                    Spacer(minLength: 0)
                    Text(row.value).fontWeight(.semibold).foregroundStyle(Color.primary).lineLimit(1)
                }.font(.caption)
            }
        }
    }

    private func bars(_ values: [Int], hue: Color) -> some View {
        GeometryReader { geometry in
            HStack(alignment: .bottom, spacing: 5) {
                ForEach(Array(values.prefix(7).enumerated()), id: \.offset) { _, value in
                    RoundedRectangle(cornerRadius: 4)
                        .fill(value > 0 ? hue : hue.opacity(0.15))
                        .frame(maxWidth: .infinity)
                        .frame(height: max(4, geometry.size.height * CGFloat(max(value, 0)) / CGFloat(max(values.max() ?? 1, 1))))
                }
            }.frame(maxHeight: .infinity, alignment: .bottom)
        }.accessibilityLabel(values.map(String.init).joined(separator: ", ") + " XP")
    }
}

@MainActor
private func socialConfiguration(_ id: String, _ title: String, _ about: String) -> some WidgetConfiguration {
    StaticConfiguration(kind: "social." + id, provider: Provider()) { SocialWidgetView(entry: $0, id: id) }
        .configurationDisplayName(Text(LocalizedStringKey(title)))
        .description(Text(LocalizedStringKey(about)))
        .supportedFamilies([.systemSmall, .systemMedium, .systemLarge])
}

struct LeagueWidget: Widget {
    var body: some WidgetConfiguration { socialConfiguration("league", "Classement", "Votre indice et le classement entre amis.") }
}
struct ProgressWidget: Widget {
    var body: some WidgetConfiguration { socialConfiguration("progress", "Progression", "Niveau, XP et activité de la semaine.") }
}
struct StreakWidget: Widget {
    var body: some WidgetConfiguration { socialConfiguration("streak", "Séries", "La régularité de vos suivis.") }
}
struct QuestsWidget: Widget {
    var body: some WidgetConfiguration { socialConfiguration("quests", "Quêtes de la semaine", "Le prochain défi ou la récompense à récupérer.") }
}
struct FriendsWidget: Widget {
    var body: some WidgetConfiguration { socialConfiguration("friends", "Amis", "Vos amis et leur progression partagée.") }
}
struct SocialOverviewWidget: Widget {
    var body: some WidgetConfiguration { socialConfiguration("social", "Vue sociale", "Profil, niveau, amis et badges.") }
}
