package it.jarvis.ipod.ui

import org.junit.Assert.*
import org.junit.Test

class NeonMathTest {
    @Test fun cyclesThroughAllHuesEveryTenSeconds() {
        for(i in 0 until 10) assertEquals(i * 36f, NeonMath.hue(i.toDouble(), 0f), .001f)
        assertEquals(0f, NeonMath.hue(10.0, 0f), .001f)
    }
    @Test fun inputImmediatelyChangesColourAndProducesDecay() {
        assertNotEquals(NeonMath.hue(1.0, 0f), NeonMath.hue(1.0, 43f))
        assertEquals(1f, NeonMath.interaction(0), .0001f)
        assertTrue(NeonMath.interaction(500) < .12f)
    }
    @Test fun deviceVolumeScalesMusicAndMuteStopsAudioPulses() {
        val soft = NeonMath.energy(0f, 1f, .5f, .2f)
        val loud = NeonMath.energy(0f, 1f, .5f, .9f)
        assertTrue(loud > soft)
        assertEquals(0f, NeonMath.energy(0f, 1f, 1f, 0f), .0001f)
    }
    @Test fun interactionStillWorksWhileMutedAndEnergyIsBounded() {
        assertEquals(.95f, NeonMath.energy(1f, 0f, 0f, 0f), .0001f)
        assertEquals(1f, NeonMath.energy(1f, 1f, 1f, 1f), .0001f)
    }
}
