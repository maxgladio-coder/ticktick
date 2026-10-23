package com.maxgladio.ticktick

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalView
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maxgladio.ticktick.audio.MetronomeService
import com.maxgladio.ticktick.core.TapTempoEstimator
import com.maxgladio.ticktick.ui.MetronomeScreen
import com.maxgladio.ticktick.ui.TickTickTheme
import com.maxgladio.ticktick.update.UpdateViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

class TapViewModel : ViewModel() {
    private val estimator = TapTempoEstimator()
    private val mutableEstimate = MutableStateFlow(TapTempoEstimator.Estimate())
    val estimate = mutableEstimate.asStateFlow()
    private val mutableLastTap = MutableStateFlow(0L)
    val lastTap = mutableLastTap.asStateFlow()
    fun tap() {
        val now = SystemClock.elapsedRealtime()
        mutableEstimate.value = estimator.tap(now)
        mutableLastTap.value = now
    }
    fun reset() {
        estimator.reset()
        mutableEstimate.value = estimator.estimate
        mutableLastTap.value = 0
    }
}

class MainActivity : ComponentActivity() {
    private val tapModel by viewModels<TapViewModel>()
    private val updateModel by viewModels<UpdateViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        setContent {
            val config by MetroStore.config.collectAsStateWithLifecycle()
            val playback by MetroStore.playback.collectAsStateWithLifecycle()
            val estimate by tapModel.estimate.collectAsStateWithLifecycle()
            val lastTap by tapModel.lastTap.collectAsStateWithLifecycle()
            val updateState by updateModel.state.collectAsStateWithLifecycle()
            val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
            val view = LocalView.current
            DisposableEffect(playback.running) {
                if (playback.running) window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
            }
            TickTickTheme {
                MetronomeScreen(
                    config = config,
                    playback = playback,
                    estimate = estimate,
                    lastTap = lastTap,
                    onConfig = { value -> MetroStore.change { value } },
                    onToggle = {
                        if (playback.running) MetronomeService.stop(this)
                        else {
                            // Notification permission improves stop access; playback also works if declined.
                            val prefs = getSharedPreferences("permissions", MODE_PRIVATE)
                            if (Build.VERSION.SDK_INT >= 33 && !prefs.getBoolean("notification_asked", false) &&
                                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                prefs.edit { putBoolean("notification_asked", true) }
                                permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            runCatching { MetronomeService.start(this) }.onFailure {
                                MetroStore.stopped("无法开始播放，请返回应用后重试")
                            }
                        }
                    },
                    onTap = {
                        tapModel.tap()
                        view.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                    },
                    onResetTap = tapModel::reset,
                    updateState = updateState,
                    onCheckUpdate = updateModel::check,
                    onOpenUpdate = { url ->
                        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            .onFailure { Toast.makeText(this, "无法打开浏览器，请安装浏览器后重试", Toast.LENGTH_LONG).show() }
                    },
                )
            }
        }
    }
}
