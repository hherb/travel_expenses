import SwiftUI
import Shared

/// Create or edit a single expense.  Works in two modes:
///   - `expenseId == nil`  → new expense
///   - `expenseId != nil`  → edit existing expense
struct ExpenseEntryView: View {

    @StateObject private var viewModel: ExpenseViewModel
    @Environment(\.dismiss) private var dismiss

    // Optional OCR pre-fill
    var ocrAmount: String?
    var ocrCurrency: String?
    var ocrVendor: String?
    var ocrDate: Kotlinx_datetimeLocalDate?
    var ocrTaxAmount: String?
    var ocrConfidence: Float?
    var receiptImagePath: String?

    @State private var showTagPicker = false
    @State private var newTagLabel = ""
    @State private var showDatePicker = false
    @State private var selectedSwiftDate = Date()

    init(
        tripId: String,
        expenseId: String?,
        container: ServiceContainer,
        ocrAmount: String? = nil,
        ocrCurrency: String? = nil,
        ocrVendor: String? = nil,
        ocrDate: Kotlinx_datetimeLocalDate? = nil,
        ocrTaxAmount: String? = nil,
        ocrConfidence: Float? = nil,
        receiptImagePath: String? = nil
    ) {
        _viewModel = StateObject(
            wrappedValue: ExpenseViewModel(
                tripId: tripId,
                expenseId: expenseId,
                container: container
            )
        )
        self.ocrAmount = ocrAmount
        self.ocrCurrency = ocrCurrency
        self.ocrVendor = ocrVendor
        self.ocrDate = ocrDate
        self.ocrTaxAmount = ocrTaxAmount
        self.ocrConfidence = ocrConfidence
        self.receiptImagePath = receiptImagePath
    }

