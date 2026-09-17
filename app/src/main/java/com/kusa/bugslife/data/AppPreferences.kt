package com.kusa.bugslife.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("bugslife_prefs", Context.MODE_PRIVATE)

    companion object {
        const val ONE_HOUR_MS = 60 * 60 * 1000L
        const val TWENTY_FOUR_HOURS_MS = 24 * 60 * 60 * 1000L
        const val LOG_RETENTION_MS = 48 * 60 * 60 * 1000L // 48時間保持
        const val MAX_COMMUNICATION_LOGS = 100 // 最大100件
    }

    var userId: String
        get() {
            var id = prefs.getString("user_id", null)
            if (id == null) {
                id = UUID.randomUUID().toString()
                prefs.edit().putString("user_id", id).apply()
            }
            return id
        }
        set(value) = prefs.edit().putString("user_id", value).apply()

    var userName: String
        get() = prefs.getString("user_name", "ユーザー") ?: "ユーザー"
        set(value) = prefs.edit().putString("user_name", value).apply()

    // 見守りグループ名 (Firebase / Firestore Room名)
    var groupName: String
        get() = prefs.getString("group_name", "family-room") ?: "family-room"
        set(value) = prefs.edit().putString("group_name", value).apply()

    // 自分のグループ内ステータス (APPROVED, PENDING, REJECTED)
    var myMemberStatus: MemberStatus
        get() {
            val str = prefs.getString("my_member_status", MemberStatus.APPROVED.name)
            return MemberStatus.fromString(str)
        }
        set(value) = prefs.edit().putString("my_member_status", value.name).apply()

    var isSyncEnabled: Boolean
        get() = prefs.getBoolean("is_sync_enabled", true)
        set(value) = prefs.edit().putBoolean("is_sync_enabled", value).apply()

    // 監視タイムアウト時間（ミリ秒） デフォルト: 24時間
    var timeoutDurationMillis: Long
        get() = prefs.getLong("timeout_duration_ms", TWENTY_FOUR_HOURS_MS)
        set(value) = prefs.edit().putLong("timeout_duration_ms", value).apply()

    var isServiceEnabled: Boolean
        get() = prefs.getBoolean("is_service_enabled", true)
        set(value) = prefs.edit().putBoolean("is_service_enabled", value).apply()

    var todayScreenOnCount: Int
        get() {
            checkAndResetDailyCount()
            return prefs.getInt("screen_on_count", 0)
        }
        set(value) {
            checkAndResetDailyCount()
            prefs.edit().putInt("screen_on_count", value).apply()
        }

    var lastLocalScreenOnTime: Long
        get() = prefs.getLong("last_screen_on_time", 0L)
        set(value) = prefs.edit().putLong("last_screen_on_time", value).apply()

    // 前回の Firebase 正常送信完了タイムスタンプ
    var lastSuccessfulSendTimestamp: Long
        get() = prefs.getLong("last_successful_send_timestamp", 0L)
        set(value) = prefs.edit().putLong("last_successful_send_timestamp", value).apply()

    // 送信保留中のステータス（次回送信時に送信してクリア）
    var pendingStatus: PacketType?
        get() {
            val str = prefs.getString("pending_status", null) ?: return null
            return PacketType.fromString(str)
        }
        set(value) {
            if (value == null) {
                prefs.edit().remove("pending_status").apply()
            } else {
                prefs.edit().putString("pending_status", value.name).apply()
            }
        }

    var pendingStatusMessage: String
        get() = prefs.getString("pending_status_message", "") ?: ""
        set(value) = prefs.edit().putString("pending_status_message", value).apply()

    var lastSyncTimestamp: Long
        get() = prefs.getLong("last_sync_timestamp", 0L)
        set(value) = prefs.edit().putLong("last_sync_timestamp", value).apply()

    // -------------------------------------------------------------
    // 内部ファイル（ローカル未送信バッファ）のタイムスタンプ管理
    // 画面ロック解除時に追加され、Firebase送信成功時に削除される
    // -------------------------------------------------------------
    fun getPendingUnlockTimestamps(): List<Long> {
        val jsonStr = prefs.getString("pending_unlock_timestamps", null) ?: return emptyList()
        val list = mutableListOf<Long>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                list.add(array.getLong(i))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun savePendingUnlockTimestamps(timestamps: List<Long>) {
        val array = JSONArray()
        for (ts in timestamps) {
            array.put(ts)
        }
        prefs.edit().putString("pending_unlock_timestamps", array.toString()).apply()
    }

    fun addPendingUnlockTimestamp(timestamp: Long = System.currentTimeMillis()) {
        val current = getPendingUnlockTimestamps().toMutableList()
        val last = current.lastOrNull()
        // 1分(60秒)以内の連続操作は重複として最新時刻に更新し、無駄なデータ蓄積を防止
        if (last != null && timestamp - last < 60_000L) {
            current[current.lastIndex] = timestamp
        } else {
            current.add(timestamp)
        }
        savePendingUnlockTimestamps(current)
    }

    fun clearPendingUnlockTimestamps() {
        prefs.edit().remove("pending_unlock_timestamps").apply()
    }

    // 後方互換・UI表示用（過去24時間のローカル操作履歴）
    fun getUnlockTimestamps(): List<Long> = getPendingUnlockTimestamps()

    private fun checkAndResetDailyCount() {
        val today = java.text.SimpleDateFormat("yyyyMMdd", java.util.Locale.getDefault()).format(java.util.Date())
        val savedDate = prefs.getString("screen_count_date", "")
        if (savedDate != today) {
            prefs.edit()
                .putString("screen_count_date", today)
                .putInt("screen_on_count", 0)
                .apply()
        }
    }

    fun getPeers(): List<PeerInfo> {
        val jsonStr = prefs.getString("peers_list", null) ?: return emptyList()
        val list = mutableListOf<PeerInfo>()
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val timestampsList = mutableListOf<Long>()
                val timestampsArray = obj.optJSONArray("unlockTimestamps")
                if (timestampsArray != null) {
                    for (j in 0 until timestampsArray.length()) {
                        timestampsList.add(timestampsArray.getLong(j))
                    }
                }
                val peer = PeerInfo(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    lastSeenTimestamp = obj.optLong("lastSeenTimestamp", 0L),
                    lastStatus = if (obj.has("lastStatus")) PacketType.fromString(obj.optString("lastStatus")) else null,
                    memberStatus = MemberStatus.fromString(obj.optString("memberStatus", "APPROVED")),
                    lastMessage = obj.optString("lastMessage", ""),
                    isAlertTriggered = obj.optBoolean("isAlertTriggered", false),
                    unlockTimestamps = timestampsList
                )
                list.add(peer)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun savePeers(peers: List<PeerInfo>) {
        val array = JSONArray()
        for (peer in peers) {
            val obj = JSONObject()
            obj.put("id", peer.id)
            obj.put("name", peer.name)
            obj.put("lastSeenTimestamp", peer.lastSeenTimestamp)
            if (peer.lastStatus != null) {
                obj.put("lastStatus", peer.lastStatus.name)
            }
            obj.put("memberStatus", peer.memberStatus.name)
            obj.put("lastMessage", peer.lastMessage)
            obj.put("isAlertTriggered", peer.isAlertTriggered)
            val tsArray = JSONArray()
            for (ts in peer.unlockTimestamps) {
                tsArray.put(ts)
            }
            obj.put("unlockTimestamps", tsArray)
            array.put(obj)
        }
        prefs.edit().putString("peers_list", array.toString()).apply()
    }

    fun updatePeerStatus(
        senderId: String,
        senderName: String,
        status: PacketType?,
        timestamp: Long,
        message: String,
        isAlert: Boolean = false,
        memberStatus: MemberStatus = MemberStatus.APPROVED,
        unlockTimestamps: List<Long> = emptyList()
    ) {
        val peers = getPeers().toMutableList()
        val index = peers.indexOfFirst { it.id == senderId }
        if (index >= 0) {
            val current = peers[index]
            peers[index] = current.copy(
                name = if (senderName.isNotBlank()) senderName else current.name,
                lastSeenTimestamp = timestamp,
                lastStatus = status ?: current.lastStatus,
                memberStatus = memberStatus,
                lastMessage = message,
                isAlertTriggered = isAlert,
                unlockTimestamps = if (unlockTimestamps.isNotEmpty()) unlockTimestamps else current.unlockTimestamps
            )
        } else {
            peers.add(
                PeerInfo(
                    id = senderId,
                    name = if (senderName.isNotBlank()) senderName else "相手",
                    lastSeenTimestamp = timestamp,
                    lastStatus = status,
                    memberStatus = memberStatus,
                    lastMessage = message,
                    isAlertTriggered = isAlert,
                    unlockTimestamps = unlockTimestamps
                )
            )
        }
        savePeers(peers)
    }

    fun getCommunicationLogs(): List<CommunicationLog> {
        val jsonStr = prefs.getString("communication_logs", null) ?: return emptyList()
        val list = mutableListOf<CommunicationLog>()
        val cutoff = System.currentTimeMillis() - LOG_RETENTION_MS
        try {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val log = CommunicationLog.fromJson(obj)
                if (log != null && log.timestamp >= cutoff) {
                    list.add(log)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    fun saveCommunicationLogs(logs: List<CommunicationLog>) {
        val cutoff = System.currentTimeMillis() - LOG_RETENTION_MS
        val validLogs = logs
            .filter { it.timestamp >= cutoff }
            .take(MAX_COMMUNICATION_LOGS)

        val array = JSONArray()
        for (log in validLogs) {
            array.put(log.toJson())
        }
        prefs.edit().putString("communication_logs", array.toString()).apply()
    }

    fun addCommunicationLog(log: CommunicationLog) {
        val current = getCommunicationLogs().toMutableList()
        current.add(0, log)
        saveCommunicationLogs(current)
    }

    fun clearCommunicationLogs() {
        prefs.edit().remove("communication_logs").apply()
    }
}
