import SwiftUI
import LocalAuthentication

/// Full-screen lock screen shown when the app requires authentication.
/// Supports PIN entry and biometric unlock (Face ID / Touch ID).
struct LockView: View {

    @EnvironmentObject private var authManager: AuthManager
    let onUnlocked: () -> Void

    @State private var pin: String = ""
    @State private var errorMessage: String?
    @State private var shakeTrigger: CGFloat = 0
    @FocusState private var pinFocused: Bool

    // Biometric label varies by device capability
    private var biometricLabel: String {
        switch authManager.biometricType {
        case .faceID:   return "Face ID"
        case .touchID:  return "Touch ID"
        default:        return "Biometrics"
        }
    }

    private var biometricIcon: String {
        switch authManager.biometricType {
        case .faceID:  return "faceid"
        case .touchID: return "touchid"
        default:       return "lock.open"
        }
    }

    var body: some View {
        ZStack {
            Color(.systemBackground).ignoresSafeArea()

            VStack(spacing: 32) {
                // Header
                VStack(spacing: 12) {
                    Image(systemName: "lock.shield")
                        .font(.system(size: 64))
                        .foregroundStyle(.tint)
                    Text("Travel Expenses")
                        .font(.largeTitle.bold())
                    Text("Enter your PIN to unlock")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }

                // PIN dots display
                HStack(spacing: 16) {
                    ForEach(0..<6, id: \.self) { index in
                        Circle()
                            .frame(width: 16, height: 16)
                            .foregroundStyle(index < pin.count ? Color.accentColor : Color(.systemGray4))
                    }
                }
                .offset(x: shakeTrigger)

                // Error message
                if let msg = errorMessage {
                    Text(msg)
                        .font(.callout)
                        .foregroundStyle(.red)
                }

                // Hidden text field that captures keypad input
                TextField("", text: $pin)
                    .keyboardType(.numberPad)
                    .focused($pinFocused)
                    .opacity(0)
                    .frame(width: 0, height: 0)
                    .onChange(of: pin) { newValue in
                        handlePinChange(newValue)
                    }

                // Tap-to-focus hint
                Button {
                    pinFocused = true
                } label: {
                    Text("Tap to enter PIN")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }

                // Biometric button
                if authManager.canUseBiometrics() {
                    Button {
                        attemptBiometric()
                    } label: {
                        Label(biometricLabel, systemImage: biometricIcon)
                            .font(.headline)
                            .padding(.horizontal, 32)
                            .padding(.vertical, 14)
                            .background(Color(.secondarySystemBackground))
                            .clipShape(Capsule())
                    }
                }
            }
            .padding(40)
        }
        .onAppear {
            // Auto-prompt biometrics when the lock screen first appears
            if authManager.canUseBiometrics() {
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.3) {
                    attemptBiometric()
                }
            } else {
                pinFocused = true
            }
        }
    }

    // MARK: - Private

    private func handlePinChange(_ newValue: String) {
        // Clamp to digits only and max 6 chars
        let digits = String(newValue.filter(\.isNumber).prefix(6))
        if digits != newValue {
            pin = digits
            return
        }
        errorMessage = nil
        if digits.count == 6 {
            verifyPin(digits)
        }
    }

    private func verifyPin(_ candidate: String) {
        if authManager.verifyPin(candidate) {
            onUnlocked()
        } else {
            errorMessage = "Incorrect PIN. Try again."
            shake()
            pin = ""
        }
    }

    private func attemptBiometric() {
        authManager.authenticateWithBiometrics { success, _ in
            if success { onUnlocked() }
        }
    }

    private func shake() {
        withAnimation(.default.repeatCount(4, autoreverses: true)) {
            shakeTrigger = 8
        }
        DispatchQueue.main.asyncAfter(deadline: .now() + 0.4) {
            shakeTrigger = 0
        }
    }
}
