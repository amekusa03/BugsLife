package com.kusa.bugslife.service

import android.app.AlarmManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import com.kusa.bugslife.data.AppPreferences
import com.kusa.bugslife.data.CommunicationLog
import com.kusa.bugslife.data.MemberStatus
import com.kusa.bugslife.data.PacketType
import com.kusa.bugslife.data.PeerInfo
import com.kusa.bugslife.data.SafetyPacket
import com.kusa.bugslife.data.WatcherStateHolder
import com.kusa.bugslife.network.FirestorePeerMessenger
import com.kusa.bugslife.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class WatcherForegroundService : Service() {
    private val tag = "WatcherService"
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private lateinit var prefs: AppPreferences
    private var screenReceiver: ScreenEventReceiver? = null
    private var watchdogJob: Job? = null
    private var messenger: FirestorePeerMessenger? = null

    private var isSendInProgress = false
    private val recentPacketIds = mutableSetOf<String>()

    companion object {
        const val ACTION_START = "com.kusa.bugslife.action.START"
        const val ACTION_STOP = "com.kusa.bugslife.action.STOP"
        const val ACTION_SEND_STATUS = "com.kusa.bugslife.action.SEND_STATUS"
        const val ACTION_MANUAL_SYNC = "com.kusa.bugslife.action.MANUAL_SYNC"
        const val ACTION_SCHEDULED_SYNC = "com.kusa.bugslife.action.SCHEDULED_SYNC"
        const val ACTION_RECORD_ACTIVITY = "com.kusa.bugslife.action.RECORD_ACTIVITY"
        const val ACTION_RELOAD_SETTINGS = "com.kusa.bugslife.action.RELOAD_SETTINGS"
        const val ACTION_APPROVE_MEMBER = "com.kusa.bugslife.action.APPROVE_MEMBER"
        const val ACTION_REJECT_MEMBER = "com.kusa.bugslife.action.REJECT_MEMBER"

        const val EXTRA_STATUS_TYPE = "extra_status_type"
        const val EXTRA_STATUS_MESSAGE = "extra_status_message"
        const val EXTRA_ACTIVITY_REASON = "extra_activity_reason"
        const val EXTRA_TARGET_USER_ID = "extra_target_user_id"

        private const val ALARM_REQUEST_CODE = 9001

        @Volatile
        var instance: WatcherForegroundService? = null
            private set

        fun startService(context: Context) {
            val intent = Intent(context, WatcherForegroundService::class.java).apply {
                action = ACTION_START
            }
            startServiceCompat(context, intent)
        }

        fun sendStatus(context: Context, type: PacketType, message: String = "") {
            val intent = Intent(context, WatcherForegroundService::class.java).apply {
                action = ACTION_SEND_STATUS
                putExtra(EXTRA_STATUS_TYPE, type.name)
                putExtra(EXTRA_STATUS_MESSAGE, message)
            }
            startServiceCompat(context, intent)
        }

        fun triggerManualSync(context: Context) {
            val intent = Intent(context, WatcherForegroundService::class.java).apply {
                action = ACTION_MANUAL_SYNC
            }
            startServiceCompat(context, intent)
        }

        fun recordUserActivity(context: Context, reason: String = "操作") {
            val intent = Intent(context, WatcherForegroundService::class.java).apply {
                action = ACTION_RECORD_ACTIVITY
                putExtra(EXTRA_ACTIVITY_REASON, reason)
            }
            startServiceCompat(context, intent)
        }

        fun reloadSettings(context: Context) {
            val intent = Intent(context, WatcherForegroundService::class.java).apply {
                action = ACTION_RELOAD_SETTINGS
            }
            startServiceCompat(context, intent)
        }

        fun approveMember(context: Context, targetUserId: String) {
            val intent = Intent(context, WatcherForegroundService::class.java).apply {
                action = ACTION_APPROVE_MEMBER
                putExtra(EXTRA_TARGET_USER_ID, targetUserId)
            }
            startServiceCompat(context, intent)
        }

        fun rejectMember(context: Context, targetUserId: String) {
            val intent = Intent(context, WatcherForegroundService::class.java).apply {
                action = ACTION_REJECT_MEMBER
                putExtra(EXTRA_TARGET_USER_ID, targetUserId)
            }
            startServiceCompat(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, WatcherForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Log.w("WatcherService", "Error stopping service: ${e.message}")
            }
        }

        fun startServiceCompat(context: Context, intent: Intent) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                Log.e("WatcherService", "Failed to start service: ${e.message}", e)
            }
        }

        fun calculateNextSyncTimestamp(fromMillis: Long = System.currentTimeMillis()): Long {
            val calendar = Calendar.getInstance().apply {
                timeInMillis = fromMillis
            }
            val currentMinute = calendar.get(Calendar.MINUTE)
            val currentSecond = calendar.get(Calendar.SECOND)

            if (currentMinute < 5 || (currentMinute == 5 && currentSecond == 0 && calendar.get(Calendar.MILLISECOND) == 0)) {
                if (currentMinute == 5 && currentSecond == 0 && calendar.get(Calendar.MILLISECOND) == 0) {
                    calendar.add(Calendar.HOUR_OF_DAY, 1)
                }
                calendar.set(Calendar.MINUTE, 5)
            } else {
                calendar.add(Calendar.HOUR_OF_DAY, 1)
                calendar.set(Calendar.MINUTE, 5)
            }
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            return calendar.timeInMillis
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = AppPreferences(this)
        Log.d(tag, "WatcherForegroundService created. Group: ${prefs.groupName}")

        NotificationHelper.createNotificationChannels(this)
        startForeground(
            NotificationHelper.NOTIFICATION_ID_SERVICE,
            NotificationHelper.buildServiceNotification(this, "見守りサービス稼働中 (Firebase連携)")
        )

        registerScreenReceiver()
        initMessenger()
        startWatchdog()

        // 状態ホルダーの初期化
        WatcherStateHolder.updatePeers(prefs.getPeers())
        WatcherStateHolder.setLogs(prefs.getCommunicationLogs())
        WatcherStateHolder.setPendingStatus(prefs.pendingStatus)
        WatcherStateHolder.setTodayScreenOnCount(prefs.todayScreenOnCount)
        WatcherStateHolder.setLastLocalScreenOnTime(prefs.lastLocalScreenOnTime)
        WatcherStateHolder.setLastSyncTimestamp(prefs.lastSyncTimestamp)
        WatcherStateHolder.setMyMemberStatus(prefs.myMemberStatus)

        // 次回定期同期アラームのスケジュール
        val nextTime = calculateNextSyncTimestamp()
        WatcherStateHolder.setNextSyncTimestamp(nextTime)
        scheduleNextSyncAlarm(this, nextTime)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(tag, "onStartCommand: action = $action")

        when (action) {
            ACTION_START -> {
                updateNotification()
            }
            ACTION_STOP -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_RECORD_ACTIVITY -> {
                val reason = intent?.getStringExtra(EXTRA_ACTIVITY_REASON) ?: "ロック解除"
                handleScreenUnlock(reason)
            }
            ACTION_SEND_STATUS -> {
                val typeStr = intent?.getStringExtra(EXTRA_STATUS_TYPE)
                val type = PacketType.fromString(typeStr)
                val message = intent?.getStringExtra(EXTRA_STATUS_MESSAGE) ?: ""
                handleSendStatus(type, message)
            }
            ACTION_MANUAL_SYNC -> {
                performFirebaseSend(isDirectStatus = false, isManualTrigger = true)
            }
            ACTION_SCHEDULED_SYNC -> {
                performFirebaseSend(isDirectStatus = false, isManualTrigger = false)
            }
            ACTION_APPROVE_MEMBER -> {
                val targetId = intent?.getStringExtra(EXTRA_TARGET_USER_ID) ?: ""
                handleApproveMember(targetId)
            }
            ACTION_REJECT_MEMBER -> {
                val targetId = intent?.getStringExtra(EXTRA_TARGET_USER_ID) ?: ""
                handleRejectMember(targetId)
            }
            ACTION_RELOAD_SETTINGS -> {
                initMessenger()
                updateNotification()
            }
        }

        return START_STICKY
    }

    fun triggerScheduledSyncFromReceiver() {
        Log.d(tag, "Scheduled sync triggered directly from receiver.")
        performFirebaseSend(isDirectStatus = false, isManualTrigger = false)
    }

    private fun initMessenger() {
        messenger?.stopListening()
        messenger = FirestorePeerMessenger(
            context = this,
            groupName = prefs.groupName,
            myUserId = prefs.userId
        )

        // リアルタイムリスナー開始（グループの更新、参加申請、自身の承認状態を受信）
        messenger?.startListeningWithRequests(
            onPacketReceived = { packet ->
                handleIncomingPacket(packet)
            },
            onPendingRequestsChanged = { requests ->
                WatcherStateHolder.setPendingRequests(requests)
            },
            onMyStatusChanged = { status ->
                val prev = prefs.myMemberStatus
                prefs.myMemberStatus = status
                WatcherStateHolder.setMyMemberStatus(status)
                if (prev != status) {
                    if (status == MemberStatus.APPROVED) {
                        serviceScope.launch {
                            WatcherStateHolder.emitUiEvent("🎉 グループへの参加が承認されました！")
                        }
                    } else if (status == MemberStatus.REJECTED) {
                        serviceScope.launch {
                            WatcherStateHolder.emitUiEvent("❌ グループへの参加が拒否されました")
                        }
                    }
                }
            }
        )
    }

    private fun handleApproveMember(targetUserId: String) {
        if (targetUserId.isBlank()) return
        serviceScope.launch(Dispatchers.IO) {
            val success = messenger?.approveMember(prefs.groupName, targetUserId) ?: false
            if (success) {
                WatcherStateHolder.removePendingRequest(targetUserId)
                WatcherStateHolder.emitUiEvent("✅ メンバーの参加を承認しました")
            }
        }
    }

    private fun handleRejectMember(targetUserId: String) {
        if (targetUserId.isBlank()) return
        serviceScope.launch(Dispatchers.IO) {
            val success = messenger?.rejectMember(prefs.groupName, targetUserId) ?: false
            if (success) {
                WatcherStateHolder.removePendingRequest(targetUserId)
                WatcherStateHolder.emitUiEvent("🚫 メンバーの参加を拒否しました")
            }
        }
    }

    /**
     * 【仕様: 送信】画面ロック解除時の処理
     * 1. 内部ファイルにタイムスタンプを保存
     * 2. 前回のFirebase正常送信から1時間以上経過していればFirebaseへ送信
     * 3. 送信成功なら内部ファイルの未送信タイムスタンプを削除
     */
    private fun handleScreenUnlock(reason: String) {
        val now = System.currentTimeMillis()
        prefs.addPendingUnlockTimestamp(now)
        prefs.todayScreenOnCount += 1
        prefs.lastLocalScreenOnTime = now
        WatcherStateHolder.setTodayScreenOnCount(prefs.todayScreenOnCount)
        WatcherStateHolder.setLastLocalScreenOnTime(now)

        val lastSend = prefs.lastSuccessfulSendTimestamp
        val elapsed = now - lastSend

        Log.d(tag, "Screen unlock recorded ($reason). Pending count: ${prefs.getPendingUnlockTimestamps().size}, Elapsed since last send: ${elapsed / 60000}m")

        if (lastSend == 0L || elapsed >= AppPreferences.ONE_HOUR_MS) {
            Log.d(tag, "1 hour elapsed since last send. Sending to Firebase...")
            performFirebaseSend(isDirectStatus = false)
        } else {
            val remainMinutes = ((AppPreferences.ONE_HOUR_MS - elapsed) / 60000L).coerceAtLeast(1L)
            serviceScope.launch {
                WatcherStateHolder.emitUiEvent("📱 $reason を記録 (次回自動送信まで約${remainMinutes}分)")
            }
        }
    }

    /**
     * 【仕様: 送信】「元気です」「良くない」ボタン押下時
     * 即座にFirebaseのDBを更新し、成功したら内部ファイルのタイムスタンプをクリア
     */
    private fun handleSendStatus(type: PacketType, message: String) {
        prefs.pendingStatus = type
        prefs.pendingStatusMessage = message
        WatcherStateHolder.setPendingStatus(type)

        Log.d(tag, "Direct status send requested: ${type.label}")
        performFirebaseSend(isDirectStatus = true)
    }

    /**
     * Firebaseへの送信処理
     */
    private fun performFirebaseSend(isDirectStatus: Boolean, isManualTrigger: Boolean = false) {
        if (isSendInProgress) {
            Log.d(tag, "Send already in progress, skipping.")
            return
        }

        // 承認されていない場合は定期送信をスキップ
        if (prefs.myMemberStatus == MemberStatus.REJECTED) {
            Log.w(tag, "Member is rejected, skipping send.")
            return
        }

        isSendInProgress = true
        WatcherStateHolder.setSyncing(true)
        serviceScope.launch {
            WatcherStateHolder.emitUiEvent(if (isDirectStatus) "📤 ステータス更新中..." else "🔄 Firebase送信中...")
        }

        serviceScope.launch(Dispatchers.IO) {
            var wakeLock: PowerManager.WakeLock? = null
            try {
                val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "bugslife:FirebaseSendWakeLock"
                )?.apply {
                    acquire(20_000L)
                }
            } catch (e: Exception) {
                Log.w(tag, "Could not acquire WakeLock: ${e.message}")
            }

            val pendingTimestamps = prefs.getPendingUnlockTimestamps()
            val status = prefs.pendingStatus
            val message = prefs.pendingStatusMessage

            val packet = SafetyPacket(
                senderId = prefs.userId,
                senderName = prefs.userName,
                type = if (isDirectStatus) (status ?: PacketType.STATUS_FINE) else PacketType.HEARTBEAT,
                status = status,
                memberStatus = prefs.myMemberStatus,
                unlockTimestamps = pendingTimestamps,
                timestamp = System.currentTimeMillis(),
                message = message
            )

            val currentMessenger = messenger ?: FirestorePeerMessenger(
                this@WatcherForegroundService,
                prefs.groupName,
                prefs.userId
            )

            // Firebaseへマージ送信（Firestore上で上限100件を超えたら古いものを削除）
            val isSuccess = currentMessenger.sendPacketWithMerge(packet, pendingTimestamps)

            if (isSuccess) {
                val now = System.currentTimeMillis()
                prefs.lastSuccessfulSendTimestamp = now
                prefs.lastSyncTimestamp = now
                WatcherStateHolder.setLastSyncTimestamp(now)
                WatcherStateHolder.setLastSentTimestamp(now)

                // ★送信が正常に完了していたら内部ファイルのタイムスタンプを削除
                prefs.clearPendingUnlockTimestamps()

                if (isDirectStatus) {
                    prefs.pendingStatus = null
                    prefs.pendingStatusMessage = ""
                    WatcherStateHolder.setPendingStatus(null)
                }

                val detailStr = if (isDirectStatus) {
                    "ステータス即座送信: ${status?.label}"
                } else if (isManualTrigger) {
                    "手動同期: タイムスタンプ${pendingTimestamps.size}件送信"
                } else {
                    "定期/解除送信: タイムスタンプ${pendingTimestamps.size}件送信"
                }

                appendLog(
                    CommunicationLog(
                        isIncoming = false,
                        peerName = prefs.groupName,
                        packetType = status ?: packet.type,
                        detail = detailStr
                    )
                )

                WatcherStateHolder.emitUiEvent("✅ Firebase送信完了 (内部ログをクリア)")
                Log.d(tag, "Firebase send success: pending timestamps cleared.")

                // ついでにグループ内の他メンバーの最新状態も取得
                fetchPeersAndCheckInactivity(currentMessenger)
            } else {
                WatcherStateHolder.emitUiEvent("⚠️ Firebase送信失敗 (内部ファイルに保持して次回再試行)")
                Log.w(tag, "Firebase send failed. Timestamps remain in pending buffer.")
            }

            isSendInProgress = false
            WatcherStateHolder.setSyncing(false)

            // 次回定期アラームをスケジュール
            val nextTime = calculateNextSyncTimestamp()
            WatcherStateHolder.setNextSyncTimestamp(nextTime)
            scheduleNextSyncAlarm(this@WatcherForegroundService, nextTime)

            updateNotification()

            try {
                if (wakeLock?.isHeld == true) {
                    wakeLock.release()
                }
            } catch (e: Exception) {
                Log.w(tag, "Error releasing wake lock: ${e.message}")
            }
        }
    }

    /**
     * 【仕様: 受信】Firestoreからグループの更新通知を受け取った時の処理
     */
    private fun handleIncomingPacket(packet: SafetyPacket) {
        serviceScope.launch {
            Log.d(tag, "Handling incoming packet: $packet")

            if (packet.senderId == prefs.userId) {
                return@launch
            }

            // 重複判定
            val isNewPacket = synchronized(recentPacketIds) {
                if (recentPacketIds.contains(packet.packetId)) {
                    false
                } else {
                    recentPacketIds.add(packet.packetId)
                    if (recentPacketIds.size > 100) {
                        recentPacketIds.remove(recentPacketIds.iterator().next())
                    }
                    true
                }
            }

            val effectiveStatus = packet.status ?: if (packet.type == PacketType.STATUS_FINE || packet.type == PacketType.STATUS_UNWELL) packet.type else null

            prefs.updatePeerStatus(
                senderId = packet.senderId,
                senderName = packet.senderName,
                status = effectiveStatus,
                timestamp = packet.timestamp,
                message = packet.message,
                isAlert = false,
                memberStatus = packet.memberStatus,
                unlockTimestamps = packet.unlockTimestamps
            )

            val updatedPeers = prefs.getPeers()
            WatcherStateHolder.updatePeers(updatedPeers)

            val isNoActivity = packet.unlockTimestamps.isEmpty() && packet.type != PacketType.PING && packet.type != PacketType.ACK

            if (isNewPacket) {
                val detailStr = buildString {
                    if (effectiveStatus != null) {
                        append(effectiveStatus.label)
                        append(" / ")
                    }
                    if (isNoActivity) {
                        append("⚠️操作履歴なし")
                    } else {
                        append("操作履歴:${packet.unlockTimestamps.size}件")
                    }
                    if (packet.message.isNotBlank()) {
                        append(" (${packet.message})")
                    }
                }

                appendLog(
                    CommunicationLog(
                        isIncoming = true,
                        peerName = packet.senderName,
                        packetType = effectiveStatus ?: packet.type,
                        detail = detailStr
                    )
                )

                // ステータス通知
                when (effectiveStatus) {
                    PacketType.STATUS_FINE -> {
                        NotificationHelper.showStatusNotification(
                            this@WatcherForegroundService,
                            packet.senderName,
                            isFine = true,
                            message = packet.message
                        )
                    }
                    PacketType.STATUS_UNWELL -> {
                        NotificationHelper.showStatusNotification(
                            this@WatcherForegroundService,
                            packet.senderName,
                            isFine = false,
                            message = packet.message
                        )
                    }
                    else -> {}
                }
            }

            val statusMsg = effectiveStatus?.label ?: if (isNoActivity) "⚠️活動なし" else "活動${packet.unlockTimestamps.size}件"
            WatcherStateHolder.emitUiEvent("受信: ${packet.senderName}さん「$statusMsg」")
        }
    }

    /**
     * 【仕様: 異常判定】
     * 24時間グループ内の更新がない場合、Firebaseの情報を取得し、
     * 自分以外の更新が24時間なければユーザー異常を検知する
     */
    private suspend fun fetchPeersAndCheckInactivity(messengerInstance: FirestorePeerMessenger) {
        val peersFromCloud = messengerInstance.fetchPeersOnce()
        val now = System.currentTimeMillis()
        val timeoutMs = prefs.timeoutDurationMillis // デフォルト24時間

        for (p in peersFromCloud) {
            val effectiveStatus = p.status ?: if (p.type == PacketType.STATUS_FINE || p.type == PacketType.STATUS_UNWELL) p.type else null
            prefs.updatePeerStatus(
                senderId = p.senderId,
                senderName = p.senderName,
                status = effectiveStatus,
                timestamp = p.timestamp,
                message = p.message,
                isAlert = false,
                memberStatus = p.memberStatus,
                unlockTimestamps = p.unlockTimestamps
            )
        }

        val allPeers = prefs.getPeers().toMutableList()
        var hasChanges = false

        for (i in allPeers.indices) {
            val peer = allPeers[i]
            val lastActivityTime = if (peer.unlockTimestamps.isNotEmpty()) {
                maxOf(peer.lastSeenTimestamp, peer.lastUnlockTimestamp)
            } else {
                peer.lastSeenTimestamp
            }

            if (lastActivityTime > 0L) {
                val elapsed = now - lastActivityTime
                if (elapsed >= timeoutMs && !peer.isAlertTriggered) {
                    val elapsedHours = elapsed.toDouble() / (1000 * 60 * 60)
                    Log.w(tag, "🚨 24h Inactivity Alert: No activity from ${peer.name} for ${elapsedHours}h")
                    NotificationHelper.showNoActivityAlert(
                        this@WatcherForegroundService,
                        peer.id,
                        peer.name
                    )
                    allPeers[i] = peer.copy(isAlertTriggered = true)
                    hasChanges = true
                }
            }
        }

        if (hasChanges) {
            prefs.savePeers(allPeers)
            WatcherStateHolder.updatePeers(allPeers)
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            while (isActive) {
                delay(60_000L) // 1分ごとにチェック
                checkPeersInactivity()
            }
        }
    }

    private suspend fun checkPeersInactivity() = withContext(Dispatchers.IO) {
        val messengerInstance = messenger ?: return@withContext
        val now = System.currentTimeMillis()
        val peers = prefs.getPeers()

        // 自分以外の相手で24時間以上更新がないメンバーがいるか確認
        val hasStalePeer = peers.any { peer ->
            val lastTime = if (peer.unlockTimestamps.isNotEmpty()) maxOf(peer.lastSeenTimestamp, peer.lastUnlockTimestamp) else peer.lastSeenTimestamp
            lastTime > 0L && (now - lastTime >= prefs.timeoutDurationMillis)
        }

        if (hasStalePeer || peers.isEmpty()) {
            // Firebaseから強制取得して最新状態を確認
            fetchPeersAndCheckInactivity(messengerInstance)
        }
    }

    private fun registerScreenReceiver() {
        if (screenReceiver != null) return

        screenReceiver = ScreenEventReceiver { reason ->
            recordUserActivity(this, reason)
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_ON)
        }
        registerReceiver(screenReceiver, filter)
        Log.d(tag, "ScreenEventReceiver registered.")
    }

    private fun unregisterScreenReceiver() {
        if (screenReceiver != null) {
            try {
                unregisterReceiver(screenReceiver)
                Log.d(tag, "ScreenEventReceiver unregistered.")
            } catch (e: Exception) {
                Log.w(tag, "Error unregistering ScreenEventReceiver: ${e.message}")
            }
            screenReceiver = null
        }
    }

    private fun scheduleNextSyncAlarm(context: Context, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, SyncAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
            Log.d(tag, "Next periodic alarm scheduled at: ${formatTime(triggerAtMillis)}")
        } catch (e: SecurityException) {
            Log.w(tag, "Exact alarm permission not granted, falling back to setWindow")
            alarmManager.setWindow(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                10 * 60 * 1000L,
                pendingIntent
            )
        } catch (e: Exception) {
            Log.e(tag, "Failed to schedule alarm: ${e.message}", e)
        }
    }

    private fun cancelSyncAlarm(context: Context) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, SyncAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            ALARM_REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun updateNotification() {
        val group = prefs.groupName
        val notification = NotificationHelper.buildServiceNotification(
            this,
            "見守り稼働中 [グループ: $group]"
        )
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as? android.app.NotificationManager
        notificationManager?.notify(NotificationHelper.NOTIFICATION_ID_SERVICE, notification)
    }

    private fun appendLog(log: CommunicationLog) {
        prefs.addCommunicationLog(log)
        WatcherStateHolder.addLog(log)
    }

    private fun formatTime(millis: Long): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
    }

    private fun stopForegroundService() {
        unregisterScreenReceiver()
        cancelSyncAlarm(this)
        watchdogJob?.cancel()
        watchdogJob = null
        messenger?.stopListening()
        messenger = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (instance == this) {
            instance = null
        }
        stopForegroundService()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
