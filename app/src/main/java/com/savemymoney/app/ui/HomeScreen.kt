package com.savemymoney.app.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.savemymoney.app.data.BalanceEstimate
import com.savemymoney.app.data.MonthSpending
import com.savemymoney.app.data.Payment
import com.savemymoney.app.data.PaymentStore
import com.savemymoney.app.data.Money
import com.savemymoney.app.data.label
import com.savemymoney.app.service.WalletWatcherService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(resumeCount: Int, onOpenSettings: () -> Unit, onOpenLog: () -> Unit) {
    val context = LocalContext.current
    val store = remember { PaymentStore.get(context) }
    val version by store.changes.collectAsState()
    var month by rememberSaveable { mutableStateOf(YearMonth.now()) }
    val spending by produceState<MonthSpending?>(null, month, version, resumeCount) {
        value = withContext(Dispatchers.IO) { MonthSpending.load(context, month) }
    }
    val hasNotificationAccess = remember(resumeCount) { SystemScreens.hasNotificationAccess(context) }
    val hasWalletWatcher = remember(resumeCount) { SystemScreens.hasWalletWatcher(context) }
    var adding by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Payment?>(null) }
    val balance by produceState<BalanceState?>(null, version, resumeCount) {
        value = BalanceState(withContext(Dispatchers.IO) { BalanceEstimate.load(context) })
    }
    var editingBalance by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Save My Money") },
                actions = {
                    IconButton(onClick = onOpenLog) {
                        Icon(Icons.Default.Notifications, contentDescription = "Wallet notifications")
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add") },
            )
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!hasNotificationAccess || !hasWalletWatcher) {
                item { SetupCard(hasNotificationAccess, hasWalletWatcher) }
            }
            item { MonthPicker(month, onChange = { month = it }) }
            val current = spending
            if (current != null) {
                item { TotalCard(current, canPreview = hasWalletWatcher) }
                val loadedBalance = balance
                if (current.isCurrentMonth && loadedBalance != null) {
                    item { BalanceCard(loadedBalance.estimate, onUpdate = { editingBalance = true }) }
                }
                if (current.payments.isEmpty()) {
                    item {
                        Text(
                            "No payments yet. Pay with Google Wallet and they’ll show up here, " +
                                "or tap Add for anything you paid another way.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
                items(current.payments, key = { it.id }) { payment ->
                    PaymentRow(payment, onClick = { editing = payment })
                }
            }
        }
    }

    if (editingBalance) {
        val estimate = balance?.estimate
        BalanceDialog(
            currentCurrency = estimate?.entered?.currency ?: spending?.mainCurrency ?: "USD",
            onDismiss = { editingBalance = false },
            onSave = { cents, currency ->
                BalanceEstimate.save(context, cents, currency)
                editingBalance = false
            },
            onRemove = estimate?.let { e ->
                {
                    BalanceEstimate.save(context, null, e.entered.currency)
                    editingBalance = false
                }
            },
        )
    }

    val beingEdited = editing
    if (adding || beingEdited != null) {
        PaymentDialog(
            initial = beingEdited,
            month = month,
            defaultCurrency = spending?.mainCurrency ?: "USD",
            onDismiss = {
                adding = false
                editing = null
            },
            onSave = { cents, currency, merchant, time ->
                if (beingEdited == null) {
                    store.addManual(cents, currency, merchant, time)
                } else {
                    store.update(beingEdited.id, cents, currency, merchant, time)
                }
                adding = false
                editing = null
            },
            onDelete = beingEdited?.let { payment ->
                {
                    store.delete(payment.id)
                    editing = null
                }
            },
        )
    }
}

@Composable
private fun SetupCard(hasNotificationAccess: Boolean, hasWalletWatcher: Boolean) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Text("Two switches to turn on", style = MaterialTheme.typography.titleMedium)
            SetupStep(
                done = hasNotificationAccess,
                title = "1. Read Google Wallet payment notifications",
                body = "This is how the app learns what you paid. Turn on “Save My Money – Wallet payments”.",
                action = "Allow notification access",
                onAction = { SystemScreens.openNotificationAccess(context) },
            )
            SetupStep(
                done = hasWalletWatcher,
                title = "2. Show your total when Wallet opens",
                body = "In Accessibility, open “Save My Money – Wallet spending” (under Downloaded apps) and turn it on. " +
                    "It only notices when Google Wallet opens; it doesn’t read your screen.",
                action = "Open Accessibility settings",
                onAction = { SystemScreens.openAccessibility(context) },
            )
            Text(
                "Switch greyed out or a “Restricted setting” message? Open App info, tap ⋮ in the top corner, " +
                    "choose “Allow restricted settings”, then try the switch again.",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { SystemScreens.openAppInfo(context) }) { Text("Open App info") }
        }
    }
}

