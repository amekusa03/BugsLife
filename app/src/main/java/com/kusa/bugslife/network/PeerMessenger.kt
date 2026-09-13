package com.kusa.bugslife.network

import com.kusa.bugslife.data.PeerInfo
import com.kusa.bugslife.data.SafetyPacket

interface PeerMessenger {
    fun startListening(port: Int, onPacketReceived: (packet: SafetyPacket, remoteIp: String) -> Unit)
    fun stopListening()
    suspend fun sendPacket(packet: SafetyPacket, targets: List<PeerInfo>): Map<String, Boolean>
    suspend fun broadcastPacket(packet: SafetyPacket, port: Int): Boolean
    fun isListening(): Boolean
}
