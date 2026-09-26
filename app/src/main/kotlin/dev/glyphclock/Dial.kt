package dev.glyphclock

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
     * Settled on the panel over three tries: 409 read as off and left one lit mark alone on a
     * dark disc, 800 was clearly visible, 614 is where it sits. The LEDs' response near the
     * bottom of their range is not a display's, so this is a hardware value and not a
     * percentage to be recomputed.
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
     * A block rather than a dot, because one LED was too faint to find. It does not travel further:
     * at orbit 1.5 a single LED's centre shifts 0.27 cells a minute on average and a 2 by 2's 0.20.
     * What a block does is change more LEDs per step, 0.80 a minute against 0.53, so each step is
     * easier to notice.
     */
    val minuteSize: Int = 2,
) {
    companion object {
        val DEFAULT = Dial()

        const val PREFS = "dial"
        const val KEY_FULL = "full"
        const val KEY_DIM = "dim"
        const val KEY_SCALE_LENGTH = "scale_length"
        const val KEY_MINUTE_ORBIT = "minute_orbit"
        const val KEY_MINUTE_SIZE = "minute_size"

        fun prefs(context: Context): SharedPreferences =
            context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

        /** Whatever a debug build last wrote, falling back to the shipped face for each value. */
        fun load(context: Context): Dial = prefs(context).let { p ->
            Dial(
                full = p.getInt(KEY_FULL, DEFAULT.full),
                dim = p.getInt(KEY_DIM, DEFAULT.dim),
                scaleLength = p.getInt(KEY_SCALE_LENGTH, DEFAULT.scaleLength),
                minuteOrbit = p.getFloat(KEY_MINUTE_ORBIT, DEFAULT.minuteOrbit.toFloat()).toDouble(),
                minuteSize = p.getInt(KEY_MINUTE_SIZE, DEFAULT.minuteSize),
            )
        }
    }

    /** One line, for the log, so a frame can be traced back to the numbers that drew it. */
    override fun toString(): String =
        "full=$full dim=$dim scale=$scaleLength orbit=$minuteOrbit minute=${minuteSize}x$minuteSize"
}
