import Foundation
import Shared

/// Form state for creating or editing a single expense.
struct ExpenseFormState {
    var amount: String = ""
    var currency: String = "USD"
    var categoryId: String = ""
    var vendor: String = ""
    var date: Kotlinx_datetimeLocalDate = DateTimeHelpers.shared.todayLocal()
    var notes: String = ""
    var taxAmount: String = ""
    var tripId: String = ""
    var selectedTagIds: [String] = []
    var ocrConfidence: Float? = nil
}

@MainActor
final class ExpenseViewModel: ObservableObject {

    @Published var formState: ExpenseFormState = ExpenseFormState()
    @Published var categories: [Shared.Category] = []
    @Published var tags: [Tag] = []
    @Published var vendorSuggestions: [String] = []
    @Published var validationErrors: [String] = []
    @Published var isSaving: Bool = false
    @Published var saveComplete: Bool = false
    @Published var errorMessage: String?

    private let tripId: String
    private let editingExpenseId: String?

    private let eventLogRepo: SqlDelightEventLogRepository
    private let expenseRepo: SqlDelightExpenseRepository
    private let categoryRepo: SqlDelightCategoryRepository
    private let tagRepo: SqlDelightTagRepository
    private let validator: ExpenseValidator
    private let deviceId: String

    // Flow bridges
    private var categoryBridge: CategoryFlowBridge?
    private var tagBridge: TagFlowBridge?

    init(tripId: String, expenseId: String?, container: ServiceContainer) {
        self.tripId = tripId
        self.editingExpenseId = expenseId
        self.eventLogRepo = container.eventLogRepo
        self.expenseRepo = container.expenseRepo
        self.categoryRepo = container.categoryRepo
        self.tagRepo = container.tagRepo
        self.validator = container.expenseValidator
        self.deviceId = container.deviceId

        formState.tripId = tripId

        observeCategories()
        observeTags()
        loadVendorSuggestions()

        if let expenseId {
            loadExistingExpense(expenseId)
        }
    }

    deinit {
        categoryBridge?.cancel()
        tagBridge?.cancel()
    }

    // Convenience initialiser when pre-filled from OCR
    func applyOcrResult(
        amount: String,
        currency: String,
        vendor: String,
        date: Kotlinx_datetimeLocalDate?,
        taxAmount: String?,
        confidence: Float?
    ) {
        formState.amount = amount
        formState.currency = currency
        formState.vendor = vendor
        if let d = date { formState.date = d }
        formState.taxAmount = taxAmount ?? ""
        formState.ocrConfidence = confidence
    }

    // MARK: - CRUD

    func save() {
        Task {
            isSaving = true
            defer { isSaving = false }

            let form = formState
            guard !form.amount.isEmpty, !form.currency.isEmpty, !form.tripId.isEmpty else {
                validationErrors = ["Amount, currency, and trip are required."]
                return
            }

            let now = DateTimeHelpers.shared.now()
            let expense = Expense(
                id: editingExpenseId ?? UUID().uuidString,
                tripId: form.tripId,
                amount: form.amount,
                currency: form.currency,
                categoryId: form.categoryId.isEmpty ? "cat-other" : form.categoryId,
                vendor: form.vendor.isEmpty ? nil : form.vendor,
                date: form.date,
                notes: form.notes.isEmpty ? nil : form.notes,
                tags: form.selectedTagIds,
                receiptImageIds: [],
                ocrConfidence: form.ocrConfidence.map { KotlinFloat(value: $0) },
                taxAmount: form.taxAmount.isEmpty ? nil : form.taxAmount,
                createdAt: now,
                lastModifiedAt: now
            )

            let errors = validator.validate(expense: expense)
            if !errors.isEmpty {
                validationErrors = errors.map { $0.message }
                return
            }
            validationErrors = []

            do {
                let seqNum = (try await eventLogRepo.count()).int64Value + 1
                let event: ExpenseEvent

                if let existingId = editingExpenseId {
                    event = ExpenseEvent.ExpenseUpdated(
                        eventId: UUID().uuidString,
                        timestamp: now,
                        sequenceNumber: seqNum,
                        deviceId: deviceId,
                        expenseId: existingId,
                        amount: form.amount,
                        currency: form.currency,
                        categoryId: form.categoryId.isEmpty ? "cat-other" : form.categoryId,
                        vendor: form.vendor,
                        date: form.date,
                        notes: form.notes,
                        tags: form.selectedTagIds,
                        taxAmount: form.taxAmount,
                        ocrConfidence: form.ocrConfidence.map { KotlinFloat(value: $0) },
                        lastModifiedAt: now
                    )
                } else {
                    event = ExpenseEvent.ExpenseCreated(
                        eventId: UUID().uuidString,
                        timestamp: now,
                        sequenceNumber: seqNum,
                        deviceId: deviceId,
                        expense: expense
                    )
                }
                try await eventLogRepo.append(event: event)
                saveComplete = true
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    func createTag(label: String) {
        Task {
            do {
                let seqNum = (try await eventLogRepo.count()).int64Value + 1
                let now = DateTimeHelpers.shared.now()
                let tagId = UUID().uuidString
                let event = ExpenseEvent.TagCreated(
                    eventId: UUID().uuidString,
                    timestamp: now,
                    sequenceNumber: seqNum,
                    deviceId: deviceId,
                    tag: Tag(id: tagId, label: label)
                )
                try await eventLogRepo.append(event: event)
                // Select the new tag automatically
                formState.selectedTagIds.append(tagId)
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    func toggleTag(_ tagId: String) {
        if formState.selectedTagIds.contains(tagId) {
            formState.selectedTagIds.removeAll { $0 == tagId }
        } else {
            formState.selectedTagIds.append(tagId)
        }
    }

    // MARK: - Private

    private func observeCategories() {
        let bridge = CategoryFlowBridge(repo: categoryRepo)
        self.categoryBridge = bridge
        bridge.collectAll(
            onEach: { [weak self] cats in
                Task { @MainActor in self?.categories = cats }
            },
            onError: { _ in }
        )
    }

    private func observeTags() {
        let bridge = TagFlowBridge(repo: tagRepo)
        self.tagBridge = bridge
        bridge.collectAll(
            onEach: { [weak self] tagList in
                Task { @MainActor in self?.tags = tagList }
            },
            onError: { _ in }
        )
    }

    private func loadVendorSuggestions() {
        Task {
            do {
                let vendors = try await expenseRepo.getDistinctVendors()
                vendorSuggestions = vendors
            } catch {
                print("[ExpenseViewModel] Failed to load vendor suggestions: \(error)")
            }
        }
    }

    private func loadExistingExpense(_ id: String) {
        Task {
            do {
                guard let expense = try await expenseRepo.getExpense(expenseId: id) else { return }
                formState.amount     = expense.amount
                formState.currency   = expense.currency
                formState.categoryId = expense.categoryId
                formState.vendor     = expense.vendor ?? ""
                formState.date       = expense.date
                formState.notes      = expense.notes ?? ""
                formState.taxAmount  = expense.taxAmount ?? ""
                formState.selectedTagIds = expense.tags
            } catch {
                print("[ExpenseViewModel] Failed to load expense: \(error)")
            }
        }
    }
}
