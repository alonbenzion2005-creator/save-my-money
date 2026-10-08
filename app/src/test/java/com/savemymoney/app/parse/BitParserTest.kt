package com.savemymoney.app.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// bit's real notification wording isn't published anywhere, so these are made-up
// messages in the shapes bit's FAQ describes. Replace them with real ones once seen.
class BitParserTest {

    private fun parse(title: String?, vararg texts: String?) = BitParser.parse(title, texts.toList())

    @Test
    fun moneyYouSentIsCounted() {
        assertEquals(ParsedPayment(5000, "ILS", BitParser.MERCHANT), parse("bit", "דנה קיבלה את ה-50 ₪ שהעברת"))
        assertEquals(12050L, parse("bit", "יוסי קיבל את 120.50 ש\"ח שהעברת לו")?.amountCents)
        assertEquals(5000L, parse("bit", "Dana received the ₪50 you sent")?.amountCents)
    }

    @Test
    fun moneyComingInIsNotCounted() {
        assertNull(parse("bit", "קיבלת 50 ₪ מדנה"))
        assertNull(parse("bit", "דנה העבירה לך 50 ₪"))
        assertNull(parse("bit", "Dana sent you ₪50"))
    }

    @Test
    fun requestsAndFailuresAreNotCounted() {
        assertNull(parse("bit", "דנה ביקשה ממך 50 ₪"))
        assertNull(parse("bit", "בקשת התשלום על סך 50 ₪ שהעברת אושרה"))
        assertNull(parse("bit", "ההעברה של 50 ₪ שהעברת נכשלה"))
        assertNull(parse("bit", "דנה סירבה לקבל את 50 ₪ שהעברת"))
    }

    @Test
    fun needsAnAmount() {
        assertNull(parse("bit", "ההעברה שהעברת התקבלה"))
        assertNull(parse("bit", "50 ₪ עודכנו ביתרה שלך"))
    }
}
