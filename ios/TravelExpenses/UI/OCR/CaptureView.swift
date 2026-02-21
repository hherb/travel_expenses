import SwiftUI
import PhotosUI

/// Presents a choice between camera capture and photo library, then
/// navigates to OcrReviewView with the chosen image path.
struct CaptureView: View {

    let tripId: String
    @EnvironmentObject private var container: ServiceContainer

    @Environment(\.dismiss) private var dismiss

    @State private var showCamera = false
    @State private var showPhotoPicker = false
    @State private var capturedImagePath: String?
    @State private var photoPickerItem: PhotosPickerItem?

    // Navigate to review once image is ready
    @State private var navigateToReview = false

    var body: some View {
        NavigationStack {
            VStack(spacing: 32) {
                Spacer()

                Image(systemName: "camera.viewfinder")
                    .font(.system(size: 80))
                    .foregroundStyle(.tint)

                Text("Scan Receipt")
                    .font(.title.bold())

                Text("Capture a photo of your receipt or choose from your library.")
                    .multilineTextAlignment(.center)
                    .foregroundStyle(.secondary)
                    .padding(.horizontal)

                Spacer()

                VStack(spacing: 16) {
                    Button {
                        showCamera = true
                    } label: {
                        Label("Take Photo", systemImage: "camera")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.borderedProminent)
                    .controlSize(.large)

                    PhotosPicker(
                        selection: $photoPickerItem,
                        matching: .images,
                        photoLibrary: .shared()
                    ) {
                        Label("Choose from Library", systemImage: "photo.on.rectangle")
                            .frame(maxWidth: .infinity)
                    }
                    .buttonStyle(.bordered)
                    .controlSize(.large)
                    .onChange(of: photoPickerItem) { item in
                        loadPhoto(from: item)
                    }

                    Button("Cancel") { dismiss() }
                        .foregroundStyle(.secondary)
                }
                .padding(.horizontal, 32)
                .padding(.bottom, 40)
            }
            .navigationTitle("Capture Receipt")
            .navigationBarTitleDisplayMode(.inline)
            .navigationDestination(isPresented: $navigateToReview) {
                if let path = capturedImagePath {
                    OcrReviewView(imagePath: path, tripId: tripId)
                }
            }
        }
        .fullScreenCover(isPresented: $showCamera) {
            CameraView { imagePath in
                capturedImagePath = imagePath
                showCamera = false
                navigateToReview = true
            }
        }
    }

    private func loadPhoto(from item: PhotosPickerItem?) {
        guard let item else { return }
        Task {
            if let data = try? await item.loadTransferable(type: Data.self) {
                let url = FileManager.default.temporaryDirectory
                    .appendingPathComponent("receipt_\(UUID().uuidString).jpg")
                try? data.write(to: url)
                capturedImagePath = url.path
                navigateToReview = true
            }
        }
    }
}

// MARK: - Camera UIKit bridge
import UIKit

/// UIViewControllerRepresentable that wraps UIImagePickerController for camera capture.
struct CameraView: UIViewControllerRepresentable {

    let onCapture: (String) -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onCapture: onCapture)
    }

    func makeUIViewController(context: Context) -> UIImagePickerController {
        let picker = UIImagePickerController()
        picker.sourceType = .camera
        picker.delegate = context.coordinator
        return picker
    }

    func updateUIViewController(_ uiViewController: UIImagePickerController, context: Context) {}

    final class Coordinator: NSObject, UIImagePickerControllerDelegate, UINavigationControllerDelegate {
        let onCapture: (String) -> Void
        init(onCapture: @escaping (String) -> Void) { self.onCapture = onCapture }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            picker.dismiss(animated: true)
            guard let image = info[.originalImage] as? UIImage else { return }
            let url = FileManager.default.temporaryDirectory
                .appendingPathComponent("receipt_\(UUID().uuidString).jpg")
            if let data = image.jpegData(compressionQuality: 0.9) {
                try? data.write(to: url)
                onCapture(url.path)
            }
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            picker.dismiss(animated: true)
        }
    }
}
