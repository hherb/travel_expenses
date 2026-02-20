package com.travelexpenses.android.ui.reports

import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.koin.androidx.compose.koinViewModel

// Chart color palette
private val chartColors = listOf(
    Color(0xFF4285F4), // Blue
    Color(0xFFEA4335), // Red
    Color(0xFFFBBC04), // Yellow
    Color(0xFF34A853), // Green
    Color(0xFFFF6D01), // Orange
    Color(0xFF46BDC6), // Teal
    Color(0xFF7B1FA2), // Purple
    Color(0xFFE91E63), // Pink
    Color(0xFF795548), // Brown
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    viewModel: ReportsViewModel = koinViewModel(),
) {
    val trips by viewModel.trips.collectAsState()
    val selectedTripId by viewModel.selectedTripId.collectAsState()
    val report by viewModel.report.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.csvOutput.collect { csv ->
            val sendIntent = Intent().apply {
                action = Intent.ACTION_SEND
                putExtra(Intent.EXTRA_TEXT, csv)
                type = "text/csv"
            }
            context.startActivity(Intent.createChooser(sendIntent, "Export CSV"))
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Reports") },
                actions = {
                    if (report != null) {
                        IconButton(onClick = { viewModel.exportCsv() }) {
                            Icon(Icons.Default.Share, contentDescription = "Export CSV")
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Trip selector
            item {
                TripSelector(
                    trips = trips,
                    selectedTripId = selectedTripId,
                    onTripSelected = { viewModel.selectTrip(it) },
                )
            }

            if (isLoading) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            }

            report?.let { tripReport ->
                // Summary card
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                        ),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Total: ${tripReport.totalAmount}",
                                style = MaterialTheme.typography.headlineMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${tripReport.expenseCount} expenses",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = "Daily average: ${tripReport.dailyAverage}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }

                // Pie chart for category distribution
                if (tripReport.categoryBreakdown.isNotEmpty()) {
                    item {
                        Text(
                            "Category Distribution",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    item {
                        CategoryPieChart(
                            breakdown = tripReport.categoryBreakdown,
                        )
                    }

                    // Category breakdown list
                    item {
                        Text(
                            "By Category",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }

                    itemsIndexed(tripReport.categoryBreakdown) { index, breakdown ->
                        CategoryBreakdownItem(
                            breakdown = breakdown,
                            color = chartColors[index % chartColors.size],
                        )
                    }
                }
            }

            if (report == null && !isLoading && trips.isNotEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "Select a trip to view reports",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (trips.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            "No trips to report on",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryPieChart(
    breakdown: List<CategoryBreakdown>,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Pie chart
            Canvas(
                modifier = Modifier.size(140.dp),
            ) {
                var startAngle = -90f
                breakdown.forEachIndexed { index, item ->
                    val sweepAngle = item.percentage / 100f * 360f
                    val color = chartColors[index % chartColors.size]
                    drawArc(
                        color = color,
                        startAngle = startAngle,
                        sweepAngle = sweepAngle,
                        useCenter = true,
                        size = Size(size.width, size.height),
                    )
                    startAngle += sweepAngle
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            // Legend
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f),
            ) {
                breakdown.forEachIndexed { index, item ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Canvas(modifier = Modifier.size(12.dp)) {
                            drawCircle(color = chartColors[index % chartColors.size])
                        }
                        Text(
                            text = item.categoryName,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = "${"%.0f".format(item.percentage)}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TripSelector(
    trips: List<com.travelexpenses.model.Trip>,
    selectedTripId: String?,
    onTripSelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedTrip = trips.find { it.id == selectedTripId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedTrip?.name ?: "Select a trip",
            onValueChange = {},
            readOnly = true,
            label = { Text("Trip") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            trips.forEach { trip ->
                DropdownMenuItem(
                    text = { Text(trip.name) },
                    onClick = {
                        onTripSelected(trip.id)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun CategoryBreakdownItem(
    breakdown: CategoryBreakdown,
    color: Color = MaterialTheme.colorScheme.primary,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Canvas(modifier = Modifier.size(12.dp)) {
                        drawCircle(color = color)
                    }
                    Text(
                        text = breakdown.categoryName,
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                Text(
                    text = breakdown.total,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            LinearProgressIndicator(
                progress = { breakdown.percentage / 100f },
                modifier = Modifier.fillMaxWidth(),
                color = color,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "${breakdown.count} expense${if (breakdown.count != 1) "s" else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "${"%.1f".format(breakdown.percentage)}%",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
