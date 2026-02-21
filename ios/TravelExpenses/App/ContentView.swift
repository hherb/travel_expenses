import SwiftUI

/// Root view: shows the lock screen when security is active, main UI otherwise.
/// Tracks foreground/background transitions for auto-lock.
struct ContentView: View {

    @EnvironmentObject private var authManager: AuthManager
    @EnvironmentObject private var container: ServiceContainer

    @State private var isLocked: Bool = false
    @State private var lastBackgroundTime: Date?

    var body: some View {
        Group {
            if isLocked && (authManager.isPinSet || authManager.canUseBiometrics()) {
                LockView {
                    isLocked = false
                }
            } else {
                MainTabView()
                    // Hide content in app switcher when security is enabled
                    .privacySensitive(authManager.isPinSet || authManager.canUseBiometrics())
            }
        }
        .onReceive(
            NotificationCenter.default.publisher(for: UIApplication.willResignActiveNotification)
        ) { _ in
            lastBackgroundTime = Date()
        }
        .onReceive(
            NotificationCenter.default.publisher(for: UIApplication.didBecomeActiveNotification)
        ) { _ in
            guard authManager.isPinSet || authManager.canUseBiometrics() else { return }
            guard let bg = lastBackgroundTime else {
                // First launch with security: lock immediately
                isLocked = true
                return
            }
            let elapsed = Date().timeIntervalSince(bg)
            let timeout = Double(authManager.autoLockTimeout.seconds)
            if timeout == 0 || elapsed >= timeout {
                isLocked = true
            }
        }
    }
}