    var body: some View {
        Form {
            amountSection
            detailsSection
            tagsSection
            notesSection

            if !viewModel.validationErrors.isEmpty {
                Section {
                    ForEach(viewModel.validationErrors, id: \.self) { error in
                        Label(error, systemImage: "exclamationmark.triangle")
                            .foregroundStyle(.red)
                    }
                }
            }
        }
        .navigationTitle(viewModel.formState.tripId.isEmpty ? "New Expense" : "Edit Expense")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .cancellationAction) {
                Button("Cancel") { dismiss() }
            }
            ToolbarItem(placement: .confirmationAction) {
                Button("Save") { viewModel.save() }
                    .disabled(viewModel.isSaving)
            }
        }
        .onChange(of: viewModel.saveComplete) { complete in
            if complete { dismiss() }
        }
        .onAppear {
            if let a = ocrAmount, !a.isEmpty {
                viewModel.applyOcrResult(
                    amount: a,
                    currency: ocrCurrency ?? "USD",
                    vendor: ocrVendor ?? "",
                    date: ocrDate,
                    taxAmount: ocrTaxAmount,
                    confidence: ocrConfidence
                )
            }
            if let path = receiptImagePath {
                viewModel.formState.receiptImagePath = path
            }
        }
        .sheet(isPresented: $showTagPicker) {
            TagPickerSheet(viewModel: viewModel)
        }
    }

    // MARK: - Form sections

    private var amountSection: some View {
        Section("Amount") {
            HStack {
                TextField("0.00", text: $viewModel.formState.amount)
                    .keyboardType(.decimalPad)
                    .font(.title2.monospacedDigit())
                Spacer()
                TextField("USD", text: $viewModel.formState.currency)
                    .textInputAutocapitalization(.characters)
                    .multilineTextAlignment(.trailing)
                    .frame(width: 60)
                    .font(.headline)
                    .foregroundStyle(.tint)
                    .onChange(of: viewModel.formState.currency) { val in
                        viewModel.formState.currency = String(val.uppercased().prefix(3))
                    }
            }
            if let tax = viewModel.formState.taxAmount.isEmpty ? nil : viewModel.formState.taxAmount {
                HStack {
                    Text("Tax").foregroundStyle(.secondary)
                    Spacer()
                    Text(tax).font(.subheadline)
                }
            }
            TextField("Tax amount (optional)", text: $viewModel.formState.taxAmount)
                .keyboardType(.decimalPad)
        }
    }

    private var detailsSection: some View {
        Section("Details") {
            // Vendor with autocomplete
            VStack(alignment: .leading, spacing: 0) {
                TextField("Vendor", text: $viewModel.formState.vendor)
                if !viewModel.vendorSuggestions.isEmpty && !viewModel.formState.vendor.isEmpty {
                    let filtered = viewModel.vendorSuggestions.filter {
                        $0.localizedCaseInsensitiveContains(viewModel.formState.vendor)
                    }.prefix(5)
                    if !filtered.isEmpty {
                        Divider().padding(.vertical, 4)
                        ForEach(Array(filtered), id: \.self) { suggestion in
                            Button(suggestion) {
                                viewModel.formState.vendor = suggestion
                            }
                            .font(.callout)
                            .foregroundStyle(.primary)
                            .padding(.vertical, 2)
                        }
                    }
                }
            }

            // Date picker
            DatePicker(
                "Date",
                selection: $selectedSwiftDate,
                displayedComponents: .date
            )
            .onChange(of: selectedSwiftDate) { date in
                let components = Calendar.current.dateComponents([.year, .month, .day], from: date)
                if let y = components.year, let m = components.month, let d = components.day {
                    viewModel.formState.date = Kotlinx_datetimeLocalDate(year: Int32(y), monthNumber: Int32(m), dayOfMonth: Int32(d))
                }
            }

            // Category picker
            if !viewModel.categories.isEmpty {
                Picker("Category", selection: $viewModel.formState.categoryId) {
                    Text("None").tag("")
                    ForEach(viewModel.categories, id: \.id) { cat in
                        Text(cat.name).tag(cat.id)
                    }
                }
            }
        }
    }

    private var tagsSection: some View {
        Section("Tags") {
            Button("Manage Tags") {
                showTagPicker = true
            }
            if !viewModel.formState.selectedTagIds.isEmpty {
                let selectedLabels = viewModel.tags
                    .filter { viewModel.formState.selectedTagIds.contains($0.id) }
                    .map(\.label)
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack {
                        ForEach(selectedLabels, id: \.self) { label in
                            TagChip(label: label) {
                                if let tag = viewModel.tags.first(where: { $0.label == label }) {
                                    viewModel.toggleTag(tag.id)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private var notesSection: some View {
        Section("Notes") {
            TextField("Additional notes", text: $viewModel.formState.notes, axis: .vertical)
                .lineLimit(3...6)
        }
    }
}

// MARK: - Tag chip
struct TagChip: View {
    let label: String
    let onRemove: () -> Void

    var body: some View {
        HStack(spacing: 4) {
            Text(label)
                .font(.caption)
            Button { onRemove() } label: {
                Image(systemName: "xmark.circle.fill")
                    .font(.caption)
            }
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(Color.accentColor.opacity(0.15))
        .clipShape(Capsule())
    }
}

// MARK: - Tag picker sheet
struct TagPickerSheet: View {
    @ObservedObject var viewModel: ExpenseViewModel
    @Environment(\.dismiss) private var dismiss
    @State private var newTagLabel = ""

    var body: some View {
        NavigationStack {
            List {
                Section("Available Tags") {
                    ForEach(viewModel.tags, id: \.id) { tag in
                        Button {
                            viewModel.toggleTag(tag.id)
                        } label: {
                            HStack {
                                Text(tag.label)
                                Spacer()
                                if viewModel.formState.selectedTagIds.contains(tag.id) {
                                    Image(systemName: "checkmark")
                                        .foregroundStyle(.tint)
                                }
                            }
                        }
                        .foregroundStyle(.primary)
                    }
                }
                Section("Create New Tag") {
                    HStack {
                        TextField("Tag name", text: $newTagLabel)
                        Button("Add") {
                            guard !newTagLabel.isEmpty else { return }
                            viewModel.createTag(label: newTagLabel)
                            newTagLabel = ""
                        }
                        .disabled(newTagLabel.isEmpty)
                    }
                }
            }
            .navigationTitle("Tags")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .confirmationAction) {
                    Button("Done") { dismiss() }
                }
            }
        }
    }
}
