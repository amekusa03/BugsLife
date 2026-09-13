package com.kusa.bugslife.network

import android.content.Context
import android.util.Log
import com.kusa.bugslife.data.PeerInfo
import com.kusa.bugslife.data.SafetyPacket
import com.kusa.bugslife.util.SkyWayTokenUtil
import com.ntt.skyway.core.SkyWayContext
import com.ntt.skyway.core.content.local.LocalDataStream
import com.ntt.skyway.core.content.local.source.DataSource
import com.ntt.skyway.core.content.remote.RemoteDataStream
import com.ntt.skyway.core.util.Logger
import com.ntt.skyway.room.RoomPublication
import com.ntt.skyway.room.member.RoomMember
import com.ntt.skyway.room.p2p.LocalP2PRoomMember
import com.ntt.skyway.room.p2p.P2PRoom
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

class SkyWayPeerMessenger(
    private val context: Context,
    private val appId: String,
    private val secretKey: String,
    private val roomName: String,
    private val memberName: String
) : PeerMessenger {
    private val tag = "SkyWayPeerMessenger"
    private val scope = CoroutineScope(Dispatchers.IO)

    private var room: P2PRoom? = null
    private var localMember: LocalP2PRoomMember? = null
    private var localDataStream: LocalDataStream? = null

    @Volatile
    private var isConnected = false
    private var connectionJob: Job? = null
    private var onPacketCallback: ((SafetyPacket, String) -> Unit)? = null

    override fun isListening(): Boolean = isConnected

    override fun startListening(
        port: Int,
        onPacketReceived: (packet: SafetyPacket, remoteIp: String) -> Unit
    ) {
        onPacketCallback = onPacketReceived
        if (isConnected) return

        connectionJob?.cancel()
        connectionJob = scope.launch {
            try {
                Log.d(tag, "Generating Auth Token & Setting up SkyWayContext...")
                val token = SkyWayTokenUtil.createAuthToken(appId, secretKey)
                val options = SkyWayContext.Options(
                    authToken = token,
                    logLevel = Logger.LogLevel.INFO
                )

                if (!SkyWayContext.isSetup) {
                    val setupSuccess = SkyWayContext.setup(context, options)
                    if (!setupSuccess) {
                        Log.e(tag, "SkyWayContext.setup failed")
                        return@launch
                    }
                } else {
                    SkyWayContext.updateAuthToken(token)
                }

                Log.d(tag, "Joining or Finding Room: $roomName")
                val targetRoom = P2PRoom.findOrCreate(name = roomName)
                if (targetRoom == null) {
                    Log.e(tag, "P2PRoom.findOrCreate failed for room: $roomName")
                    return@launch
                }
                room = targetRoom

                val memberInit = RoomMember.Init(
                    name = memberName.ifBlank { "member_" + UUID.randomUUID().toString().take(8) }
                )
                val member = targetRoom.join(memberInit)
                if (member == null) {
                    Log.e(tag, "Failed to join room: $roomName")
                    return@launch
                }
                localMember = member

                // Local DataStream の作成 & Publish
                val dataSource = DataSource()
                val dataStream = dataSource.createStream()
                localDataStream = dataStream
                member.publish(dataStream)

                // 相手からの Stream Publication ハンドラを登録
                targetRoom.onStreamPublishedHandler = { publication ->
                    handleNewPublication(publication)
                }

                // 既に公開されている相手の Stream を購読
                targetRoom.publications.forEach { pub ->
                    handleNewPublication(pub)
                }

                isConnected = true
                Log.d(tag, "SkyWay connected! Room=$roomName, Member=${member.name}")
            } catch (e: Exception) {
                Log.e(tag, "SkyWay connection error: ${e.message}", e)
                isConnected = false
            }
        }
    }

    private fun handleNewPublication(publication: RoomPublication) {
        val member = localMember ?: return
        if (publication.publisher?.id == member.id) {
            return
        }

        scope.launch {
            try {
                val subscription = member.subscribe(publication.id)
                val stream = subscription?.stream
                if (stream is RemoteDataStream) {
                    stream.onDataHandler = { dataString ->
                        Log.d(tag, "SkyWay packet received from ${publication.publisher?.name}: $dataString")
                        val packet = SafetyPacket.fromJson(dataString)
                        if (packet != null) {
                            val senderDesc = publication.publisher?.name ?: "SkyWay"
                            scope.launch(Dispatchers.Main) {
                                onPacketCallback?.invoke(packet, "SkyWay ($senderDesc)")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(tag, "Error subscribing to SkyWay publication: ${e.message}", e)
            }
        }
    }

    override fun stopListening() {
        isConnected = false
        connectionJob?.cancel()
        connectionJob = null
        scope.launch {
            try {
                localMember?.leave()
                localMember = null
                room?.dispose()
                room = null
                localDataStream?.dispose()
                localDataStream = null
                Log.d(tag, "SkyWay disconnected.")
            } catch (e: Exception) {
                Log.e(tag, "Error disconnecting SkyWay: ${e.message}", e)
            }
        }
    }

    override suspend fun sendPacket(
        packet: SafetyPacket,
        targets: List<PeerInfo>
    ): Map<String, Boolean> = withContext(Dispatchers.IO) {
        val jsonStr = packet.toJson()
        val results = mutableMapOf<String, Boolean>()

        try {
            val stream = localDataStream
            if (stream != null && isConnected) {
                stream.write(jsonStr)
                Log.d(tag, "Published packet via SkyWay DataStream: $jsonStr")
                for (target in targets) {
                    results[target.id] = true
                }
            } else {
                Log.w(tag, "SkyWay stream not available. Packet not sent.")
                for (target in targets) {
                    results[target.id] = false
                }
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to write packet to SkyWay stream: ${e.message}", e)
            for (target in targets) {
                results[target.id] = false
            }
        }
        results
    }

    override suspend fun broadcastPacket(packet: SafetyPacket, port: Int): Boolean =
        withContext(Dispatchers.IO) {
            try {
                val stream = localDataStream
                if (stream != null && isConnected) {
                    stream.write(packet.toJson())
                    Log.d(tag, "Broadcasted packet via SkyWay DataStream")
                    true
                } else {
                    false
                }
            } catch (e: Exception) {
                Log.e(tag, "SkyWay broadcast error: ${e.message}", e)
                false
            }
        }
}
