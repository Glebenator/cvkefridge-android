package com.cvke.fridge.inventory

import java.math.BigDecimal

/** A quantity that the person typed. */
sealed interface TypedQuantity {
    /** A quantity above 0, in thousandths. */
    data class Value(val thousandths: Long) : TypedQuantity

    /** Why the text is not a quantity. */
    enum class Error : TypedQuantity { EMPTY, NOT_A_NUMBER, TOO_MANY_DECIMALS, NOT_POSITIVE, TOO_LARGE }
}

/** Reads and writes a quantity as the text of a number field. The core keeps a quantity as whole thousandths. */
object QuantityText {
    private val number = Regex("[0-9]*[.]?[0-9]*")

    /**
     * The quantity in the [text]. The decimal sign is a point or a comma, because the decimal keyboard of some phones
     * has only one of the two. The text has no sign for thousands. The largest quantity is 99999.999.
     */
    fun parse(text: String): TypedQuantity {
        val trimmed = text.trim().replace(',', '.')
        if (trimmed.isEmpty()) return TypedQuantity.Error.EMPTY
        if (!number.matches(trimmed) || trimmed.none(Char::isDigit)) return TypedQuantity.Error.NOT_A_NUMBER
        if (trimmed.substringAfter('.', "").length > 3) return TypedQuantity.Error.TOO_MANY_DECIMALS
        if (trimmed.substringBefore('.').trimStart('0').length > 5) return TypedQuantity.Error.TOO_LARGE
        val value = BigDecimal(if (trimmed.startsWith('.')) "0$trimmed" else trimmed).movePointRight(3).longValueExact()
        return if (value <= 0) TypedQuantity.Error.NOT_POSITIVE else TypedQuantity.Value(value)
    }

    /** The text of a quantity for a number field: no zeros at the end, and the decimal sign of the locale. */
    fun format(thousandths: Long, decimalSeparator: Char = '.'): String =
        BigDecimal.valueOf(thousandths, 3).stripTrailingZeros().toPlainString().replace('.', decimalSeparator)
}
