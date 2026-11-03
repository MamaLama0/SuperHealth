import SwiftUI
import WidgetKit

struct StepsWidgetView: View {
    @Environment(\.widgetFamily) var family
    var entry: Entry
    var body: some View {
        Group {
            if let snap = entry.snap, let steps = snap.steps {
                switch family {
                case .accessoryInline:
                    Label(steps, systemImage: "figure.walk")
                case .accessoryCircular:
                    VStack(spacing: 2) {
                        Image(systemName: "figure.walk")
                        Text(steps).font(.caption2.bold()).minimumScaleFactor(0.6).lineLimit(1)
                    }
                case .accessoryRectangular:
                    Label(steps + " " + snap.label("steps"), systemImage: "figure.walk").font(.headline)
                default:
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Chip(symbol: "figure.walk", color: Color(hex: "#36A98F"))
                            Text(snap.label("steps")).font(.caption).foregroundStyle(.secondary)
                        }
                        Spacer(minLength: 0)
                        Text(steps).font(.system(size: family == .systemSmall ? 32 : 40, weight: .bold, design: .rounded))
                            .minimumScaleFactor(0.6).lineLimit(1)
                        if snap.updatedMs > 0 {
                            Text(Date(timeIntervalSince1970: Double(snap.updatedMs) / 1000), style: .time)
                                .font(.caption2).foregroundStyle(.secondary)
                        }
                    }.frame(maxWidth: .infinity, alignment: .leading)
                }
            } else { Empty() }
        }
        .widgetCard()
        .open("h/metric/STEPS")
    }
}

struct StepsWidget: Widget {
    var body: some WidgetConfiguration {
        StaticConfiguration(kind: "steps", provider: Provider()) { StepsWidgetView(entry: $0) }
            .configurationDisplayName("Pas")
            .description("Vos pas du jour, depuis Apple Santé.")
            .supportedFamilies([.systemSmall, .systemMedium, .accessoryCircular, .accessoryRectangular, .accessoryInline])
    }
}
