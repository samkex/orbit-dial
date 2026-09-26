package com.kexsam.orbitdial

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Changes the dial from adb, while the toy is on the panel. Debug builds only.
 *
 * ```
 * adb shell am broadcast -n com.kexsam.orbitdial/.TuneReceiver -a com.kexsam.orbitdial.TUNE --ei dim 700
 * adb shell am broadcast -n com.kexsam.orbitdial/.TuneReceiver -a com.kexsam.orbitdial.TUNE --ei full 2047 --ef minute_orbit 2.5
 * adb shell am broadcast -n com.kexsam.orbitdial/.TuneReceiver -a com.kexsam.orbitdial.TUNE --ez reset true
 * ```
 *
 * The component must be named with `-n`. A manifest-declared receiver does not get implicit
 * broadcasts on current Android, so the same command with `-a` alone reports
 * `Broadcast completed: result=0` and does nothing.
 *
 * It writes into the same preferences the service watches, so the LEDs change within a frame
 * rather than at the next minute's tick. `tools/tuner.py` puts an HTTP endpoint in front of
 * these commands; they are the same thing without it.
 *
 * This class and its manifest entry both live in `src/debug`, so a release build has neither and
 * the shipped dial cannot be moved at all.
 */
class TuneReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = Dial.prefs(context)
        val edit = prefs.edit()

        if (intent.getBooleanExtra("reset", false)) {
            edit.clear().apply()
            Log.i(TAG, "reset to the shipped dial: ${Dial.DEFAULT}")
            return
        }

        var touched = 0
        fun int(extra: String, key: String) {
            if (intent.hasExtra(extra)) { edit.putInt(key, intent.getIntExtra(extra, 0)); touched++ }
        }
        int("full", Dial.KEY_FULL)
        int("dim", Dial.KEY_DIM)
        int("scale_length", Dial.KEY_SCALE_LENGTH)
        int("minute_size", Dial.KEY_MINUTE_SIZE)
        if (intent.hasExtra("minute_orbit")) {
            edit.putFloat(Dial.KEY_MINUTE_ORBIT, intent.getFloatExtra("minute_orbit", 1.5f))
            touched++
        }

        if (touched == 0) {
            Log.w(TAG, "nothing to change; extras were ${intent.extras?.keySet()}")
            return
        }
        edit.apply()
        Log.i(TAG, "tuned $touched value(s) -> ${Dial.load(context)}")
    }

    private companion object {
        const val TAG = "GlyphClock"
    }
}
