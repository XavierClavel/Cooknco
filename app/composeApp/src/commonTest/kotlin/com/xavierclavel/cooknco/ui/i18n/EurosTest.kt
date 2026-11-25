package com.xavierclavel.cooknco.ui.i18n

import kotlin.test.Test
import kotlin.test.assertEquals

/** A recipe's cost, as each language writes an amount of euros. */
class EurosTest {

    @Test
    fun `an amount is written to the cent, with each language's separator and symbol`() {
        assertEquals("≈ €2.40", EnStrings.approxEuros(2.4f))
        assertEquals("≈ 2,40 €", FrStrings.approxEuros(2.4f))
        assertEquals("≈ 0,05 €", FrStrings.approxEuros(0.05f))
    }

    @Test
    fun `an amount is rounded to the nearest cent, carrying into the euros`() {
        assertEquals("≈ €2.00", EnStrings.approxEuros(1.996f))
        assertEquals("≈ €12.35", EnStrings.approxEuros(12.347f))
    }
}
