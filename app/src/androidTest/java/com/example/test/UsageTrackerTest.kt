package com.example.test

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class UsageTrackerTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    @Test fun reminderAndEndCountsPersistAndAreSeparatedByDay() {
        val prefs = context.getSharedPreferences("tracker_test_counts", 0)
        prefs.edit().clear().commit()
        val tracker = UsageTracker(prefs)
        tracker.increment("reminder"); tracker.increment("reminder"); tracker.increment("ended")
        assertEquals(2, UsageTracker(prefs).countToday("reminder"))
        assertEquals(1, UsageTracker(prefs).countToday("ended"))
        val oldDay = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }.timeInMillis
        tracker.increment("reminder", oldDay)
        assertEquals(2, tracker.countToday("reminder"))
        prefs.edit().clear().commit()
    }
    @Test fun usagePersistsAcrossTrackerInstancesAndIgnoresZeroDuration() {
        val prefs = context.getSharedPreferences("tracker_test", 0)
        prefs.edit().clear().commit()
        val tracker = UsageTracker(prefs)
        tracker.record(1200)
        tracker.record(800)
        tracker.record(0)
        assertEquals(2000L, UsageTracker(prefs).today())
        prefs.edit().clear().commit()
    }
    @Test fun midnightSplitsUsageAcrossLocalDates() {
        val prefs = context.getSharedPreferences("tracker_test_midnight", 0)
        prefs.edit().clear().commit()
        val midnight = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 11, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        UsageTracker(prefs).record(2000, midnight + 1000)
        assertEquals(1000L, prefs.getLong("usage_${UsageTracker.day(midnight - 1000)}", 0))
        assertEquals(1000L, prefs.getLong("usage_${UsageTracker.day(midnight)}", 0))
        prefs.edit().clear().commit()
    }
}
