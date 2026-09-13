package com.kusa.bugslife.network

import com.kusa.bugslife.data.SafetyPacket

interface PeerMessenger {
    fun startListening(onPacketReceived: (packet: SafetyPacket) -> Unit)
    fun stopListening()
    suspend fun sendPacket(packet: SafetyPacket): Boolean
    fun isListening(): Boolean
}
