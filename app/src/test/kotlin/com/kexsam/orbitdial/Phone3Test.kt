package com.kexsam.orbitdial

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Phone (3) face, 25 by 25, with [Dial.PHONE_3]. The numbers here are what the README will
 * state once this face has been checked on the phone.
 */
class Phone3Test {

    private val side = 25
    private val dial = Dial.PHONE_3

    @Test fun `489 of the 625 positions have an LED`() =
        assertEquals(489, ClockFace.ledCount(side))

    @Test fun `each panel gets its own face`() {
        assertEquals(Dial.PHONE_3, Dial.defaultFor(25))
        assertEquals(Dial.DEFAULT, Dial.defaultFor(13))
        assertEquals(dial, dial.clamped(side))
    }

    @Test fun `every scale is a straight stroke of three`() {
        for (h in 0 until 12) {
            val cells = ClockFace.scaleRay(h * 30.0, 3, side)
            assertEquals("hour $h", 3, cells.size)
            val steps = cells.zipWithNext { a, b -> (b.first - a.first) to (b.second - a.second) }.toSet()
            assertEquals("hour $h bends", 1, steps.size)
        }
    }

    @Test fun `one and two o'clock sit where the design puts them`() {
        assertEquals(listOf(18 to 2, 17 to 3, 16 to 4), ClockFace.scaleRay(30.0, 3, side))
        assertEquals(listOf(22 to 6, 21 to 7, 20 to 8), ClockFace.scaleRay(60.0, 3, side))
    }

    @Test fun `no two scales share a cell`() {
        val cells = (0 until 12).flatMap { ClockFace.scaleRay(it * 30.0, 3, side) }
        assertEquals(cells.size, cells.toSet().size)
    }

    @Test fun `the block lands on 42 distinct positions and never on a scale`() {
        assertEquals(42, ClockFace.minutePositions(side, dial))
        val scales = (0 until 12).flatMap { ClockFace.scaleRay(it * 30.0, 3, side) }.toSet()
        for (m in 0 until 60) {
            val block = ClockFace.render(10, m, side, dial.copy(dim = 0)).withIndex()
                .filter { it.value == dial.full }.map { (it.index % side) to (it.index / side) }
                .filterNot { it in ClockFace.scaleRay(300.0, 3, side) }      // ten o'clock is lit too
            assertTrue("minute $m", block.none { it in scales })
        }
    }

    @Test fun `the longest stall is 2 minutes`() {
        val frames = (0 until 60).map { ClockFace.render(10, it, side, dial) }
        var run = 1
        var longest = 0
        for (m in 1 until 120) {
            if (frames[m % 60].contentEquals(frames[(m - 1) % 60])) run++ else { longest = maxOf(longest, run); run = 1 }
        }
        assertEquals(2, longest)
    }

    @Test fun `golden frame at 00h00`() = assertGolden(0, 0, listOf(
        "         ...#...         ",
        "       .....#.....       ",
        "     .+.....#.....+.     ",
        "    ...+.........+...    ",
        "   .....+.......+.....   ",
        "  .....................  ",
        "  +.........##........+  ",
        " ..+........##.......+.. ",
        " ...+...............+... ",
        ".........................",
        ".........................",
        ".........................",
        "+++...................+++",
        ".........................",
        ".........................",
        ".........................",
        " ...+...............+... ",
        " ..+.................+.. ",
        "  +...................+  ",
        "  .....................  ",
        "   .....+.......+.....   ",
        "    ...+.........+...    ",
        "     .+.....+.....+.     ",
        "       .....+.....       ",
        "         ...+...         ",
    ))

    @Test fun `golden frame at 03h15`() = assertGolden(3, 15, listOf(
        "         ...+...         ",
        "       .....+.....       ",
        "     .+.....+.....+.     ",
        "    ...+.........+...    ",
        "   .....+.......+.....   ",
        "  .....................  ",
        "  +...................+  ",
        " ..+.................+.. ",
        " ...+...............+... ",
        ".........................",
        ".........................",
        ".........................",
        "+++...............##..###",
        "..................##.....",
        ".........................",
        ".........................",
        " ...+...............+... ",
        " ..+.................+.. ",
        "  +...................+  ",
        "  .....................  ",
        "   .....+.......+.....   ",
        "    ...+.........+...    ",
        "     .+.....+.....+.     ",
        "       .....+.....       ",
        "         ...+...         ",
    ))

    @Test fun `golden frame at 06h30`() = assertGolden(6, 30, listOf(
        "         ...+...         ",
        "       .....+.....       ",
        "     .+.....+.....+.     ",
        "    ...+.........+...    ",
        "   .....+.......+.....   ",
        "  .....................  ",
        "  +...................+  ",
        " ..+.................+.. ",
        " ...+...............+... ",
        ".........................",
        ".........................",
        ".........................",
        "+++...................+++",
        ".........................",
        ".........................",
        ".........................",
        " ...+...............+... ",
        " ..+.................+.. ",
        "  +.........##........+  ",
        "  ..........##.........  ",
        "   .....+.......+.....   ",
        "    ...+.........+...    ",
        "     .+.....#.....+.     ",
        "       .....#.....       ",
        "         ...#...         ",
    ))

    @Test fun `golden frame at 09h45`() = assertGolden(9, 45, listOf(
        "         ...+...         ",
        "       .....+.....       ",
        "     .+.....+.....+.     ",
        "    ...+.........+...    ",
        "   .....+.......+.....   ",
        "  .....................  ",
        "  +...................+  ",
        " ..+.................+.. ",
        " ...+...............+... ",
        ".........................",
        ".........................",
        ".........................",
        "###...##..............+++",
        "......##.................",
        ".........................",
        ".........................",
        " ...+...............+... ",
        " ..+.................+.. ",
        "  +...................+  ",
        "  .....................  ",
        "   .....+.......+.....   ",
        "    ...+.........+...    ",
        "     .+.....+.....+.     ",
        "       .....+.....       ",
        "         ...+...         ",
    ))

    @Test fun `golden frame at 11h59`() = assertGolden(11, 59, listOf(
        "         ...+...         ",
        "       .....+.....       ",
        "     .#.....+.....+.     ",
        "    ...#.........+...    ",
        "   .....#.......+.....   ",
        "  .....................  ",
        "  +........##.........+  ",
        " ..+.......##........+.. ",
        " ...+...............+... ",
        ".........................",
        ".........................",
        ".........................",
        "+++...................+++",
        ".........................",
        ".........................",
        ".........................",
        " ...+...............+... ",
        " ..+.................+.. ",
        "  +...................+  ",
        "  .....................  ",
        "   .....+.......+.....   ",
        "    ...+.........+...    ",
        "     .+.....+.....+.     ",
        "       .....+.....       ",
        "         ...+...         ",
    ))

    /** `#` full, `+` dim, `.` an LED that is off, space no LED. */
    private fun assertGolden(hour: Int, minute: Int, rows: List<String>) {
        val frame = ClockFace.render(hour, minute, side, dial)
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
