package com.maxgladio.ticktick.core

import org.junit.Assert.*
import org.junit.Test
import java.util.Random
import kotlin.math.abs

class TapTempoEstimatorTest {
    private fun taps(vararg intervals: Long): TapTempoEstimator.Estimate {
        val estimator = TapTempoEstimator()
        var time = 0L
        estimator.tap(time)
        intervals.forEach { time += it; estimator.tap(time) }
        return estimator.estimate
    }

    @Test fun requiresFourTaps() {
        assertNull(taps(500, 500).bpm)
        assertEquals(120.0, taps(500, 500, 500).bpm!!, 0.01)
    }

    @Test fun humanTimestampJitterConvergesAcrossTempos() {
        for (bpm in listOf(40, 60, 90, 120, 180, 240, 280)) {
            val estimator = TapTempoEstimator()
            val random = Random(2026)
            repeat(40) { index ->
                // Independent timestamp jitter creates correlated adjacent interval errors.
                val time = (index * 60_000.0 / bpm + random.nextGaussian() * 12).toLong()
                estimator.tap(time)
            }
            assertEquals("$bpm BPM with 12ms hand jitter", bpm.toDouble(), estimator.estimate.bpm!!, 2.5)
        }
    }

    @Test fun rejectsBounceWithoutMovingAnchor() {
        val estimator = TapTempoEstimator()
        estimator.tap(0)
        estimator.tap(500)
        estimator.tap(550)
        estimator.tap(1000)
        val result = estimator.tap(1500)
        assertEquals(120.0, result.bpm!!, 0.01)
        assertEquals(3, result.intervals)
    }

    @Test fun isolatedMissedBeatDoesNotHalveTempo() {
        val result = taps(500, 500, 500, 500, 1000, 500, 500, 500)
        assertEquals(120.0, result.bpm!!, 0.01)
    }

    @Test fun isolatedLongAndShortIntervalsDoNotDominate() {
        val result = taps(500, 500, 500, 500, 260, 740, 500, 500, 500)
        assertEquals(120.0, result.bpm!!, 1.0)
    }

    @Test fun sustainedHalfAndDoubleTempoAreRecognized() {
        assertEquals(60.0, taps(500, 500, 500, 500, 500, 1000, 1000, 1000).bpm!!, 0.1)
        assertEquals(240.0, taps(500, 500, 500, 500, 500, 250, 250, 250).bpm!!, 0.1)
        assertTrue(taps(500, 500, 500, 500, 500, 400, 400, 400, 400).bpm!! > 145)
    }

    @Test fun recentTempoEventuallyReplacesOldHistory() {
        val estimator = TapTempoEstimator()
        var now = 0L
        estimator.tap(now)
        repeat(16) { now += 500; estimator.tap(now) }
        repeat(24) { now += 460; estimator.tap(now) }
        assertEquals(60_000.0 / 460, estimator.estimate.bpm!!, 0.1)
    }

    @Test fun continuousTappingKeepsUpdatingBeyondMultipleHistoryWindows() {
        val estimator = TapTempoEstimator()
        var now = 0L
        var count = 0
        estimator.tap(now)
        // More than two minutes of continuous tapping, with modest tempo changes.
        // No explicit reset or pause: each recent window must replace its predecessor.
        for (period in listOf(500L, 460L, 520L, 480L, 530L, 490L, 510L)) {
            repeat(40) {
                now += period
                count++
                val result = estimator.tap(now)
                if (count >= 3) assertNotNull("Lost estimate after tap $count", result.bpm)
                assertTrue(result.intervals <= TapTempoEstimator.MAX_INTERVALS)
            }
            assertEquals("Window still contains an old tempo", 60_000.0 / period, estimator.estimate.bpm!!, 0.1)
        }
        assertTrue(now > 120_000)
    }

    @Test fun pauseResetAndBackwardClockAreSafe() {
        val estimator = TapTempoEstimator()
        listOf(0L, 500, 1000, 1500).forEach { estimator.tap(it) }
        assertEquals(120.0, estimator.tap(1400).bpm!!, 0.01)
        assertNull(estimator.tap(6000).bpm)
        assertEquals(0, estimator.estimate.intervals)
        estimator.reset()
        assertNull(estimator.estimate.bpm)
    }

    @Test fun historyIsBoundedByCountAndTime() {
        val estimator = TapTempoEstimator()
        repeat(100) { estimator.tap(it * 250L) }
        assertEquals(24, estimator.estimate.intervals)
        estimator.reset()
        repeat(100) { estimator.tap(it * 2000L) }
        assertTrue(estimator.estimate.intervals <= 7)
        assertEquals(30.0, estimator.estimate.bpm!!, 0.01)
    }

    @Test fun irregularTappingHasLowerStability() {
        val stable = taps(500, 500, 500, 500, 500, 500, 500, 500)
        val irregular = taps(400, 570, 450, 610, 430, 540, 410, 590)
        assertTrue(stable.stability > irregular.stability)
        assertTrue(stable.stability >= 90)
    }

    @Test fun noNanForArbitraryInputs() {
        val random = Random(8)
        val estimator = TapTempoEstimator()
        var now = 0L
        repeat(10_000) {
            now += random.nextInt(4500)
            val result = estimator.tap(now)
            result.bpm?.let { assertTrue(it.isFinite() && it in 30.0..300.0) }
            assertTrue(result.stability in 0..100)
            assertTrue(result.intervals <= TapTempoEstimator.MAX_INTERVALS)
        }
    }
}
