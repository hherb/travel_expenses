import Foundation
import Shared

@MainActor
final class SettingsViewModel: ObservableObject {

    @Published var exportedArchive: IdentifiableString?
    @Published var exportedCsv: IdentifiableString?
    @Published var importResult: String?
    @Published var isLoading: Bool = false
    @Published var errorMessage: String?

    // PIN setup state
    @Published var showPinSetup: Bool = false
    @Published var newPin: String = ""
    @Published var confirmPin: String = ""
    @Published var pinError: String?

    private let syncManager: SyncManager
    private let csvExporter: CsvExporter

    init(container: ServiceContainer) {
        self.syncManager  = container.syncManager
        self.csvExporter  = container.csvExporter
    }

    // MARK: - PIN management (delegated to AuthManager via environment)

    func setupPin(authManager: AuthManager) {
        guard newPin == confirmPin else {
            pinError = "PINs do not match."
            return
        }
        do {
            try authManager.setPin(newPin)
            showPinSetup = false
            newPin = ""
            confirmPin = ""
            pinError = nil
        } catch {
            pinError = error.localizedDescription
        }
    }

    func clearPin(authManager: AuthManager) {
        authManager.clearPin()
    }

    // MARK: - Data export / import

    func exportArchive() {
        Task {
            isLoading = true
            defer { isLoading = false }
            do {
                let archive = try await syncManager.exportArchive()
                let json = EventArchiveSerializer.shared.serialize(archive: archive)
                exportedArchive = IdentifiableString(value: json)
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }

    func importArchive(_ json: String) {
        Task {
            isLoading = true
            defer { isLoading = false }
            do {
                let archive = EventArchiveSerializer.shared.deserialize(jsonString: json)
                try await syncManager.importArchive(archive: archive)
                importResult = "Import complete (\(archive.eventCount) events)"
            } catch {
                errorMessage = "Import failed: \(error.localizedDescription)"
            }
        }
    }

    func exportAllCsv() {
        Task {
            isLoading = true
            defer { isLoading = false }
            do {
                let csv = try await csvExporter.exportAll()
                exportedCsv = IdentifiableString(value: csv)
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }
}
