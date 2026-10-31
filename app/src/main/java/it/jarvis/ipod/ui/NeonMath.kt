package it.jarvis.ipod.ui

import kotlin.math.exp
import kotlin.math.max

/** Pure animation envelope, shared by rendering and deterministic tests. */
object NeonMath {
    fun hue(seconds: Double, offset: Float): Float = ((seconds * 36.0 + offset) % 360.0).toFloat()
    fun interaction(ageMillis: Long): Float = if (ageMillis < 0) 0f else exp(-ageMillis / 230f)
    fun energy(interaction: Float, beat: Float, level: Float, volume: Float): Float =
        max(interaction * .95f, (beat * .8f + level * .35f) * volume.coerceIn(0f, 1f)).coerceIn(0f, 1f)
}
