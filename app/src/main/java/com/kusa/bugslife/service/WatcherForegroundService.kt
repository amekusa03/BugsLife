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
import com.kusa.bugslife.data.PeerInfo
import com.kusa.bugslife.data.SafetyPacket
import com.kusa.bugslife.data.WatcherStateHolder
import com.kusa.bugslife.network.PeerMessenger
import com.kusa.bugslife.network.UdpPeerMessenger
import com.kusa.bugslife.util.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class WatcherForegroundService : Service() {
    private val tag = "WatcherService"
    private val serviceScope = CoroutineScope(Dispatchers.Default)

    private lateinit var prefs: AppPreferences
    private val messenger: PeerMessenger = UdpPeerMessenger()

    private var screenReceiver: ScreenEventReceiver? = null
    private var watchdogJob: Job? = null
    private var lastScreenOnSendTime: Long = 0L

    companion object {
        const val ACTION_START = "com.kusa.bugslife.START_SERVICE"
        const val ACTION_STOP = "com.kusa.bugslife.STOP_SERVICE"
        const val ACTION_SEND_STATUS = "com.kusa.bugslife.SEND_STATUS"
        const val ACTION_SEND_SCREEN_ON = "com.kusa.bugslife.SEND_SCREEN_ON"
        const val ACTION_RELOAD_SETTINGS = "com.kusa.bugslife.RELOAD_SETTINGS"

        const val EXTRA_STATUS_TYPE = "extra_status_type"
        const val EXTRA_STATUS_MESSAGE = "extra_status_message"

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

        fun reloadSettings(context: Context) {
            val intent = Intent(context, WatcherForegroundService::class.java).apply {
                action = ACTION_RELOAD_SETTINGS
            }
            context.startService(intent)
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
        WatcherStateHolder.updatePeers(prefs.getPeers())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: ACTION_START
        Log.d(tag, "Service onStartCommand: action=$action")

        when (action) {
            ACTION_START -> {
                startForegroundWithNotification()
                setupListenersAndWatchdog()
            }
            ACTION_STOP -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_SEND_STATUS -> {
                val typeName = intent?.getStringExtra(EXTRA_STATUS_TYPE)
                val msg = intent?.getStringExtra(EXTRA_STATUS_MESSAGE) ?: ""
                val type = PacketType.fromString(typeName)
                sendPacketToPeers(type, msg)
            }
            ACTION_SEND_SCREEN_ON -> {
                handleScreenOnEvent()
            }
            ACTION_RELOAD_SETTINGS -> {
                WatcherStateHolder.updatePeers(prefs.getPeers())
                setupListenersAndWatchdog()
                updateNotification()
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
        return "相互見守り稼働中: ピア ${peers.size} 台 (本日画面点灯: ${prefs.todayScreenOnCount}回)"
    }

    private fun updateNotification() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        val notification = NotificationHelper.buildServiceNotification(this, getServiceStatusSummary())
        manager.notify(NotificationHelper.NOTIFICATION_ID_SERVICE, notification)
    }

    private fun setupListenersAndWatchdog() {
        val port = prefs.port
        messenger.startListening(port) { packet, remoteIp ->
            handleIncomingPacket(packet, remoteIp)
        }

        registerScreenReceiver()
        startWatchdog()
    }

    private fun registerScreenReceiver() {
        if (screenReceiver == null) {
            screenReceiver = ScreenEventReceiver {
                handleScreenOnEvent()
            }
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_SCREEN_ON)
                addAction(Intent.ACTION_USER_PRESENT)
            }
            registerReceiver(screenReceiver, filter)
            Log.d(tag, "Screen receiver registered.")
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

    private fun handleScreenOnEvent() {
        val now = System.currentTimeMillis()
        prefs.todayScreenOnCount += 1
        if (now - lastScreenOnSendTime >= 15_000L) {
            lastScreenOnSendTime = now
            sendPacketToPeers(
                PacketType.HEARTBEAT,
                "画面点灯を検知 (本日 ${prefs.todayScreenOnCount} 回目)"
            )
        }
        updateNotification()
    }

    private fun sendPacketToPeers(type: PacketType, message: String = "") {
        serviceScope.launch {
            val packet = SafetyPacket(
                senderId = prefs.userId,
                senderName = prefs.userName,
                type = type,
                timestamp = System.currentTimeMillis(),
                message = message
            )

            val peers = prefs.getPeers()
            Log.d(tag, "Sending $type packet to ${peers.size} peer(s)")

            val results = messenger.sendPacket(packet, peers)

            if (peers.isEmpty()) {
                messenger.broadcastPacket(packet, prefs.port)
            }

            WatcherStateHolder.setLastSentTimestamp(packet.timestamp)
            WatcherStateHolder.addLog(
                CommunicationLog(
                    isIncoming = false,
                    peerName = if (peers.isNotEmpty()) "${peers.size}件の送信先" else "LANブロードキャスト",
                    packetType = type,
                    detail = message.ifBlank { type.label }
                )
            )

            WatcherStateHolder.emitUiEvent("送信完了: ${type.emoji} ${type.label}")
        }
    }

    private fun handleIncomingPacket(packet: SafetyPacket, remoteIp: String) {
        serviceScope.launch {
            Log.d(tag, "Handling incoming packet: $packet from $remoteIp")

            if (packet.senderId == prefs.userId) {
                return@launch
            }

            prefs.updatePeerStatus(
                senderId = packet.senderId,
                senderName = packet.senderName,
                ipAddress = remoteIp,
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
                    peerName = "${packet.senderName} ($remoteIp)",
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
                    val targetPeer = PeerInfo(name = packet.senderName, ipAddress = remoteIp, port = prefs.port)
                    messenger.sendPacket(ackPacket, listOf(targetPeer))
                }
                else -> {}
            }

            WatcherStateHolder.emitUiEvent("受信: ${packet.senderName}さんから「${packet.type.label}」")
        }
    }

    private fun startWatchdog() {
        watchdogJob?.cancel()
        watchdogJob = serviceScope.launch {
            while (isActive) {
                delay(10_000L)
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

    private fun stopForegroundService() {
        unregisterScreenReceiver()
        watchdogJob?.cancel()
        watchdogJob = null
        messenger.stopListening()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        stopForegroundService()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
