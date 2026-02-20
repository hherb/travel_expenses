package com.travelexpenses.android.ui.capture

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.travelexpenses.android.ocr.ImagePreprocessor
import com.travelexpenses.ocr.OcrEngine
import com.travelexpenses.ocr.OcrParser
import com.travelexpenses.ocr.OcrResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OcrReviewScreen(
    tripId: String,
    imagePath: String,
    onSaveExpense: (vendor: String, amount: String, currency: String, date: String, taxAmount: String) -> Unit,
    onCancel: () -> Unit,
) {
    val ocrEngine: OcrEngine = koinInject()
    val ocrParser: OcrParser = koinInject()

    var ocrResult by remember { mutableStateOf<OcrResult?>(null) }
    var isProcessing by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Editable fields from OCR
    var vendor by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf("") }
    var date by remember { mutableStateOf("") }
    var taxAmount by remember { mutableStateOf("") }

    LaunchedEffect(imagePath) {
        // Preprocess image for better OCR accuracy (grayscale + contrast)
        val preprocessedPath = withContext(Dispatchers.IO) {
            ImagePreprocessor.preprocess(imagePath)
        } ?: imagePath

        val textResult = ocrEngine.recognizeText(preprocessedPath)
        if (textResult == null) {
            errorMessage = "Failed to recognize text from image"
            isProcessing = false
            return@LaunchedEffect
        }

        val result = ocrParser.parse(textResult.fullText)
        ocrResult = result

        // Populate editable fields
        vendor = result.vendor ?: ""
        amount = result.total ?: ""
        currency = result.currency ?: "USD"
        date = result.date ?: ""
        taxAmount = result.tax ?: ""

        isProcessing = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Review Receipt") },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Cancel")
                    }
                },
                actions = {
                    if (!isProcessing && errorMessage == null) {
                        IconButton(onClick = {
                            onSaveExpense(vendor, amount, currency, date, taxAmount)
                        }) {
                            Icon(Icons.Default.Save, contentDescription = "Save")
                        }
                    }
                }
            )
        }
    ) { padding ->
        if (isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator()
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Processing receipt...")
                }
            }
        } else if (errorMessage != null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "Review extracted data",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "Correct any fields that were not extracted accurately.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Vendor
                OutlinedTextField(
                    value = vendor,
                    onValueChange = { vendor = it },
                    label = { Text("Vendor") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        ocrResult?.vendorConfidence?.let {
                            ConfidenceLabel(it)
                        }
                    }
                )

                // Amount + Currency
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = amount,
                        onValueChange = { amount = it },
                        label = { Text("Total") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.weight(2f),
                        supportingText = {
                            ocrResult?.totalConfidence?.let {
                                ConfidenceLabel(it)
                            }
                        }
                    )
                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it.uppercase().take(3) },
                        label = { Text("Currency") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        supportingText = {
                            ocrResult?.currencyConfidence?.let {
                                ConfidenceLabel(it)
                            }
                        }
                    )
                }

                // Date
                OutlinedTextField(
                    value = date,
                    onValueChange = { date = it },
                    label = { Text("Date") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        ocrResult?.dateConfidence?.let {
                            ConfidenceLabel(it)
                        }
                    }
                )

                // Tax
                OutlinedTextField(
                    value = taxAmount,
                    onValueChange = { taxAmount = it },
                    label = { Text("Tax amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    supportingText = {
                        ocrResult?.taxConfidence?.let {
                            ConfidenceLabel(it)
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = {
                        onSaveExpense(vendor, amount, currency, date, taxAmount)
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Save Expense")
                }

                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun ConfidenceLabel(confidence: Float) {
    val level = when {
        confidence >= 0.8f -> "High"
        confidence >= 0.5f -> "Medium"
        confidence > 0f -> "Low"
        else -> "Not found"
    }
    val color = when {
        confidence >= 0.8f -> MaterialTheme.colorScheme.primary
        confidence >= 0.5f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }
    Text(
        text = "Confidence: $level (${(confidence * 100).toInt()}%)",
        style = MaterialTheme.typography.bodySmall,
        color = color,
    )
}
