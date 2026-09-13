package com.kusa.bugslife.network

import android.content.Context
import android.util.Log
import com.kusa.bugslife.data.PeerInfo
import com.kusa.bugslife.data.SafetyPacket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * LAN内 UDP と インターネット SkyWay WebRTC を統合し、
 * 両方のネットワーク経路で同時に待受・送信を行うハイブリッドメッセンジャー
 */
class CompositePeerMessenger(
    context: Context,
    appId: String,
    secretKey: String,
    roomName: String,
    memberName: String,
    private val isSkyWayEnabled: Boolean = true
) : PeerMessenger {
    private val tag = "CompositePeerMessenger"
    private val udpMessenger = UdpPeerMessenger()
    private val skywayMessenger: SkyWayPeerMessenger? = if (isSkyWayEnabled && appId.isNotBlank() && secretKey.isNotBlank()) {
        SkyWayPeerMessenger(context, appId, secretKey, roomName, memberName)
    } else null

    override fun isListening(): Boolean {
        return udpMessenger.isListening() || (skywayMessenger?.isListening() == true)
    }

    override fun startListening(
        port: Int,
        onPacketReceived: (packet: SafetyPacket, remoteIp: String) -> Unit
    ) {
        Log.d(tag, "Starting Composite Messenger (UDP + SkyWay)...")
        udpMessenger.startListening(port, onPacketReceived)
        skywayMessenger?.startListening(port, onPacketReceived)
    }

    override fun stopListening() {
        udpMessenger.stopListening()
        skywayMessenger?.stopListening()
    }

    override suspend fun sendPacket(
        packet: SafetyPacket,
        targets: List<PeerInfo>
    ): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Boolean>()

        // 1. UDP 送信 (LAN内)
        val udpResults = udpMessenger.sendPacket(packet, targets)
        results.putAll(udpResults)

        // 2. SkyWay 送信 (インターネット)
        if (skywayMessenger != null) {
            val skywayResults = skywayMessenger.sendPacket(packet, targets)
            for ((peerId, success) in skywayResults) {
                if (success) {
                    results[peerId] = true
                }
            }
        }

        results
    }

    override suspend fun broadcastPacket(packet: SafetyPacket, port: Int): Boolean =
        withContext(Dispatchers.IO) {
            val udpSuccess = udpMessenger.broadcastPacket(packet, port)
            val skywaySuccess = skywayMessenger?.broadcastPacket(packet, port) ?: false
            udpSuccess || skywaySuccess
        }
}
