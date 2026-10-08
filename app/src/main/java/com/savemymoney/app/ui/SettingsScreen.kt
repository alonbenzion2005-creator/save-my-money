package com.savemymoney.app.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.savemymoney.app.data.Currencies
import com.savemymoney.app.data.Money
import com.savemymoney.app.data.Prefs
import com.savemymoney.app.service.WalletWatcherService

private val AUTO_CLOSE_CHOICES = listOf(
    0 to "When I tap “Continue to Wallet”",
    5 to "By itself after 5 seconds",
    10 to "By itself after 10 seconds",
)

@Composable
private fun Choice(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect, role = Role.RadioButton)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    val automaticCurrency = remember { Currencies.automatic(context) }
    var bannerEnabled by remember { mutableStateOf(prefs.bannerEnabled) }
    var fullScreen by remember { mutableStateOf(prefs.fullScreen) }
    var autoClose by remember { mutableIntStateOf(prefs.autoCloseSeconds) }
    var currency by remember { mutableStateOf(prefs.currencyOverride ?: "") }
    var budget by remember { mutableStateOf(prefs.budgetCents.takeIf { it > 0 }?.let(Money::plain) ?: "") }
    val budgetCurrency = currency.takeIf { Currencies.isValid(it) } ?: automaticCurrency

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Show my spending when Google Wallet opens", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Also needs the Accessibility switch from the setup steps.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = bannerEnabled,
                    onCheckedChange = {
                        bannerEnabled = it
                        prefs.bannerEnabled = it
                    },
                )
            }

            if (bannerEnabled) {
                Column {
                    Text("How to show it", style = MaterialTheme.typography.titleSmall)
                    Choice("Full screen, before Wallet", selected = fullScreen) {
                        fullScreen = true
                        prefs.fullScreen = true
                    }
                    Choice("Small banner at the top", selected = !fullScreen) {
                        fullScreen = false
                        prefs.fullScreen = false
                    }
                }
                if (fullScreen) {
                    Column {
                        Text("Go on to Wallet", style = MaterialTheme.typography.titleSmall)
                        for ((seconds, label) in AUTO_CLOSE_CHOICES) {
                            Choice(label, selected = autoClose == seconds) {
                                autoClose = seconds
                                prefs.autoCloseSeconds = seconds
                            }
                        }
                    }
                }
                TextButton(
                    onClick = {
                        if (!WalletWatcherService.preview()) {
                            Toast.makeText(context, "Turn on the Accessibility switch first", Toast.LENGTH_SHORT).show()
                        }
                    },
                ) { Text("Preview") }
            }

            OutlinedTextField(
                value = currency,
                onValueChange = { value ->
                    currency = value.uppercase().take(3)
                    prefs.currencyOverride = currency.takeIf { Currencies.isValid(it) }
                },
                label = { Text("Main currency") },
                placeholder = { Text("Automatic ($automaticCurrency)") },
                supportingText = { Text("Leave empty to use the currency you pay in most.") },
                isError = currency.isNotEmpty() && !Currencies.isValid(currency),
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = budget,
                onValueChange = { value ->
                    budget = value
                    val cents = Money.parseInput(value)
                    when {
                        value.isBlank() -> prefs.budgetCents = 0
                        cents != null && cents > 0 -> prefs.budgetCents = cents
                    }
                },
                label = { Text("Monthly budget ($budgetCurrency)") },
                supportingText = { Text("Optional. The banner shows how much of it is left.") },
                isError = budget.isNotBlank() && (Money.parseInput(budget) ?: 0L) <= 0L,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            HorizontalDivider()

            Text("How it works", style = MaterialTheme.typography.titleSmall)
            Text(
                "Google Wallet doesn’t let other apps read your payments, so Save My Money reads the " +
                    "notification Wallet shows after each tap-to-pay. Payments you made before installing " +
                    "the app, or with Wallet’s notifications turned off, aren’t counted — add those by hand.",
                style = MaterialTheme.typography.bodyMedium,
            )
            Text("Privacy", style = MaterialTheme.typography.titleSmall)
            Text(
                "Everything stays on this phone. The app has no internet access, and it only keeps " +
                    "notifications from Google Wallet and Google Play services.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}
