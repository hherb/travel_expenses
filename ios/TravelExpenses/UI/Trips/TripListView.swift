import SwiftUI
import Shared

struct TripListView: View {

    @StateObject private var viewModel: TripViewModel
    @State private var showCreateSheet = false
    @State private var showArchived = false

    private let container: ServiceContainer

    init(container: ServiceContainer) {
        self.container = container
        _viewModel = StateObject(wrappedValue: TripViewModel(container: container))
    }

    var body: some View {
        NavigationStack {
            Group {
                if viewModel.activeTrips.isEmpty && !showArchived {
                    emptyState
                } else {
                    tripList
                }
            }
            .navigationTitle("Travel Expenses")
            .toolbar {
                ToolbarItem(placement: .primaryAction) {
                    Button { showCreateSheet = true } label: {
                        Image(systemName: "plus")
                    }
                }
                if !viewModel.archivedTrips.isEmpty {
                    ToolbarItem(placement: .secondaryAction) {
                        Button {
                            showArchived.toggle()
                        } label: {
                            Label(
                                showArchived ? "Hide Archived" : "Show Archived",
                                systemImage: showArchived ? "archivebox.fill" : "archivebox"
                            )
                        }
                    }
                }
            }
            .sheet(isPresented: $showCreateSheet) {
                CreateTripSheet(viewModel: viewModel)
            }
        }
    }

    // MARK: - Sub-views

    private var emptyState: some View {
        VStack(spacing: 16) {
            Image(systemName: "suitcase")
                .font(.system(size: 64))
                .foregroundStyle(.secondary)
            Text("No trips yet")
                .font(.title2.bold())
            Text("Tap + to create your first trip")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Button("New Trip") { showCreateSheet = true }
                .buttonStyle(.borderedProminent)
                .padding(.top, 8)
        }
        .padding()
    }

    private var tripList: some View {
        List {
            if !viewModel.activeTrips.isEmpty {
                Section("Active") {
                    ForEach(viewModel.activeTrips, id: \.id) { trip in
                        NavigationLink {
                            TripDetailView(trip: trip, container: container)
                        } label: {
                            TripRowView(trip: trip)
                        }
                        .swipeActions(edge: .trailing) {
                            Button(role: .destructive) {
                                viewModel.archiveTrip(trip.id)
                            } label: {
                                Label("Archive", systemImage: "archivebox")
                            }
                        }
                    }
                }
            }

            if showArchived && !viewModel.archivedTrips.isEmpty {
                Section("Archived") {
                    ForEach(viewModel.archivedTrips, id: \.id) { trip in
                        NavigationLink {
                            TripDetailView(trip: trip, container: container)
                        } label: {
                            TripRowView(trip: trip, isArchived: true)
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
    }
}

// MARK: - Trip row
struct TripRowView: View {
    let trip: Trip
    var isArchived: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            HStack {
                Text(trip.name)
                    .font(.headline)
                    .foregroundStyle(isArchived ? .secondary : .primary)
                Spacer()
                Text(trip.baseCurrency)
                    .font(.caption.bold())
                    .foregroundStyle(.tint)
            }
            if let dest = trip.destination {
                Text(dest)
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
            if let start = trip.startDate, let end = trip.endDate {
                Text("\(start.toString()) – \(end.toString())")
                    .font(.caption)
                    .foregroundStyle(.tertiary)
            }
        }
        .padding(.vertical, 2)
    }
}

// MARK: - Create trip sheet
struct CreateTripSheet: View {
    @ObservedObject var viewModel: TripViewModel
    @Environment(\.dismiss) private var dismiss

    @State private var name: String = ""
    @State private var destination: String = ""
    @State private var currency: String = "USD"

    var body: some View {
        NavigationStack {
            Form {
                Section("Trip Details") {
                    TextField("Trip name", text: $name)
                    TextField("Destination (optional)", text: $destination)
                }
                Section("Currency") {
                    TextField("Base currency (ISO 4217)", text: $currency)
                        .textInputAutocapitalization(.characters)
                        .onChange(of: currency) { newVal in
                            currency = String(newVal.uppercased().prefix(3))
                        }
                }
            }
            .navigationTitle("New Trip")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancel") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Create") {
                        viewModel.createTrip(
                            name: name.trimmingCharacters(in: .whitespaces),
                            destination: destination.isEmpty ? nil : destination,
                            baseCurrency: currency,
                            startDate: nil,
                            endDate: nil
                        )
                        dismiss()
                    }
                    .disabled(name.isEmpty || currency.count != 3)
                }
            }
        }
    }
}
