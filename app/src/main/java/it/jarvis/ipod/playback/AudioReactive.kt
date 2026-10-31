package it.jarvis.ipod.playback

import android.os.SystemClock
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sqrt

/** Livelli audio condivisi tra il lettore (thread audio) e l'interfaccia. Non richiede alcun permesso. */
object AudioReactive {
    @Volatile var rawLevel = 0f
    @Volatile var beatAt = 0L
    @Volatile var beatStrength = 0f
    @Volatile var bufferAt = 0L

    /** Volume corrente 0..1; zero se il lettore non sta producendo audio. */
    fun level(now: Long): Float = if (now - bufferAt > 400) 0f else rawLevel
    /** Impulso del colpo di basso: 1 sul beat, poi decade in ~0,2 s. */
    fun beat(now: Long): Float = if (beatAt == 0L) 0f else beatStrength * exp(-(now - beatAt) / 170f)
}

/** Ascolta i campioni PCM che ExoPlayer sta per riprodurre: volume e battiti dai bassi (< ~140 Hz). */
@androidx.annotation.OptIn(UnstableApi::class)
class BeatAnalyzer : TeeAudioProcessor.AudioBufferSink {
    private var rate = 44100
    private var channels = 2
    private var encoding = C.ENCODING_PCM_16BIT
    private var low = 0f
    private var average = 0f
    private var lastBeat = 0L

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        rate = sampleRateHz.coerceAtLeast(8000); channels = channelCount.coerceAtLeast(1); this.encoding = encoding; low = 0f
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        val bytes = when (encoding) { C.ENCODING_PCM_16BIT -> 2; C.ENCODING_PCM_FLOAT -> 4; else -> return }
        val data = buffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN)
        val frames = data.remaining() / (bytes * channels)
        if (frames <= 0) return
        val alpha = (1.0 - exp(-2.0 * PI * 140.0 / rate)).toFloat()
        var lowSum = 0.0
        var allSum = 0.0
        repeat(frames) {
            var mono = 0f
            repeat(channels) { mono += if (bytes == 2) data.getShort() / 32768f else data.getFloat() }
            mono /= channels
            low += alpha * (mono - low)
            lowSum += low * low
            allSum += mono * mono
        }
        val lowRms = sqrt(lowSum / frames).toFloat()
        val rms = sqrt(allSum / frames).toFloat()
        val now = SystemClock.uptimeMillis()
        // Media mobile (~1 s) del livello dei bassi: un battito è un picco chiaramente sopra la media.
        average += (frames.toFloat() / rate).coerceAtMost(1f) * (lowRms - average)
        if (lowRms > average * 1.35f + 0.015f && now - lastBeat > 170) {
            lastBeat = now
            AudioReactive.beatStrength = ((lowRms - average) / (average + 0.05f) + 0.4f).coerceIn(0.45f, 1f)
            AudioReactive.beatAt = now
        }
        AudioReactive.rawLevel = (rms * 3f).coerceIn(0f, 1f)
        AudioReactive.bufferAt = now
    }
}
