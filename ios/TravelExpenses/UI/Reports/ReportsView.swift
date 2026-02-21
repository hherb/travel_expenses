import SwiftUI
import Shared

struct ReportsView: View {

    @StateObject private var viewModel: ReportsViewModel
    @State private var showShareSheet = false
    @State private var csvToShare: IdentifiableString?

    init(container: ServiceContainer) {
        _viewModel = StateObject(wrappedValue: ReportsViewModel(container: container))
    }

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.trips.isEmpty {
                    emptyState
                } else {
                    reportContent
                }
            }
            .navigationTitle("Reports")
            .toolbar {
                if viewModel.report != nil {
                    ToolbarItem(placement: .primaryAction) {
                        Button {
                            viewModel.exportCsv()
                        } label: {
                            Image(systemName: "square.and.arrow.up")
                        }
                    }
                }
            }
            .onChange(of: viewModel.csvOutput) { csv in
                if let csv { csvToShare = IdentifiableString(value: csv) }
            }
            .sheet(item: $csvToShare) { wrapper in
                ShareSheet(items: [wrapper.value])
            }
        }
    }

    // MARK: - Sub-views

    private var emptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "chart.pie")
                .font(.system(size: 64))
                .foregroundStyle(.secondary)
            Text("No Trips Yet")
                .font(.title2.bold())
            Text("Create a trip to see reports here.")
                .foregroundStyle(.secondary)
        }
    }

    private var reportContent: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Trip picker
                tripPicker

                if viewModel.isLoading {
                    ProgressView()
                        .padding()
                } else if let report = viewModel.report {
                    summaryCards(report)
                    if !report.categoryBreakdown.isEmpty {
                        chartSection(report)
                        breakdownList(report)
                    }
                }
            }
            .padding()
        }
    }

    private var tripPicker: some View {
        Picker("Trip", selection: $viewModel.selectedTripId) {
            ForEach(viewModel.trips, id: \.id) { trip in
                Text(trip.name).tag(Optional(trip.id))
            }
        }
        .pickerStyle(.menu)
        .onChange(of: viewModel.selectedTripId) { id in
            if let id { viewModel.selectTrip(id) }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func summaryCards(_ report: TripReport) -> some View {
        HStack(spacing: 12) {
            SummaryCard(title: "Total", value: report.totalAmount, icon: "dollarsign.circle")
            SummaryCard(title: "Expenses", value: "\(report.expenseCount)", icon: "list.bullet.rectangle")
            SummaryCard(title: "Daily Avg", value: report.dailyAverage, icon: "calendar")
        }
    }

    private func chartSection(_ report: TripReport) -> some View {
        let slices = report.categoryBreakdown.enumerated().map { idx, breakdown in
            PieChartView.PieSlice(
                id: breakdown.id,
                label: breakdown.categoryName,
                value: breakdown.percentage,
                color: PieChartView.palette[idx % PieChartView.palette.count]
            )
        }
        return VStack(alignment: .leading, spacing: 12) {
            Text("Category Distribution")
                .font(.headline)
            HStack(alignment: .top, spacing: 16) {
                PieChartView(slices: slices)
                    .frame(width: 160, height: 160)
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(Array(slices.enumerated()), id: \.element.id) { idx, slice in
                        HStack(spacing: 6) {
                            Circle()
                                .fill(slice.color)
                                .frame(width: 10, height: 10)
                            Text(slice.label)
                                .font(.caption)
                            Spacer()
                            Text("\(Int(slice.value))%")
                                .font(.caption.monospacedDigit())
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
        .padding()
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func breakdownList(_ report: TripReport) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("By Category")
                .font(.headline)
            ForEach(report.categoryBreakdown) { item in
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(item.categoryName)
                        Spacer()
                        Text("\(item.total) (\(item.count) expense\(item.count == 1 ? "" : "s"))")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)
                    }
                    GeometryReader { geo in
                        RoundedRectangle(cornerRadius: 4)
                            .fill(Color.accentColor.opacity(0.25))
                            .frame(width: geo.size.width * CGFloat(item.percentage / 100))
                            .frame(height: 6)
                    }
                    .frame(height: 6)
                }
            }
        }
        .padding()
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

// MARK: - Summary card
struct SummaryCard: View {
    let title: String
    let value: String
    let icon: String

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Image(systemName: icon)
                .foregroundStyle(.tint)
            Text(value)
                .font(.headline)
                .minimumScaleFactor(0.7)
            Text(title)
                .font(.caption)
                .foregroundStyle(.secondary)
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(.secondarySystemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 10))
    }
}

// MARK: - Share sheet
struct ShareSheet: UIViewControllerRepresentable {
    let items: [Any]

    func makeUIViewController(context: Context) -> UIActivityViewController {
        UIActivityViewController(activityItems: items, applicationActivities: nil)
    }
    func updateUIViewController(_ vc: UIActivityViewController, context: Context) {}
}

// MARK: - Identifiable string wrapper for sheets
struct IdentifiableString: Identifiable {
    let id = UUID()
    let value: String
}
