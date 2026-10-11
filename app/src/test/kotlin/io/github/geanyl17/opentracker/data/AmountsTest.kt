package io.github.geanyl17.opentracker.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Currency
import java.util.Locale

class AmountsTest {
    @Test
    fun parsesPlainNumbers() {
        assertEquals(25_000L, Amounts.parse("250"))
        assertEquals(25_050L, Amounts.parse("250.5"))
        assertEquals(50L, Amounts.parse(".5"))
        assertEquals(500L, Amounts.parse("5."))
    }

    @Test
    fun handlesBothDecimalStyles() {
        assertEquals(123_450L, Amounts.parse("1,234.50"))
        assertEquals(123_450L, Amounts.parse("1.234,50"))
        assertEquals(1_250L, Amounts.parse("12,5"))
        assertEquals(123_400L, Amounts.parse("1,234"))
        assertEquals(123_456_700L, Amounts.parse("1,234,567"))
    }

    @Test
    fun keepsLeadingZeroDecimals() {
        assertEquals(13L, Amounts.parse("0.125"))
    }

    @Test
    fun ignoresSymbolsAndRejectsGarbage() {
        assertEquals(99_900L, Amounts.parse("₱ 999"))
        assertNull(Amounts.parse(""))
        assertNull(Amounts.parse("lunch"))
        assertNull(Amounts.parse("99999999999999999"))
    }

    @Test
    fun formatsForEditingAndFiles() {
        assertEquals("250", Amounts.editable(25_000))
        assertEquals("250.50", Amounts.editable(25_050))
        assertEquals("0.05", Amounts.plain(5))
    }

    @Test
    fun dropsZeroCentsWhenFormatting() {
        val money = Money(Currency.getInstance("USD"), Locale.US)
        assertEquals("$1,250", money.format(125_000))
        assertEquals("$1,250.50", money.format(125_050))
    }
}
