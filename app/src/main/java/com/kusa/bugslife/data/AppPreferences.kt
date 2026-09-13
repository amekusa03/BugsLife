package com.kusa.bugslife.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("bugslife_prefs", Context.MODE_PRIVATE)

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

    // SkyWay 認証設定
    var skywayAppId: String
        get() = prefs.getString("skyway_app_id", "") ?: ""
        set(value) = prefs.edit().putString("skyway_app_id", value).apply()

    var skywaySecretKey: String
        get() = prefs.getString("skyway_secret_key", "") ?: ""
        set(value) = prefs.edit().putString("skyway_secret_key", value).apply()

    var skywayRoomName: String
        get() = prefs.getString("skyway_room_name", "family-mutual-room") ?: "family-mutual-room"
        set(value) = prefs.edit().putString("skyway_room_name", value).apply()

    var isSkyWayEnabled: Boolean
        get() = prefs.getBoolean("is_skyway_enabled", true)
        set(value) = prefs.edit().putBoolean("is_skyway_enabled", value).apply()

    // 監視タイムアウト時間（ミリ秒） デフォルト: 24時間 (24 * 60 * 60 * 1000L)
    var timeoutDurationMillis: Long
        get() = prefs.getLong("timeout_duration_ms", 24 * 60 * 60 * 1000L)
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
                val peer = PeerInfo(
                    id = obj.getString("id"),
                    name = obj.getString("name"),
                    lastSeenTimestamp = obj.optLong("lastSeenTimestamp", 0L),
                    lastStatus = if (obj.has("lastStatus")) PacketType.fromString(obj.optString("lastStatus")) else null,
                    lastMessage = obj.optString("lastMessage", ""),
                    isAlertTriggered = obj.optBoolean("isAlertTriggered", false)
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
            obj.put("lastMessage", peer.lastMessage)
            obj.put("isAlertTriggered", peer.isAlertTriggered)
            array.put(obj)
        }
        prefs.edit().putString("peers_list", array.toString()).apply()
    }

    fun updatePeerStatus(
        senderId: String,
        senderName: String,
        status: PacketType,
        timestamp: Long,
        message: String,
        isAlert: Boolean = false
    ) {
        val peers = getPeers().toMutableList()
        val index = peers.indexOfFirst { it.id == senderId }
        if (index >= 0) {
            val current = peers[index]
            peers[index] = current.copy(
                name = if (senderName.isNotBlank()) senderName else current.name,
                lastSeenTimestamp = timestamp,
                lastStatus = status,
                lastMessage = message,
                isAlertTriggered = isAlert
            )
        } else {
            peers.add(
                PeerInfo(
                    id = senderId,
                    name = if (senderName.isNotBlank()) senderName else "相手",
                    lastSeenTimestamp = timestamp,
                    lastStatus = status,
                    lastMessage = message,
                    isAlertTriggered = isAlert
                )
            )
        }
        savePeers(peers)
    }
}
