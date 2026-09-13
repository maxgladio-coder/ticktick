package com.maxgladio.ticktick

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import com.maxgladio.ticktick.core.MetronomeConfig
import com.maxgladio.ticktick.core.PlaybackState
import com.maxgladio.ticktick.core.TapTempoEstimator
import com.maxgladio.ticktick.ui.MetronomeScreen
import com.maxgladio.ticktick.ui.TickTickTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class MetronomeScreenTest {
    @get:Rule val compose = createComposeRule()

    @Test fun appliesTempoOnlyOnExplicitAction() {
        var result = MetronomeConfig()
        compose.setContent {
            TickTickTheme {
                MetronomeScreen(MetronomeConfig(), PlaybackState(),
                    TapTempoEstimator.Estimate(bpm = 98.4, intervals = 8, stability = 95), 0L,
                    { result = it }, {}, {}, {})
            }
        }
        assertEquals(120, result.bpm)
        compose.onNodeWithText("应用速度").performScrollTo().performClick()
        assertEquals(98, result.bpm)
    }

    @Test fun transportIsVisibleAndOperable() {
        var toggled = false
        compose.setContent {
            TickTickTheme {
                MetronomeScreen(MetronomeConfig(), PlaybackState(), TapTempoEstimator.Estimate(), 0L,
                    {}, { toggled = true }, {}, {})
            }
        }
        compose.onNodeWithText("开始节拍").assertIsDisplayed().performClick()
        assertEquals(true, toggled)
    }
}
