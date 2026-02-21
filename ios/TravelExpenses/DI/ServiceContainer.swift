import Foundation
import Shared

/// Central dependency injection container.
/// Initialised once at app startup and propagated via SwiftUI's environment.
@MainActor
final class ServiceContainer: ObservableObject {

    // MARK: - KMP Shared infrastructure
    let db: any TravelExpensesDb
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
        let queryContext = IosDispatchers.shared.default_
        db = TravelExpensesDbCompanion.shared.invoke(driver: driver)

        // Replay engine for event log
        let replayEngine = EventReplayEngine(db: db, queryContext: queryContext)

        // Repositories – use background dispatcher for I/O
        eventLogRepo = SqlDelightEventLogRepository(
            db: db,
            queryContext: queryContext,
            replayEngine: replayEngine,
            json: IosJson.shared.instance
        )
        tripRepo = SqlDelightTripRepository(db: db, queryContext: queryContext)
        expenseRepo = SqlDelightExpenseRepository(db: db, queryContext: queryContext)
        categoryRepo = SqlDelightCategoryRepository(db: db, queryContext: queryContext)
        exchangeRateRepo = SqlDelightExchangeRateRepository(db: db, queryContext: queryContext)
        tagRepo = SqlDelightTagRepository(db: db, queryContext: queryContext)

        // Business logic singletons
        currencyConverter = CurrencyConverter(exchangeRateRepo: exchangeRateRepo)
        exchangeRateService = ExchangeRateService(exchangeRateRepo: exchangeRateRepo, httpClient: nil)
        csvExporter = CsvExporter(
            expenseRepo: expenseRepo,
            tripRepo: tripRepo,
            categoryRepo: categoryRepo,
            currencyConverter: nil
        )
        ocrParser = OcrParser(tripCurrency: nil)
        syncManager = SyncManager(
            eventLogRepo: eventLogRepo,
            replayEngine: replayEngine,
            deviceId: id
        )
        expenseValidator = ExpenseValidator.shared
    }
}
