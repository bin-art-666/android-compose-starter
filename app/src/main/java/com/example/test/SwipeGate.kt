package com.example.test

/** Counts central vertical swipe attempts. A hold is continuous; releasing resets its progress. */
class SwipeGate {
    private var attempts = 0
    private var startedAt: Long? = null
    fun requiresHold(frequency: Int): Boolean {
        attempts++
        return attempts % frequency.coerceIn(1, 5) == 0
    }
    fun beginHold(now: Long) { startedAt = now }
    fun cancelHold() { startedAt = null }
    fun progress(now: Long, seconds: Int): Float = startedAt?.let {
        ((now - it).coerceAtLeast(0).toFloat() / (seconds.coerceIn(1, 5) * 1000)).coerceIn(0f, 1f)
    } ?: 0f
}
