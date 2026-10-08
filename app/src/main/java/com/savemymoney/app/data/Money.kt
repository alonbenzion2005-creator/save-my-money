package com.savemymoney.app.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object Money {
    private val THOUSANDS = Regex("""[-+]?\d{1,3}(,\d{3})+""")

    fun format(cents: Long, currencyCode: String): String {
        val value = BigDecimal.valueOf(cents, 2)
        if (!Currencies.isValid(currencyCode)) return "$currencyCode ${value.toPlainString()}"
        val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
        format.currency = Currency.getInstance(currencyCode)
        return format.format(value)
    }

    /** An amount for a text field: 12.5 rather than 12.50, and no currency symbol. */
    fun plain(cents: Long): String = BigDecimal.valueOf(cents, 2).stripTrailingZeros().toPlainString()

    /** Reads an amount the user typed ("12.50", "12,5", "5,000", "-3") into cents. */
    fun parseInput(text: String): Long? {
        var t = text.trim().replace(" ", "")
        if (t.isEmpty()) return null
        t = when {
            '.' in t && ',' in t -> t.replace(",", "")
            // Commas grouping thousands ("5,000", "1,234,567"): a bank balance is usually typed like this.
            THOUSANDS.matches(t) -> t.replace(",", "")
            // Otherwise a comma is the decimal point ("12,5").
            else -> t.replace(',', '.')
        }
        return try {
            BigDecimal(t).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (e: NumberFormatException) {
            null
        } catch (e: ArithmeticException) {
            null
        }
    }
}
