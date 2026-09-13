package com.kusa.bugslife.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

class ScreenEventReceiver(private val onScreenOn: () -> Unit) : BroadcastReceiver() {
    private val tag = "ScreenEventReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        Log.d(tag, "Received broadcast action: $action")
        if (action == Intent.ACTION_SCREEN_ON || action == Intent.ACTION_USER_PRESENT) {
            onScreenOn()
        }
    }
}
