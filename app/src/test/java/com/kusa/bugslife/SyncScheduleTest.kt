package com.kusa.bugslife

import com.kusa.bugslife.service.WatcherForegroundService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class SyncScheduleTest {

    @Test
    fun testCalculateNextSyncTimestamp_fromExact5Min() {
        // 例: 14:05:00 の場合 -> 15秒未満なので次の 15:05:00 になるべき
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 5)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nextTime = WatcherForegroundService.calculateNextSyncTimestamp(cal.timeInMillis)
        
        val nextCal = Calendar.getInstance().apply { timeInMillis = nextTime }
        assertEquals(15, nextCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(5, nextCal.get(Calendar.MINUTE))
        assertEquals(0, nextCal.get(Calendar.SECOND))
    }

    @Test
    fun testCalculateNextSyncTimestamp_from5Min5Sec() {
        // 例: 14:05:05 の場合 -> 次の 15:05:00 になるべき (以前のバグでは14:05:00の過去になっていた)
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 5)
            set(Calendar.SECOND, 5)
            set(Calendar.MILLISECOND, 0)
        }
        val nextTime = WatcherForegroundService.calculateNextSyncTimestamp(cal.timeInMillis)
        
        val nextCal = Calendar.getInstance().apply { timeInMillis = nextTime }
        assertEquals(15, nextCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(5, nextCal.get(Calendar.MINUTE))
        assertEquals(0, nextCal.get(Calendar.SECOND))
    }

    @Test
    fun testCalculateNextSyncTimestamp_fromBefore5Min() {
        // 例: 14:02:00 の場合 -> 今時の 14:05:00 になるべき
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 2)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nextTime = WatcherForegroundService.calculateNextSyncTimestamp(cal.timeInMillis)
        
        val nextCal = Calendar.getInstance().apply { timeInMillis = nextTime }
        assertEquals(14, nextCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(5, nextCal.get(Calendar.MINUTE))
        assertEquals(0, nextCal.get(Calendar.SECOND))
    }

    @Test
    fun testCalculateNextSyncTimestamp_fromAfter5Min() {
        // 例: 14:06:30 の場合 -> 次の 15:05:00 になるべき
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 14)
            set(Calendar.MINUTE, 6)
            set(Calendar.SECOND, 30)
            set(Calendar.MILLISECOND, 0)
        }
        val nextTime = WatcherForegroundService.calculateNextSyncTimestamp(cal.timeInMillis)
        
        val nextCal = Calendar.getInstance().apply { timeInMillis = nextTime }
        assertEquals(15, nextCal.get(Calendar.HOUR_OF_DAY))
        assertEquals(5, nextCal.get(Calendar.MINUTE))
        assertEquals(0, nextCal.get(Calendar.SECOND))
    }
}
