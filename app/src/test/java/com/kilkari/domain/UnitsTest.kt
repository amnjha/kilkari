package com.kilkari.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class UnitsTest {

    @Test
    fun `pounds and ounces read the way a scale does`() {
        assertEquals(7 to 8, Units.lbOz(3.402))
        assertEquals(0 to 0, Units.lbOz(0.0))
    }

    @Test
    fun `a whisker under a pound rounds up to the pound, not to sixteen ounces`() {
        val (lb, oz) = Units.lbOz(Units.lbToKg(0.9999))
        assertEquals(1, lb)
        assertEquals(0, oz)
    }

    @Test
    fun `what is typed in pounds and ounces comes back as the same weight`() {
        val kg = Units.fromLbOz(7.0, 8.0)
        assertEquals(3.402, kg, 0.001)
        assertEquals(7 to 8, Units.lbOz(kg))
    }

    @Test
    fun `inches convert back to the centimetres they came from`() {
        val cm = Units.lengthFromField(Units.cmToIn(54.5), metric = false)
        assertEquals(54.5, cm, 0.0001)
    }

    @Test
    fun `a metric field is left alone`() {
        assertEquals(54.5, Units.lengthFromField(54.5, metric = true), 0.0001)
        assertEquals("54.5", Units.lengthField(54.5, metric = true))
    }

    @Test
    fun `a gain shows in grams or in ounces, never in pounds`() {
        assertEquals("+150 g", Units.deltaLabel(0.15, metric = true))
        assertEquals("+5.3 oz", Units.deltaLabel(0.15, metric = false))
        assertEquals("−150 g", Units.deltaLabel(-0.15, metric = true))
    }

    @Test
    fun `weight reads as pounds and ounces, length as inches`() {
        assertEquals("7 lb 8 oz", Units.weightLabel(3.402))
        assertEquals("21.5 in", Units.lengthLabel(54.5))
        assertEquals("3.4 kg", Fmt.weight(3.4, metric = true))
        assertEquals("7 lb 8 oz", Fmt.weight(3.402, metric = false))
        assertEquals("54.5 cm", Fmt.length(54.5, metric = true))
    }

    @Test
    fun `nothing measured reads as a dash in either system`() {
        assertEquals("—", Fmt.weight(null, metric = true))
        assertEquals("—", Fmt.length(null, metric = false))
    }
}
