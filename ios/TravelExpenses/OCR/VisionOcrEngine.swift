import Foundation
import Vision
import UIKit
import Shared

/// Apple Vision text recognition wrapper that implements the KMP OcrEngine interface.
/// Runs on-device, no network required.
final class VisionOcrEngine: OcrEngine {

    // MARK: - OcrEngine protocol

    /// Recognises text in the image at `imagePath` using Vision's VNRecognizeTextRequest.
    /// Returns nil if the image cannot be loaded or recognition fails.
    func recognizeText(imagePath: String) async throws -> OcrTextResult? {
        guard let image = UIImage(contentsOfFile: imagePath),
              let cgImage = image.cgImage else {
            return nil
        }

        return try await withCheckedThrowingContinuation { continuation in
            let request = VNRecognizeTextRequest { request, error in
                if let error {
                    continuation.resume(throwing: error)
                    return
                }

                guard let observations = request.results as? [VNRecognizedTextObservation] else {
                    continuation.resume(returning: OcrTextResult(fullText: "", blocks: []))
                    return
                }

                var lines: [String] = []
                var blocks: [TextBlock] = []

                for observation in observations {
                    guard let candidate = observation.topCandidates(1).first else { continue }
                    lines.append(candidate.string)
                    blocks.append(
                        TextBlock(
                            text: candidate.string,
                            confidence: candidate.confidence
                        )
                    )
                }

                let fullText = lines.joined(separator: "\n")
                continuation.resume(returning: OcrTextResult(fullText: fullText, blocks: blocks))
            }

            // Accurate (neural-network) recognition with automatic language detection
            request.recognitionLevel = .accurate
            request.usesLanguageCorrection = true

            let handler = VNImageRequestHandler(cgImage: cgImage, options: [:])
            do {
                try handler.perform([request])
            } catch {
                continuation.resume(throwing: error)
            }
        }
    }
}
