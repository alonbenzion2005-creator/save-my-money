package com.savemymoney.app.parse

import java.util.Locale

/**
 * Reads money the user SENT out of a bit notification or SMS.
 *
 * bit tells the sender (by push or SMS) when the other person has received the money.
 * Its exact wording isn't published anywhere, so this only counts a message that both
 * has an amount and clearly says the user sent it ("שהעברת", "you sent"…). Anything about
 * money coming in, a request, or a failed transfer is skipped: counting someone else's
 * transfer to you as your spending would be worse than missing one.
 */
object BitParser {

    fun parse(title: String?, texts: List<String?>): ParsedPayment? {
        val lower = (listOf(title) + texts).filterNotNull().joinToString("\n").lowercase(Locale.ROOT)
        if (INCOMING.any { it in lower }) return null
        if (OUTGOING.none { it in lower }) return null
        val payment = PaymentParser.parse(title, texts, dollarCurrency = "USD") ?: return null
        return payment.copy(merchant = MERCHANT)
    }

    const val MERCHANT = "bit transfer"

    private val OUTGOING = listOf(
        "שהעברת", "העברת ל", "שלחת", "שילמת", "ששלחת",
        "you sent", "you paid", "you transferred", "you've sent", "you have sent",
    )

    // Money coming to the user, requests, and anything that didn't happen.
    private val INCOMING = listOf(
        "קיבלת", "העביר לך", "העבירה לך", "העבירו לך", "שלח לך", "שלחה לך", "שלחו לך",
        "ביקש", "בקשה", "בקשת", "סירב", "סירבה",
        "sent you", "you received", "received from", "request", "declined", "refused",
    )
}
