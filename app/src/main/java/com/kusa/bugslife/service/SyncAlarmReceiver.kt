package com.kusa.bugslife.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log

/**
 * 毎時05分の定期同期用 AlarmManager レシーバー
 * スリープ中（Dozeモード）の端末を叩き起こして WatcherForegroundService に同期を実行させます。
 */
class SyncAlarmReceiver : BroadcastReceiver() {
    private val tag = "SyncAlarmReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        Log.d(tag, "Sync alarm fired! Waking up for hourly sync window.")

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
            // サービスが既にメモリ上に常駐している場合は直接同期を実行
            val runningService = WatcherForegroundService.instance
            if (runningService != null) {
                Log.d(tag, "Directly triggering scheduled sync on existing service instance.")
                runningService.triggerScheduledSyncFromReceiver()
            } else {
                // サービスが停止している場合はフォアグラウンドサービスとして起動
                Log.d(tag, "Service instance not found. Starting WatcherForegroundService.")
                val serviceIntent = Intent(context, WatcherForegroundService::class.java).apply {
                    action = WatcherForegroundService.ACTION_SCHEDULED_SYNC
                }
                WatcherForegroundService.startServiceCompat(context, serviceIntent)
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to dispatch scheduled sync: ${e.message}", e)
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
