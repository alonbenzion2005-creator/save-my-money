package com.savemymoney.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.savemymoney.app.data.LoggedNotification
import com.savemymoney.app.data.PaymentStore
import com.savemymoney.app.service.BitApp
import com.savemymoney.app.service.WalletApps
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val timeFormat = DateTimeFormatter.ofPattern("d MMM, HH:mm", Locale.getDefault())

/** The notifications the app saw and what it made of each — for when a payment goes missing. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PaymentStore.get(context) }
    val version by store.changes.collectAsState()
    val entries by produceState(emptyList<LoggedNotification>(), version) {
        value = withContext(Dispatchers.IO) { store.recentNotifications() }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Payment notifications") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (entries.isNotEmpty()) TextButton(onClick = { store.clearLog() }) { Text("Clear") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 16.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "The latest notifications from Google Wallet and bit, and what Save My Money did with each. " +
                        "If a payment is missing from your total, look for it here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (entries.isEmpty()) {
                item {
                    Text(
                        "Nothing yet. Pay with Google Wallet or send money with bit, and the notification will appear here.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(entries) { entry -> LogEntry(entry) }
        }
    }
}

@Composable
private fun LogEntry(entry: LoggedNotification) {
    val time = Instant.ofEpochMilli(entry.timeMillis).atZone(ZoneId.systemDefault()).format(timeFormat)
    val app = when (entry.packageName) {
        WalletApps.GOOGLE_WALLET -> "Google Wallet"
        WalletApps.PLAY_SERVICES -> "Google Play services"
        BitApp.PACKAGE -> "bit"
        else -> "SMS from bit"
    }
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text("$time · $app", style = MaterialTheme.typography.labelMedium)
            entry.title?.let { Text(it, style = MaterialTheme.typography.titleSmall) }
            entry.body?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
            Text(
                entry.result,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
