package com.maxgladio.ticktick.core

data class MetronomeConfig(
    val bpm: Int = 120,
    val beatsPerBar: Int = 4,
    val beatUnit: Int = 4,
    val subdivision: Int = 1,
    val accent: Boolean = true,
    val sound: ClickSound = ClickSound.WOOD,
    val volume: Float = 0.8f,
    val haptics: Boolean = false,
) {
    fun validated() = copy(
        bpm = bpm.coerceIn(30, 300),
        beatsPerBar = beatsPerBar.coerceIn(1, 12),
        beatUnit = if (beatUnit == 8) 8 else 4,
        subdivision = subdivision.coerceIn(1, 4),
        volume = if (volume.isFinite()) volume.coerceIn(0f, 1f) else 0.8f,
    )
}

enum class ClickSound(val label: String) { WOOD("木鱼"), DIGITAL("电子") }

data class PlaybackState(
    val running: Boolean = false,
    val beat: Int = -1,
    val serial: Long = 0,
    val error: String? = null,
)
