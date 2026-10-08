package com.savemymoney.app.parse

import java.util.Currency
import java.util.Locale
import kotlin.math.abs

/** A payment read out of a notification. [amountCents] is negative for refunds. */
data class ParsedPayment(
    val amountCents: Long,
    val currency: String,
    val merchant: String?,
)

/**
 * Pulls the amount, currency and merchant out of a payment notification.
 *
 * Google Wallet doesn't document its notification text and it changes with the
 * phone's language, so rather than matching one exact sentence this looks for
 * a number written next to a currency symbol or code ("$4.50", "45.90 ₪",
 * "ILS 12", "12,34 €") and treats the rest of the notification as the merchant.
 */
object PaymentParser {

    /**
     * @param title the notification title (usually the merchant).
     * @param texts the other text fields of the notification.
     * @param requirePaymentWording only accept notifications that also mention a
     *   card or a word like "paid" — used for apps that post lots of unrelated
     *   notifications.
     * @param dollarCurrency what a bare "$" means.
     */
    fun parse(
        title: String?,
        texts: List<String?>,
        requirePaymentWording: Boolean = false,
        dollarCurrency: String = "USD",
    ): ParsedPayment? {
        val rawLines = (listOf(title) + texts)
            .flatMap { it?.split('\n').orEmpty() }
            .map(::clean)
            .filter { it.isNotEmpty() }
            .distinct()
        if (rawLines.isEmpty()) return null
        val lower = rawLines.joinToString("\n").lowercase(Locale.ROOT)

        if (IGNORE_WORDS.any { it in lower }) return null
        if (requirePaymentWording && PAYMENT_WORDS.none { it in lower } && !CARD.containsMatchIn(lower)) {
            return null
        }

        // Card numbers ("Visa •••• 1234") would otherwise be read as amounts.
        val lines = rawLines.map { CARD.replace(it, " ").replace(SPACES, " ").trim() }
        val titleLine = if (title.isNullOrBlank()) null else lines.first()

        for (line in lines) {
            val amount = findAmount(line, dollarCurrency) ?: continue
            if (amount.cents == 0L) return null
            val refund = amount.negative || REFUND_WORDS.any { it in lower }
            val cents = if (refund) -abs(amount.cents) else amount.cents
            return ParsedPayment(cents, amount.currency, merchant(lines, titleLine, line, amount.range, dollarCurrency))
        }
        return null
    }

    /** Converts a number as written in a notification ("1,234.56", "12,34", "4.500") to cents. */
    internal fun toCents(number: String): Long? {
        val lastSeparator = number.indexOfLast { !it.isDigit() }
        val digitsAfter = number.length - lastSeparator - 1
        // A trailing separator followed by one or two digits is the decimal point;
        // every other separator groups thousands.
        val hasDecimals = lastSeparator >= 0 && number[lastSeparator] in ".," && digitsAfter in 1..2
        val whole = (if (hasDecimals) number.substring(0, lastSeparator) else number).filter(Char::isDigit)
        val fraction = if (hasDecimals) number.substring(lastSeparator + 1).padEnd(2, '0') else "00"
        if (whole.isEmpty() || whole.length > 12) return null
        return (whole + fraction).toLong()
    }

    private class Amount(val cents: Long, val currency: String, val negative: Boolean, val range: IntRange)

    private fun findAmount(line: String, dollarCurrency: String): Amount? {
        var start = 0
        while (start < line.length) {
            val m = AMOUNT.find(line, start) ?: return null
            val g = m.groupValues
            val prefixForm = g[2].isNotEmpty()
            val token = if (prefixForm) g[2] else g[7]
            val number = if (prefixForm) g[4] else g[6]
            val sign = if (prefixForm) g[1] + g[3] else g[5]
            val currency = currencyFor(token, dollarCurrency)
            val cents = toCents(number)
            val before = line.substring(maxOf(0, m.range.first - 20), m.range.first).lowercase(Locale.ROOT)
            if (currency == null || cents == null || BALANCE_WORDS.any { it in before }) {
                // Not a usable amount (e.g. "USA 5" or "Balance: $20"); keep looking just past it.
                start = m.range.first + 1
                continue
            }
            return Amount(cents, currency, sign.isNotEmpty(), m.range)
        }
        return null
    }

    private fun currencyFor(token: String, dollarCurrency: String): String? = when (token) {
        "$" -> dollarCurrency
        "NIS" -> "ILS"
        in SYMBOLS -> SYMBOLS.getValue(token)
        in ISO_CODES -> token
        else -> null
    }

    private fun merchant(
        lines: List<String>,
        titleLine: String?,
        amountLine: String,
        amountRange: IntRange,
        dollarCurrency: String,
    ): String? {
        // Prefer the title, then the other lines, and only then whatever is left of the
        // line the amount was in ("$4.50 at Starbucks" -> "Starbucks").
        val ordered = listOfNotNull(titleLine) + lines.filter { it != titleLine && it != amountLine }
        for (line in ordered) {
            if (findAmount(line, dollarCurrency) != null) continue
            tidy(line)?.let { return it }
        }
        val rest = TRAILING_FILLER.replace(amountLine.removeRange(amountRange), "")
        return tidy(rest)
    }

