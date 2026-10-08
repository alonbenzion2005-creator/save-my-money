package com.savemymoney.app.data

import android.content.Context
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs

/** What was spent in one month, ready to show. */
data class MonthSpending(
    val month: YearMonth,
    val payments: List<Payment>,
    val mainCurrency: String,
    val mainTotalCents: Long,
    /** Totals in any other currencies, largest first. */
    val otherTotals: List<Pair<String, Long>>,
    val budgetCents: Long,
) {
    val isCurrentMonth: Boolean get() = month == YearMonth.now()

    val mainTotal: String get() = Money.format(mainTotalCents, mainCurrency)

    val otherTotalsLine: String
        get() = otherTotals.joinToString(" · ") { (currency, cents) -> "+ " + Money.format(cents, currency) }

    val countLine: String get() = if (payments.size == 1) "1 payment" else "${payments.size} payments"

    val hasBudget: Boolean get() = budgetCents > 0

    val overBudget: Boolean get() = hasBudget && mainTotalCents > budgetCents

    val budgetFraction: Float
        get() = if (hasBudget) (mainTotalCents.toFloat() / budgetCents).coerceIn(0f, 1f) else 0f

    val budgetLine: String
        get() {
            val budget = Money.format(budgetCents, mainCurrency)
            val difference = Money.format(abs(budgetCents - mainTotalCents), mainCurrency)
            return if (overBudget) "$difference over your $budget budget" else "$difference left of $budget"
        }

    companion object {
        fun load(context: Context, month: YearMonth): MonthSpending {
            val zone = ZoneId.systemDefault()
            val from = month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val until = month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
            val payments = PaymentStore.get(context).paymentsBetween(from, until)
            val main = Currencies.main(context)
            val totals = payments.groupBy { it.currency }.mapValues { (_, list) -> list.sumOf { it.amountCents } }
            return MonthSpending(
                month = month,
                payments = payments,
                mainCurrency = main,
                mainTotalCents = totals[main] ?: 0L,
                otherTotals = totals.filterKeys { it != main }.toList().sortedByDescending { abs(it.second) },
                budgetCents = Prefs(context).budgetCents,
            )
        }
    }
}

fun YearMonth.label(): String =
    month.getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + year

fun YearMonth.monthName(): String = month.getDisplayName(TextStyle.FULL, Locale.getDefault())
