package com.savemymoney.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.savemymoney.app.data.Currencies
import com.savemymoney.app.data.Money

/** Asks for the balance the bank app shows right now. */
@Composable
fun BalanceDialog(
    currentCurrency: String,
    onDismiss: () -> Unit,
    onSave: (cents: Long, currency: String) -> Unit,
    onRemove: (() -> Unit)?,
) {
    // Starts empty: the point is to type in today's number, not to edit the old one.
    var amount by remember { mutableStateOf("") }
    var currency by remember { mutableStateOf(currentCurrency) }
    val cents = Money.parseInput(amount)
    val currencyOk = Currencies.isValid(currency)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bank balance") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Type in what your bank app shows right now. Save My Money will subtract what you " +
                        "spend from now on.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Balance") },
                    supportingText = { Text("Put a minus sign in front if you're overdrawn") },
                    isError = amount.isNotBlank() && cents == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = currency,
                    onValueChange = { currency = it.uppercase().take(3) },
                    label = { Text("Currency, e.g. ILS") },
                    isError = !currencyOk,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = cents != null && currencyOk,
                onClick = { if (cents != null) onSave(cents, currency) },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onRemove != null) {
                    TextButton(onClick = onRemove) {
                        Text("Remove", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
