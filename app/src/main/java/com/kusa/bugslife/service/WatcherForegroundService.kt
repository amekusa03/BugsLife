package com.kusa.bugslife.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import com.kusa.bugslife.data.AppPreferences
import com.kusa.bugslife.data.CommunicationLog
import com.kusa.bugslife.data.PacketType
import com.kusa.bugslife.data.SafetyPacket
import com.kusa.bugslife.data.WatcherStateHolder
import com.kusa.bugslife.network.PeerMessenger
import com.kusa.bugslife.network.SkyWayPeerMessenger
import com.kusa.bugslife.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class WatcherForegroundService : Service() {
    private val tag = "WatcherService"
    private val serviceScope = CoroutineScope(Dispatchers.Default)

    private lateinit var prefs: AppPreferences
    private var messenger: PeerMessenger? = null

    private var screenReceiver: ScreenEventReceiver? = null
    private var watchdogJob: Job? = null
    private var scheduledSyncJob: Job? = null
    private val syncMutex = Mutex()

    companion object {
        const val ACTION_START = "com.kusa.bugslife.START_SERVICE"
        const val ACTION_STOP = "com.kusa.bugslife.STOP_SERVICE"
        const val ACTION_SEND_STATUS = "com.kusa.bugslife.SEND_STATUS"
        const val ACTION_MANUAL_SYNC = "com.kusa.bugslife.MANUAL_SYNC"
        const val ACTION_SEND_SCREEN_ON = "com.kusa.bugslife.SEND_SCREEN_ON"
        const val ACTION_RELOAD_SETTINGS = "com.kusa.bugslife.RELOAD_SETTINGS"

        const val EXTRA_STATUS_TYPE = "extra_status_type"
        const val EXTRA_STATUS_MESSAGE = "extra_status_message"

        // 同期ウィンドウの時間 (毎時同期: 90秒, 手動同期: 30秒)
        const val SYNC_WINDOW_SCHEDULED_MS = 90_000L
        const val SYNC_WINDOW_MANUAL_MS = 30_000L

        fun startService(context: Context) {
            val intent = Intent(context, WatcherForegroundService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun sendStatus(context: Context, type: PacketType, message: String = "") {
            val intent = Intent(context, WatcherForegroundService::class.java).apply {
                action = ACTION_SEND_STATUS
                putExtra(EXTRA_STATUS_TYPE, type.name)
                putExtra(EXTRA_STATUS_MESSAGE, message)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun triggerManualSync(context: Context) {
            val intent = Intent(context, WatcherForegroundService::class.java).apply {
                action = ACTION_MANUAL_SYNC
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun reloadSettings(context: Context) {
            val intent = Intent(context, WatcherForegroundService::class.java).apply {
                action = ACTION_RELOAD_SETTINGS
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, WatcherForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = AppPreferences(this)
        Log.d(tag, "WatcherForegroundService onCreate")
        WatcherStateHolder.updatePeers(prefs.getPeers())
        WatcherStateHolder.setLastSyncTimestamp(prefs.lastSyncTimestamp)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(tag, "onStartCommand action=")

        when (action) {
            ACTION_START -> {
                startForegroundWithNotification()
                setupListenersAndSchedule()
            }
            ACTION_SEND_STATUS -> {
                val typeStr = intent?.getStringExtra(EXTRA_STATUS_TYPE)
                val message = intent?.getStringExtra(EXTRA_STATUS_MESSAGE) ?: ""
                val type = PacketType.fromString(typeStr)
                
                // ローカルステータス更新
                prefs.myLastStatus = type
                prefs.myLastStatusMessage = message
                WatcherStateHolder.emitUiEventSync("ステータス更新: ${type.emoji} ${type.label}")

                // 即時手動同期を実行
                serviceScope.launch {
                    performSyncWindow(isManual = true, customMessage = message)
                }
            }
            ACTION_MANUAL_SYNC -> {
                serviceScope.launch {
                    performSyncWindow(isManual = true)
                }
            }
            ACTION_RELOAD_SETTINGS -> {
                setupListenersAndSchedule()
            }
            ACTION_STOP -> {
                stopForegroundService()
            }
        }

        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = NotificationHelper.buildServiceNotification(
            this,
            getServiceStatusSummary()
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                NotificationHelper.NOTIFICATION_ID_SERVICE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NotificationHelper.NOTIFICATION_ID_SERVICE, notification)
        }
    }

    private fun getServiceStatusSummary(): String {
        val peers = prefs.getPeers()
        val nextSyncTimeStr = formatTime(calculateNextSyncTimestamp())
        return "相互見守り稼働中 (毎時05分同期) | 次回: $nextSyncTimeStr (本日操作: ${prefs.todayScreenOnCount}回)"
    }

    private fun updateNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        val notification = NotificationHelper.buildServiceNotification(this, getServiceStatusSummary())
        manager.notify(NotificationHelper.NOTIFICATION_ID_SERVICE, notification)
    }

    private fun setupListenersAndSchedule() {
        registerScreenReceiver()
        startWatchdog()
        startScheduledSyncLoop()
        updateNotification()
    }

    private fun registerScreenReceiver() {
        if (screenReceiver == null) {
            screenReceiver = ScreenEventReceiver {
                handleUserPresentEvent()
            }
            // 着信や通知での画面点灯誤検知を防ぐため、ユーザーが明示的にロック解除・操作した時のみ検知
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(screenReceiver, filter)
            Log.d(tag, "User present (Screen unlock) receiver registered.")
        }
    }

    private fun unregisterScreenReceiver() {
        screenReceiver?.let {
            try {
                unregisterReceiver(it)
            } catch (e: Exception) {
                Log.e(tag, "Error unregistering screen receiver: ${e.message}")
            }
            screenReceiver = null
            Log.d(tag, "Screen receiver unregistered.")
        }
    }

    private fun handleUserPresentEvent() {
        val now = System.currentTimeMillis()
        prefs.todayScreenOnCount += 1
        prefs.lastLocalScreenOnTime = now
        Log.d(tag, "User present (Unlock) detected: total=${prefs.todayScreenOnCount} times (Saved for 05min sync)")
        updateNotification()
    }

    /**
     * 次の「毎時05分（XX:05:00）」のミリ秒タイムスタンプを計算
     */
    private fun calculateNextSyncTimestamp(): Long {
        val calendar = Calendar.getInstance()
        val minute = calendar.get(Calendar.MINUTE)
        val second = calendar.get(Calendar.SECOND)
        if (minute < 5 || (minute == 5 && second < 10)) {
            // 今時 05分 00秒
            calendar.set(Calendar.MINUTE, 5)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
        } else {
            // 次時 05分 00秒
            calendar.add(Calendar.HOUR_OF_DAY, 1)
            calendar.set(Calendar.MINUTE, 5)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
        }
        return calendar.timeInMillis
    }

    /**
     * 毎時05分の定期同期間隔ループ
     */
    private fun startScheduledSyncLoop() {
        scheduledSyncJob?.cancel()
        scheduledSyncJob = serviceScope.launch {
            while (isActive) {
                val nextSyncTime = calculateNextSyncTimestamp()
                WatcherStateHolder.setNextSyncTimestamp(nextSyncTime)
                val now = System.currentTimeMillis()
                val waitMs = (nextSyncTime - now).coerceAtLeast(1000L)

                Log.d(tag, "Next scheduled sync at ${formatTime(nextSyncTime)} (in ${waitMs / 1000}s)")
                updateNotification()

                delay(waitMs)

                Log.d(tag, "=== 毎時05分 定期同期ウィンドウ 開始 ===")
                performSyncWindow(isManual = false)
            }
        }
    }

    /**
     * 同期ウィンドウを実行（SkyWay接続 -> パケット送受信 -> 切断）
     */
    private suspend fun performSyncWindow(
        isManual: Boolean,
        customMessage: String? = null
    ) = withContext(Dispatchers.IO) {
        if (!prefs.isSkyWayEnabled || prefs.skywayAppId.isBlank() || prefs.skywaySecretKey.isBlank()) {
            Log.w(tag, "SkyWay is not configured. Skipping sync window.")
            WatcherStateHolder.emitUiEvent("⚠️ SkyWay未設定のため同期をスキップしました")
            return@withContext
        }

        syncMutex.withLock {
            val syncTypeStr = if (isManual) "手動同期" else "毎時05分同期"
            Log.d(tag, "performSyncWindow start: $syncTypeStr")
            WatcherStateHolder.setSyncing(true)
            WatcherStateHolder.emitUiEvent("🔄 $syncTypeStr を開始中 (SkyWay接続中...)")

            val windowDurationMs = if (isManual) SYNC_WINDOW_MANUAL_MS else SYNC_WINDOW_SCHEDULED_MS
            val currentMessenger = SkyWayPeerMessenger(
                context = applicationContext,
                appId = prefs.skywayAppId,
                secretKey = prefs.skywaySecretKey,
                roomName = prefs.skywayRoomName,
                memberName = prefs.userName
            )
            messenger = currentMessenger

            try {
                // 接続開始 & パケット受信リスナー登録
                currentMessenger.startListening { incomingPacket ->
                    handleIncomingPacket(incomingPacket)
                }

                // 接続確立を最大15秒待機
                val connectWaitStart = System.currentTimeMillis()
                while (isActive && System.currentTimeMillis() - connectWaitStart < 15_000L) {
                    delay(500L)
                }

                // 自端末の最新パケットを作成
                val msgDetail = customMessage ?: if (prefs.myLastStatusMessage.isNotBlank()) {
                    prefs.myLastStatusMessage
                } else {
                    "スマホ操作: 本日${prefs.todayScreenOnCount}回"
                }

                val myPacket = SafetyPacket(
                    senderId = prefs.userId,
                    senderName = prefs.userName,
                    type = prefs.myLastStatus,
                    timestamp = prefs.lastLocalScreenOnTime,
                    message = msgDetail
                )

                // 送信
                val sendSuccess = currentMessenger.sendPacket(myPacket)
                if (sendSuccess) {
                    WatcherStateHolder.setLastSentTimestamp(myPacket.timestamp)
                    WatcherStateHolder.addLog(
                        CommunicationLog(
                            isIncoming = false,
                            peerName = "見守りグループ (${prefs.skywayRoomName})",
                            packetType = myPacket.type,
                            detail = msgDetail
                        )
                    )
                    WatcherStateHolder.emitUiEvent("送信完了: ${myPacket.type.emoji} ${myPacket.type.label}")
                }

                // 相手からのパケット受信・すれ違い防止のため同期ウィンドウ期間待機
                // 15秒ごとにパケットを再同報（遅れて入室したピアに届ける）
                val windowStartTime = System.currentTimeMillis()
                while (isActive && (System.currentTimeMillis() - windowStartTime < windowDurationMs)) {
                    delay(15_000L)
                    if (System.currentTimeMillis() - windowStartTime < windowDurationMs) {
                        currentMessenger.sendPacket(myPacket)
                    }
                }

            } catch (t: Throwable) {
                Log.e(tag, "Error during sync window: ${t.message}", t)
            } finally {
                // 同期ウィンドウ終了 -> SkyWay完全切断
                currentMessenger.stopListening()
                messenger = null

                val now = System.currentTimeMillis()
                prefs.lastSyncTimestamp = now
                WatcherStateHolder.setLastSyncTimestamp(now)
                WatcherStateHolder.setSyncing(false)
                WatcherStateHolder.emitUiEvent("✅ $syncTypeStr 完了 (SkyWay切断・スリープ)")
                Log.d(tag, "performSyncWindow finished: $syncTypeStr completed and disconnected.")
                updateNotification()
            }
        }
    }

    private fun handleIncomingPacket(packet: SafetyPacket) {
        serviceScope.launch {
            Log.d(tag, "Handling incoming packet: $packet")

            if (packet.senderId == prefs.userId) {
                return@launch
            }

            prefs.updatePeerStatus(
                senderId = packet.senderId,
                senderName = packet.senderName,
                status = packet.type,
                timestamp = packet.timestamp,
                message = packet.message,
                isAlert = false
            )

            val updatedPeers = prefs.getPeers()
            WatcherStateHolder.updatePeers(updatedPeers)

            WatcherStateHolder.addLog(
                CommunicationLog(
                    isIncoming = true,
                    peerName = packet.senderName,
                    packetType = packet.type,
                    detail = packet.message.ifBlank { packet.type.label }
                )
            )

            when (packet.type) {
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
                PacketType.PING -> {
                    val ackPacket = SafetyPacket(
                        senderId = prefs.userId,
                        senderName = prefs.userName,
                        type = PacketType.ACK,
                        message = "接続OK"
                    )
                    messenger?.sendPacket(ackPacket)
                }
                else -> {}
            }

            WatcherStateHolder.emitUiEvent("受信: ${packet.senderName}さん「${packet.type.label}」")
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            while (isActive) {
                delay(30_000L)
                checkPeersInactivity()
            }
        }
    }

    private suspend fun checkPeersInactivity() = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        val timeoutMs = prefs.timeoutDurationMillis
        val peers = prefs.getPeers().toMutableList()
        var hasChanges = false

        for (i in peers.indices) {
            val peer = peers[i]
            if (peer.lastSeenTimestamp > 0L) {
                val elapsed = now - peer.lastSeenTimestamp
                if (elapsed >= timeoutMs && !peer.isAlertTriggered) {
                    val elapsedHours = elapsed.toDouble() / (1000 * 60 * 60)
                    NotificationHelper.showInactivityAlert(
                        this@WatcherForegroundService,
                        peer.id,
                        peer.name,
                        elapsedHours
                    )
                    peers[i] = peer.copy(isAlertTriggered = true)
                    hasChanges = true
                    Log.w(tag, "Inactivity alert triggered for ${peer.name} (${elapsedHours}h)")
                }
            }
        }

        if (hasChanges) {
            prefs.savePeers(peers)
            WatcherStateHolder.updatePeers(peers)
        }
    }

    private fun formatTime(millis: Long): String {
        return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(millis))
    }

    private fun stopForegroundService() {
        unregisterScreenReceiver()
        watchdogJob?.cancel()
        watchdogJob = null
        scheduledSyncJob?.cancel()
        scheduledSyncJob = null
        messenger?.stopListening()
        messenger = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopForegroundService()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
