import UIKit
import CoreImage
import CoreImage.CIFilterBuiltins

/// Applies grayscale + contrast enhancement to a receipt image before OCR.
/// Returns the path to the processed image, or the original path if preprocessing fails.
enum ImagePreprocessor {

    /// Pre-processes the image at `sourcePath` and writes the result to a temporary file.
    /// - Returns: path to the pre-processed image, or `sourcePath` on failure.
    static func preprocess(imagePath sourcePath: String) -> String {
        guard let sourceImage = UIImage(contentsOfFile: sourcePath),
              let ciInput = CIImage(image: sourceImage) else {
            return sourcePath
        }

        let context = CIContext()

        // 1. Convert to grayscale
        let grayFilter = CIFilter.colorControls()
        grayFilter.inputImage = ciInput
        grayFilter.saturation = 0   // fully desaturate -> grayscale
        grayFilter.contrast = 1.5   // increase contrast
        grayFilter.brightness = 0

        guard let grayOutput = grayFilter.outputImage else { return sourcePath }

        // 2. Sharpen
        let sharpenFilter = CIFilter.sharpenLuminance()
        sharpenFilter.inputImage = grayOutput
        sharpenFilter.sharpness = 0.5

        let finalOutput = sharpenFilter.outputImage ?? grayOutput

        // Write to a temp file
        let tempDir = FileManager.default.temporaryDirectory
        let filename = "ocr_preprocessed_\(UUID().uuidString).jpg"
        let destURL = tempDir.appendingPathComponent(filename)

        do {
            if let cgImage = context.createCGImage(finalOutput, from: finalOutput.extent) {
                let uiImage = UIImage(cgImage: cgImage)
                if let jpegData = uiImage.jpegData(compressionQuality: 0.95) {
                    try jpegData.write(to: destURL)
                    return destURL.path
                }
            }
        } catch {
            // Fall through to return original
        }
        return sourcePath
    }
}
