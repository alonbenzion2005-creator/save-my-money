package com.savemymoney.app.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

object Money {
    fun format(cents: Long, currencyCode: String): String {
        val value = BigDecimal.valueOf(cents, 2)
        if (!Currencies.isValid(currencyCode)) return "$currencyCode ${value.toPlainString()}"
        val format = NumberFormat.getCurrencyInstance(Locale.getDefault())
        format.currency = Currency.getInstance(currencyCode)
        return format.format(value)
    }

    /** An amount for a text field: 12.5 rather than 12.50, and no currency symbol. */
    fun plain(cents: Long): String = BigDecimal.valueOf(cents, 2).stripTrailingZeros().toPlainString()

    /** Reads an amount the user typed ("12.50", "12,5", "-3") into cents. */
    fun parseInput(text: String): Long? {
        var t = text.trim().replace(" ", "")
        if (t.isEmpty()) return null
        t = if ('.' in t && ',' in t) t.replace(",", "") else t.replace(',', '.')
        return try {
            BigDecimal(t).movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
        } catch (e: NumberFormatException) {
            null
        } catch (e: ArithmeticException) {
            null
        }
    }
}
