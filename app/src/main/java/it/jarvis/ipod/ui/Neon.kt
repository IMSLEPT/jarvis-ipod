package it.jarvis.ipod.ui

import android.os.SystemClock
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import it.jarvis.ipod.playback.AudioReactive
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min

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

    fun kick() { kickAt = SystemClock.uptimeMillis(); offset += 28f }

    fun tick(frameNanos: Long) {
        val now = SystemClock.uptimeMillis()
        val nav = if (kickAt == 0L) 0f else exp(-(now - kickAt) / 230f)
        val beat = AudioReactive.beat(now)
        val level = AudioReactive.level(now)
        hue = ((frameNanos / 1_000_000_000.0) * 36.0 + offset + beat * 40.0).rem(360.0).toFloat()
        energy = min(1f, max(nav * .9f, beat) + level * .25f)
    }
}
