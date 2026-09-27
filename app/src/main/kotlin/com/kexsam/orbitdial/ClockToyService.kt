package com.kexsam.orbitdial

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
    /** True between onBind and onUnbind; an SDK callback that lands outside that is ignored. */
    private var active = false

    /* A debug build can change the dial over adb while the toy is on the panel. Watching the
       preferences rather than polling them means a slider move reaches the LEDs in the time it
       takes to write a file, instead of waiting for the next minute's EVENT_AOD. A release build
       never fires this: the receiver that writes these values is only in the debug manifest. */
    private val onDialChanged = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        dial = Dial.load(this, side)
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
        active = true
        Log.i(TAG, "onBind action=${intent?.action}")

        side = panelSide()
        dial = Dial.load(this, side)
        Dial.prefs(this).registerOnSharedPreferenceChangeListener(onDialChanged)

        GlyphMatrixManager.getInstance(applicationContext).let { gm ->
            matrix = gm
            gm.init(object : GlyphMatrixManager.Callback {
                override fun onServiceConnected(name: android.content.ComponentName?) {
                    if (!active) {
                        Log.w(TAG, "glyph service connected after unbind, ignored")
                        return
                    }
                    /* The two phones with a Glyph Matrix, told apart the way the kit does. Anything
                       else is reported, registered as a (4a) Pro and drawn on a 13 by 13, since
                       the kit gives such a phone no matrix length at all. */
                    val target = when {
                        Common.is23112() -> Glyph.DEVICE_23112
                        Common.is25111p() -> Glyph.DEVICE_25111p
                        else -> {
                            Log.w(TAG, "${android.os.Build.MODEL} is neither a Phone (3) nor a " +
                                "Phone (4a) Pro; the dial is untested here")
                            Glyph.DEVICE_25111p
                        }
                    }
                    val registered = gm.register(target)
                    side = panelSide()
                    Log.i(TAG, "registered $target = $registered, matrix side $side, " +
                        "${ClockFace.ledCount(side)} LEDs, " +
                        "${ClockFace.minutePositions(side, dial.clamped(side))} minute positions")
                    Log.i(TAG, "dial: $dial")
                    draw("bind")
                    scheduleMinute()
                }
                override fun onServiceDisconnected(name: android.content.ComponentName?) {
                    Log.w(TAG, "glyph service disconnected")
                    last = null                 // whatever comes back gets a full frame
                }
            })
        }
        return messenger.binder
    }

    override fun onUnbind(intent: Intent?): Boolean {
        Log.i(TAG, "onUnbind after ${System.currentTimeMillis() - boundAt} ms")
        active = false
        handler.removeCallbacks(minuteTick)
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
        if (side <= 0) side = panelSide()

        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR)
        val minute = now.get(Calendar.MINUTE)
        val drawn = dial.clamped(side)
        if (drawn != dial) Log.w(TAG, "dial clamped to what the panel can draw: $drawn")
        val frame = ClockFace.render(hour, minute, side, drawn)

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

    /* A carousel visit on the Phone (3) gets no EVENT_AOD, so without this the dial would keep
       the minute it was bound at for as long as the visit lasts (the user sets that, up to 30
       minutes). This redraws on every minute boundary regardless; on an always-on bind it lands a
       few milliseconds after EVENT_AOD, finds the frame unchanged and pushes nothing. */
    private val minuteTick = Runnable {
        if (!active) return@Runnable
        draw("minute")
        scheduleMinute()
    }

    private fun scheduleMinute() {
        handler.removeCallbacks(minuteTick)
        val now = System.currentTimeMillis()
        handler.postDelayed(minuteTick, 60_000 - now % 60_000 + 50)
    }

    /** The kit's matrix length for this phone, or a (4a) Pro's where the kit has none. */
    private fun panelSide(): Int =
        Common.getDeviceMatrixLength().takeIf { it > 0 } ?: Glyph.DEVICE_25111p_MATRIX_LENGTH

    private companion object {
        const val TAG = "OrbitDial"
    }
}
