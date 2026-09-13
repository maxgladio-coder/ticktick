package com.maxgladio.ticktick

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.maxgladio.ticktick.core.ClickSound
import com.maxgladio.ticktick.core.MetronomeConfig
import com.maxgladio.ticktick.core.PlaybackState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class TickTickApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        MetroStore.initialize(this)
    }
}

object MetroStore {
    private lateinit var prefs: SharedPreferences
    private val mutableConfig = MutableStateFlow(MetronomeConfig())
    val config = mutableConfig.asStateFlow()
    private val mutablePlayback = MutableStateFlow(PlaybackState())
    val playback = mutablePlayback.asStateFlow()

    fun initialize(context: Context) {
        prefs = context.applicationContext.getSharedPreferences("metronome", Context.MODE_PRIVATE)
        mutableConfig.value = MetronomeConfig(
            bpm = prefs.getInt("bpm", 120),
            beatsPerBar = prefs.getInt("beats", 4),
            beatUnit = prefs.getInt("unit", 4),
            subdivision = prefs.getInt("subdivision", 1),
            accent = prefs.getBoolean("accent", true),
            sound = ClickSound.entries.firstOrNull { it.name == prefs.getString("sound", "WOOD") } ?: ClickSound.WOOD,
            volume = prefs.getFloat("volume", 0.8f),
            haptics = prefs.getBoolean("haptics", false),
        ).validated()
    }

    fun change(transform: (MetronomeConfig) -> MetronomeConfig) {
        mutableConfig.update { transform(it).validated() }
        val value = config.value
        prefs.edit {
            putInt("bpm", value.bpm); putInt("beats", value.beatsPerBar); putInt("unit", value.beatUnit)
            putInt("subdivision", value.subdivision); putBoolean("accent", value.accent)
            putString("sound", value.sound.name); putFloat("volume", value.volume)
            putBoolean("haptics", value.haptics)
        }
    }

    fun started() { mutablePlayback.value = PlaybackState(running = true) }
    fun beat(index: Int) { mutablePlayback.update { it.copy(beat = index, serial = it.serial + 1) } }
    fun stopped(error: String? = null) { mutablePlayback.value = PlaybackState(error = error) }
}
