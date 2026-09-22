package com.kusa.bugslife.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class PacketType(val label: String, val emoji: String) {
    HEARTBEAT("画面点灯 (活動中)", "📱"),
    STATUS_FINE("元気です！", "😄"),
    STATUS_UNWELL("良くない", "😣"),
    PING("疎通テスト", "📡"),
    ACK("応答確認", "✅");

    companion object {
        fun fromString(value: String?): PacketType {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: HEARTBEAT
        }
    }
}

enum class MemberStatus(val label: String) {
    APPROVED("承認済み"),
    PENDING("承認待ち"),
    REJECTED("拒否");

    companion object {
        fun fromString(value: String?): MemberStatus {
            return entries.find { it.name.equals(value, ignoreCase = true) } ?: APPROVED
        }
    }
}

data class PendingJoinRequest(
    val userId: String,
    val userName: String,
    val requestedAt: Long = System.currentTimeMillis()
)

data class SafetyPacket(
    val packetId: String = UUID.randomUUID().toString(),
    val senderId: String,
    val senderName: String,
    val type: PacketType = PacketType.HEARTBEAT,
    val status: PacketType? = null,
    val memberStatus: MemberStatus = MemberStatus.APPROVED,
    val unlockTimestamps: List<Long> = emptyList(),
    val isAlertTriggered: Boolean = false,
    val timestamp: Long = System.currentTimeMillis(),
    val message: String = ""
) {
    fun toJson(): String {
        val obj = JSONObject()
        obj.put("packetId", packetId)
        obj.put("senderId", senderId)
        obj.put("senderName", senderName)
        obj.put("type", type.name)
        if (status != null) {
            obj.put("status", status.name)
        }
        obj.put("memberStatus", memberStatus.name)
        obj.put("isAlertTriggered", isAlertTriggered)
        val timestampsArray = JSONArray()
        for (ts in unlockTimestamps) {
            timestampsArray.put(ts)
        }
        obj.put("unlockTimestamps", timestampsArray)
        obj.put("timestamp", timestamp)
        obj.put("message", message)
        return obj.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): SafetyPacket? {
            return try {
                val obj = JSONObject(jsonStr)
                val statusStr = if (obj.has("status") && !obj.isNull("status")) obj.optString("status") else null
                val parsedStatus = if (!statusStr.isNullOrBlank()) PacketType.fromString(statusStr) else null

                val timestampsList = mutableListOf<Long>()
                val array = obj.optJSONArray("unlockTimestamps")
                if (array != null) {
                    for (i in 0 until array.length()) {
                        timestampsList.add(array.getLong(i))
                    }
                }

                SafetyPacket(
                    packetId = obj.optString("packetId", UUID.randomUUID().toString()),
                    senderId = obj.getString("senderId"),
                    senderName = obj.optString("senderName", "相手"),
                    type = PacketType.fromString(obj.optString("type")),
                    status = parsedStatus,
                    memberStatus = MemberStatus.fromString(obj.optString("memberStatus", "APPROVED")),
                    unlockTimestamps = timestampsList,
                    isAlertTriggered = obj.optBoolean("isAlertTriggered", false),
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
    val customName: String? = null,
    val lastSeenTimestamp: Long = 0L,
    val lastStatus: PacketType? = null,
    val memberStatus: MemberStatus = MemberStatus.APPROVED,
    val lastMessage: String = "",
    val isAlertTriggered: Boolean = false,
    val unlockTimestamps: List<Long> = emptyList()
) {
    val displayName: String
        get() = if (!customName.isNullOrBlank()) customName else name

    val lastUnlockTimestamp: Long
        get() = unlockTimestamps.maxOrNull() ?: 0L

    val unlockCount24h: Int
        get() = unlockTimestamps.size
}

data class CommunicationLog(
    val id: String = UUID.randomUUID().toString(),
    val timestamp: Long = System.currentTimeMillis(),
    val isIncoming: Boolean,
    val peerName: String,
    val packetType: PacketType,
    val detail: String = ""
) {
    fun toJson(): JSONObject {
        val obj = JSONObject()
        obj.put("id", id)
        obj.put("timestamp", timestamp)
        obj.put("isIncoming", isIncoming)
        obj.put("peerName", peerName)
        obj.put("packetType", packetType.name)
        obj.put("detail", detail)
        return obj
    }

    companion object {
        fun fromJson(obj: JSONObject): CommunicationLog? {
            return try {
                CommunicationLog(
                    id = obj.optString("id", UUID.randomUUID().toString()),
                    timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                    isIncoming = obj.optBoolean("isIncoming", true),
                    peerName = obj.optString("peerName", "相手"),
                    packetType = PacketType.fromString(obj.optString("packetType")),
                    detail = obj.optString("detail", "")
                )
            } catch (e: Exception) {
                null
            }
        }
    }
}
