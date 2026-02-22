import SwiftUI
import Shared

/// Shows OCR-extracted fields for user review/correction before saving an expense.
struct OcrReviewView: View {

    let imagePath: String
    let tripId: String
    let container: ServiceContainer

    @StateObject private var ocrViewModel: OcrViewModel
    @State private var navigateToExpenseEntry = false

    init(imagePath: String, tripId: String, container: ServiceContainer) {
        self.imagePath = imagePath
        self.tripId = tripId
        self.container = container
        _ocrViewModel = StateObject(wrappedValue: OcrViewModel(container: container))
    }

    var body: some View {
        Group {
            if ocrViewModel.isProcessing {
                processingView
            } else if let err = ocrViewModel.errorMessage {
                errorView(err)
            } else {
                reviewForm
            }
        }
        .navigationTitle("Review Receipt")
        .navigationBarTitleDisplayMode(.inline)
        .onAppear {
            ocrViewModel.processImage(at: imagePath)
        }
        .navigationDestination(isPresented: $navigateToExpenseEntry) {
            ExpenseEntryView(
                tripId: tripId,
                expenseId: nil,
                container: container,
                ocrAmount: ocrViewModel.amount,
                ocrCurrency: ocrViewModel.currency.isEmpty ? "USD" : ocrViewModel.currency,
                ocrVendor: ocrViewModel.vendor,
                ocrDate: Self.parseDate(ocrViewModel.date.isEmpty ? nil : ocrViewModel.date),
                ocrTaxAmount: ocrViewModel.taxAmount.isEmpty ? nil : ocrViewModel.taxAmount,
                ocrConfidence: ocrViewModel.confidence
            )
        }
    }

    // MARK: - Sub-views

    private var processingView: some View {
        VStack(spacing: 24) {
            ProgressView()
                .scaleEffect(2)
            Text("Reading receipt…")
                .font(.headline)
                .foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func errorView(_ message: String) -> some View {
        VStack(spacing: 16) {
            Image(systemName: "exclamationmark.triangle")
                .font(.system(size: 48))
                .foregroundStyle(.red)
            Text("OCR Failed")
                .font(.title2.bold())
            Text(message)
                .multilineTextAlignment(.center)
                .foregroundStyle(.secondary)
            Button("Enter Manually") {
                navigateToExpenseEntry = true
            }
            .buttonStyle(.borderedProminent)
        }
        .padding()
    }

    private var reviewForm: some View {
        Form {
            // Receipt image thumbnail
            Section {
                if let image = loadImage() {
                    Image(uiImage: image)
                        .resizable()
                        .scaledToFit()
                        .frame(maxHeight: 200)
                        .clipShape(RoundedRectangle(cornerRadius: 8))
                }
            }

            Section("Extracted Information") {
                confidenceRow("Vendor", value: $ocrViewModel.vendor, confidence: ocrViewModel.confidence)
                confidenceRow("Amount", value: $ocrViewModel.amount, confidence: ocrViewModel.confidence)
                    .keyboardType(.decimalPad)

                HStack {
                    Text("Currency")
                        .foregroundStyle(.secondary)
                    Spacer()
                    TextField("USD", text: $ocrViewModel.currency)
                        .textInputAutocapitalization(.characters)
                        .multilineTextAlignment(.trailing)
                        .frame(width: 60)
                }

                HStack {
                    Text("Date")
                        .foregroundStyle(.secondary)
                    Spacer()
                    TextField("YYYY-MM-DD", text: $ocrViewModel.date)
                        .multilineTextAlignment(.trailing)
                        .keyboardType(.numbersAndPunctuation)
                }

                if !ocrViewModel.taxAmount.isEmpty {
                    HStack {
                        Text("Tax")
                            .foregroundStyle(.secondary)
                        Spacer()
                        TextField("0.00", text: $ocrViewModel.taxAmount)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.trailing)
                    }
                }
            }

            if let conf = ocrViewModel.confidence {
                Section {
                    HStack {
                        Text("OCR Confidence")
                        Spacer()
                        Text("\(Int(conf * 100))%")
                            .foregroundStyle(conf > 0.7 ? .green : conf > 0.4 ? .orange : .red)
                            .bold()
                    }
                }
            }

            Section {
                Button("Use These Values") {
                    navigateToExpenseEntry = true
                }
                .frame(maxWidth: .infinity, alignment: .center)
                .disabled(ocrViewModel.amount.isEmpty)

                Button("Enter Manually") {
                    ocrViewModel.amount = ""
                    ocrViewModel.vendor = ""
                    navigateToExpenseEntry = true
                }
                .frame(maxWidth: .infinity, alignment: .center)
                .foregroundStyle(.secondary)
            }
        }
    }

    private func confidenceRow(_ label: String, value: Binding<String>, confidence: Float?) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(label)
                    .font(.caption)
                    .foregroundStyle(.secondary)
                TextField(label, text: value)
                    .font(.body)
            }
            Spacer()
            if let c = confidence, c > 0 {
                Circle()
                    .fill(c > 0.7 ? Color.green : c > 0.4 ? Color.orange : Color.red)
                    .frame(width: 8, height: 8)
            }
        }
    }

    private func loadImage() -> UIImage? {
        UIImage(contentsOfFile: imagePath)
    }

    /// Parses an ISO-8601 date string (YYYY-MM-DD) into a Kotlinx_datetimeLocalDate.
    private static func parseDate(_ dateString: String?) -> Kotlinx_datetimeLocalDate? {
        guard let dateString, !dateString.isEmpty else { return nil }
        let parts = dateString.split(separator: "-")
        guard parts.count == 3,
              let y = Int32(parts[0]),
              let m = Int32(parts[1]),
              let d = Int32(parts[2]) else { return nil }
        return Kotlinx_datetimeLocalDate(year: y, monthNumber: m, dayOfMonth: d)
    }
}
