package com.example.test

import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Calendar

/** Stores only aggregate time by local date; no content or accessibility text is saved. */
internal class UsageTracker(private val prefs: SharedPreferences) {
    fun record(milliseconds: Long, wallTime: Long = System.currentTimeMillis()) {
        if (milliseconds <= 0) return
        var start = wallTime - milliseconds
        val editor = prefs.edit()
        while (start < wallTime) {
            val midnight = Calendar.getInstance().apply {
                timeInMillis = start
                add(Calendar.DAY_OF_MONTH, 1)
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }.timeInMillis
            val end = minOf(midnight, wallTime)
            val key = "usage_${day(start)}"
            editor.putLong(key, prefs.getLong(key, 0) + end - start)
            start = end
        }
        editor.apply()
    }
    fun today(): Long = prefs.getLong("usage_${day(System.currentTimeMillis())}", 0)
    fun increment(event: String, wallTime: Long = System.currentTimeMillis()) {
        require(event == "reminder" || event == "ended")
        val key = "${event}_${day(wallTime)}"
        prefs.edit().putInt(key, prefs.getInt(key, 0) + 1).apply()
    }
    fun countToday(event: String): Int = prefs.getInt("${event}_${day(System.currentTimeMillis())}", 0)
    companion object {
        fun day(time: Long): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(time))
    }
}
