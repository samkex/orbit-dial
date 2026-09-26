package com.kexsam.orbitdial

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The numbers the README states, as tests, so a change to the drawing shows up here before it
 * shows up on a phone. Everything is on the shipped 13 by 13 with [Dial.DEFAULT] unless said.
 */
class ClockFaceTest {

    private val side = 13
    private val dial = Dial.DEFAULT

    @Test fun `137 of the 169 positions have an LED`() =
        assertEquals(137, ClockFace.ledCount(side))

    @Test fun `the mask is a disc of radius side over two`() {
        for (r in 0 until side) for (c in 0 until side) {
            val d = Math.hypot(c - 6.0, r - 6.0)
            assertEquals("($c,$r)", d <= 6.5, ClockFace.hasLed(c, r, side))
        }
    }

    @Test fun `the one o'clock scale steps inward on a diagonal`() =
        assertEquals(listOf(9 to 1, 8 to 2), ClockFace.scaleRay(30.0, 2, side))

    @Test fun `a frame has one value per grid position`() =
        assertEquals(side * side, ClockFace.render(10, 8, side).size)

    @Test fun `positions without an LED stay off`() {
        val frame = ClockFace.render(10, 8, side)
        for (r in 0 until side) for (c in 0 until side) {
            if (!ClockFace.hasLed(c, r, side)) assertEquals("($c,$r)", 0, frame[r * side + c])
        }
    }

    @Test fun `nothing exceeds full`() {
        for (h in 0 until 12) for (m in 0 until 60) {
            assertTrue(ClockFace.render(h, m, side).all { it in 0..dial.full })
        }
    }

    @Test fun `the shipped frame lights the expected cells at each level`() {
        val frame = ClockFace.render(10, 8, side)
        assertEquals(6, frame.count { it == dial.full })   // one scale of two, one block of four
        assertEquals(22, frame.count { it == dial.dim })   // eleven scales of two
    }

    @Test fun `where the block meets a dim scale the brighter value wins`() {
        // A wide orbit puts the block on the rim, over the twelve o'clock scale, at minute 0.
        val wide = dial.copy(minuteOrbit = 5.5)
        val frame = ClockFace.render(3, 0, side, wide)
        val block = ClockFace.render(3, 0, side, wide.copy(dim = 0)).withIndex()
            .filter { it.value == wide.full }.map { it.index }
        val scale = ClockFace.scaleRay(0.0, wide.scaleLength, side).map { (c, r) -> r * side + c }
        val overlap = block.intersect(scale.toSet())
        assertTrue("the test needs an overlap to mean anything", overlap.isNotEmpty())
        overlap.forEach { assertEquals(wide.full, frame[it]) }
    }

    @Test fun `hours wrap on twelve`() {
        assertArrayEquals(ClockFace.render(0, 5, side), ClockFace.render(12, 5, side))
        assertArrayEquals(ClockFace.render(0, 5, side), ClockFace.render(24, 5, side))
        assertArrayEquals(ClockFace.render(11, 5, side), ClockFace.render(-1, 5, side))
        assertFalse(ClockFace.render(1, 5, side).contentEquals(ClockFace.render(2, 5, side)))
    }

    @Test fun `identical frames are recognised, different ones are not`() {
        val a = ClockFace.render(10, 8, side)
        assertTrue(ClockFace.same(a, ClockFace.render(10, 8, side)))
        assertFalse(ClockFace.same(a, ClockFace.render(10, 9, side)))
        assertFalse(ClockFace.same(null, a))
    }

    @Test fun `the shipped block lands on twelve distinct positions in an hour`() =
        assertEquals(12, ClockFace.minutePositions(side, dial))

    @Test fun `minute positions count what is visible, not the block's footprint`() {
        // At orbit 7.0 a 2 x 2 spends part of the hour beyond the grid and the disc: 42 distinct
        // footprints, 33 of them distinct on the panel.
        assertEquals(33, ClockFace.minutePositions(side, dial.copy(minuteOrbit = 7.0)))
    }

    @Test fun `on average 0_80 LEDs change per minute and the longest stall is seven minutes`() {
        val frames = (0 until 60).map { ClockFace.render(10, it, side) }
        var changes = 0
        var run = 1
        var longest = 0
        for (m in 1 until 120) {                       // twice round, so a stall across :00 counts
            val now = frames[m % 60]
            val before = frames[(m - 1) % 60]
            val differing = now.indices.count { now[it] != before[it] }
            if (m < 60) changes += differing
            if (differing == 0) run++ else { longest = maxOf(longest, run); run = 1 }
        }
        changes += frames[0].indices.count { frames[0][it] != frames[59][it] }
        assertEquals(0.80, changes / 60.0, 1e-9)
        assertEquals(7, longest)
    }

    @Test fun `golden frame at 00h00`() = assertGolden(0, 0, listOf(
        "    ..#..    ",
        "  .+..#..+.  ",
        " ...+...+... ",
        " +.........+ ",
        "..+...##..+..",
        "......##.....",
        "++.........++",
        ".............",
        "..+.......+..",
        " +.........+ ",
        " ...+...+... ",
        "  .+..+..+.  ",
        "    ..+..    ",
    ))
    @Test fun `golden frame at 03h15`() = assertGolden(3, 15, listOf(
        "    ..+..    ",
        "  .+..+..+.  ",
        " ...+...+... ",
        " +.........+ ",
        "..+.......+..",
        ".............",
        "++.....##..##",
        ".......##....",
        "..+.......+..",
        " +.........+ ",
        " ...+...+... ",
        "  .+..+..+.  ",
        "    ..+..    ",
    ))
    @Test fun `golden frame at 06h30`() = assertGolden(6, 30, listOf(
        "    ..+..    ",
        "  .+..+..+.  ",
        " ...+...+... ",
        " +.........+ ",
        "..+.......+..",
        ".............",
        "++.........++",
        "......##.....",
        "..+...##..+..",
        " +.........+ ",
        " ...+...+... ",
        "  .+..#..+.  ",
        "    ..#..    ",
    ))
    @Test fun `golden frame at 09h45`() = assertGolden(9, 45, listOf(
        "    ..+..    ",
        "  .+..+..+.  ",
        " ...+...+... ",
        " +.........+ ",
        "..+.......+..",
        ".............",
        "##..##.....++",
        "....##.......",
        "..+.......+..",
        " +.........+ ",
        " ...+...+... ",
        "  .+..+..+.  ",
        "    ..+..    ",
    ))
    @Test fun `golden frame at 11h59`() = assertGolden(11, 59, listOf(
        "    ..+..    ",
        "  .#..+..+.  ",
        " ...#...+... ",
        " +.........+ ",
        "..+..##...+..",
        ".....##......",
        "++.........++",
        ".............",
        "..+.......+..",
        " +.........+ ",
        " ...+...+... ",
        "  .+..+..+.  ",
        "    ..+..    ",
    ))

    /** `#` full, `+` dim, `.` an LED that is off, space no LED. */
    private fun assertGolden(hour: Int, minute: Int, rows: List<String>) {
        val frame = ClockFace.render(hour, minute, side)
        val drawn = (0 until side).map { r ->
            (0 until side).joinToString("") { c ->
                when {
                    !ClockFace.hasLed(c, r, side) -> " "
                    frame[r * side + c] == dial.full -> "#"
                    frame[r * side + c] == dial.dim -> "+"
                    frame[r * side + c] == 0 -> "."
                    else -> "?"
                }
            }
        }
        assertEquals("%02d:%02d".format(hour, minute), rows.joinToString("\n"), drawn.joinToString("\n"))
    }
}