    private fun tidy(text: String): String? {
        if (text.lowercase(Locale.ROOT).trim() in GENERIC) return null
        var t = text.trim()
        repeat(4) { t = LEADING_FILLER.replace(t, "").trim() }
        t = t.trim { it.isWhitespace() || it in "·•-–—:,.|()" }
        if (t.none(Char::isLetter) || t.lowercase(Locale.ROOT) in GENERIC) return null
        return t.take(60)
    }

    private fun clean(text: String): String =
        text.replace(INVISIBLE, "").replace('−', '-').replace(SPACES, " ").trim()

    private val SYMBOLS = mapOf(
        "US$" to "USD", "CA$" to "CAD", "AU$" to "AUD", "A$" to "AUD", "NZ$" to "NZD",
        "HK$" to "HKD", "MX$" to "MXN", "S$" to "SGD", "R$" to "BRL",
        "₪" to "ILS", "€" to "EUR", "£" to "GBP", "¥" to "JPY", "₹" to "INR", "₩" to "KRW",
        "₺" to "TRY", "₽" to "RUB", "₴" to "UAH", "฿" to "THB", "₫" to "VND", "₱" to "PHP",
        "zł" to "PLN", "Kč" to "CZK",
        "ש\"ח" to "ILS", "ש״ח" to "ILS", "ש'ח" to "ILS", "שקלים" to "ILS", "שקל" to "ILS",
    )

    private val ISO_CODES: Set<String> by lazy {
        Currency.getAvailableCurrencies().mapTo(HashSet()) { it.currencyCode }
    }

    // Plain spaces aren't treated as thousands separators: "7 $4.50" must not become 74.50.
    private const val WS = """[\s   ]"""
    private const val NUMBER =
        """(?:\d{1,3}(?:[,.'’  ]\d{3})+(?:[.,]\d{1,2})?|\d+(?:[.,]\d{1,2})?)(?!\d)"""
    private val CURRENCY = "(?:(?<![A-Za-z])[A-Z]{3}(?![A-Za-z])|" +
        (SYMBOLS.keys + "$").sortedByDescending { it.length }.joinToString("|") { Regex.escape(it) } + ")"

    // Groups: 1 sign, 2 currency, 3 sign, 4 number  — "$4.50", "-₪12", "ILS 45"
    //         5 sign, 6 number, 7 currency           — "45.90 ₪", "12,34 €"
    // The second form must not be followed by another number, so that in
    // "Oct 7 $4.50" the amount is 4.50 rather than 7.
    private val AMOUNT = Regex(
        "([-–]?)($CURRENCY)$WS*([-–]?)$WS*($NUMBER)" +
            "|(?<![\\d.,])([-–]?)($NUMBER)$WS*($CURRENCY)(?!$WS*[-–]?\\d)",
    )

    private val CARD = Regex(
        """(?:(?:with|using|via|באמצעות)\s+)?""" +
            """(?:(?:visa|mastercard|master\s?card|maestro|amex|american\s+express|discover|diners(?:\s+club)?|""" +
            """isracard|ישראכרט|ויזה|מאסטרקארד|debit|credit)\s*(?:card\s*)?)?""" +
            """(?:[•·*●∙]{2,}|…|\.{3,})\s?\d{2,4}\b|(?:card\s+)?ending\s+in\s+\d{2,4}\b""",
        RegexOption.IGNORE_CASE,
    )

    private val INVISIBLE = Regex("[​-‏‪-‮⁦-⁩؜﻿]")
    private val SPACES = Regex("[ \t]+")

    private val LEADING_FILLER = Regex(
        "^(?:you|paid|spent|payment|purchase|charged|transaction|at|to|from|" +
            "שילמת|שולם|תשלום|רכישה|עסקה|אצל)(?=[\\s:]|$)[\\s:]*",
        RegexOption.IGNORE_CASE,
    )
    private val TRAILING_FILLER = Regex("\\s(?:with|using|via|באמצעות)\\s.*$", RegexOption.IGNORE_CASE)

    private val GENERIC = setOf(
        "google wallet", "google pay", "wallet", "payment", "payment complete", "payment completed",
        "payment successful", "payment sent", "payment approved", "purchase", "transaction", "paid",
        "contactless payment", "new transaction", "תשלום", "התשלום בוצע", "תשלום בוצע", "ארנק google",
        "google ארנק", "עסקה חדשה",
    )

    private val IGNORE_WORDS = listOf(
        "declined", "failed", "unsuccessful", "not completed", "couldn't", "could not", "wasn't",
        "cancelled", "canceled", "cashback", "cash back", "reward", "offer", "promo", "coupon",
        "you received", "received from", "requested", "request from",
        "נדחה", "נכשל", "לא הושלם", "לא בוצע", "בוטל", "הטבה", "קיבלת", "בקשת תשלום",
    )
    private val PAYMENT_WORDS = listOf(
        "paid", "payment", "purchase", "spent", "charged", "transaction",
        "שולם", "שילמת", "תשלום", "רכישה", "חיוב", "עסקה",
    )
    private val REFUND_WORDS = listOf("refund", "reversal", "reversed", "returned", "זיכוי", "החזר", "הוחזר")
    private val BALANCE_WORDS = listOf("balance", "remaining", "יתרה", "נותרו")
}
