package com.maxgladio.ticktick.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import android.os.Process
import com.maxgladio.ticktick.core.ClickSynthesizer
import com.maxgladio.ticktick.core.MetronomeConfig
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class AudioEngine(
    private val onBeat: (Int) -> Unit,
    private val onError: () -> Unit,
) {
    private val config = AtomicReference(MetronomeConfig())
    private val active = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private var worker: Thread? = null

    fun configure(value: MetronomeConfig) { config.set(value.validated()) }

    fun start() {
        if (!active.compareAndSet(false, true)) return
        worker = Thread({ stream() }, "TickTickAudio").also { it.start() }
    }

    private fun stream() {
        var track: AudioTrack? = null
        try {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val rate = 48_000
            val minimum = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
            check(minimum > 0)
            track = AudioTrack.Builder()
                .setAudioAttributes(attributes())
                .setAudioFormat(AudioFormat.Builder().setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).setEncoding(AudioFormat.ENCODING_PCM_16BIT).build())
                .setBufferSizeInBytes(maxOf(minimum, 1024))
                .setTransferMode(AudioTrack.MODE_STREAM)
                .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
                .build()
            check(track.state == AudioTrack.STATE_INITIALIZED)
            val synth = ClickSynthesizer(rate)
            val buffer = ShortArray(256)
            var headWrap = 0L
            var previousHead = 0L
            track.play()
            while (active.get()) {
                val events = synth.render(buffer, config.get())
                var offset = 0
                while (offset < buffer.size && active.get()) {
                    val written = track.write(buffer, offset, buffer.size - offset, AudioTrack.WRITE_BLOCKING)
                    check(written > 0) { "AudioTrack write failed: $written" }
                    offset += written
                }
                val head = track.playbackHeadPosition.toLong() and 0xFFFFFFFFL
                if (head < previousHead) headWrap += 1L shl 32
                previousHead = head
                events.forEach { event ->
                    val delay = ((event.frame - head - headWrap) * 1000 / rate).coerceAtLeast(0)
                    handler.postDelayed({ if (active.get()) onBeat(event.beat) }, delay)
                }
            }
        } catch (_: Exception) {
            if (active.getAndSet(false)) handler.post { onError() }
        } finally {
            runCatching { track?.pause() }
            runCatching { track?.flush() }
            track?.release()
        }
    }

    fun stop() {
        active.set(false)
        handler.removeCallbacksAndMessages(null)
        worker?.join(500)
        worker = null
    }

    companion object {
        fun attributes(): AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    }
}
