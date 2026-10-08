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
import com.savemymoney.app.data.Payment
import com.savemymoney.app.data.label
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Adds a payment by hand (cash, another card…) or fixes one the app read wrong. */
@Composable
fun PaymentDialog(
    initial: Payment?,
    month: YearMonth,
    defaultCurrency: String,
    onDismiss: () -> Unit,
    onSave: (amountCents: Long, currency: String, merchant: String, timeMillis: Long) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val zone = ZoneId.systemDefault()
    val initialTime = initial?.let { Instant.ofEpochMilli(it.timeMillis).atZone(zone) }
    val targetMonth = initialTime?.let { YearMonth.from(it) } ?: month
    val today = LocalDate.now()
    val defaultDay = initialTime?.dayOfMonth ?: if (YearMonth.from(today) == targetMonth) today.dayOfMonth else 1

    var amount by remember { mutableStateOf(initial?.let { Money.plain(it.amountCents) } ?: "") }
    var currency by remember { mutableStateOf(initial?.currency ?: defaultCurrency) }
    var merchant by remember { mutableStateOf(initial?.merchant ?: "") }
    var day by remember { mutableStateOf(defaultDay.toString()) }

    val cents = Money.parseInput(amount)
    val currencyOk = Currencies.isValid(currency)
    val dayNumber = day.toIntOrNull()?.takeIf { it in 1..targetMonth.lengthOfMonth() }
    // The time matters for the bank balance: payments after the balance was typed are subtracted from it.
    var timeText by remember {
        mutableStateOf((initialTime?.toLocalTime() ?: LocalTime.now()).format(DateTimeFormatter.ofPattern("HH:mm")))
    }
    val clock = parseClock(timeText)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add a payment" else "Edit payment") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = amount,
                    onValueChange = { amount = it },
                    label = { Text("Amount") },
                    supportingText = { Text("Put a minus sign in front of a refund") },
                    isError = amount.isNotBlank() && cents == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                OutlinedTextField(
                    value = currency,
                    onValueChange = { currency = it.uppercase().take(3) },
                    label = { Text("Currency, e.g. ILS or USD") },
                    isError = !currencyOk,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                )
                OutlinedTextField(
                    value = merchant,
                    onValueChange = { merchant = it },
                    label = { Text("Where / what for") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                OutlinedTextField(
                    value = day,
                    onValueChange = { day = it.filter(Char::isDigit).take(2) },
                    label = { Text("Day of ${targetMonth.label()}") },
                    isError = dayNumber == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
                OutlinedTextField(
                    value = timeText,
                    onValueChange = { timeText = it.filter { c -> c.isDigit() || c == ':' }.take(5) },
                    label = { Text("Time, e.g. 14:30") },
                    isError = clock == null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = cents != null && cents != 0L && currencyOk && dayNumber != null && clock != null,
                onClick = {
                    if (cents != null && dayNumber != null && clock != null) {
                        val millis = targetMonth.atDay(dayNumber).atTime(clock).atZone(zone).toInstant().toEpochMilli()
                        onSave(cents, currency, merchant.trim().ifEmpty { "Payment" }, millis)
                    }
                },
            ) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (onDelete != null) {
                    TextButton(onClick = onDelete) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}

/** "14:30", "9:05", or "1430" (the number keypad may have no colon). */
private fun parseClock(text: String): LocalTime? {
    val t = text.trim()
    val withColon = if (':' !in t && t.length in 3..4) t.dropLast(2) + ":" + t.takeLast(2) else t
    return runCatching { LocalTime.parse(withColon, DateTimeFormatter.ofPattern("H:mm")) }.getOrNull()
}
