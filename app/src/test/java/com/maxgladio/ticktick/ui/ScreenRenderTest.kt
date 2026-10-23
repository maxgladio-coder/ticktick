package com.maxgladio.ticktick.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.maxgladio.ticktick.core.MetronomeConfig
import com.maxgladio.ticktick.core.PlaybackState
import com.maxgladio.ticktick.core.TapTempoEstimator
import com.maxgladio.ticktick.update.AppRelease
import com.maxgladio.ticktick.update.UpdateState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Modifier
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w393dp-h852dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ScreenRenderTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun phoneLayoutAndTempoApplication() {
        var config by mutableStateOf(MetronomeConfig())
        compose.setContent {
            TickTickTheme {
                MetronomeScreen(config, PlaybackState(),
                    TapTempoEstimator.Estimate(98.4, 10, 94, listOf(99.0, 97.0, 100.0, 98.0, 99.0, 98.0)),
                    0L, { config = it }, {}, {}, {})
            }
        }
        compose.onNodeWithContentDescription("开始节拍").assertIsDisplayed()
        screenshot("phone-home")
        compose.onNodeWithText("98").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("98.4").assertDoesNotExist()
        compose.onNodeWithText("应用速度").performScrollTo().performClick()
        assertEquals(98, config.bpm)
        screenshot("phone-tap")
        compose.onNodeWithContentDescription("降低 5 BPM").performScrollTo().performClick()
        assertEquals(93, config.bpm)
        compose.onNodeWithContentDescription("声音与反馈设置").performScrollTo().performClick()
        compose.onNodeWithText("首拍重音").assertIsDisplayed()
        compose.onNodeWithText("检查更新").performScrollTo().assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "zh-rCN-w320dp-h640dp-hdpi")
    fun smallScreenKeepsTransportAndTapReachable() {
        org.robolectric.RuntimeEnvironment.setFontScale(1.5f)
        var taps = 0
        compose.setContent {
            TickTickTheme {
                MetronomeScreen(MetronomeConfig(beatsPerBar = 12), PlaybackState(),
                    TapTempoEstimator.Estimate(), 0L, {}, {}, { taps++ }, {})
            }
        }
        compose.onNodeWithContentDescription("开始节拍").assertIsDisplayed()
        compose.onNodeWithContentDescription("快捷敲击测速").assertIsDisplayed()
        compose.onNodeWithContentDescription("快捷敲击测速").performTouchInput { down(center) }
        compose.runOnIdle { assertEquals(1, taps) }
        compose.onNodeWithContentDescription("快捷敲击测速").performTouchInput { advanceEventTime(180); up() }
        compose.runOnIdle { assertEquals(1, taps) }
        compose.onNodeWithContentDescription("敲击测速，跟随节奏连续敲击至少四次").performScrollTo().assertIsDisplayed()
        screenshot("small-phone")
    }

    @Test fun updateStatesAndDownloadRequireExplicitActions() {
        var state by mutableStateOf<UpdateState>(UpdateState.Idle)
        var checks = 0
        var opened = ""
        val apk = "https://github.com/maxgladio-coder/ticktick/releases/download/v1.2.0/ticktick.apk"
        compose.setContent {
            TickTickTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    UpdateCard(state, { checks++; state = UpdateState.Checking }, { opened = it })
                }
            }
        }
        assertEquals(0, checks)
        compose.onNodeWithText("检查更新").performClick()
        compose.onNodeWithText("正在检查更新…").assertIsDisplayed()
        compose.onNodeWithText("重新检查").assertIsNotEnabled()
        compose.runOnIdle { state = UpdateState.Available(AppRelease("v1.2.0", "改善敲击测速稳定性", "https://github.com/maxgladio-coder/ticktick/releases/tag/v1.2.0", apk)) }
        compose.onNodeWithText("发现新版本 v1.2.0").assertIsDisplayed()
        assertEquals("", opened)
        screenshot("update-available")
        compose.onNodeWithText("下载新版 APK").performClick()
        assertEquals(apk, opened)
        compose.runOnIdle { state = UpdateState.Error("连接 GitHub 失败，请检查网络后重试。") }
        compose.onNodeWithText("重新检查").performClick()
        assertEquals(2, checks)
        compose.runOnIdle { state = UpdateState.Current }
        compose.onNodeWithText("已是最新版本").assertIsDisplayed()
    }

    private fun screenshot(name: String) {
        val file = File("build/screenshots/$name.png")
        file.parentFile?.mkdirs()
        compose.runOnUiThread {
            val view = compose.activity.window.decorView
            val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
            view.draw(Canvas(bitmap))
            file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
    }
}
