package com.example.test

import org.junit.Assert.*
import org.junit.Test

class SlowTimingTest {
    @Test fun countdownCompletesAtDeadline() {
        assertEquals(3, SlowTiming.remainingSeconds(1000, 1000, 3))
        assertEquals(1, SlowTiming.remainingSeconds(1000, 3999, 3))
        assertEquals(0, SlowTiming.remainingSeconds(1000, 4000, 3))
        assertTrue(SlowTiming.progress(1000, 3999, 3) < 1f)
        assertEquals(1f, SlowTiming.progress(1000, 4000, 3), 0f)
    }
    @Test fun invalidSettingsAndClockValuesAreBounded() {
        assertEquals(0f, SlowTiming.progress(1000, 0, 3), 0f)
        assertEquals(1f, SlowTiming.progress(1000, 2000, 0), 0f)
        assertEquals(1, SlowTiming.remainingSeconds(1000, 8000, 100))
        assertEquals(0, SlowTiming.remainingSeconds(1000, 10000, 3))
    }
}
