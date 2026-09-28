package com.kexsam.orbitdial

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveFrameTest {

    @Test fun `a frame survives encoding on both panels`() {
        for (side in listOf(13, 25)) {
            val frame = ClockFace.render(10, 8, side, Dial.defaultFor(side))
            val text = LiveFrame.encode(frame)
            assertEquals(side * side * 3, text.length)
            assertArrayEquals(frame, LiveFrame.decode(text, side))
        }
    }

    @Test fun `a frame for the other panel is refused`() {
        val phone3 = LiveFrame.encode(ClockFace.render(10, 8, 25, Dial.PHONE_3))
        assertNull(LiveFrame.decode(phone3, 13))
    }

    @Test fun `bad text is refused`() {
        assertNull(LiveFrame.decode(null, 13))
        assertNull(LiveFrame.decode("zz" + "0".repeat(13 * 13 * 3 - 2), 13))
        assertNull(LiveFrame.decode("fff".repeat(13 * 13), 13))      // 4095 is over the panel's 2047
    }

    @Test fun `values over the panel's range are clamped on the way out`() =
        assertEquals("7ff", LiveFrame.encode(intArrayOf(5000)))

    @Test fun `positions with no LED stay off`() {
        val side = 13
        val lit = LiveFrame.masked(IntArray(side * side) { 2047 }, side)
        assertEquals(0, lit[0])                                       // the corner has no LED
        assertEquals(137, lit.count { it > 0 })
    }

    @Test fun `a frame is held for thirty seconds`() {
        assertTrue(LiveFrame.fresh(1_000, 1_000 + 29_999))
        assertFalse(LiveFrame.fresh(1_000, 1_000 + 30_001))
        assertFalse(LiveFrame.fresh(0, 5_000))
    }
}
