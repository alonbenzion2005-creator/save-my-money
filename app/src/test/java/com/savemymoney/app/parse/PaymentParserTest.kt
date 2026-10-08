package com.savemymoney.app.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PaymentParserTest {

    private fun parse(title: String?, vararg texts: String?, dollar: String = "USD") =
        PaymentParser.parse(title, texts.toList(), dollarCurrency = dollar)

    @Test
    fun merchantInTitleAmountInText() {
        assertEquals(
            ParsedPayment(450, "USD", "Starbucks"),
            parse("Starbucks", "$4.50 with Visa •••• 1234"),
        )
    }

    @Test
    fun shekelsInEnglish() {
        assertEquals(
            ParsedPayment(12340, "ILS", "Shufersal Deal"),
            parse("Shufersal Deal", "₪123.40 with Mastercard ••1234"),
        )
    }

    @Test
    fun shekelsInHebrewWithDirectionMarks() {
        assertEquals(
            ParsedPayment(4590, "ILS", "שופרסל"),
            parse("שופרסל", "‏45.90 ‏₪ באמצעות Mastercard •••• 1234"),
        )
        assertEquals(ParsedPayment(1200, "ILS", "רמי לוי"), parse("רמי לוי", "12 ש\"ח"))
        assertEquals(ParsedPayment(1200, "ILS", "רמי לוי"), parse("רמי לוי", "12 ש״ח"))
        // Hebrew prefix hyphen ("for 50 ₪"), not a minus sign.
        assertEquals(5000L, parse("רמי לוי", "שולם ב-50 ₪")?.amountCents)
        assertEquals(5000L, parse("רמי לוי", "שולם ב-₪50")?.amountCents)
    }

    @Test
    fun amountAndMerchantInOneLine() {
        assertEquals(ParsedPayment(123456, "USD", "Best Buy"), parse("$1,234.56 at Best Buy"))
        assertEquals(ParsedPayment(1234, "EUR", "Café Paris"), parse("Google Wallet", "You paid €12,34 at Café Paris"))
    }

    @Test
    fun noMerchantFound() {
        assertEquals(ParsedPayment(4590, "ILS", null), parse(null, "₪45.90 with Mastercard •••• 1234"))
        assertEquals(ParsedPayment(4590, "ILS", null), parse("Payment complete", "₪45.90"))
    }

    @Test
    fun currencyCodesAndFormats() {
        assertEquals(4590L, parse("Cafe", "ILS 45.90")?.amountCents)
        assertEquals("ILS", parse("Cafe", "45.90 NIS")?.currency)
        assertEquals(ParsedPayment(1200050, "EUR", "Saturn"), parse("Saturn", "12.000,50 €"))
        assertEquals(ParsedPayment(120000, "JPY", "Lawson"), parse("Lawson", "¥1,200"))
        assertEquals(ParsedPayment(1000, "GBP", "Tesco"), parse("Tesco", "£10"))
        assertEquals(ParsedPayment(4590, "CHF", "Migros"), parse("Migros", "CHF 45.90"))
    }

    @Test
    fun bareDollarUsesConfiguredCurrency() {
        assertEquals("CAD", parse("Tim Hortons", "$3.20", dollar = "CAD")?.currency)
        assertEquals("AUD", parse("Woolworths", "A$3.20", dollar = "CAD")?.currency)
    }

    @Test
    fun refunds() {
        assertEquals(-2000L, parse("Amazon", "-$20.00")?.amountCents)
        assertEquals(-2000L, parse("Refund from Amazon", "$20.00")?.amountCents)
        assertEquals(-5000L, parse("זיכוי", "50 ₪")?.amountCents)
    }

    @Test
    fun ignoresNotificationsThatAreNotPayments() {
        assertNull(parse("Payment declined", "$5.00 at Starbucks"))
        assertNull(parse("Boarding pass added", "Your pass for flight LY 001 is ready"))
        assertNull(parse("Visa •••• 1234 is ready to use", "Tap to pay in stores"))
        assertNull(parse("Balance: $17.50", null))
    }

    @Test
    fun cardDigitsAndDatesAreNotAmounts() {
        assertEquals(4590L, parse("Cafe", "Visa •••• 1234 ₪45.90")?.amountCents)
        assertEquals(450L, parse("Cafe", "Oct 7 $4.50")?.amountCents)
        assertEquals(450L, parse("Cafe", "14:32 $4.50")?.amountCents)
        assertEquals(250L, parse("Clipper", "Paid $2.50 · Balance $17.50")?.amountCents)
        assertEquals(500L, parse("Cafe", "USA $5")?.amountCents)
    }

    // Real Google Wallet notifications captured by other projects (title, text), 2025-2026.
    @Test
    fun realWalletNotifications() {
        assertEquals(
            ParsedPayment(1205, "GBP", "LONDON NORTH EASTERN RAILWAY"),
            parse("LONDON NORTH EASTERN RAILWAY", "£12.05 with The American Express® Rewards Credit Card ••2002"),
        )
        assertEquals(
            ParsedPayment(22900, "CZK", "131 - SUPER ZOO"),
            parse("131 - SUPER ZOO", "229,00 CZK with Visa Debit Infinite ••2665"),
        )
        assertEquals(
            ParsedPayment(80400, "HUF", "75. SZ. ABC ÁRUHÁZ"),
            parse("75. SZ. ABC ÁRUHÁZ", "HUF804.00 with Revolut Mastercard ••1413"),
        )
        assertEquals(
            ParsedPayment(240000, "CLP", "MERPAGO*MAXIMARKETLIM"),
            parse("MERPAGO*MAXIMARKETLIM", "CLP2,400 con credito"),
        )
        assertEquals(
            ParsedPayment(4099, "EUR", "SP HOLY ENERGY FR"),
            parse("SP HOLY ENERGY FR", "40,99 € avec la carte Revolut Visa ••5239"),
        )
    }

    // Google Play services' notification templates: "%1$s with %2$s", "DECLINED - %1$s with %2$s",
    // "%1$s was refunded to %2$s", and "Unknown store" when there's no merchant name.
    @Test
    fun walletTemplates() {
        assertNull(parse("Starbucks", "DECLINED - $5.00 with Visa ••1234"))
        assertEquals(-12345L, parse("Amazon", "$123.45 was refunded to Amex ••1234")?.amountCents)
        assertEquals(ParsedPayment(450, "USD", null), parse("Unknown store", "$4.50 with Visa ••1234"))
    }

    @Test
    fun multiLineText() {
        assertEquals(ParsedPayment(450, "USD", "Starbucks"), parse(null, "Starbucks\n$4.50 with Visa ••1234"))
    }

    @Test
    fun toCents() {
        assertEquals(123456L, PaymentParser.toCents("1,234.56"))
        assertEquals(123456L, PaymentParser.toCents("1.234,56"))
        assertEquals(123400L, PaymentParser.toCents("1,234"))
        assertEquals(4590L, PaymentParser.toCents("45.9"))
        assertEquals(1234L, PaymentParser.toCents("12,34"))
        assertEquals(500L, PaymentParser.toCents("5"))
    }
}
