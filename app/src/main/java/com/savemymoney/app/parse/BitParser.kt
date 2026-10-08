package com.savemymoney.app.parse

import java.util.Locale

/**
 * Reads money the user SENT out of a bit notification or SMS.
 *
 * bit tells the sender (by push or SMS) when the other person has received the money. Its
 * exact wording isn't published anywhere, so this is deliberately strict: it only counts a
 * message shaped like "<someone> received the <amount> you sent", and skips anything that
 * hints at money coming in or back, a request, a transfer that failed or hasn't arrived yet,
 * an offer, or a scam ("not you? cancel", links, phone numbers). Missing a transfer just means
 * adding it by hand; counting someone else's money, or a fake SMS, would be worse.
 */
object BitParser {

    fun parse(title: String?, texts: List<String?>): ParsedPayment? {
        val all = (listOf(title) + texts).filterNotNull().joinToString("\n")
        val lower = all.lowercase(Locale.ROOT)
        if (!SOMEONE_RECEIVED.containsMatchIn(lower) || !YOU_SENT.containsMatchIn(lower)) return null
        if (NOT_A_DONE_TRANSFER.any { it.containsMatchIn(lower) }) return null
        if ('?' in all || '%' in all || LINK_OR_PHONE.containsMatchIn(lower)) return null
        val payment = PaymentParser.parse(title, texts, dollarCurrency = "USD") ?: return null
        if (payment.amountCents <= 0) return null
        return payment.copy(merchant = MERCHANT)
    }

    const val MERCHANT = "bit transfer"

    /** [words] as whole words: not part of a longer word (so "שלחתי", "I sent", isn't "שלחת", "you sent"). */
    private fun words(vararg words: String) = Regex("(?<!\\p{L})(?:${words.joinToString("|")})(?!\\p{L})")

    // He/she/they received — not "קיבלת" (you received).
    private val SOMEONE_RECEIVED = Regex(
        "(?<!\\p{L})(?:קיבל|קיבלה|קיבלו)(?!\\p{L})|(?<!you )(?<!\\p{L})received(?!\\p{L})",
    )
    private val YOU_SENT = words("שהעברת", "ששלחת", "you sent", "you transferred")

    private val NOT_A_DONE_TRANSFER = listOf(
        // Money coming to the user or back to them, and requests.
        words("קיבלת", "ש?לך", "אליך", "לחשבונך", "sent you", "paid you", "you received", "received from"),
        Regex("חזר|זיכוי|ביקש|בקש|return|refund|revers|request"),
        // Not done: cancelled, refused, failed, pending, expired, or a negation ("לא", "עדיין לא").
        Regex("ביטל|בוטל|ביטול|סירב|דחת|נכשל|ממתין|טרם|עדיין|פג תוקף|cancel|declin|reject|refus|fail|pending|expire"),
        words("[וש]?לא", "דחה", "דחו", "didn't", "did not", "hasn't", "has not", "not yet", "not you"),
        // Offers and reminders.
        Regex("קבלו|הטבה|מתנה|עד |up to|offer|reward|gift"),
    )

    private val LINK_OR_PHONE = Regex(
        """https?://|www\.|[a-z0-9-]\.(?:co|com|net|org|il|ly|me|io|app|link|info|site)(?![a-z])|""" +
            """(?<![\d,.])(?:\+?972-?|0)\d{1,2}-?\d{3}-?\d{4}(?!\d)|\*\d{4}(?!\d)""",
    )
}
