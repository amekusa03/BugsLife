package com.kusa.bugslife

import com.kusa.bugslife.data.PacketType
import com.kusa.bugslife.data.SafetyPacket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SafetyPacketTest {

    @Test
    fun testPacketSerializationAndDeserialization() {
        val packet = SafetyPacket(
            packetId = "test-uuid-1234",
            senderId = "sender-5678",
            senderName = "おじいちゃん",
            type = PacketType.STATUS_FINE,
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
        assertEquals(1700000000000L, restored?.timestamp)
        assertEquals("散歩から帰りました", restored?.message)
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
    }
}
