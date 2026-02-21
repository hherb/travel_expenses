import SwiftUI
import Shared

/// Application entry point.
/// Sets up the shared KMP infrastructure, seeds default categories on first launch,
/// and injects all dependencies into the SwiftUI environment.
@main
struct TravelExpensesApp: App {

    @StateObject private var container = ServiceContainer()
    @StateObject private var authManager = AuthManager()

    init() {
        // Nothing platform-specific needed here; KMP initialises lazily.
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environmentObject(container)
                .environmentObject(authManager)
                .onAppear {
                    // Seed default categories once on first launch
                    DefaultCategoryInitializer(container: container).initializeIfNeeded()
                }
        }
    }
}
