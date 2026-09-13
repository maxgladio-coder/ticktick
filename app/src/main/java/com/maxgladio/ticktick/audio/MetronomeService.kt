package com.maxgladio.ticktick.audio

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.maxgladio.ticktick.MainActivity
import com.maxgladio.ticktick.MetroStore
import com.maxgladio.ticktick.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class MetronomeService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var engine: AudioEngine
    private lateinit var audioManager: AudioManager
    private var focus: AudioFocusRequest? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var running = false
    private var failure: String? = null
    private val noisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) stopPlayback()
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AudioManager::class.java)
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "节拍器播放", NotificationManager.IMPORTANCE_LOW).apply {
                setSound(null, null)
                enableVibration(false)
            }
        )
        engine = AudioEngine(onBeat = { MetroStore.beat(it) }, onError = {
            failure = "音频设备暂时不可用，请重新开始"
            stopPlayback()
        })
        ContextCompat.registerReceiver(this, noisyReceiver,
            IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY), ContextCompat.RECEIVER_NOT_EXPORTED)
        scope.launch {
            MetroStore.config.collect {
                engine.configure(it)
                if (running) getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification())
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == STOP) {
            stopPlayback()
            return START_NOT_STICKY
        }
        if (running) return START_NOT_STICKY
        failure = null
        if (Build.VERSION.SDK_INT >= 29) {
            startForeground(NOTIFICATION_ID, notification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
        } else startForeground(NOTIFICATION_ID, notification())

        focus = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioEngine.attributes())
            .setOnAudioFocusChangeListener { change ->
                if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT ||
                    change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK) stopPlayback()
            }.build()
        if (audioManager.requestAudioFocus(focus!!) != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            failure = "无法获取音频焦点，请在通话或其他音频结束后重试"
            stopPlayback()
            return START_NOT_STICKY
        }
        acquirePlaybackWakeLock()
        running = true
        MetroStore.started()
        engine.configure(MetroStore.config.value)
        engine.start()
        return START_NOT_STICKY
    }

    @SuppressLint("WakelockTimeout")
    private fun acquirePlaybackWakeLock() {
        // Playback has no time limit. The foreground service owns this lock and releases it
        // on stop, focus loss, audio failure, or destruction; process death also releases it.
        wakeLock = getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "TickTick:Playback").apply { acquire() }
    }

    private fun notification(): Notification {
        val config = MetroStore.config.value
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val stop = PendingIntent.getService(this, 1, Intent(this, MetronomeService::class.java).setAction(STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_beat).setContentTitle("律动 · ${config.bpm} BPM")
            .setContentText("${config.beatsPerBar}/${config.beatUnit} 拍 · 点此返回节拍器")
            .setContentIntent(open).setOnlyAlertOnce(true).setOngoing(true)
            .setCategory(Notification.CATEGORY_TRANSPORT)
            .addAction(Notification.Action.Builder(null, "停止", stop).build()).build()
    }

    private fun stopPlayback() {
        running = false
        engine.stop()
        focus?.let { audioManager.abandonAudioFocusRequest(it) }
        focus = null
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
        MetroStore.stopped(failure)
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        unregisterReceiver(noisyReceiver)
        stopPlayback()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL = "metronome_playback"
        private const val NOTIFICATION_ID = 1
        private const val STOP = "com.maxgladio.ticktick.STOP"

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MetronomeService::class.java))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, MetronomeService::class.java).setAction(STOP))
        }
    }
}
