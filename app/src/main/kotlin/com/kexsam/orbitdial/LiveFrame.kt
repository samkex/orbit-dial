package com.kexsam.orbitdial

/**
 * A frame sent from a design tool while it previews on the phone, shown in place of the
 * dial so everything the tool draws (time, minute hand, freehand) reaches the panel as drawn.
 *
 * Only a debug build can receive one: the receiver that writes it exists in the debug manifest
 * alone, so in a release build [KEY_FRAME] is never set and the dial always draws itself.
 *
 * Encoded as three hex digits per position, row-major, 0..2047 each, so a Phone (3) frame is
 * 1875 characters and fits one adb broadcast. A frame is shown for [HOLD_MS] after it last
 * arrived; the tool resends while it previews, so a bridge that dies leaves the panel on the dial
 * again within that time rather than frozen on the last frame.
 */
object LiveFrame {
    const val KEY_FRAME = "live_frame"
    const val KEY_AT = "live_at"
    const val HOLD_MS = 30_000L

    fun encode(frame: IntArray): String =
        frame.joinToString("") { it.coerceIn(0, Dial.MAX_BRIGHTNESS).toString(16).padStart(3, '0') }

    /** The frame for a [side]-wide panel, or null when the text is not exactly one. */
    fun decode(text: String?, side: Int): IntArray? {
        if (text == null || side <= 0 || text.length != side * side * 3) return null
        val frame = IntArray(side * side)
        for (i in frame.indices) {
            val v = text.substring(i * 3, i * 3 + 3).toIntOrNull(16) ?: return null
            if (v !in 0..Dial.MAX_BRIGHTNESS) return null
            frame[i] = v
        }
        return frame
    }

    /** Positions with no LED are zeroed, so a frame cannot light what the panel does not have. */
    fun masked(frame: IntArray, side: Int): IntArray =
        IntArray(frame.size) { i -> if (ClockFace.hasLed(i % side, i / side, side)) frame[i] else 0 }

    fun fresh(at: Long, now: Long): Boolean = at > 0 && now - at in 0..HOLD_MS
}
