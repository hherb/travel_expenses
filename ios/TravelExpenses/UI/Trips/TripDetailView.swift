import SwiftUI
import Shared

struct TripDetailView: View {

    let trip: Trip
    @EnvironmentObject private var container: ServiceContainer
    @StateObject private var viewModel: TripViewModel

    @State private var showAddExpense = false
    @State private var showCapture = false
    @State private var showAddChoiceAlert = false
    @State private var expenseToEdit: String?

    init(trip: Trip, container: ServiceContainer) {
        self.trip = trip
        _viewModel = StateObject(wrappedValue: TripViewModel(container: container))
    }

    var body: some View {
        List {
            // Summary card
            Section {
                summaryCard
            }

            // Expense list
            Section(header: Text("Expenses")) {
                if viewModel.tripExpenses.isEmpty {
                    Text("No expenses yet.")
                        .foregroundStyle(.secondary)
                        .font(.subheadline)
                } else {
                    ForEach(viewModel.tripExpenses, id: \.id) { expense in
                        NavigationLink {
                            ExpenseEntryView(
                                tripId: trip.id,
                                expenseId: expense.id,
                                container: container
                            )
                        } label: {
                            ExpenseRowView(expense: expense)
                        }
                        .swipeActions(edge: .trailing) {
                            Button(role: .destructive) {
                                viewModel.deleteExpense(expense.id)
                            } label: {
                                Label("Delete", systemImage: "trash")
                            }
                        }
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
        .navigationTitle(trip.name)
        .navigationBarTitleDisplayMode(.large)
        .toolbar {
            ToolbarItem(placement: .primaryAction) {
                Button {
                    showAddChoiceAlert = true
                } label: {
                    Image(systemName: "plus")
                }
            }
        }
        .confirmationDialog("Add Expense", isPresented: $showAddChoiceAlert, titleVisibility: .visible) {
            Button("Scan Receipt") { showCapture = true }
            Button("Enter Manually") { showAddExpense = true }
        }
        .sheet(isPresented: $showAddExpense) {
            NavigationStack {
                ExpenseEntryView(tripId: trip.id, expenseId: nil, container: container)
            }
        }
        .sheet(isPresented: $showCapture) {
            CaptureView(tripId: trip.id, container: container)
        }
        .onAppear {
            viewModel.loadTripDetail(tripId: trip.id)
        }
    }

    private var summaryCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("Total", systemImage: "sum")
                .font(.caption)
                .foregroundStyle(.secondary)
            Text(viewModel.tripTotal)
                .font(.title.bold())
                .foregroundStyle(.primary)

            if let dest = trip.destination {
                Divider()
                Label(dest, systemImage: "mappin.circle")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
        .padding(.vertical, 4)
    }
}

// MARK: - Expense row
struct ExpenseRowView: View {
    let expense: Expense

    var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                Text(expense.vendor ?? "Unknown vendor")
                    .font(.headline)
                Text(expense.date.description())
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text("\(expense.amount) \(expense.currency)")
                    .font(.subheadline.bold())
                    .foregroundStyle(.primary)
                if let tax = expense.taxAmount {
                    Text("Tax: \(tax)")
                        .font(.caption2)
                        .foregroundStyle(.tertiary)
                }
            }
        }
    }
}
