import SwiftUI

/// Root tab bar matching the SPEC.md navigation structure:
///   Trips | Reports | Settings
struct MainTabView: View {

    @EnvironmentObject private var container: ServiceContainer

    var body: some View {
        TabView {
            TripListView(container: container)
                .tabItem {
                    Label("Trips", systemImage: "suitcase")
                }

            ReportsView(container: container)
                .tabItem {
                    Label("Reports", systemImage: "chart.pie")
                }

            SettingsView(container: container)
                .tabItem {
                    Label("Settings", systemImage: "gear")
                }
        }
    }
}
