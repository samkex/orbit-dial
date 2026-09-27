package com.kexsam.orbitdial

import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.atan2
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

    /**
     * One hour scale: a straight line of [length] cells, starting on the rim and running inward
     * along whichever of the eight grid directions points most nearly at the centre, placed where
     * its cells sit closest to the hour's angle on average.
     *
     * Straight, because a scale that bends reads as uneven. Walking inward and re-aiming at the
     * centre at every step, which is what this did before, gives the same cells up to two cells
     * long on both panels, and bends eight of the twelve scales at three cells on the Phone (3):
     * at two o'clock (22,6), (21,7), then sideways to (20,7). This rule gives (22,6), (21,7), (20,8).
     * "Rim" means the first cell is an LED and the cell outward of it along the line is not.
     */
    fun scaleRay(deg: Double, length: Int, side: Int): List<Pair<Int, Int>> {
        val centre = (side - 1) / 2.0
        val a = Math.toRadians(deg)
        val inX = -sin(a)
        val inY = cos(a)              // rows grow downward, so inward from twelve is +row
        val (dx, dy) = DIRECTIONS.maxByOrNull { (x, y) -> (x * inX + y * inY) / hypot(x.toDouble(), y.toDouble()) }!!

        fun offHour(c: Int, r: Int): Double {
            val at = (Math.toDegrees(atan2(c - centre, centre - r)) + 360.0) % 360.0
            val d = abs(at - deg) % 360.0
            return min(d, 360.0 - d)
        }

        var best: List<Pair<Int, Int>> = emptyList()
        var bestError = Double.MAX_VALUE
        for (c in 0 until side) for (r in 0 until side) {
            if (!hasLed(c, r, side) || hasLed(c - dx, r - dy, side)) continue
            val cells = (0 until length).map { k -> (c + dx * k) to (r + dy * k) }
            if (!cells.all { (x, y) -> x in 0 until side && y in 0 until side && hasLed(x, y, side) }) continue
            val error = cells.sumOf { (x, y) -> offHour(x, y) } / length
            if (error < bestError) { bestError = error; best = cells }
        }
        return best
    }

    /**
     * A block's corner, to the nearest cell, with a half cell always going up.
     *
     * The tolerance is not cosmetic. On the Phone (3) at orbit 6.0 the corner lands exactly on a
     * half cell in twelve minutes of the hour, and at two of them, 20 and 55 past, the JVM's cosine
     * or sine comes out one bit under the half (14.4999… and 8.4999…) where Python's is on it. A
     * plain half-up round then puts the phone and the generated images a cell apart. Adding 1e-9
     * before the floor sends every one of those halves up on every platform. With the shipped
     * (4a) Pro dial no corner is affected.
     */
    private fun toCell(x: Double): Int = floor(x + 0.5 + 1e-9).toInt()

    /** The eight grid steps. Their order would only matter on a tie, and no hour angle makes one. */
    private val DIRECTIONS = listOf(1 to 0, 1 to 1, 0 to 1, -1 to 1, -1 to 0, -1 to -1, 0 to -1, 1 to -1)

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

        for ((c, r) in minuteCells(minute, side, dial)) put(c, r, dial.full)
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
    fun minutePositions(side: Int, dial: Dial = Dial.DEFAULT): Int =
        (0 until 60).map { minuteCells(it, side, dial) }.toSet().size

    /**
     * The cells the minute mark lights at [minute], on the panel: the block centred on the exact
     * polar point, so an even-sided block is not biased a half cell one way, with cells off the
     * grid or without an LED left out. [render], [minutePositions] and the tests all read the
     * mark from here, so they cannot disagree about where it is.
     */
    fun minuteCells(minute: Int, side: Int, dial: Dial = Dial.DEFAULT): Set<Pair<Int, Int>> {
        val a = Math.toRadians(minute * 6.0)
        val centre = (side - 1) / 2.0
        val left = toCell(centre + dial.minuteOrbit * sin(a) - (dial.minuteSize - 1) / 2.0)
        val top = toCell(centre - dial.minuteOrbit * cos(a) - (dial.minuteSize - 1) / 2.0)
        val cells = mutableSetOf<Pair<Int, Int>>()
        for (dy in 0 until dial.minuteSize) for (dx in 0 until dial.minuteSize) {
            val c = left + dx
            val r = top + dy
            if (c in 0 until side && r in 0 until side && hasLed(c, r, side)) cells += c to r
        }
        return cells
    }

    /** True when two frames would look identical, so an unchanged minute costs no push. */
    fun same(a: IntArray?, b: IntArray): Boolean = a?.contentEquals(b) == true
}
