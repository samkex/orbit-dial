package dev.glyphclock

import android.app.Service
import android.content.Intent
import android.content.SharedPreferences
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Message
import android.os.Messenger
import android.util.Log
import com.nothing.ketchum.Common
import com.nothing.ketchum.Glyph
import com.nothing.ketchum.GlyphMatrixManager
import com.nothing.ketchum.GlyphToy
import java.util.Calendar

/**
 * The clock, as a Glyph Toy.
 *
 * On Phone (4a) Pro this is the only shape the app can take. The developer kit's device table
 * says `DEVICE_25111p` has no Glyph Touch and supports AOD toys only, so there is no button to
 * react to and no carousel visit to animate for: the toy is selected once as the always-on toy
 * and then simply shows the time.
 *
 * `EVENT_AOD` arrives once a minute, which is exactly the resolution a clock needs. Measured on
 * a Phone (4a) Pro, that tick lands on the wall-clock minute within about 20 ms, not a minute
 * after the bind, so the dial changes when the minute does. The timing in the log below is what
 * that was read from.
 */
class ClockToyService : Service() {

    private var matrix: GlyphMatrixManager? = null
    private var side = 0
    private var last: IntArray? = null
    private var boundAt = 0L
    private var dial = Dial.DEFAULT

    /* A debug build can change the dial over adb while the toy is on the panel. Watching the
       preferences rather than polling them means a slider move reaches the LEDs in the time it
       takes to write a file, instead of waiting for the next minute's EVENT_AOD. A release build
       never fires this: the receiver that writes these values is only in the debug manifest. */
    private val onDialChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        dial = Dial.load(this)
        last = null                     // force a push, even if the time has not moved
        draw("tuned")
    }

    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            /* Log every message before branching. A toy that only logs what it recognises tells
               you nothing on the run where it recognises nothing, which is the run you are
               debugging. */
            val data = msg.data?.getString(GlyphToy.MSG_GLYPH_TOY_DATA)
            Log.i(TAG, "message what=${msg.what} data=$data")
            if (msg.what == GlyphToy.MSG_GLYPH_TOY && data == GlyphToy.EVENT_AOD) draw("aod")
            else super.handleMessage(msg)
        }
    }
    private val messenger = Messenger(handler)

    override fun onBind(intent: Intent?): IBinder {
        boundAt = System.currentTimeMillis()
        val isAod = runCatching { intent?.getBooleanExtra(EXTRA_AOD, false) == true }.getOrDefault(false)
        Log.i(TAG, "onBind action=${intent?.action} isAod=$isAod")

        dial = Dial.load(this)
        Dial.prefs(this).registerOnSharedPreferenceChangeListener(onDialChanged)

        GlyphMatrixManager.getInstance(applicationContext).let { gm ->
            matrix = gm
            gm.init(object : GlyphMatrixManager.Callback {
                override fun onServiceConnected(name: android.content.ComponentName?) {
                    /* Registered for the (4a) Pro unconditionally. The toy has only ever been
                       built and tested for that device, so any other one is reported rather than
                       quietly assumed to behave the same. */
                    if (!Common.is25111p()) {
                        Log.w(TAG, "this is ${android.os.Build.MODEL}, not a Phone (4a) Pro; " +
                            "the dial is untested here")
                    }
                    val target = Glyph.DEVICE_25111p
                    val registered = gm.register(target)
                    side = Common.getDeviceMatrixLength()
                    Log.i(TAG, "registered $target = $registered, matrix side $side, " +
                        "${ClockFace.ledCount(side)} LEDs, " +
                        "${ClockFace.minutePositions(side, dial)} minute positions")
                    Log.i(TAG, "dial: $dial")
                    draw("bind")
                }
                override fun onServiceDisconnected(name: android.content.ComponentName?) {
                    Log.w(TAG, "glyph service disconnected")
                }
            })
        }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "onUnbind after ${System.currentTimeMillis() - boundAt} ms")
        runCatching { Dial.prefs(this).unregisterOnSharedPreferenceChangeListener(onDialChanged) }
        runCatching { matrix?.unInit() }.onFailure { Log.w(TAG, "unInit failed: $it") }
        matrix = null
        last = null
        return false
    }

    /**
     * Draws the current time, and says what it drew.
     *
     * The frame is skipped when it would be identical to the one already showing, which happens
     * whenever a tick arrives inside a minute the dial cannot distinguish anyway. The log line
     * carries the wall-clock time to the millisecond so the tick's cadence can be read straight
     * out of logcat rather than inferred.
     */
    private fun draw(reason: String) {
        val gm = matrix ?: run {
            Log.w(TAG, "draw($reason) before the manager was ready")
            return
        }
        if (side <= 0) side = Common.getDeviceMatrixLength()

        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR)
        val minute = now.get(Calendar.MINUTE)
        val frame = ClockFace.render(hour, minute, side, dial)

        if (ClockFace.same(last, frame)) {
            Log.i(TAG, "$reason: %02d:%02d unchanged, nothing pushed".format(hour, minute))
            return
        }
        runCatching { gm.setMatrixFrame(frame) }
            .onSuccess {
                last = frame
                Log.i(TAG, "$reason: %02d:%02d drawn at %tT.%<tL, %d lit"
                    .format(hour, minute, now, frame.count { it > 0 }))
            }
            .onFailure { Log.e(TAG, "$reason: setMatrixFrame refused: $it") }
    }

    private companion object {
        const val TAG = "GlyphClock"

        /** Undocumented, and the only way to tell an always-on bind from a carousel one. */
        const val EXTRA_AOD = "isAod"
    }
}
