import SwiftUI
import PhotosUI

/// Presents a choice between camera capture and photo library, then
/// navigates to OcrReviewView with the chosen image path.
struct CaptureView: View {

    let tripId: String
    let container: ServiceContainer

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
                    OcrReviewView(imagePath: path, tripId: tripId, container: container)
                }
            }
        }
        .fullScreenCover(isPresented: $showCamera) {
            CameraView(
                onCapture: { imagePath in
                    print("[CaptureView] onCapture received path=\(imagePath)")
                    capturedImagePath = imagePath
                    showCamera = false
                    // Delay navigation until the fullScreenCover dismiss animation completes.
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.5) {
                        print("[CaptureView] Navigating to review, capturedImagePath=\(capturedImagePath ?? "nil")")
                        navigateToReview = true
                    }
                },
                onCancel: {
                    showCamera = false
                }
            )
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
/// Dismissal is managed by SwiftUI via the `isPresented` binding on fullScreenCover —
/// the coordinator must NOT call picker.dismiss() to avoid cascading dismiss of parent sheets.
struct CameraView: UIViewControllerRepresentable {

    let onCapture: (String) -> Void
    let onCancel: () -> Void

    func makeCoordinator() -> Coordinator {
        Coordinator(onCapture: onCapture, onCancel: onCancel)
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
        let onCancel: () -> Void
        init(onCapture: @escaping (String) -> Void, onCancel: @escaping () -> Void) {
            self.onCapture = onCapture
            self.onCancel = onCancel
        }

        func imagePickerController(
            _ picker: UIImagePickerController,
            didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
        ) {
            guard let image = info[.originalImage] as? UIImage else { return }
            let url = FileManager.default.temporaryDirectory
                .appendingPathComponent("receipt_\(UUID().uuidString).jpg")
            if let data = image.jpegData(compressionQuality: 0.9) {
                do {
                    try data.write(to: url)
                    let exists = FileManager.default.fileExists(atPath: url.path)
                    print("[CaptureView] Wrote \(data.count) bytes to \(url.path), exists=\(exists)")
                    onCapture(url.path)
                } catch {
                    print("[CaptureView] Failed to write image: \(error)")
                }
            }
        }

        func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
            onCancel()
        }
    }
}
