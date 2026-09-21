package com.kusa.bugslife

import com.kusa.bugslife.data.AppPreferences
import com.kusa.bugslife.data.PacketType
import com.kusa.bugslife.data.CommunicationLog
import com.kusa.bugslife.data.SafetyPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SafetyPacketTest {

    @Test
    fun testPacketSerializationAndDeserialization() {
        val timestamps = listOf(1700000000000L, 1700003600000L, 1700007200000L)
        val packet = SafetyPacket(
            packetId = "test-uuid-1234",
            senderId = "sender-5678",
            senderName = "おじいちゃん",
            type = PacketType.STATUS_FINE,
            status = PacketType.STATUS_FINE,
            unlockTimestamps = timestamps,
            timestamp = 1700000000000L,
            message = "散歩から帰りました"
        )

        val json = packet.toJson()
        assertNotNull(json)

        val restored = SafetyPacket.fromJson(json)
        assertNotNull(restored)
        assertEquals("test-uuid-1234", restored?.packetId)
        assertEquals("sender-5678", restored?.senderId)
        assertEquals("おじいちゃん", restored?.senderName)
        assertEquals(PacketType.STATUS_FINE, restored?.type)
        assertEquals(PacketType.STATUS_FINE, restored?.status)
        assertEquals(3, restored?.unlockTimestamps?.size)
        assertEquals(timestamps, restored?.unlockTimestamps)
        assertEquals(1700000000000L, restored?.timestamp)
        assertEquals("散歩から帰りました", restored?.message)
    }

    @Test
    fun testEmptyTimestampsPacketSerialization() {
        // 過去24時間活動なし（0件）の重要ケース
        val packet = SafetyPacket(
            packetId = "test-uuid-no-activity",
            senderId = "sender-9999",
            senderName = "一人暮らしの親",
            type = PacketType.HEARTBEAT,
            status = null,
            unlockTimestamps = emptyList(),
            timestamp = 1700000000000L,
            message = ""
        )

        val json = packet.toJson()
        val restored = SafetyPacket.fromJson(json)
        assertNotNull(restored)
        assertNull(restored?.status)
        assertNotNull(restored?.unlockTimestamps)
        assertTrue(restored?.unlockTimestamps?.isEmpty() == true)
    }

    @Test
    fun testHeartbeatPacketCreation() {
        val packet = SafetyPacket(
            senderId = "sender-001",
            senderName = "親類",
            type = PacketType.HEARTBEAT,
            message = "画面点灯検知"
        )
        val json = packet.toJson()
        val restored = SafetyPacket.fromJson(json)
        assertNotNull(restored)
        assertEquals(PacketType.HEARTBEAT, restored?.type)
        assertNull(restored?.status)
    }

    @Test
    fun testPeriodicSyncVersusDirectStatusPacket() {
        // 定期同期パケット: typeはHEARTBEAT、statusはnull
        val periodicPacket = SafetyPacket(
            senderId = "peer-01",
            senderName = "親",
            type = PacketType.HEARTBEAT,
            status = null,
            unlockTimestamps = listOf(1700000000000L),
            timestamp = 1700003600000L,
            message = ""
        )
        val restoredPeriodic = SafetyPacket.fromJson(periodicPacket.toJson())
        assertNotNull(restoredPeriodic)
        assertEquals(PacketType.HEARTBEAT, restoredPeriodic?.type)
        assertNull(restoredPeriodic?.status)

        // 手動ステータス送信パケット: typeはSTATUS_FINE、statusはSTATUS_FINE
        val directStatusPacket = SafetyPacket(
            senderId = "peer-01",
            senderName = "親",
            type = PacketType.STATUS_FINE,
            status = PacketType.STATUS_FINE,
            unlockTimestamps = listOf(1700000000000L),
            timestamp = 1700003600000L,
            message = "散歩中"
        )
        val restoredDirect = SafetyPacket.fromJson(directStatusPacket.toJson())
        assertNotNull(restoredDirect)
        assertEquals(PacketType.STATUS_FINE, restoredDirect?.type)
        assertEquals(PacketType.STATUS_FINE, restoredDirect?.status)
    }

    @Test
    fun testCommunicationLogSerialization() {
        // 受信ログテスト
        val incomingLog = CommunicationLog(
            id = "log-123",
            timestamp = 1700000000000L,
            isIncoming = true,
            peerName = "kusa",
            packetType = PacketType.STATUS_FINE,
            detail = "元気です！ (散歩)"
        )
        val json = incomingLog.toJson()
        val restored = CommunicationLog.fromJson(json)
        assertNotNull(restored)
        assertEquals("log-123", restored?.id)
        assertEquals(1700000000000L, restored?.timestamp)
        assertTrue(restored?.isIncoming == true)
        assertEquals("kusa", restored?.peerName)
        assertEquals(PacketType.STATUS_FINE, restored?.packetType)
        assertEquals("元気です！ (散歩)", restored?.detail)

        // 送信ログテスト
        val outgoingLog = CommunicationLog(
            id = "log-456",
            timestamp = 1700003600000L,
            isIncoming = false,
            peerName = "グループ「家族」",
            packetType = PacketType.STATUS_FINE,
            detail = "「😄 元気です」を送信"
        )
        val outJson = outgoingLog.toJson()
        val restoredOut = CommunicationLog.fromJson(outJson)
        assertNotNull(restoredOut)
        assertEquals("log-456", restoredOut?.id)
        assertFalse(restoredOut?.isIncoming == true)
        assertEquals("グループ「家族」", restoredOut?.peerName)
        assertEquals(PacketType.STATUS_FINE, restoredOut?.packetType)
        assertEquals("「😄 元気です」を送信", restoredOut?.detail)
    }

    @Test
    fun testStatusExpiryOneHourLogic() {
        val now = 1700003600000L // T
        val recentTimestamp = now - (30 * 60 * 1000L) // 30分前
        val staleTimestamp = now - (61 * 60 * 1000L) // 61分前

        val isRecentValid = recentTimestamp > 0L && (now - recentTimestamp) < AppPreferences.ONE_HOUR_MS
        val isStaleValid = staleTimestamp > 0L && (now - staleTimestamp) < AppPreferences.ONE_HOUR_MS

        assertTrue("30分前のステータスは有効期間内", isRecentValid)
        assertFalse("61分前のステータスは1時間経過で無効（通常表示へ復帰）", isStaleValid)
    }
}
