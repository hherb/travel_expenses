import Foundation
import Shared

/// Seeds the 9 default categories on first launch via CategoryCreated events.
/// Must be called once after the ServiceContainer is ready.
@MainActor
final class DefaultCategoryInitializer {

    private let categoryRepo: SqlDelightCategoryRepository
    private let eventLogRepo: SqlDelightEventLogRepository
    private let deviceId: String

    init(container: ServiceContainer) {
        self.categoryRepo = container.categoryRepo
        self.eventLogRepo = container.eventLogRepo
        self.deviceId = container.deviceId
    }

    func initializeIfNeeded() {
        Task {
            do {
                let existing = try await categoryRepo.getAllCategories()
                guard existing.isEmpty else { return }

                let now = DateTimeHelpers.shared.now()
                let defaults = DefaultCategories.shared.all
                let baseSeqNum = try await eventLogRepo.count()

                for (index, category) in defaults.enumerated() {
                    let seqNum = baseSeqNum.int64Value + Int64(index + 1)
                    let event = ExpenseEvent.CategoryCreated(
                        eventId: UUID().uuidString,
                        timestamp: now,
                        sequenceNumber: seqNum,
                        deviceId: deviceId,
                        category: category
                    )
                    try await eventLogRepo.append(event: event)
                }
            } catch {
                // Non-fatal – categories will be absent but app still functions
                print("[DefaultCategoryInitializer] Failed: \(error)")
            }
        }
    }
}
