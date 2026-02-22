import Foundation
import Shared

/// Drives the OCR review flow: runs Vision OCR on a captured image,
/// passes raw text through KMP OcrParser, and exposes structured fields.
@MainActor
final class OcrViewModel: ObservableObject {

    @Published var isProcessing: Bool = false
    @Published var vendor: String = ""
    @Published var amount: String = ""
    @Published var currency: String = ""
    @Published var date: String = ""
    @Published var taxAmount: String = ""
    @Published var confidence: Float? = nil
    @Published var errorMessage: String?
    @Published var processingComplete: Bool = false

    private let ocrEngine: VisionOcrEngine
    private let ocrParser: OcrParser

    init(container: ServiceContainer) {
        self.ocrEngine = VisionOcrEngine()
        self.ocrParser = container.ocrParser
    }

    func processImage(at imagePath: String) {
        Task {
            isProcessing = true
            defer { isProcessing = false }

            // Preprocess image first (grayscale + contrast)
            let processedPath = ImagePreprocessor.preprocess(imagePath: imagePath)

            do {
                guard let textResult = try await ocrEngine.recognizeText(imagePath: processedPath) else {
                    errorMessage = "Could not read text from image."
                    return
                }

                // Parse structured fields from raw OCR text via KMP OcrParser
                let result = ocrParser.parse(rawText: textResult.fullText)

                vendor     = result.vendor ?? ""
                amount     = result.total  ?? ""
                currency   = result.currency ?? ""
                taxAmount  = result.tax    ?? ""
                date       = result.date ?? ""
                confidence = result.totalConfidence

                processingComplete = true
            } catch {
                errorMessage = "OCR failed: \(error.localizedDescription)"
            }
        }
    }
}
