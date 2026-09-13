package com.kusa.bugslife.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.kusa.bugslife.data.AppPreferences

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null) return
        val action = intent?.action
        Log.d("BootReceiver", "Received boot action: $action")
        if (action == Intent.ACTION_BOOT_COMPLETED || action == Intent.ACTION_MY_PACKAGE_REPLACED) {
            val prefs = AppPreferences(context)
            if (prefs.isServiceEnabled) {
                WatcherForegroundService.startService(context)
            }
        }
    }
}
