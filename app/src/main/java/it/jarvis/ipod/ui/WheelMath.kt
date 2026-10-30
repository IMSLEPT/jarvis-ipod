package it.jarvis.ipod.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/** Pure geometry shared by the native wheel and its tests. Angles increase clockwise on screen. */
class WheelAccumulator(var sensitivity: Float = 1f) {
    private var previous: Double? = null
    private var accumulated = 0.0
    fun begin(x: Float, y: Float) { previous = atan2(y.toDouble(), x.toDouble()); accumulated = 0.0 }
    fun move(x: Float, y: Float): Int {
        val current = atan2(y.toDouble(), x.toDouble())
        val old = previous ?: run { begin(x, y); return 0 }
        previous = current
        var delta = current - old
        if (delta > PI) delta -= 2 * PI
        if (delta < -PI) delta += 2 * PI
        if (abs(delta) > PI / 3) { accumulated = 0.0; return 0 } // reject discontinuous jumps
        accumulated += delta
        val step = PI / 12 / sensitivity.coerceIn(0.5f, 2f)
        val count = (accumulated / step).toInt()
        accumulated -= count * step
        return count
    }
    fun end() { previous = null; accumulated = 0.0 }
    companion object {
        fun onRing(x: Float, y: Float, radius: Float): Boolean = hypot(x, y) in (radius * .40f)..radius
        fun button(x: Float, y: Float, radius: Float): String {
            if (hypot(x, y) < radius * .40f) return "select"
            return if (abs(y) > abs(x)) { if (y < 0) "menu" else "play" }
                else if (x < 0) "previous" else "next"
        }
    }
}
