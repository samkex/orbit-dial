package com.kexsam.orbitdial

import android.content.Context
import android.content.SharedPreferences

/**
 * The five numbers that decide what the dial looks like.
 *
 * Held as data rather than constants so that a debug build can change them over adb while the
 * toy is on the panel, without a rebuild. Brightness in particular has to be judged on the
 * LEDs, not on a screen, and each guess would otherwise cost an edit, a build and an install.
 * See `tools/tuner.py`.
 *
 * [DEFAULT] is the shipped face, and a release build has no way to move off it: the receiver that
 * writes these preferences only exists in the debug manifest.
 */
data class Dial(
    /** The current hour and the minute mark. The panel's range is 0..2047, not 0..255. */
    val full: Int = 2047,
    /**
     * The other eleven scales.
     *
     * Settled by looking at the panel. The LEDs' response near the bottom of their range is not
     * a display's, so this is a hardware value and not a percentage to be recomputed.
     */
    val dim: Int = 614,
    /** Cells per scale, counted inward from the rim. */
    val scaleLength: Int = 2,
    /**
     * The minute mark's ring, in cells from the centre.
     *
     * Not 1.0: a circle of radius 1 passes through so few cells that the mark lands on six
     * distinct positions in an hour and stands still for up to sixteen minutes.
     */
    val minuteOrbit: Double = 1.5,
    /**
     * The minute mark's side, in cells.
     *
     * A block, because a single LED is too faint to pick out on this panel. At orbit 1.5 the
     * 2 by 2 changes 0.80 LEDs a minute on average, which is what makes each step noticeable.
     */
    val minuteSize: Int = 2,
) {
    /**
     * This dial kept inside what a [side]-wide panel can draw.
     *
     * A debug build can write any value over adb; rendering goes through this so a stray one
     * (negative brightness, a zero-sized mark, a non-finite orbit) cannot make the panel lie
     * about what a setting looks like. Applied by the service, not by the receiver, so adb and
     * `tools/tuner.py` cannot disagree.
     */
    fun clamped(side: Int): Dial {
        val fullOk = full.coerceIn(0, MAX_BRIGHTNESS)
        return Dial(
            full = fullOk,
            dim = dim.coerceIn(0, fullOk),
            scaleLength = scaleLength.coerceIn(1, maxOf(1, side / 2)),
            minuteOrbit = if (minuteOrbit.isFinite()) minuteOrbit.coerceIn(0.0, side / 2.0)
                          else DEFAULT.minuteOrbit,
            minuteSize = minuteSize.coerceIn(1, side),
        )
    }

    companion object {
        /** The (4a) Pro's shipped face, 13 by 13. */
        val DEFAULT = Dial()

        /**
         * The Phone (3)'s face, 25 by 25: longer scales and a wider orbit for the bigger grid.
         * Brightness starts at the (4a) Pro's values and is settled on this panel.
         */
        val PHONE_3 = Dial(scaleLength = 3, minuteOrbit = 6.0)

        /** The shipped face for a panel of this side. */
        fun defaultFor(side: Int): Dial = if (side >= 25) PHONE_3 else DEFAULT

        /** The top of `setMatrixFrame`'s range on the panel, which is 2^11 - 1, not 255. */
        const val MAX_BRIGHTNESS = 2047

        const val PREFS = "dial"
        const val KEY_FULL = "full"
        const val KEY_DIM = "dim"
        const val KEY_SCALE_LENGTH = "scale_length"
        const val KEY_MINUTE_ORBIT = "minute_orbit"
        const val KEY_MINUTE_SIZE = "minute_size"

        fun prefs(context: Context): SharedPreferences =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /** Whatever a debug build last wrote, falling back to this panel's shipped face for each value. */
        fun load(context: Context, side: Int): Dial = prefs(context).let { p ->
            val d = defaultFor(side)
            Dial(
                full = p.getInt(KEY_FULL, d.full),
                dim = p.getInt(KEY_DIM, d.dim),
                scaleLength = p.getInt(KEY_SCALE_LENGTH, d.scaleLength),
                minuteOrbit = p.getFloat(KEY_MINUTE_ORBIT, d.minuteOrbit.toFloat()).toDouble(),
                minuteSize = p.getInt(KEY_MINUTE_SIZE, d.minuteSize),
            )
        }
    }

    /** One line, for the log, so a frame can be traced back to the numbers that drew it. */
    override fun toString(): String =
        "full=$full dim=$dim scale=$scaleLength orbit=$minuteOrbit minute=${minuteSize}x$minuteSize"
}
