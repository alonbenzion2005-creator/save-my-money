package com.savemymoney.app.data

import android.content.Context
import java.util.Currency
import java.util.Locale

object Currencies {
    private val codes: Set<String> by lazy {
        Currency.getAvailableCurrencies().mapTo(HashSet()) { it.currencyCode }
    }

    fun isValid(code: String) = code in codes

    /** The currency totals and the budget are shown in. */
    fun main(context: Context): String = Prefs(context).currencyOverride ?: automatic(context)

    /** The currency most payments were in, else the phone's region's currency. */
    fun automatic(context: Context): String =
        PaymentStore.get(context).mostUsedCurrency() ?: localeCurrency() ?: "USD"

    /** What a bare "$" in a notification means: the main currency if it's a dollar, else US dollars. */
    fun dollarCurrency(context: Context): String {
        val main = main(context)
        val symbol = runCatching { Currency.getInstance(main).getSymbol(Locale.US) }.getOrNull()
        return if (symbol != null && '$' in symbol) main else "USD"
    }

    private fun localeCurrency(): String? =
        runCatching { Currency.getInstance(Locale.getDefault())?.currencyCode }.getOrNull()
}