@Composable
private fun SetupStep(done: Boolean, title: String, body: String, action: String, onAction: () -> Unit) {
    Row {
        Icon(
            if (done) Icons.Default.CheckCircle else Icons.Default.Warning,
            contentDescription = if (done) "Done" else "Not done yet",
            tint = if (done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
        )
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (!done) {
                Text(body, style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(onClick = onAction, modifier = Modifier.padding(top = 8.dp)) { Text(action) }
            }
        }
    }
}

@Composable
private fun MonthPicker(month: YearMonth, onChange: (YearMonth) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange(month.minusMonths(1)) }) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
        }
        Text(
            month.label(),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { onChange(month.plusMonths(1)) }, enabled = month < YearMonth.now()) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun TotalCard(spending: MonthSpending, canPreview: Boolean) {
    val context = LocalContext.current
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text(
                if (spending.isCurrentMonth) "Spent this month" else "Spent in ${spending.month.label()}",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                spending.mainTotal,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
            )
            if (spending.otherTotals.isNotEmpty()) {
                Text(spending.otherTotalsLine, style = MaterialTheme.typography.bodyLarge.copy(textDirection = TextDirection.Ltr))
            }
            Text(spending.countLine, style = MaterialTheme.typography.bodyMedium)
            if (spending.hasBudget) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { spending.budgetFraction },
                    modifier = Modifier.fillMaxWidth(),
                    color = if (spending.overBudget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.height(4.dp))
                Text(spending.budgetLine, style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr))
            }
            if (canPreview && spending.isCurrentMonth) {
                TextButton(
                    onClick = {
                        if (!WalletWatcherService.preview()) {
                            Toast.makeText(context, "Turn on the Accessibility switch first", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.padding(top = 4.dp),
                ) { Text("Preview what Wallet will show") }
            }
        }
    }
}

/** Wraps the estimate so "loaded, but no balance entered" differs from "still loading". */
private class BalanceState(val estimate: BalanceEstimate?)

@Composable
private fun BalanceCard(estimate: BalanceEstimate?, onUpdate: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            if (estimate == null) {
                Text("Bank balance", style = MaterialTheme.typography.labelLarge)
                Text(
                    "Type in your bank balance every couple of days. The app subtracts what you spend " +
                        "after that, so you can see roughly what's left.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                FilledTonalButton(onClick = onUpdate, modifier = Modifier.padding(top = 8.dp)) { Text("Enter balance") }
            } else {
                Text("Bank balance (estimated)", style = MaterialTheme.typography.labelLarge)
                Text(
                    estimate.estimate,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (estimate.estimateCents < 0) MaterialTheme.colorScheme.error else Color.Unspecified,
                )
                Text(
                    estimate.explanation,
                    style = MaterialTheme.typography.bodyMedium.copy(textDirection = TextDirection.Ltr),
                )
                if (estimate.isStale) {
                    Text(
                        "It's been 2 days or more — time to type in a fresh balance.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                FilledTonalButton(onClick = onUpdate, modifier = Modifier.padding(top = 8.dp)) { Text("Update balance") }
            }
        }
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.getDefault())

@Composable
private fun PaymentRow(payment: Payment, onClick: () -> Unit) {
    val time = Instant.ofEpochMilli(payment.timeMillis).atZone(ZoneId.systemDefault()).format(dateFormat)
    val source = if (payment.source == PaymentStore.SOURCE_MANUAL) "added by you" else "Google Wallet"
    ListItem(
        modifier = Modifier.clip(MaterialTheme.shapes.medium).clickable(onClick = onClick),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        headlineContent = { Text(payment.merchant, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = { Text("$time · $source") },
        trailingContent = {
            Text(Money.format(payment.amountCents, payment.currency), style = MaterialTheme.typography.titleMedium)
        },
    )
}
