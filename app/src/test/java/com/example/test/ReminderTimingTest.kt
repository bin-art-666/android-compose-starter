package com.example.test

import org.junit.Assert.*
import org.junit.Test

class ReminderTimingTest {
    @Test fun reminderWaitsUntilIntervalBoundary() {
        assertFalse(ReminderTiming.isDue(179999, 3))
        assertTrue(ReminderTiming.isDue(180000, 3))
        assertFalse(ReminderTiming.isDue(599999, 10))
        assertTrue(ReminderTiming.isDue(600000, 10))
    }
    @Test fun confirmationCannotUnlockBeforeThreeSeconds() {
        assertEquals(3, ReminderTiming.remainingSeconds(10000, 10000))
        assertEquals(1, ReminderTiming.remainingSeconds(10000, 12999))
        assertEquals(0, ReminderTiming.remainingSeconds(10000, 13000))
        assertEquals(0, ReminderTiming.remainingSeconds(10000, 90000))
        assertEquals(3, ReminderTiming.remainingSeconds(10000, 9999))
    }
}
