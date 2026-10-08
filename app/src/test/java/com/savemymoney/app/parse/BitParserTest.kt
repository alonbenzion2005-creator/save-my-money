package com.savemymoney.app.parse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

// bit's real notification wording isn't published anywhere, so these are made-up
// messages in the shapes bit's FAQ describes. Replace them with real ones once seen.
class BitParserTest {

    private fun parse(text: String, title: String? = "bit") = BitParser.parse(title, listOf(text))

    private fun assertNotCounted(vararg texts: String) {
        for (text in texts) assertNull(text, parse(text))
    }

    @Test
    fun confirmationThatSomeoneReceivedYourMoneyIsCounted() {
        assertEquals(ParsedPayment(5000, "ILS", BitParser.MERCHANT), parse("דנה קיבלה את ה-50 ₪ שהעברת"))
        assertEquals(12050L, parse("יוסי קיבל את 120.50 ש\"ח ששלחת לו")?.amountCents)
        assertEquals(125050L, parse("דנה קיבלה את ה-1,250.50 ₪ שהעברת")?.amountCents)
        assertEquals(5000L, parse("Dana received the ₪50 you sent")?.amountCents)
    }

    @Test
    fun moneyComingInIsNotCounted() {
        assertNotCounted(
            "קיבלת 50 ₪ מדנה",
            "דנה העבירה לך 50 ₪",
            "Dana sent you ₪50",
            "You received ₪50 from Dana",
            // Notes quoting the user's earlier payment, and first-person "I sent".
            "דנה שילמה לך 50 ₪: \"על מה ששילמת אתמול\"",
            "דנה שילמה לך 50 ₪: \"שלחתי לך על הפיצה\"",
            "Dana paid you ₪50: \"for what you paid at dinner\"",
        )
    }

    @Test
    fun moneyComingBackIsNotCounted() {
        assertNotCounted(
            "דנה החזירה לך את ה-50 ₪ ששלחת לה",
            "Dana sent back the ₪50 you sent",
            "ה-50 ₪ ששלחת לדנה חזרו אליך",
            "50 ₪ שהעברת לדנה הוחזרו לחשבון שלך",
            "The ₪50 you sent to Dana was returned to you",
        )
    }

    @Test
    fun requestsAndTransfersThatDidNotHappenAreNotCounted() {
        assertNotCounted(
            "דנה ביקשה ממך 50 ₪",
            "בקשת התשלום על סך 50 ₪ שהעברת אושרה",
            "ההעברה של 50 ₪ שהעברת נכשלה",
            "דנה סירבה לקבל את 50 ₪ שהעברת",
            "ביטלת את ההעברה של 50 ₪ ששלחת לדנה",
            "ההעברה של 50 ₪ ששלחת לדנה לא הצליחה",
            "The ₪50 you sent to Dana didn't go through",
            "דנה דחתה את ה-50 ₪ ששלחת לה",
            "Dana rejected the ₪50 you sent",
            "דנה עדיין לא קיבלה את ה-50 ₪ ששלחת לה",
            "Dana hasn't received the ₪50 you sent yet",
        )
    }

    @Test
    fun offersAndScamsAreNotCounted() {
        assertNotCounted(
            "שלחת כבר מתנה ליום הולדת? שלחו עד 100 ₪ בקלות",
            "שילמת עם bit בסופר? קבלו 5% החזר עד 20 ₪",
            "עדיין לא שילמת לדנה 50 ₪",
            "שילמת 899 ₪ באמצעות bit. לא אתה? לביטול היכנס: bit-il.co/x",
            "You sent ₪1,500 with bit. Not you? Cancel: bit-il.co/x",
            "העברת לחשבון 2,450 ₪ בוצעה. אם לא ביצעת את הפעולה התקשר 03-1234567",
            "דנה קיבלה את ה-50 ₪ שהעברת. לפרטים: 050-1234567",
        )
    }

    @Test
    fun needsAnAmount() {
        assertNotCounted("דנה קיבלה את ההעברה שהעברת", "50 ₪ עודכנו ביתרה")
    }
}
