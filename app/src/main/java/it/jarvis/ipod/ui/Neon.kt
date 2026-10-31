package it.jarvis.ipod.ui

import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import it.jarvis.ipod.playback.AudioReactive
import kotlin.math.abs

/**
 * Stato dell'alone neon: il colore scorre di continuo, l'intensità sale con i colpi di basso della
 * musica e con ogni scatto della rotella o pressione dei tasti (che sposta anche il colore).
 */
@Stable
class NeonState {
    var hue by mutableFloatStateOf(200f)
    var energy by mutableFloatStateOf(0f)
    private var kickAt = 0L
    private var offset = 0f

    fun kick(steps: Int = 1) {
        kickAt = SystemClock.uptimeMillis()
        offset = (offset + 43f * abs(steps).coerceAtLeast(1)) % 360f
    }

    fun tick(frameNanos: Long, volume: Float) {
        val now = SystemClock.uptimeMillis()
        val nav = if (kickAt == 0L) 0f else NeonMath.interaction(now - kickAt)
        val beat = AudioReactive.beat(now)
        val level = AudioReactive.level(now)
        hue = NeonMath.hue(frameNanos / 1_000_000_000.0, offset)
        energy = NeonMath.energy(nav, beat, level, volume * AudioReactive.playerVolume)
    }
}
