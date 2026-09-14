package com.kusa.bugslife.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * ユーザーの端末操作（画面ロック解除）を検知するレシーバー
 * 着信や通知受信による画面点灯での誤検知を防ぐため、ACTION_USER_PRESENT（本人の明示的な操作）のみを検知対象とします。
 */
class ScreenEventReceiver(private val onUserActive: () -> Unit) : BroadcastReceiver() {
    private val tag = "ScreenEventReceiver"

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        Log.d(tag, "Received broadcast action: $action")
        if (action == Intent.ACTION_USER_PRESENT) {
            onUserActive()
        }
    }
}
