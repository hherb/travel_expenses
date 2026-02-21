import SwiftUI

/// Canvas-based pie chart that renders category breakdown as coloured slices.
struct PieChartView: View {

    let slices: [PieSlice]

    struct PieSlice: Identifiable {
        let id: String
        let label: String
        let value: Float    // 0-100 percentage
        let color: Color
    }

    var body: some View {
        Canvas { ctx, size in
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            let radius = min(size.width, size.height) / 2

            var startAngle = Angle.degrees(-90)
            for slice in slices {
                guard slice.value > 0 else { continue }
                let sweep = Angle.degrees(Double(slice.value) * 3.6) // 360 / 100
                let endAngle = startAngle + sweep

                var path = Path()
                path.move(to: center)
                path.addArc(
                    center: center,
                    radius: radius,
                    startAngle: startAngle,
                    endAngle: endAngle,
                    clockwise: false
                )
                path.closeSubpath()

                ctx.fill(path, with: .color(slice.color))

                // Hairline separator
                ctx.stroke(path, with: .color(.white), lineWidth: 1.5)

                startAngle = endAngle
            }
        }
    }
}

// MARK: - Palette
extension PieChartView {
    static let palette: [Color] = [
        .blue, .green, .orange, .purple, .red,
        .teal, .indigo, .yellow, .pink, .cyan,
    ]
}
