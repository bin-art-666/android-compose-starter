package com.example.test

/** Monotonic time is supplied by the caller so device clock changes cannot unlock early. */
internal object ReminderTiming {
    const val CONFIRM_DELAY_MS = 3000L
    fun isDue(elapsedMs: Long, intervalMinutes: Int): Boolean = elapsedMs >= intervalMinutes.coerceIn(3, 30) * 60000L
    fun remainingSeconds(shownAt: Long, now: Long): Int =
        ((CONFIRM_DELAY_MS - (now - shownAt).coerceAtLeast(0)).coerceAtLeast(0) + 999).div(1000).toInt()
}
