package com.kusa.bugslife.network

import android.util.Log
import com.kusa.bugslife.data.PeerInfo
import com.kusa.bugslife.data.SafetyPacket
import com.kusa.bugslife.util.NetworkUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets

class UdpPeerMessenger : PeerMessenger {
    private val tag = "UdpPeerMessenger"
    private var listenSocket: DatagramSocket? = null
    private var listeningJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO)

    @Volatile
    private var isRunning = false

    override fun isListening(): Boolean = isRunning

    override fun startListening(
        port: Int,
        onPacketReceived: (packet: SafetyPacket, remoteIp: String) -> Unit
    ) {
        if (isRunning) {
            stopListening()
        }

        listeningJob = scope.launch {
            try {
                val socket = DatagramSocket(null).apply {
                    reuseAddress = true
                    bind(InetSocketAddress(port))
                    broadcast = true
                }
                listenSocket = socket
                isRunning = true
                Log.d(tag, "Started listening UDP on port $port")

                val buffer = ByteArray(4096)
                while (isActive && isRunning) {
                    val datagramPacket = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(datagramPacket)
                        val dataStr = String(
                            datagramPacket.data,
                            datagramPacket.offset,
                            datagramPacket.length,
                            StandardCharsets.UTF_8
                        )
                        val remoteIp = datagramPacket.address.hostAddress ?: ""
                        Log.d(tag, "Received UDP packet from $remoteIp: $dataStr")

                        val packet = SafetyPacket.fromJson(dataStr)
                        if (packet != null) {
                            withContext(Dispatchers.Main) {
                                onPacketReceived(packet, remoteIp)
                            }
                        }
                    } catch (e: Exception) {
                        if (!socket.isClosed) {
                            Log.e(tag, "Error receiving packet: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to start UDP listener: ${e.message}", e)
            } finally {
                isRunning = false
            }
        }
    }

    override fun stopListening() {
        isRunning = false
        try {
            listenSocket?.close()
            listenSocket = null
            listeningJob?.cancel()
            listeningJob = null
            Log.d(tag, "Stopped UDP listener")
        } catch (e: Exception) {
            Log.e(tag, "Error closing UDP socket: ${e.message}", e)
        }
    }

    /**
     * 登録された1:nのピアに対してパケットを一斉送信
     */
    override suspend fun sendPacket(
        packet: SafetyPacket,
        targets: List<PeerInfo>
    ): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val results = mutableMapOf<String, Boolean>()
        val jsonBytes = packet.toJson().toByteArray(StandardCharsets.UTF_8)

        var socket: DatagramSocket? = null
        try {
            socket = DatagramSocket()
            socket.broadcast = true

            for (target in targets) {
                if (target.ipAddress.isBlank()) continue
                try {
                    val address = InetAddress.getByName(target.ipAddress)
                    val datagramPacket = DatagramPacket(
                        jsonBytes,
                        jsonBytes.size,
                        address,
                        target.port
                    )
                    socket.send(datagramPacket)
                    results[target.id] = true
                    Log.d(tag, "Sent packet to ${target.name} (${target.ipAddress}:${target.port})")
                } catch (e: Exception) {
                    Log.e(tag, "Failed to send packet to ${target.ipAddress}: ${e.message}")
                    results[target.id] = false
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Socket creation error: ${e.message}", e)
        } finally {
            socket?.close()
        }
        results
    }

    /**
     * LAN内へのブロードキャスト送信（ピアのIPが不明な場合や探索用）
     */
    override suspend fun broadcastPacket(packet: SafetyPacket, port: Int): Boolean =
        withContext(Dispatchers.IO) {
            var socket: DatagramSocket? = null
            try {
                socket = DatagramSocket()
                socket.broadcast = true
                val jsonBytes = packet.toJson().toByteArray(StandardCharsets.UTF_8)
                val broadcastAddress = InetAddress.getByName(NetworkUtils.getBroadcastAddress())
                val datagramPacket = DatagramPacket(
                    jsonBytes,
                    jsonBytes.size,
                    broadcastAddress,
                    port
                )
                socket.send(datagramPacket)
                Log.d(tag, "Broadcasted packet to $broadcastAddress:$port")
                true
            } catch (e: Exception) {
                Log.e(tag, "Broadcast failed: ${e.message}", e)
                false
            } finally {
                socket?.close()
            }
        }
}
