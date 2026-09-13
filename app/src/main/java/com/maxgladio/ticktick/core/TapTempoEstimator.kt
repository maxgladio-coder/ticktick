package com.maxgladio.ticktick.core

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Monotonic milliseconds in; no wall clock, Android dependency, or hidden auto-apply. */
class TapTempoEstimator {
    data class Estimate(
        val bpm: Double? = null,
        val intervals: Int = 0,
        val stability: Int = 0,
        val recentBpm: List<Double> = emptyList(),
        val changedTempo: Boolean = false,
    )

    private data class Interval(val end: Long, val ms: Double)
    private val history = ArrayDeque<Interval>()
    private var lastTap: Long? = null
    var estimate = Estimate()
        private set

    fun reset() {
        history.clear()
        lastTap = null
        estimate = Estimate()
    }

    fun tap(nowMs: Long): Estimate {
        val previous = lastTap
        if (previous == null || nowMs - previous > RESET_GAP_MS) {
            reset()
            lastTap = nowMs
            return estimate
        }
        val interval = (nowMs - previous).toDouble()
        // Ignore bounce without moving the accepted tap's timestamp.
        if (interval < 150.0) return estimate
        lastTap = nowMs
        history.addLast(Interval(nowMs, interval))
        while (history.size > MAX_INTERVALS || (history.isNotEmpty() && nowMs - history.first().end > WINDOW_MS)) {
            history.removeFirst()
        }

        // Three consistent new intervals mean an intentional tempo change, even at half/double time.
        var changed = false
        if (history.size >= 6) {
            val old = median(history.dropLast(3).map { it.ms })
            val recent = history.takeLast(3)
            val center = recent.map { it.ms }.average()
            val consistent = recent.all { abs(it.ms - center) / center < 0.12 }
            if (consistent && abs(center - old) / old >= 0.18) {
                history.clear()
                history.addAll(recent)
                changed = true
            }
        }

        val center = median(history.map { it.ms })
        val corrected = history.map { sample ->
            val multiple = (sample.ms / center).roundToInt()
            // An isolated missed beat contributes one normalized interval, never several votes.
            if (multiple in 2..3 && abs(sample.ms / multiple - center) / center < 0.14) {
                sample.copy(ms = sample.ms / multiple)
            } else sample
        }
        val mad = median(corrected.map { abs(it.ms - center) })
        val scale = maxOf(12.0, mad * 1.4826)
        val weights = corrected.map { sample ->
            val residual = abs(sample.ms - center)
            val robust = if (residual > center * 0.45) 0.0 else minOf(1.0, 1.5 * scale / maxOf(1.0, residual))
            exp(-(nowMs - sample.end) / 7000.0) * robust
        }
        val totalWeight = weights.sum()
        val period = if (totalWeight > 0) corrected.indices.sumOf { corrected[it].ms * weights[it] } / totalWeight else center
        val count = weights.count { it > 0.1 }
        val rawBpm = 60_000.0 / period
        val bpm = rawBpm.takeIf { count >= 3 && it in 30.0..300.0 }
        val variance = if (totalWeight > 0) corrected.indices.sumOf {
            val difference = corrected[it].ms - period
            weights[it] * difference * difference
        } / totalWeight else 0.0
        // A consistency indicator, not a statistical confidence probability.
        val stability = ((1.0 - sqrt(variance) / period / 0.18).coerceIn(0.0, 1.0) *
            minOf(1.0, count / 8.0) * count / history.size * 100).roundToInt()
        estimate = Estimate(bpm, history.size, stability, corrected.takeLast(16).map { 60_000.0 / it.ms }, changed)
        return estimate
    }

    private fun median(values: List<Double>): Double {
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) (sorted[middle - 1] + sorted[middle]) / 2 else sorted[middle]
    }

    companion object {
        const val WINDOW_MS = 12_000L
        const val RESET_GAP_MS = 4_000L
        const val MAX_INTERVALS = 24
    }
}
