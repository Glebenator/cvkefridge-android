package com.cvke.fridge.inventory

import org.junit.Assert.assertEquals
import org.junit.Test

/** The text of a quantity field, to and from thousandths. */
class QuantityTextTest {
    @Test
    fun aNumberWithAPointOrACommaIsAQuantity() {
        assertEquals(TypedQuantity.Value(1_000), QuantityText.parse("1"))
        assertEquals(TypedQuantity.Value(1_500), QuantityText.parse(" 1.5 "))
        assertEquals(TypedQuantity.Value(1_500), QuantityText.parse("1,5"))
        assertEquals(TypedQuantity.Value(250), QuantityText.parse(".25"))
        assertEquals(TypedQuantity.Value(2_000), QuantityText.parse("2."))
        assertEquals(TypedQuantity.Value(1), QuantityText.parse("0.001"))
        assertEquals(TypedQuantity.Value(99_999_999), QuantityText.parse("099999.999"))
    }

    @Test
    fun otherTextHasAReason() {
        assertEquals(TypedQuantity.Error.EMPTY, QuantityText.parse("  "))
        assertEquals(TypedQuantity.Error.NOT_A_NUMBER, QuantityText.parse("two"))
        assertEquals(TypedQuantity.Error.NOT_A_NUMBER, QuantityText.parse("."))
        assertEquals(TypedQuantity.Error.NOT_A_NUMBER, QuantityText.parse("-1"))
        assertEquals(TypedQuantity.Error.NOT_A_NUMBER, QuantityText.parse("1.2.3"))
        assertEquals(TypedQuantity.Error.NOT_A_NUMBER, QuantityText.parse("1 000"))
        assertEquals(TypedQuantity.Error.TOO_MANY_DECIMALS, QuantityText.parse("0.0001"))
        assertEquals(TypedQuantity.Error.NOT_POSITIVE, QuantityText.parse("0"))
        assertEquals(TypedQuantity.Error.NOT_POSITIVE, QuantityText.parse("0,000"))
        assertEquals(TypedQuantity.Error.TOO_LARGE, QuantityText.parse("100000"))
    }

    @Test
    fun theTextOfAQuantityHasNoZerosAtTheEnd() {
        assertEquals("1", QuantityText.format(1_000))
        assertEquals("1.5", QuantityText.format(1_500))
        assertEquals("0,125", QuantityText.format(125, decimalSeparator = ','))
        assertEquals("600", QuantityText.format(600_000))
    }
}
