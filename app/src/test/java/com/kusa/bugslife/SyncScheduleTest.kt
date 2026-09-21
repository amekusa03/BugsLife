package com.kusa.bugslife

import com.kusa.bugslife.service.WatcherForegroundService
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class SyncScheduleTest {

    @Test
    fun testCalculateNextSyncTimestamp_fromBefore0005() {
        // 例: 00:02:00 の場合 -> 今日の 00:05:00 になるべき
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 2)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nextTime = WatcherForegroundService.calculateNextSyncTimestamp(cal.timeInMillis)
        
        val nextCal = Calendar.getInstance().apply { timeInMillis = nextTime }
        assertEquals(0, nextCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(5, nextCal.get(Calendar.MINUTE))
        assertEquals(0, nextCal.get(Calendar.SECOND))
    }

    @Test
    fun testCalculateNextSyncTimestamp_fromExact0005() {
        // 例: 00:05:00 の場合 -> 次の 06:05:00 になるべき
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 5)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nextTime = WatcherForegroundService.calculateNextSyncTimestamp(cal.timeInMillis)
        
        val nextCal = Calendar.getInstance().apply { timeInMillis = nextTime }
        assertEquals(6, nextCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(5, nextCal.get(Calendar.MINUTE))
        assertEquals(0, nextCal.get(Calendar.SECOND))
    }

    @Test
    fun testCalculateNextSyncTimestamp_from000505() {
        // 例: 00:05:05 の場合 -> 次の 06:05:00 になるべき
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 5)
            set(Calendar.SECOND, 5)
            set(Calendar.MILLISECOND, 0)
        }
        val nextTime = WatcherForegroundService.calculateNextSyncTimestamp(cal.timeInMillis)
        
        val nextCal = Calendar.getInstance().apply { timeInMillis = nextTime }
        assertEquals(6, nextCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(5, nextCal.get(Calendar.MINUTE))
        assertEquals(0, nextCal.get(Calendar.SECOND))
    }

    @Test
    fun testCalculateNextSyncTimestamp_fromMidday() {
        // 例: 09:30:00 の場合 -> 今日の 12:05:00 になるべき
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 9)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nextTime = WatcherForegroundService.calculateNextSyncTimestamp(cal.timeInMillis)
        
        val nextCal = Calendar.getInstance().apply { timeInMillis = nextTime }
        assertEquals(12, nextCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(5, nextCal.get(Calendar.MINUTE))
        assertEquals(0, nextCal.get(Calendar.SECOND))
    }

    @Test
    fun testCalculateNextSyncTimestamp_fromAfternoon() {
        // 例: 15:30:00 の場合 -> 今日の 18:05:00 になるべき
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 15)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nextTime = WatcherForegroundService.calculateNextSyncTimestamp(cal.timeInMillis)
        
        val nextCal = Calendar.getInstance().apply { timeInMillis = nextTime }
        assertEquals(18, nextCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(5, nextCal.get(Calendar.MINUTE))
        assertEquals(0, nextCal.get(Calendar.SECOND))
    }

    @Test
    fun testCalculateNextSyncTimestamp_fromNight_crossesDay() {
        // 例: 21:00:00 の場合 -> 翌日の 00:05:00 になるべき
        val cal = Calendar.getInstance().apply {
            set(Calendar.DAY_OF_YEAR, 100)
            set(Calendar.HOUR_OF_DAY, 21)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nextTime = WatcherForegroundService.calculateNextSyncTimestamp(cal.timeInMillis)
        
        val nextCal = Calendar.getInstance().apply { timeInMillis = nextTime }
        assertEquals(101, nextCal.get(Calendar.DAY_OF_YEAR))
        assertEquals(0, nextCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(5, nextCal.get(Calendar.MINUTE))
        assertEquals(0, nextCal.get(Calendar.SECOND))
    }
}
