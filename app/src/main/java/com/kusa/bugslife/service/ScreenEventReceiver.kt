package com.kusa.bugslife.service

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * ユーザーの端末操作（画面点灯・画面ロック解除）を検知するレシーバー
 * - ACTION_USER_PRESENT: パターン/PIN等のキーガード明示解除
 * - ACTION_SCREEN_ON: 画面点灯。Keyguardがロックされていない状態（SmartLockやロックなし設定）での操作開始を検知
 */
class ScreenEventReceiver(private val onUserActive: (reason: String) -> Unit) : BroadcastReceiver() {
    private val tag = "ScreenEventReceiver"
    private var lastTriggerTime = 0L

    override fun onReceive(context: Context?, intent: Intent?) {
        val action = intent?.action
        Log.d(tag, "Received broadcast action: $action")

        val now = System.currentTimeMillis()

        when (action) {
            Intent.ACTION_USER_PRESENT -> {
                Log.d(tag, "User present (Keyguard unlocked)")
                if (now - lastTriggerTime > 3_000L) { // 3秒以内の連打・二重検知を防止
                    lastTriggerTime = now
                    onUserActive("ロック解除")
                }
            }
            Intent.ACTION_SCREEN_ON -> {
                val keyguardManager = context?.getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                val isLocked = keyguardManager?.isKeyguardLocked ?: false
                if (!isLocked) {
                    Log.d(tag, "Screen turned ON (Unset or unlocked keyguard)")
                    if (now - lastTriggerTime > 3_000L) {
                        lastTriggerTime = now
                        onUserActive("画面点灯(解除済)")
                    }
                } else {
                    Log.d(tag, "Screen turned ON (Keyguard locked, waiting for unlock/USER_PRESENT)")
                }
            }
        }
    }
}
