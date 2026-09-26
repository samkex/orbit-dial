package com.kexsam.orbitdial

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * The dial, as one frame of brightness.
 *
 * The face was designed in a browser model before it was written here, and this is a direct
 * port of that model, so what the design shows is what the panel gets.
 *
 * Twelve scales around the rim, all the same length. The current hour is told apart by
 * brightness alone and never grows an arm; an earlier version lengthened it and that pushed the
 * minute's orbit inward for a distinction the brightness was already making. Inside them, one
 * dot for the minute.
 */
object ClockFace {

    /**
     * The disc of positions that physically exist.
     *
     * The allocation is exactly a disc of radius `side / 2`, checked cell by cell against the
     * developer kit's own allocation diagram: 137 of the 169 grid positions on the Phone (4a)
     * Pro. So the mask is a radius test rather than a table.
     */
    fun hasLed(col: Int, row: Int, side: Int): Boolean {
        val c = (side - 1) / 2.0
        return hypot(col - c, row - c) <= side / 2.0
    }

    fun ledCount(side: Int): Int =
        (0 until side).sumOf { r -> (0 until side).count { c -> hasLed(c, r, side) } }

    /** Degrees run clockwise from twelve o'clock, so sin for x and -cos for y. */
    private fun polarCell(radius: Double, deg: Double, side: Int): Pair<Int, Int> {
        val c = (side - 1) / 2.0
        val a = Math.toRadians(deg)
        return (c + radius * sin(a)).roundToInt() to (c - radius * cos(a)).roundToInt()
    }

    /**
     * One hour scale: the rim cell for that hour, then a walk inward, one grid step at a time,
     * in whichever of the eight directions points most nearly at the centre.
     *
     * Solving the polar position again at a smaller radius is the obvious alternative and it is
     * wrong in a way that only shows on the panel. At one o'clock radius 6 gives (9,1) and
     * radius 5 gives (9,2): the same column, so the mark reads as a vertical pair rather than a
     * stroke aimed at the middle. Stepping gives (9,1) then (8,2), a diagonal, and the dial
     * reads as twelve ticks. Checked cell by cell against the intended arrangement for all
     * twelve hours.
     */
    fun scaleRay(deg: Double, length: Int, side: Int): List<Pair<Int, Int>> {
        val centre = (side - 1) / 2.0
        val outer = (side / 2.0).toInt().toDouble()
        var (c, r) = polarCell(outer, deg, side)
        val out = mutableListOf(c to r)
        for (k in 1 until length) {
            val dx = centre - c
            val dy = centre - r
            val m = hypot(dx, dy)
            if (m < 0.5) break          // already at the middle, nowhere left to go
            c += (dx / m).roundToInt()
            r += (dy / m).roundToInt()
            out += c to r
        }
        return out
    }

    /**
     * The frame for a given time, row-major, one value per addressing position.
     *
     * @param side the device's matrix length, from `Common.getDeviceMatrixLength()`
     */
    fun render(hour: Int, minute: Int, side: Int, dial: Dial = Dial.DEFAULT): IntArray {
        val frame = IntArray(side * side)
        fun put(c: Int, r: Int, v: Int) {
            if (c in 0 until side && r in 0 until side && hasLed(c, r, side)) {
                val i = r * side + c
                if (v > frame[i]) frame[i] = v
            }
        }

        val h12 = ((hour % 12) + 12) % 12
        for (h in 0 until 12) {
            val value = if (h == h12) dial.full else dial.dim
            for ((c, r) in scaleRay(h * 30.0, dial.scaleLength, side)) put(c, r, value)
        }

        /* Centred on the exact polar point rather than on a cell, so an even-sided block is
           not biased a half cell one way. Cells with no LED behind them are dropped by put. */
        val a = Math.toRadians(minute * 6.0)
        val centre = (side - 1) / 2.0
        val fx = centre + dial.minuteOrbit * sin(a)
        val fy = centre - dial.minuteOrbit * cos(a)
        val left = (fx - (dial.minuteSize - 1) / 2.0).roundToInt()
        val top = (fy - (dial.minuteSize - 1) / 2.0).roundToInt()
        for (dy in 0 until dial.minuteSize) for (dx in 0 until dial.minuteSize) {
            put(left + dx, top + dy, dial.full)
        }
        return frame
    }

    /**
     * How many distinct positions the minute mark can occupy on this device with this dial.
     *
     * Sixty minutes are not sixty positions and cannot be: the ring the mark travels passes
     * through far fewer cells than that. The shipped 2 by 2 mark at orbit 1.5 on a 13 by 13 has
     * twelve, and stands still for up to seven minutes at a time. Counted over the mark's whole
     * footprint, not its centre cell, so it matches what is visible on the panel. Kept here so
     * the number is checkable rather than remembered.
     */
    fun minutePositions(side: Int, dial: Dial = Dial.DEFAULT): Int {
        val centre = (side - 1) / 2.0
        val seen = mutableSetOf<Set<Pair<Int, Int>>>()
        for (m in 0 until 60) {
            val a = Math.toRadians(m * 6.0)
            val fx = centre + dial.minuteOrbit * sin(a)
            val fy = centre - dial.minuteOrbit * cos(a)
            val left = (fx - (dial.minuteSize - 1) / 2.0).roundToInt()
            val top = (fy - (dial.minuteSize - 1) / 2.0).roundToInt()
            val cells = mutableSetOf<Pair<Int, Int>>()
            for (dy in 0 until dial.minuteSize) for (dx in 0 until dial.minuteSize) {
                val c = left + dx
                val r = top + dy
                // The same filter render applies: a cell off the grid or with no LED is not a position.
                if (c in 0 until side && r in 0 until side && hasLed(c, r, side)) cells += c to r
            }
            seen += cells
        }
        return seen.size
    }

    /** True when two frames would look identical, so an unchanged minute costs no push. */
    fun same(a: IntArray?, b: IntArray): Boolean =
        a != null && a.size == b.size && a.indices.all { abs(a[it] - b[it]) == 0 }
}
