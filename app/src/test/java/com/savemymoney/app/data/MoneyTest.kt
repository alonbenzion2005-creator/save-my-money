package com.savemymoney.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MoneyTest {

    @Test
    fun parseInput() {
        assertEquals(500000L, Money.parseInput("5,000"))
        assertEquals(1234500L, Money.parseInput("12,345"))
        assertEquals(-120000L, Money.parseInput("-1,200"))
        assertEquals(123456700L, Money.parseInput("1,234,567"))
        assertEquals(500050L, Money.parseInput("5,000.50"))
        assertEquals(1250L, Money.parseInput("12,5"))
        assertEquals(1250L, Money.parseInput("12,50"))
        assertEquals(1250L, Money.parseInput("12.5"))
        assertEquals(-300L, Money.parseInput("-3"))
        assertEquals(450000L, Money.parseInput(" 4 500 "))
        assertNull(Money.parseInput(""))
        assertNull(Money.parseInput("abc"))
    }
}
