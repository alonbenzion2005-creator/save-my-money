package com.savemymoney.app.data

import android.content.Context
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The bank balance the user typed in, minus what they've spent since then.
 *
 * It's an estimate: it only knows about payments the app recorded, and credit-card
 * purchases usually leave the bank account later, when the card bill is paid.
 */
data class BalanceEstimate(
    val entered: EnteredBalance,
    /** Spent in the balance's currency since it was typed in. */
    val spentSinceCents: Long,
) {
    val estimateCents: Long get() = entered.cents - spentSinceCents

    val estimate: String get() = Money.format(estimateCents, entered.currency)

    /** True once it's been [STALE_AFTER] since the balance was typed in. */
    val isStale: Boolean
        get() = Duration.between(Instant.ofEpochMilli(entered.timeMillis), Instant.now()) >= STALE_AFTER

    /** "₪5,000.00 on Tue 6 Oct − ₪420.00 spent since", or just the first part if nothing was spent. */
    val explanation: String
        get() {
            val typed = Money.format(entered.cents, entered.currency) + " on " + enteredWhen
            if (spentSinceCents == 0L) return "$typed, nothing spent since"
            return "$typed − ${Money.format(spentSinceCents, entered.currency)} spent since"
        }

    /** "today 14:05", "yesterday 09:30" or "Tue 6 Oct". */
    val enteredWhen: String
        get() {
            val zone = ZoneId.systemDefault()
            val time = Instant.ofEpochMilli(entered.timeMillis).atZone(zone)
            val today = LocalDate.now(zone)
            val clock = time.format(DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()))
            return when (time.toLocalDate()) {
                today -> "today $clock"
                today.minusDays(1) -> "yesterday $clock"
                else -> time.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
            }
        }

    companion object {
        /** The user plans to type the balance in every couple of days. */
        val STALE_AFTER: Duration = Duration.ofDays(2)

        fun load(context: Context): BalanceEstimate? {
            val entered = Prefs(context).enteredBalance ?: return null
            val spent = PaymentStore.get(context).spentSince(entered.currency, entered.timeMillis)
            return BalanceEstimate(entered, spent)
        }

        /** Saves a balance the user just typed in (or clears it with null) and updates every screen. */
        fun save(context: Context, cents: Long?, currency: String) {
            Prefs(context).enteredBalance = cents?.let { EnteredBalance(it, currency, System.currentTimeMillis()) }
            PaymentStore.get(context).changed()
        }
    }
}
