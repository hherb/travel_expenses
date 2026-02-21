import Foundation
import Shared

@MainActor
final class TripViewModel: ObservableObject {

    @Published var activeTrips: [Trip] = []
    @Published var archivedTrips: [Trip] = []
    @Published var selectedTrip: Trip?
    @Published var tripExpenses: [Expense] = []
    @Published var tripTotal: String = "0.00"
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?

    private let tripRepo: SqlDelightTripRepository
    private let expenseRepo: SqlDelightExpenseRepository
    private let eventLogRepo: SqlDelightEventLogRepository
    private let currencyConverter: CurrencyConverter
    private let deviceId: String

    // Flow bridges (keep alive while ViewModel is alive)
    private var tripBridge: TripFlowBridge?
    private var expenseBridge: ExpenseFlowBridge?

    init(container: ServiceContainer) {
        self.tripRepo       = container.tripRepo
        self.expenseRepo    = container.expenseRepo
        self.eventLogRepo   = container.eventLogRepo
        self.currencyConverter = container.currencyConverter
        self.deviceId       = container.deviceId

        observeTrips()
    }

    // MARK: - Flow observation

    private func observeTrips() {
        let bridge = TripFlowBridge(repo: tripRepo)
        self.tripBridge = bridge

        bridge.collectActiveTrips(
            onEach: { [weak self] trips in
                Task { @MainActor in
                    self?.activeTrips = trips as? [Trip] ?? []
                }
            },
            onError: { [weak self] error in
                Task { @MainActor in self?.errorMessage = error.localizedDescription }
            }
        )

        bridge.collectAllTrips(
            onEach: { [weak self] trips in
                Task { @MainActor in
                    let all = trips as? [Trip] ?? []
                    self?.archivedTrips = all.filter { $0.isArchived == true }
                }
            },
            onError: { _ in }
        )
    }

    // MARK: - Trip CRUD

    func createTrip(
        name: String,
        destination: String?,
        baseCurrency: String,
        startDate: Kotlinx_datetimeLocalDate?,
        endDate: Kotlinx_datetimeLocalDate?
    ) {
        Task {
            do {
                let seqNum = try await eventLogRepo.count() + 1
                let now = Kotlinx_datetimeClock.companion.System.now()
                let trip = Trip(
                    id: UUID().uuidString,
                    name: name,
                    destination: destination,
                    startDate: startDate,
                    endDate: endDate,
                    baseCurrency: baseCurrency,
                    createdAt: now
                )
                let event = ExpenseEvent.TripCreated(
                    eventId: UUID().uuidString,
                    timestamp: now,
                    sequenceNumber: seqNum,
                    deviceId: deviceId,
                    trip: trip
                )
                try await eventLogRepo.append(event: event)
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    func archiveTrip(_ tripId: String) {
        Task {
            do {
                let seqNum = try await eventLogRepo.count() + 1
                let now = Kotlinx_datetimeClock.companion.System.now()
                let event = ExpenseEvent.TripArchived(
                    eventId: UUID().uuidString,
                    timestamp: now,
                    sequenceNumber: seqNum,
                    deviceId: deviceId,
                    tripId: tripId
                )
                try await eventLogRepo.append(event: event)
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    // MARK: - Trip detail

    func loadTripDetail(tripId: String) {
        Task {
            isLoading = true
            do {
                selectedTrip = try await tripRepo.getTrip(tripId: tripId)

                // Start observing expenses for this trip
                let bridge = ExpenseFlowBridge(repo: expenseRepo)
                self.expenseBridge = bridge
                bridge.collectForTrip(
                    tripId: tripId,
                    onEach: { [weak self] expenses in
                        Task { @MainActor in
                            self?.tripExpenses = expenses as? [Expense] ?? []
                            self?.recalculateTotal()
                        }
                    },
                    onError: { _ in }
                )
            } catch {
                errorMessage = error.localizedDescription
            }
            isLoading = false
        }
    }

    func deleteExpense(_ expenseId: String) {
        Task {
            do {
                let seqNum = try await eventLogRepo.count() + 1
                let now = Kotlinx_datetimeClock.companion.System.now()
                let event = ExpenseEvent.ExpenseDeleted(
                    eventId: UUID().uuidString,
                    timestamp: now,
                    sequenceNumber: seqNum,
                    deviceId: deviceId,
                    expenseId: expenseId
                )
                try await eventLogRepo.append(event: event)
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    // MARK: - Private

    private func recalculateTotal() {
        guard let trip = selectedTrip else { return }
        Task {
            do {
                let result = try await currencyConverter.aggregateTripTotal(
                    expenses: tripExpenses,
                    baseCurrency: trip.baseCurrency
                )
                tripTotal = "\(result.total) \(trip.baseCurrency)"
            } catch {
                tripTotal = "—"
            }
        }
    }
}
