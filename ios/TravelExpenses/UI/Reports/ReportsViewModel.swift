import Foundation
import Shared

struct CategoryBreakdown: Identifiable {
    let id: String
    let categoryName: String
    let total: String
    let count: Int
    let percentage: Float
}

struct TripReport {
    let trip: Trip
    let totalAmount: String
    let expenseCount: Int
    let categoryBreakdown: [CategoryBreakdown]
    let dailyAverage: String
}

@MainActor
final class ReportsViewModel: ObservableObject {

    @Published var trips: [Trip] = []
    @Published var selectedTripId: String?
    @Published var report: TripReport?
    @Published var csvOutput: String?
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?

    private let tripRepo: SqlDelightTripRepository
    private let expenseRepo: SqlDelightExpenseRepository
    private let categoryRepo: SqlDelightCategoryRepository
    private let currencyConverter: CurrencyConverter
    private let csvExporter: CsvExporter
    private var tripBridge: TripFlowBridge?

    init(container: ServiceContainer) {
        self.tripRepo         = container.tripRepo
        self.expenseRepo      = container.expenseRepo
        self.categoryRepo     = container.categoryRepo
        self.currencyConverter = container.currencyConverter
        self.csvExporter      = container.csvExporter

        observeTrips()
    }

    deinit {
        tripBridge?.cancel()
    }

    func selectTrip(_ tripId: String) {
        selectedTripId = tripId
        Task { await loadReport(tripId: tripId) }
    }

    func exportCsv() {
        guard let tripId = selectedTripId else { return }
        Task {
            do {
                csvOutput = try await csvExporter.exportTrip(tripId: tripId)
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    // MARK: - Private

    private func observeTrips() {
        let bridge = TripFlowBridge(repo: tripRepo)
        self.tripBridge = bridge
        bridge.collectActiveTrips(
            onEach: { [weak self] tripsAny in
                Task { @MainActor in
                    self?.trips = tripsAny as? [Trip] ?? []
                    // Auto-select first trip if none selected
                    if self?.selectedTripId == nil, let first = self?.trips.first {
                        self?.selectTrip(first.id)
                    }
                }
            },
            onError: { _ in }
        )
    }

    private func loadReport(tripId: String) async {
        isLoading = true
        defer { isLoading = false }

        do {
            guard let trip = try await tripRepo.getTrip(tripId: tripId) else { return }
            let expenses = try await expenseRepo.getExpensesForTrip(tripId: tripId)
            let expensesArr = expenses as? [Expense] ?? []
            let categories = try await categoryRepo.getAllCategories()
            let catNameMap = Dictionary(uniqueKeysWithValues: (categories as? [Category] ?? []).map { ($0.id, $0.name) })

            // Total in trip base currency
            let totalResult = try await currencyConverter.aggregateTripTotal(
                expenses: expensesArr,
                baseCurrency: trip.baseCurrency
            )
            let grandTotal = Double(totalResult.total) ?? 0

            // Category breakdown
            var byCategory: [String: [Expense]] = [:]
            for expense in expensesArr {
                byCategory[expense.categoryId, default: []].append(expense)
            }

            var breakdownList: [CategoryBreakdown] = []
            for (catId, catExpenses) in byCategory {
                let catResult = try await currencyConverter.aggregateTripTotal(
                    expenses: catExpenses,
                    baseCurrency: trip.baseCurrency
                )
                let catTotal = Double(catResult.total) ?? 0
                let pct = grandTotal > 0 ? Float(catTotal / grandTotal * 100) : 0
                breakdownList.append(CategoryBreakdown(
                    id: catId,
                    categoryName: catNameMap[catId] ?? catId,
                    total: String(format: "%.2f", catTotal),
                    count: catExpenses.count,
                    percentage: pct
                ))
            }
            let breakdown = breakdownList.sorted { ($0.total as NSString).doubleValue > ($1.total as NSString).doubleValue }

            // Daily average
            let days: Int
            if let start = trip.startDate, let end = trip.endDate {
                let d = Int(end.toEpochDays() - start.toEpochDays()) + 1
                days = max(d, 1)
            } else if !expensesArr.isEmpty {
                let minDay = expensesArr.map { $0.date.toEpochDays() }.min() ?? 0
                let maxDay = expensesArr.map { $0.date.toEpochDays() }.max() ?? 0
                days = max(Int(maxDay - minDay) + 1, 1)
            } else {
                days = 1
            }
            let avg = grandTotal > 0 ? String(format: "%.2f", grandTotal / Double(days)) : "0.00"

            report = TripReport(
                trip: trip,
                totalAmount: "\(totalResult.total) \(trip.baseCurrency)",
                expenseCount: expensesArr.count,
                categoryBreakdown: breakdown,
                dailyAverage: "\(avg) \(trip.baseCurrency)"
            )
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}
