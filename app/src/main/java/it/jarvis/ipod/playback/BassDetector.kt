package it.jarvis.ipod.playback

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sqrt

/** Read-only PCM meter. Fixed 10 ms windows are independent of decoder buffer size. */
class BassDetector {
    data class Meter(val level: Float, val bass: Float, val beat: Float)
    private var channels = 2
    private var alpha = 0f
    private var windowFrames = 441
    private var low = FloatArray(2)
    private var count = 0
    private var total = 0.0
    private var bass = 0.0
    private var average = 0f
    private var elapsedFrames = 0L
    private var lastBeatFrame = Long.MIN_VALUE / 2
    private var rate = 44100

    fun reset(sampleRate: Int, channelCount: Int) {
        rate = sampleRate.coerceAtLeast(8000)
        channels = channelCount.coerceAtLeast(1)
        alpha = (1 - exp(-2 * PI * 140 / rate)).toFloat()
        windowFrames = (rate / 100).coerceAtLeast(1)
        low = FloatArray(channels)
        count = 0; total = 0.0; bass = 0.0; average = 0f
        elapsedFrames = 0; lastBeatFrame = Long.MIN_VALUE / 2
    }

    /** Neither samples nor position/limit of the player's buffer are changed. */
    fun read(buffer: ByteBuffer, floatPcm: Boolean, onWindow: (Meter) -> Unit) {
        val bytes = if (floatPcm) 4 else 2
        val data = buffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
        while (data.remaining() >= channels * bytes) {
            repeat(channels) { channel ->
                val sample = if (floatPcm) data.float.let { if (it.isFinite()) it.coerceIn(-1f, 1f) else 0f }
                    else data.short / 32768f
                low[channel] += alpha * (sample - low[channel])
                // Per-channel energy: opposite stereo phases must not cancel.
                total += sample * sample / channels
                bass += low[channel] * low[channel] / channels
            }
            elapsedFrames++; count++
            if (count == windowFrames) {
                val rms = sqrt(total / count).toFloat()
                val lowRms = sqrt(bass / count).toFloat()
                val onset = lowRms > average * 1.45f + .012f && elapsedFrames - lastBeatFrame >= rate * .17
                val beat = if (onset) ((lowRms - average) * 5f).coerceIn(.15f, 1f) else 0f
                if (onset) lastBeatFrame = elapsedFrames
                average += .01f * (lowRms - average)
                onWindow(Meter((rms * 3).coerceIn(0f, 1f), lowRms, beat))
                count = 0; total = 0.0; bass = 0.0
            }
        }
    }
}
