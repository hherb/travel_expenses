package com.travelexpenses.android.ui.expenses

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.travelexpenses.model.Category
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEntryScreen(
    tripId: String?,
    expenseId: String?,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    viewModel: ExpenseViewModel = koinViewModel(),
) {
    val formState by viewModel.formState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val validationErrors by viewModel.validationErrors.collectAsState()

    LaunchedEffect(tripId, expenseId) {
        when {
            expenseId != null -> viewModel.initForEdit(expenseId)
            tripId != null -> viewModel.initForTrip(tripId)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.saveComplete.collect { success ->
            if (success) onSaved()
        }
    }

    val isEdit = expenseId != null

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEdit) "Edit Expense" else "New Expense") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.save() }) {
                        Icon(Icons.Default.Save, contentDescription = "Save")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Amount + Currency row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = formState.amount,
                    onValueChange = { viewModel.updateAmount(it) },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    isError = validationErrors.any {
                        it == com.travelexpenses.validation.ValidationError.INVALID_AMOUNT ||
                            it == com.travelexpenses.validation.ValidationError.NON_POSITIVE_AMOUNT
                    },
                    modifier = Modifier.weight(2f),
                )
                OutlinedTextField(
                    value = formState.currency,
                    onValueChange = { viewModel.updateCurrency(it.uppercase().take(3)) },
                    label = { Text("Currency") },
                    singleLine = true,
                    isError = validationErrors.any {
                        it == com.travelexpenses.validation.ValidationError.INVALID_CURRENCY
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            // Category picker
            CategoryDropdown(
                categories = categories,
                selectedCategoryId = formState.categoryId,
                onCategorySelected = { viewModel.updateCategory(it) },
            )

            // Vendor
            OutlinedTextField(
                value = formState.vendor,
                onValueChange = { viewModel.updateVendor(it) },
                label = { Text("Vendor (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Date
            OutlinedTextField(
                value = formState.date.toString(),
                onValueChange = { /* Date picker would be used in production */ },
                label = { Text("Date") },
                singleLine = true,
                readOnly = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Tax amount
            OutlinedTextField(
                value = formState.taxAmount,
                onValueChange = { viewModel.updateTaxAmount(it) },
                label = { Text("Tax amount (optional)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            // Notes
            OutlinedTextField(
                value = formState.notes,
                onValueChange = { viewModel.updateNotes(it) },
                label = { Text("Notes (optional)") },
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )

            // OCR confidence indicator
            if (formState.ocrConfidence != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            "Scanned from receipt",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            "Confidence: ${(formState.ocrConfidence!! * 100).toInt()}%",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }

            // Validation errors
            if (validationErrors.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        validationErrors.forEach { error ->
                            Text(
                                text = error.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = { viewModel.save() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isEdit) "Update Expense" else "Save Expense")
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDropdown(
    categories: List<Category>,
    selectedCategoryId: String,
    onCategorySelected: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedCategory = categories.find { it.id == selectedCategoryId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = selectedCategory?.name ?: "Select category",
            onValueChange = {},
            readOnly = true,
            label = { Text("Category") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text("${category.icon} ${category.name}") },
                    onClick = {
                        onCategorySelected(category.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
