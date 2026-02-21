import SwiftUI
import LocalAuthentication
import Shared

struct SettingsView: View {

    @StateObject private var viewModel: SettingsViewModel
    @EnvironmentObject private var authManager: AuthManager

    @State private var showImportPicker = false
    @State private var importFileURL: URL?

    init(container: ServiceContainer) {
        _viewModel = StateObject(wrappedValue: SettingsViewModel(container: container))
    }

    var body: some View {
        NavigationStack {
            Form {
                securitySection
                dataSection
                aboutSection
            }
            .navigationTitle("Settings")
            .sheet(isPresented: $viewModel.showPinSetup) {
                PinSetupSheet(viewModel: viewModel)
            }
            .fileImporter(
                isPresented: $showImportPicker,
                allowedContentTypes: [.json],
                allowsMultipleSelection: false
            ) { result in
                handleImport(result)
            }
            .sheet(item: $viewModel.exportedArchive) { json in
                ShareSheet(items: [json])
            }
            .sheet(item: $viewModel.exportedCsv) { csv in
                ShareSheet(items: [csv])
            }
            .alert("Import Result", isPresented: .constant(viewModel.importResult != nil)) {
                Button("OK") { viewModel.importResult = nil }
            } message: {
                Text(viewModel.importResult ?? "")
            }
            .alert("Error", isPresented: .constant(viewModel.errorMessage != nil)) {
                Button("OK") { viewModel.errorMessage = nil }
            } message: {
                Text(viewModel.errorMessage ?? "")
            }
        }
    }

    // MARK: - Sections

    private var securitySection: some View {
        Section("Security") {
            // Biometric toggle
            if authManager.biometricType != .none {
                Toggle(biometricLabel, isOn: $authManager.biometricEnabled)
            }

            // PIN toggle
            if authManager.isPinSet {
                HStack {
                    Text("6-Digit PIN")
                    Spacer()
                    Text("Enabled")
                        .foregroundStyle(.secondary)
                }
                Button("Change PIN") { viewModel.showPinSetup = true }
                Button("Remove PIN", role: .destructive) {
                    viewModel.clearPin(authManager: authManager)
                }
            } else {
                Button("Set Up PIN") { viewModel.showPinSetup = true }
            }

            // Auto-lock timeout
            Picker("Auto-Lock", selection: $authManager.autoLockTimeout) {
                ForEach(AutoLockTimeout.allCases) { timeout in
                    Text(timeout.label).tag(timeout)
                }
            }
        }
    }

    private var dataSection: some View {
        Section("Data") {
            Button {
                viewModel.exportArchive()
            } label: {
                Label("Export Backup Archive", systemImage: "square.and.arrow.up")
            }

            Button {
                showImportPicker = true
            } label: {
                Label("Import Backup Archive", systemImage: "square.and.arrow.down")
            }

            Button {
                viewModel.exportAllCsv()
            } label: {
                Label("Export All as CSV", systemImage: "tablecells")
            }
        }
    }

    private var aboutSection: some View {
        Section("About") {
            HStack {
                Text("Version")
                Spacer()
                Text(Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "1.0")
                    .foregroundStyle(.secondary)
            }
            HStack {
                Text("Data stays on-device")
                Spacer()
                Image(systemName: "checkmark.shield")
                    .foregroundStyle(.green)
            }
        }
    }

    // MARK: - Helpers

    private var biometricLabel: String {
        switch authManager.biometricType {
        case .faceID:  return "Face ID"
        case .touchID: return "Touch ID"
        default:       return "Biometrics"
        }
    }

    private func handleImport(_ result: Result<[URL], Error>) {
        switch result {
        case .success(let urls):
            guard let url = urls.first,
                  url.startAccessingSecurityScopedResource() else { return }
            defer { url.stopAccessingSecurityScopedResource() }
            if let json = try? String(contentsOf: url, encoding: .utf8) {
                viewModel.importArchive(json)
            }
        case .failure(let error):
            viewModel.errorMessage = error.localizedDescription
        }
    }
}

// MARK: - PIN setup sheet
struct PinSetupSheet: View {
    @ObservedObject var viewModel: SettingsViewModel
    @EnvironmentObject var authManager: AuthManager
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            Form {
                Section("New PIN") {
                    SecureField("6-digit PIN", text: $viewModel.newPin)
                        .keyboardType(.numberPad)
                    SecureField("Confirm PIN", text: $viewModel.confirmPin)
                        .keyboardType(.numberPad)
                }
                if let err = viewModel.pinError {
                    Section {
                        Text(err)
                            .foregroundStyle(.red)
                    }
                }
            }
            .navigationTitle("Set PIN")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") {
                        viewModel.newPin = ""
                        viewModel.confirmPin = ""
                        viewModel.pinError = nil
                        dismiss()
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Save") {
                        viewModel.setupPin(authManager: authManager)
                    }
                    .disabled(viewModel.newPin.count != 6 || viewModel.confirmPin.isEmpty)
                }
            }
        }
    }
}
