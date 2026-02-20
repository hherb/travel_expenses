package com.travelexpenses.android.ui.expenses

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.travelexpenses.model.Category
import com.travelexpenses.model.Tag
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.toLocalDateTime
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseEntryScreen(
    tripId: String?,
    expenseId: String?,
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    ocrVendor: String? = null,
    ocrAmount: String? = null,
    ocrCurrency: String? = null,
    ocrDate: String? = null,
    ocrTaxAmount: String? = null,
    viewModel: ExpenseViewModel = koinViewModel(),
) {
    val formState by viewModel.formState.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val validationErrors by viewModel.validationErrors.collectAsState()
    val vendorSuggestions by viewModel.vendorSuggestions.collectAsState()
    val allTags by viewModel.allTags.collectAsState()
    var showDatePicker by remember { mutableStateOf(false) }
    var showTagPicker by remember { mutableStateOf(false) }

    LaunchedEffect(tripId, expenseId) {
        when {
            expenseId != null -> viewModel.initForEdit(expenseId)
            ocrAmount != null && tripId != null -> viewModel.initFromOcr(
                tripId = tripId,
                amount = ocrAmount.ifEmpty { null },
                currency = ocrCurrency?.ifEmpty { null },
                vendor = ocrVendor?.ifEmpty { null },
                date = null,
                taxAmount = ocrTaxAmount?.ifEmpty { null },
                ocrConfidence = null,
            )
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

            // Vendor with autocomplete
            VendorAutocompleteField(
                value = formState.vendor,
                onValueChange = { viewModel.updateVendor(it) },
                suggestions = vendorSuggestions,
            )

            // Date with picker — Box overlay makes the entire field tappable
            Box {
                OutlinedTextField(
                    value = formState.date.toString(),
                    onValueChange = {},
                    label = { Text("Date") },
                    singleLine = true,
                    readOnly = true,
                    trailingIcon = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Pick date")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                // Transparent overlay to intercept taps on the read-only field
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clickable { showDatePicker = true },
                )
            }

            // Tags
            TagSection(
                selectedTagIds = formState.tags,
                allTags = allTags,
                onShowTagPicker = { showTagPicker = true },
                onRemoveTag = { viewModel.removeTag(it) },
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

    // Date picker dialog
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = formState.date
                .atStartOfDayIn(TimeZone.UTC)
                .toEpochMilliseconds()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val instant = Instant.fromEpochMilliseconds(millis)
                        val localDate = instant.toLocalDateTime(TimeZone.UTC).date
                        viewModel.updateDate(localDate)
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            },
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // Tag picker dialog
    if (showTagPicker) {
        TagPickerDialog(
            allTags = allTags,
            selectedTagIds = formState.tags,
            onTagToggle = { tagId, selected ->
                if (selected) viewModel.addTag(tagId) else viewModel.removeTag(tagId)
            },
            onCreateTag = { label -> viewModel.createTag(label) },
            onDismiss = { showTagPicker = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VendorAutocompleteField(
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>,
) {
    var expanded by remember { mutableStateOf(false) }
    val filteredSuggestions = if (value.isNotEmpty()) {
        suggestions.filter { it.contains(value, ignoreCase = true) && !it.equals(value, ignoreCase = true) }
    } else {
        emptyList()
    }

    ExposedDropdownMenuBox(
        expanded = expanded && filteredSuggestions.isNotEmpty(),
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text("Vendor (optional)") },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(),
        )
        if (filteredSuggestions.isNotEmpty()) {
            ExposedDropdownMenu(
                expanded = expanded && filteredSuggestions.isNotEmpty(),
                onDismissRequest = { expanded = false },
            ) {
                filteredSuggestions.take(5).forEach { suggestion ->
                    DropdownMenuItem(
                        text = { Text(suggestion) },
                        onClick = {
                            onValueChange(suggestion)
                            expanded = false
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun TagSection(
    selectedTagIds: List<String>,
    allTags: List<Tag>,
    onShowTagPicker: () -> Unit,
    onRemoveTag: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Tags",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = onShowTagPicker) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add tags")
            }
        }
        if (selectedTagIds.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                selectedTagIds.forEach { tagId ->
                    val tag = allTags.find { it.id == tagId }
                    if (tag != null) {
                        InputChip(
                            selected = true,
                            onClick = { onRemoveTag(tagId) },
                            label = { Text(tag.label) },
                            trailingIcon = {
                                Icon(Icons.Default.Close, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                            },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TagPickerDialog(
    allTags: List<Tag>,
    selectedTagIds: List<String>,
    onTagToggle: (String, Boolean) -> Unit,
    onCreateTag: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var newTagLabel by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Tags") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                // Inline tag creation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = newTagLabel,
                        onValueChange = { newTagLabel = it },
                        label = { Text("New tag") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = {
                            if (newTagLabel.isNotBlank()) {
                                onCreateTag(newTagLabel.trim())
                                newTagLabel = ""
                            }
                        },
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create tag")
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Existing tags as checkboxes
                allTags.forEach { tag ->
                    val selected = tag.id in selectedTagIds
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onTagToggle(tag.id, !selected) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = selected,
                            onCheckedChange = { onTagToggle(tag.id, it) },
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(tag.label)
                    }
                }

                if (allTags.isEmpty()) {
                    Text(
                        "No tags yet. Create one above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
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
