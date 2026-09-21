package com.kusa.bugslife.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

/**
 * 6時間定期同期 & 無操作タイムアウト用の AlarmManager レシーバー
 * スリープ中（Dozeモード）の端末を叩き起こして WatcherForegroundService に同期を実行させます。
 */
class SyncAlarmReceiver : BroadcastReceiver() {
    private val tag = "SyncAlarmReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        val action = intent?.action ?: WatcherForegroundService.ACTION_SCHEDULED_SYNC
        Log.d(tag, "Sync alarm fired! Action: $action")

        val pendingResult = goAsync()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val wakeLock = powerManager?.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BugsLife:SyncAlarmReceiverWakeLock"
        )
        try {
            wakeLock?.acquire(15_000L) // サービス引き継ぎ用（最大15秒）
        } catch (e: Exception) {
            Log.w(tag, "Failed to acquire receiver wake lock: ${e.message}")
        }

        try {
            val runningService = WatcherForegroundService.instance
            if (runningService != null) {
                if (action == WatcherForegroundService.ACTION_INACTIVITY_TIMEOUT) {
                    Log.d(tag, "Directly triggering inactivity timeout sync on existing service.")
                    runningService.triggerInactivityTimeoutFromReceiver()
                } else {
                    Log.d(tag, "Directly triggering scheduled sync on existing service instance.")
                    runningService.triggerScheduledSyncFromReceiver()
                }
            } else {
                Log.d(tag, "Service instance not found. Starting WatcherForegroundService with action: $action")
                val serviceIntent = Intent(context, WatcherForegroundService::class.java).apply {
                    this.action = action
                }
                WatcherForegroundService.startServiceCompat(context, serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to dispatch sync: ${e.message}", e)
        } finally {
            pendingResult.finish()
            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (e: Exception) {
                Log.w(tag, "Failed to release receiver wake lock: ${e.message}")
            }
        }
    }
}
