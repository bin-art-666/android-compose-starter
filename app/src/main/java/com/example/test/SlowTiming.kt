package com.example.test

internal object SlowTiming {
    fun progress(startedAt: Long, now: Long, seconds: Int): Float =
        ((now - startedAt).coerceAtLeast(0).toFloat() / (seconds.coerceIn(1, 8) * 1000)).coerceIn(0f, 1f)

    fun remainingSeconds(startedAt: Long, now: Long, seconds: Int): Int =
        ((seconds.coerceIn(1, 8) * 1000L - (now - startedAt).coerceAtLeast(0)).coerceAtLeast(0) + 999L).div(1000L).toInt()
}
