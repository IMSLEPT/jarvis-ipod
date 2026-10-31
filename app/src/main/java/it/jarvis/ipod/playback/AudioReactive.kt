package it.jarvis.ipod.playback

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.exp

/** Audio-thread output only; no microphone, permissions, file writes or audio modification. */
object AudioReactive {
    @Volatile var enabled = true
    @Volatile var playing = false
    @Volatile var playerVolume = 1f
    @Volatile private var rawLevel = 0f
    @Volatile private var beatAt = 0L
    @Volatile private var beatStrength = 0f
    @Volatile private var bufferAt = 0L

    fun reset() { rawLevel = 0f; beatStrength = 0f; beatAt = 0L; bufferAt = 0L }
    fun level(now: Long): Float = if (!enabled || !playing || now - bufferAt > 400) 0f else rawLevel
    fun beat(now: Long): Float = if (!enabled || !playing || beatAt == 0L || now - bufferAt > 400) 0f
        else beatStrength * exp(-(now - beatAt).coerceAtLeast(0) / 170f)
    fun accept(meter: BassDetector.Meter, now: Long) {
        rawLevel = meter.level
        if (meter.beat > 0f) { beatStrength = meter.beat; beatAt = now }
        bufferAt = now
    }
}

@androidx.annotation.OptIn(UnstableApi::class)
class BeatAnalyzer : TeeAudioProcessor.AudioBufferSink {
    private val detector = BassDetector()
    private var encoding = C.ENCODING_PCM_16BIT
    private var wasEnabled = true
    private var rate = 44100
    private var channels = 2

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        rate = sampleRateHz; channels = channelCount; this.encoding = encoding
        detector.reset(rate, channels); AudioReactive.reset()
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (!AudioReactive.enabled) { wasEnabled = false; return }
        if (!wasEnabled) { detector.reset(rate, channels); wasEnabled = true }
        if (encoding != C.ENCODING_PCM_16BIT && encoding != C.ENCODING_PCM_FLOAT) return
        detector.read(buffer, encoding == C.ENCODING_PCM_FLOAT) { AudioReactive.accept(it, SystemClock.uptimeMillis()) }
    }
}
