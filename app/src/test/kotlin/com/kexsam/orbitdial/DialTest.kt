package com.kexsam.orbitdial

import org.junit.Assert.assertEquals
import org.junit.Test

class DialTest {

    private val side = 13
    private val d = Dial.DEFAULT

    @Test fun `the shipped dial is already inside the panel's range`() =
        assertEquals(d, d.clamped(side))

    @Test fun `brightness stays within 0 and 2047`() {
        assertEquals(2047, d.copy(full = 5000).clamped(side).full)
        assertEquals(0, d.copy(full = -1).clamped(side).full)
        assertEquals(0, d.copy(dim = -9).clamped(side).dim)
    }

    @Test fun `dim never exceeds full`() {
        assertEquals(100, d.copy(full = 100, dim = 500).clamped(side).dim)
        assertEquals(2047, d.copy(full = 9000, dim = 9000).clamped(side).dim)
    }

    @Test fun `scale length is at least one cell and at most the radius`() {
        assertEquals(1, d.copy(scaleLength = 0).clamped(side).scaleLength)
        assertEquals(6, d.copy(scaleLength = 99).clamped(side).scaleLength)
    }

    @Test fun `the minute mark is at least one cell and at most the panel`() {
        assertEquals(1, d.copy(minuteSize = 0).clamped(side).minuteSize)
        assertEquals(13, d.copy(minuteSize = 99).clamped(side).minuteSize)
    }

    @Test fun `the orbit is finite and within the disc`() {
        assertEquals(d.minuteOrbit, d.copy(minuteOrbit = Double.NaN).clamped(side).minuteOrbit, 0.0)
        assertEquals(d.minuteOrbit, d.copy(minuteOrbit = Double.POSITIVE_INFINITY).clamped(side).minuteOrbit, 0.0)
        assertEquals(0.0, d.copy(minuteOrbit = -1.0).clamped(side).minuteOrbit, 0.0)
        assertEquals(6.5, d.copy(minuteOrbit = 99.0).clamped(side).minuteOrbit, 0.0)
    }

    @Test fun `a panel of unknown size is clamped without throwing`() {
        assertEquals(1, Dial.defaultFor(0).clamped(0).minuteSize)
    }

    @Test fun `a non-finite orbit falls back to the panel's own orbit`() =
        assertEquals(6.0, Dial.PHONE_3.copy(minuteOrbit = Double.NaN).clamped(25).minuteOrbit, 0.0)
}
