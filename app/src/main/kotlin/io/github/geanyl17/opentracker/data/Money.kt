package io.github.geanyl17.opentracker.data

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

class Money(val currency: Currency, locale: Locale = Locale.getDefault()) {
    val symbol: String = currency.getSymbol(locale)

    private val withCents = formatter(locale, currency.defaultFractionDigits.coerceAtLeast(0))
    private val whole = formatter(locale, 0)

    /** Whole amounts drop the cents: ₱1,250 but ₱1,250.50. */
    fun format(cents: Long): String {
        val value = BigDecimal.valueOf(cents, 2)
        val format = if (cents % 100 == 0L) whole else withCents
        return synchronized(format) { format.format(value) }
    }

    private fun formatter(locale: Locale, digits: Int) = NumberFormat.getCurrencyInstance(locale).also {
        it.currency = currency
        it.minimumFractionDigits = digits
        it.maximumFractionDigits = digits
    }

    companion object {
        fun of(code: String): Money = Money(runCatching { Currency.getInstance(code) }.getOrElse { Currency.getInstance("USD") })

        fun defaultCurrencyCode(locale: Locale = Locale.getDefault()): String =
            runCatching { Currency.getInstance(locale).currencyCode }.getOrNull() ?: "USD"
    }
}

object Amounts {
    private val max = BigDecimal("1000000000000")

    /**
     * Reads what someone typed, in either decimal style: "1,234.50", "1.234,50", "12,5" and "1250" all work.
     * A lone separator followed by three digits is a thousands separator.
     */
    fun parse(input: String): Long? {
        val s = input.filter { it.isDigit() || it == '.' || it == ',' }
        if (s.none(Char::isDigit)) return null
        val lastDot = s.lastIndexOf('.')
        val lastComma = s.lastIndexOf(',')
        val decimalAt = when {
            lastDot >= 0 && lastComma >= 0 -> maxOf(lastDot, lastComma)
            lastDot >= 0 || lastComma >= 0 -> {
                val at = maxOf(lastDot, lastComma)
                val single = s.count { it == s[at] } == 1
                val zeroBefore = s.substring(0, at).all { it == '0' }
                if (single && (s.length - at - 1 != 3 || zeroBefore)) at else -1
            }
            else -> -1
        }
        val whole = (if (decimalAt >= 0) s.substring(0, decimalAt) else s).filter(Char::isDigit).ifEmpty { "0" }
        val fraction = if (decimalAt >= 0) s.substring(decimalAt + 1).filter(Char::isDigit).ifEmpty { "0" } else "0"
        return fromDecimal(BigDecimal("$whole.$fraction"))
    }

    fun fromDecimal(value: BigDecimal): Long? =
        if (value.signum() < 0 || value > max) null else value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).toLong()

    /** "250.00" style, for CSV files and the assistant. */
    fun plain(cents: Long): String = BigDecimal.valueOf(cents, 2).toPlainString()

    /** What goes in an amount field when editing: "250" or "250.50". */
    fun editable(cents: Long): String = if (cents % 100 == 0L) (cents / 100).toString() else plain(cents)
}
