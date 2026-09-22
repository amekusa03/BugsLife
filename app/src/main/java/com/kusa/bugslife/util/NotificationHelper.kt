package com.kusa.bugslife.util

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.kusa.bugslife.MainActivity
import com.kusa.bugslife.R

object NotificationHelper {
    const val CHANNEL_SERVICE = "channel_watcher_service"
    const val CHANNEL_ALERT = "channel_safety_alert"
    const val CHANNEL_STATUS = "channel_status_updates"

    const val NOTIFICATION_ID_SERVICE = 1001
    const val NOTIFICATION_ID_ALERT_BASE = 2000
    const val NOTIFICATION_ID_SELF_INACTIVITY = 2999
    const val NOTIFICATION_ID_STATUS_BASE = 3000

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            // 常駐サービス用
            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                "見守り常駐サービス",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "安否見守りと通信待受をバックグラウンドで維持します"
                setShowBadge(false)
            }

            // 緊急・24時間無通信/無活動アラート用 (高優先度・音・バイブ)
            val alertChannel = NotificationChannel(
                CHANNEL_ALERT,
                "安否警告アラート (重要)",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "24時間端末が無反応な場合や活動が確認できない緊急時の通知"
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 500, 200, 500, 200, 1000)
                setShowBadge(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            // 通常のステータス更新 (元気/良くない)
            val statusChannel = NotificationChannel(
                CHANNEL_STATUS,
                "安否ステータス通知",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "親類からの元気・体調連絡通知"
                enableVibration(true)
                setShowBadge(true)
            }

            notificationManager.createNotificationChannels(
                listOf(serviceChannel, alertChannel, statusChannel)
            )
        }
    }

    fun buildServiceNotification(
        context: Context,
        contentText: String
    ): Notification {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setContentTitle("遠隔安否見守り 稼働中")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_notification_bug)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    /**
     * 自身が24時間無操作になった場合の警告通知（見守られ側本人用）
     */
    fun showSelfInactivityWarning(
        context: Context,
        hours: Long
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            NOTIFICATION_ID_SELF_INACTIVITY,
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setContentTitle("⚠️ 安否確認: ${hours}時間スマートフォンの操作がありません")
            .setContentText("見守り相手に活動停止状態が共有されています。")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "【安否確認のお願い】\n過去${hours}時間スマートフォンのロック解除等の操作が確認できていません。\n見守り相手に活動停止が通知されています。ご無事の場合は画面のロックを解除するか、アプリで「元気です」をタップしてください。"
                )
            )
            .setSmallIcon(R.drawable.ic_notification_bug)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 200, 500, 200, 1000))
            .build()

        notificationManager.notify(NOTIFICATION_ID_SELF_INACTIVITY, notification)
    }

    /**
     * 自身の無操作警告通知を解除
     */
    fun cancelSelfInactivityWarning(context: Context) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID_SELF_INACTIVITY)
    }

    /**
     * 相手の安否アラート通知を解除（活動再開時）
     */
    fun cancelPeerAlert(context: Context, peerId: String) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID_ALERT_BASE + (peerId.hashCode() % 500))
        notificationManager.cancel(NOTIFICATION_ID_ALERT_BASE + ((peerId + "_no_activity").hashCode() % 500))
    }

    /**
     * 過去24時間活動なし（タイムスタンプ0件）の人的異常アラート（仕様D - 人的異常）
     */
    fun showNoActivityAlert(
        context: Context,
        peerId: String,
        peerName: String
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            (peerId + "_no_activity").hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setContentTitle("🚨 緊急安否警告: ${peerName}さんの活動が確認できません")
            .setContentText("${peerName}さんは過去24時間スマートフォンを一度も操作していません。")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "【緊急安否確認】\n${peerName}さんの端末通信は届いていますが、過去24時間画面ロック解除（スマホ操作）が一度も行われていません。\n倒れている等の緊急事態の可能性があります。至急連絡や現地確認を行ってください。"
                )
            )
            .setSmallIcon(R.drawable.ic_notification_bug)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 800, 200, 800, 200, 1500))
            .build()

        notificationManager.notify(NOTIFICATION_ID_ALERT_BASE + (peerId.hashCode() % 500), notification)
    }

    /**
     * 一定時間通信自体が途絶えた場合の機器・通信障害アラート
     */
    fun showInactivityAlert(
        context: Context,
        peerId: String,
        peerName: String,
        elapsedHours: Double
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            peerId.hashCode(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val hoursFormatted = String.format(java.util.Locale.JAPAN, "%.1f", elapsedHours)
        val notification = NotificationCompat.Builder(context, CHANNEL_ALERT)
            .setContentTitle("⚠️ 通信途絶警告: ${peerName}さんの端末が無反応です")
            .setContentText("${peerName}さんの端末から約${hoursFormatted}時間定期通信が届いていません。")
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    "【端末通信未達】\n${peerName}さんのスマートフォンから ${hoursFormatted}時間 以上定期通信が途絶えています。\n端末の電源切れ、故障、圏外、またはアプリ停止の可能性があります。"
                )
            )
            .setSmallIcon(R.drawable.ic_notification_bug)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVibrate(longArrayOf(0, 500, 300, 500, 300, 1000))
            .build()

        notificationManager.notify(NOTIFICATION_ID_ALERT_BASE + (peerId.hashCode() % 500), notification)
    }

    fun showStatusNotification(
        context: Context,
        senderName: String,
        isFine: Boolean,
        message: String?
    ) {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            System.currentTimeMillis().toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val title = if (isFine) "😄 ${senderName}さん: 元気です！" else "😣 ${senderName}さん: 良くない"
        val body = if (message.isNullOrBlank()) {
            if (isFine) "${senderName}さんから「元気」の連絡が届きました。" else "${senderName}さんが「良くない」と伝えています。"
        } else {
            message
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setSmallIcon(R.drawable.ic_notification_bug)
            .setPriority(if (isFine) NotificationCompat.PRIORITY_DEFAULT else NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(
            NOTIFICATION_ID_STATUS_BASE + ((senderName.hashCode() + System.currentTimeMillis()).toInt() % 500),
            notification
        )
    }
}
