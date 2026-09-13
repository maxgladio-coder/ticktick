package com.maxgladio.ticktick.core

import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/** Audio frame clock. Fractional deadlines prevent cumulative rounding drift at e.g. 137 BPM. */
class ClickSynthesizer(val sampleRate: Int = 48_000) {
    data class BeatEvent(val frame: Long, val beat: Int)
    private var frame = 0L
    private var nextClick = 0.0
    private var beat = 0
    private var subdivisionIndex = 0
    private var activeConfig = MetronomeConfig()
    private var click: ShortArray = shortArrayOf()
    private var clickPosition = 0
    private val sounds = ClickSound.entries.associateWith { sound ->
        (0..2).map { strength -> createClick(sound, strength) }
    }

    fun render(output: ShortArray, config: MetronomeConfig): List<BeatEvent> {
        val events = mutableListOf<BeatEvent>()
        for (i in output.indices) {
            if (frame.toDouble() >= nextClick) {
                // Apply tempo and meter changes at a main beat boundary, preserving phase.
                if (subdivisionIndex == 0) {
                    activeConfig = config
                    beat %= activeConfig.beatsPerBar
                    events += BeatEvent(frame, beat)
                }
                val strength = when {
                    subdivisionIndex != 0 -> 0
                    beat == 0 && activeConfig.accent -> 2
                    else -> 1
                }
                click = sounds.getValue(config.sound)[strength]
                clickPosition = 0
                nextClick += sampleRate * 60.0 / activeConfig.bpm / activeConfig.subdivision
                subdivisionIndex++
                if (subdivisionIndex >= activeConfig.subdivision) {
                    subdivisionIndex = 0
                    beat = (beat + 1) % activeConfig.beatsPerBar
                }
            }
            output[i] = if (clickPosition < click.size) {
                (click[clickPosition++].toInt() * config.volume).toInt().toShort()
            } else 0
            frame++
        }
        return events
    }

    private fun createClick(sound: ClickSound, strength: Int): ShortArray {
        val frequency = when (sound) {
            ClickSound.WOOD -> doubleArrayOf(900.0, 1250.0, 1850.0)[strength]
            ClickSound.DIGITAL -> doubleArrayOf(1100.0, 1550.0, 2300.0)[strength]
        }
        val amplitude = doubleArrayOf(0.30, 0.65, 0.95)[strength]
        return ShortArray((sampleRate * 0.025).toInt()) { index ->
            val time = index.toDouble() / sampleRate
            val attack = minOf(1.0, index / (sampleRate * 0.0006))
            val envelope = exp(-time * if (sound == ClickSound.WOOD) 230.0 else 150.0)
            val wave = sin(2 * PI * frequency * time) * 0.8 + sin(2 * PI * frequency * 1.53 * time) * 0.2
            (wave * envelope * attack * amplitude * Short.MAX_VALUE).toInt().toShort()
        }
    }
}
