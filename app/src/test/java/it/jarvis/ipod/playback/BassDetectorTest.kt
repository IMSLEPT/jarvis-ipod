package it.jarvis.ipod.playback

import org.junit.Assert.*
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

class BassDetectorTest {
    private fun tone(hz: Int, amplitude: Float, seconds: Float = 1f, rate: Int = 48000,
        channels: Int = 2, floatPcm: Boolean = false, opposite: Boolean = false): ByteBuffer {
        val frames = (seconds * rate).toInt()
        return ByteBuffer.allocate(frames * channels * if (floatPcm) 4 else 2).order(ByteOrder.LITTLE_ENDIAN).apply {
            repeat(frames) { frame -> repeat(channels) { channel ->
                val value = (sin(2 * PI * hz * frame / rate) * amplitude * if (opposite && channel == 1) -1 else 1).toFloat()
                if (floatPcm) putFloat(value) else putShort((value * 32767).toInt().toShort())
            } }
            flip()
        }
    }
    private fun analyze(buffer: ByteBuffer, floatPcm: Boolean = false, rate: Int = 48000, channels: Int = 2): List<BassDetector.Meter> =
        mutableListOf<BassDetector.Meter>().also { out -> BassDetector().apply { reset(rate, channels); read(buffer, floatPcm) { out += it } } }

    @Test fun silenceHasNoLevelOrBeats() {
        val values = analyze(tone(60, 0f))
        assertEquals(100, values.size)
        assertTrue(values.all { it.level == 0f && it.beat == 0f })
    }
    @Test fun bassPulseTriggersAndTrebleDoesNot() {
        assertTrue(analyze(tone(60, .5f, .10f)).any { it.beat > 0f })
        assertTrue(analyze(tone(8000, .5f, .10f)).all { it.beat == 0f })
    }
    @Test fun louderSignalIncreasesLevel() {
        val soft = analyze(tone(60, .05f)).map { it.level }.average()
        val loud = analyze(tone(60, .3f)).map { it.level }.average()
        assertTrue(loud > soft * 4)
    }
    @Test fun stereoOppositePhaseStillReacts() {
        val normal = analyze(tone(60, .2f))
        val opposite = analyze(tone(60, .2f, opposite = true))
        assertEquals(normal.last().level, opposite.last().level, .0001f)
        assertTrue(opposite.any { it.beat > 0f })
    }
    @Test fun readsWithoutChangingBufferAndIgnoresBytesOutsideLimit() {
        val input = tone(60, .2f)
        val copy = input.array().clone()
        input.position(8); input.limit(input.limit() - 12)
        val position = input.position(); val limit = input.limit()
        analyze(input)
        assertEquals(position, input.position()); assertEquals(limit, input.limit())
        assertArrayEquals(copy, input.array())
    }
    @Test fun detectionIsIndependentOfDecoderBufferBoundaries() {
        val input = tone(60, .25f)
        val expected = analyze(input)
        val detector = BassDetector().apply { reset(48000, 2) }
        val actual = mutableListOf<BassDetector.Meter>()
        while (input.hasRemaining()) {
            val size = minOf(308, input.remaining())
            val part = input.slice().apply { limit(size) }
            detector.read(part, false) { actual += it }
            input.position(input.position() + size)
        }
        assertEquals(expected, actual)
    }
    @Test fun floatPcmAndMonoAt44100AreSupported() {
        assertTrue(analyze(tone(60, .2f, rate = 44100, channels = 1, floatPcm = true), true, 44100, 1).any { it.beat > 0 })
    }
    @Test fun nonFiniteFloatsDoNotBreakMeter() {
        val input = ByteBuffer.allocate(480 * 4).order(ByteOrder.LITTLE_ENDIAN).apply {
            repeat(480) { putFloat(if(it % 2 == 0) Float.NaN else Float.POSITIVE_INFINITY) }; flip()
        }
        assertTrue(analyze(input, true, channels = 1).all { it.level == 0f && it.beat == 0f })
    }
    @Test fun resetClearsPreviousBassEnergy() {
        val detector = BassDetector().apply { reset(48000, 2) }
        detector.read(tone(60, .5f), false) {}
        detector.reset(48000, 2)
        detector.read(tone(60, 0f), false) { assertEquals(0f, it.level); assertEquals(0f, it.beat) }
    }
    @Test fun repeatedKicksRemainDetectableAfterSilence() {
        val detector = BassDetector().apply { reset(48000, 2) }
        var beats = 0
        repeat(4) {
            detector.read(tone(60, .5f, .08f), false) { if(it.beat > 0) beats++ }
            detector.read(tone(60, 0f, .42f), false) {}
        }
        assertEquals(4, beats)
    }
}
