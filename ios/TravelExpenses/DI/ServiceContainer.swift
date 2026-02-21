import Foundation
import Shared

/// Central dependency injection container.
/// Initialised once at app startup and propagated via SwiftUI's environment.
@MainActor
final class ServiceContainer: ObservableObject {

    // MARK: - KMP Shared infrastructure
    let db: TravelExpensesDb
    let eventLogRepo: SqlDelightEventLogRepository
    let tripRepo: SqlDelightTripRepository
    let expenseRepo: SqlDelightExpenseRepository
    let categoryRepo: SqlDelightCategoryRepository
    let exchangeRateRepo: SqlDelightExchangeRateRepository
    let tagRepo: SqlDelightTagRepository

    // MARK: - Business logic
    let currencyConverter: CurrencyConverter
    let exchangeRateService: ExchangeRateService
    let csvExporter: CsvExporter
    let ocrParser: OcrParser
    let syncManager: SyncManager
    let expenseValidator: ExpenseValidator

    // MARK: - Device identity (stable per install)
    let deviceId: String

    // MARK: - Initialisation
    init() {
        // Generate or restore a stable device ID
        let storedId = UserDefaults.standard.string(forKey: "deviceId")
        let id = storedId ?? UUID().uuidString
        if storedId == nil {
            UserDefaults.standard.set(id, forKey: "deviceId")
        }
        self.deviceId = id

        // SQLite via KMP
        let driver = DatabaseDriverFactory().createDriver()
        db = TravelExpensesDb(driver: driver)

        // Repositories – use background dispatcher for I/O
        eventLogRepo = SqlDelightEventLogRepository(
            db: db,
            dispatcher: Dispatchers.shared.Default,
            deviceId: id
        )
        tripRepo = SqlDelightTripRepository(db: db, dispatcher: Dispatchers.shared.Default)
        expenseRepo = SqlDelightExpenseRepository(db: db, dispatcher: Dispatchers.shared.Default)
        categoryRepo = SqlDelightCategoryRepository(db: db, dispatcher: Dispatchers.shared.Default)
        exchangeRateRepo = SqlDelightExchangeRateRepository(db: db, dispatcher: Dispatchers.shared.Default)
        tagRepo = SqlDelightTagRepository(db: db, dispatcher: Dispatchers.shared.Default)

        // Business logic singletons
        currencyConverter = CurrencyConverter(exchangeRateRepository: exchangeRateRepo)
        exchangeRateService = ExchangeRateService(repository: exchangeRateRepo)
        csvExporter = CsvExporter(
            expenseRepository: expenseRepo,
            tripRepository: tripRepo,
            categoryRepository: categoryRepo,
            tagRepository: tagRepo
        )
        ocrParser = OcrParser()
        syncManager = SyncManager(
            eventLogRepository: eventLogRepo,
            expenseRepository: expenseRepo,
            deviceId: id
        )
        expenseValidator = ExpenseValidator()
    }
}
