package it.jarvis.ipod.ui

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*

class WheelAccumulatorTest {
    private fun point(degrees: Double) = (cos(degrees * PI / 180) * 100).toFloat() to (sin(degrees * PI / 180) * 100).toFloat()
    private fun turn(wheel: WheelAccumulator, from: Int, to: Int, step: Int): Int {
        val start = point(from.toDouble()); wheel.begin(start.first, start.second)
        var count = 0
        var angle = from + step
        while(if(step > 0) angle <= to else angle >= to) {
            val p = point(angle.toDouble()); count += wheel.move(p.first, p.second); angle += step
        }
        return count
    }
    @Test fun clockwiseAndCounterclockwiseAreSymmetric() {
        val clockwise = turn(WheelAccumulator(), 0, 360, 5)
        val reverse = turn(WheelAccumulator(), 360, 0, -5)
        assertTrue(clockwise in 23..24)
        assertEquals(-clockwise, reverse)
    }
    @Test fun crossesAngleBoundaryWithoutJumping() {
        val wheel = WheelAccumulator()
        val p = point(175.0); wheel.begin(p.first, p.second)
        val q = point(-175.0)
        assertEquals(0, wheel.move(q.first, q.second))
        val r = point(-165.0)
        assertEquals(1, wheel.move(r.first, r.second))
    }
    @Test fun sensitivityChangesRate() {
        val slow = turn(WheelAccumulator(.5f), 0, 180, 5)
        val fast = turn(WheelAccumulator(2f), 0, 180, 5)
        assertTrue(fast >= slow * 3)
    }
    @Test fun jitterDoesNotAccumulateIntoClicks() {
        val wheel = WheelAccumulator(); wheel.begin(100f, 0f)
        var count = 0
        repeat(100) { count += wheel.move(100f, 1f); count += wheel.move(100f, 0f) }
        assertEquals(0, count)
    }
    @Test fun discontinuousMotionIsRejected() {
        val wheel = WheelAccumulator(); wheel.begin(100f, 0f)
        assertEquals(0, wheel.move(-100f, 0f))
    }
    @Test fun centerAndRingAreDistinct() {
        assertFalse(WheelAccumulator.onRing(0f, 0f, 100f))
        assertTrue(WheelAccumulator.onRing(0f, -80f, 100f))
        assertEquals("select", WheelAccumulator.button(0f, 0f, 100f))
        assertEquals("menu", WheelAccumulator.button(0f, -80f, 100f))
        assertEquals("next", WheelAccumulator.button(80f, 0f, 100f))
        assertEquals("previous", WheelAccumulator.button(-80f, 0f, 100f))
        assertEquals("play", WheelAccumulator.button(0f, 80f, 100f))
    }
}
