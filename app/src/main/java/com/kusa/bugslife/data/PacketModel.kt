package com.kusa.bugslife.data

import org.json.JSONObject
import java.util.UUID

enum class PacketType(val label: String, val emoji: String) {
    HEARTBEAT("画面点灯 (活動中)", "📱"),
    STATUS_FINE("元気です！", "😄"),
    STATUS_UNWELL("体調が良くない", "😣"),
    PING("疎通テスト", "📡"),
    ACK("応答確認", "✅");

    companion object {
        fun fromString(value: String?): PacketType {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: HEARTBEAT
        }
    }
}

data class SafetyPacket(
    val packetId: String = UUID.randomUUID().toString(),
    val senderId: String,
    val senderName: String,
    val type: PacketType,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String = ""
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("packetId", packetId)
        obj.put("senderId", senderId)
        obj.put("senderName", senderName)
        obj.put("type", type.name)
        obj.put("timestamp", timestamp)
        obj.put("message", message)
        return obj.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): SafetyPacket? {
            return try {
                val obj = JSONObject(jsonStr)
                SafetyPacket(
                    packetId = obj.optString("packetId", UUID.randomUUID().toString()),
                    senderId = obj.getString("senderId"),
                    senderName = obj.optString("senderName", "相手"),
                    type = PacketType.fromString(obj.optString("type")),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    message = obj.optString("message", "")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}

data class PeerInfo(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val lastSeenTimestamp: Long = 0L,
    val lastStatus: PacketType? = null,
    val lastMessage: String = "",
    val isAlertTriggered: Boolean = false
)

data class CommunicationLog(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val isIncoming: Boolean,
    val peerName: String,
    val packetType: PacketType,
    val detail: String = ""
)
