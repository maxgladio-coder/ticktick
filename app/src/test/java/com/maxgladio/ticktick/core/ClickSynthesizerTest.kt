package com.maxgladio.ticktick.core

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class ClickSynthesizerTest {
    @Test fun fractionalFrameClockDoesNotAccumulateDrift() {
        val synth = ClickSynthesizer()
        val config = MetronomeConfig(bpm = 137, subdivision = 3)
        val buffer = ShortArray(4096)
        var beats = 0
        // Two minutes of the real PCM renderer, including subdivision clicks.
        repeat(48_000 * 120 / buffer.size) {
            synth.render(buffer, config).forEach { event ->
                val expected = beats * 48_000 * 60.0 / 137
                assertTrue("beat $beats drift=${event.frame - expected}", abs(event.frame - expected) <= 1.01)
                assertEquals(beats % 4, event.beat)
                beats++
            }
        }
        assertTrue(beats >= 273)
    }

    @Test fun emitsSoundAndRespectsMute() {
        val audible = ShortArray(2048)
        ClickSynthesizer().render(audible, MetronomeConfig())
        assertTrue(audible.any { it.toInt() != 0 })
        val silent = ShortArray(2048)
        ClickSynthesizer().render(silent, MetronomeConfig(volume = 0f))
        assertTrue(silent.all { it.toInt() == 0 })
    }

    @Test fun meterChangesStayInRangeAndAdvanceTime() {
        val synth = ClickSynthesizer()
        val buffer = ShortArray(256)
        var last = -1L
        repeat(1500) { index ->
            val config = if (index < 300) MetronomeConfig() else MetronomeConfig(bpm = 240, beatsPerBar = 3, subdivision = 4)
            synth.render(buffer, config).forEach {
                assertTrue(it.frame > last)
                assertTrue(it.beat in 0 until config.beatsPerBar)
                last = it.frame
            }
        }
    }

    @Test fun accentsAreStrongerAndCanBeDisabled() {
        fun energies(accent: Boolean): List<Long> {
            val synth = ClickSynthesizer()
            val config = MetronomeConfig(bpm = 120, accent = accent)
            return (0..3).map {
                val buffer = ShortArray(24_000)
                synth.render(buffer, config)
                buffer.sumOf { sample -> sample.toLong() * sample }
            }
        }
        val accented = energies(true)
        assertTrue(accented[0] > accented[1] * 1.5)
        val uniform = energies(false)
        assertEquals(uniform[0], uniform[1])
    }
}
