package com.kusa.bugslife.network

import android.content.Context
import android.util.Log
import com.google.android.gms.tasks.Task
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import com.kusa.bugslife.data.MemberStatus
import com.kusa.bugslife.data.PacketType
import com.kusa.bugslife.data.PendingJoinRequest
import com.kusa.bugslife.data.SafetyPacket
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FirestorePeerMessenger(
    private val context: Context,
    private val groupName: String,
    private val myUserId: String
) : PeerMessenger {
    private val tag = "FirestoreMessenger"
    private var listenerRegistration: ListenerRegistration? = null
    private var isListeningFlag = false

    private val db: FirebaseFirestore by lazy {
        FirebaseFirestore.getInstance()
    }

    override fun startListening(onPacketReceived: (packet: SafetyPacket) -> Unit) {
        startListeningWithRequests(
            onPacketReceived = onPacketReceived,
            onPendingRequestsChanged = {},
            onMyStatusChanged = {}
        )
    }

    fun startListeningWithRequests(
        onPacketReceived: (packet: SafetyPacket) -> Unit,
        onPendingRequestsChanged: (List<PendingJoinRequest>) -> Unit,
        onMyStatusChanged: (MemberStatus) -> Unit
    ) {
        if (groupName.isBlank()) {
            Log.w(tag, "Group name is blank, skipping Firestore listening.")
            return
        }

        stopListening()
        isListeningFlag = true
        Log.d(tag, "Starting Firestore listener for group: $groupName (myUserId: $myUserId)")

        try {
            listenerRegistration = db.collection("groups")
                .document(groupName)
                .collection("members")
                .addSnapshotListener { snapshots, error ->
                    if (error != null) {
                        Log.e(tag, "Firestore snapshot listener error: ${error.message}", error)
                        return@addSnapshotListener
                    }

                    if (snapshots != null) {
                        val pendingRequests = mutableListOf<PendingJoinRequest>()

                        for (doc in snapshots.documents) {
                            val senderId = doc.getString("senderId") ?: doc.id
                            val statusStr = doc.getString("memberStatus")
                            val memberStatus = MemberStatus.fromString(statusStr)

                            // 自身のステータス変更を検知
                            if (senderId == myUserId) {
                                onMyStatusChanged(memberStatus)
                                continue
                            }

                            // 既存メンバー向け: 新規参加申請の検知
                            if (memberStatus == MemberStatus.PENDING) {
                                val senderName = doc.getString("senderName") ?: "新規ユーザー"
                                val reqTime = doc.getLong("timestamp") ?: System.currentTimeMillis()
                                pendingRequests.add(
                                    PendingJoinRequest(
                                        userId = senderId,
                                        userName = senderName,
                                        requestedAt = reqTime
                                    )
                                )
                            } else if (memberStatus == MemberStatus.APPROVED) {
                                // 承認済みメンバーのパケットのみ受信処理
                                val packet = parseDocumentToPacket(doc)
                                if (packet != null) {
                                    Log.d(tag, "Received approved packet via Firestore from ${packet.senderName}: $packet")
                                    onPacketReceived(packet)
                                }
                            }
                        }

                        onPendingRequestsChanged(pendingRequests)
                    }
                }
        } catch (e: Exception) {
            Log.e(tag, "Failed to start Firestore snapshot listener: ${e.message}", e)
        }
    }

    override fun stopListening() {
        if (listenerRegistration != null) {
            try {
                listenerRegistration?.remove()
                Log.d(tag, "Firestore listener removed.")
            } catch (e: Exception) {
                Log.w(tag, "Error removing Firestore listener: ${e.message}")
            }
            listenerRegistration = null
        }
        isListeningFlag = false
    }

    override suspend fun sendPacket(packet: SafetyPacket): Boolean {
        return sendPacketWithMerge(packet, packet.unlockTimestamps)
    }

    /**
     * 新しいタイムスタンプを既存のFirestore配列にマージ（上限100件で古いものを削除）して送信
     */
    suspend fun sendPacketWithMerge(packet: SafetyPacket, newTimestamps: List<Long>): Boolean {
        if (groupName.isBlank()) {
            Log.w(tag, "Group name is blank, cannot send packet.")
            return false
        }

        return try {
            val docRef = db.collection("groups")
                .document(groupName)
                .collection("members")
                .document(packet.senderId)

            val existingSnapshot = try {
                withTimeoutOrNull(8_000L) {
                    docRef.get().awaitTask()
                }
            } catch (e: Exception) {
                null
            }

            val existingTimestamps = if (existingSnapshot != null && existingSnapshot.exists()) {
                @Suppress("UNCHECKED_CAST")
                val list = existingSnapshot.get("unlockTimestamps") as? List<*>
                list?.mapNotNull { (it as? Number)?.toLong() } ?: emptyList()
            } else {
                emptyList()
            }

            val cutoffTime = System.currentTimeMillis() - 24 * 60 * 60 * 1000L

            // 新旧タイムスタンプをマージして過去24時間以内のものをソート・重複除外
            val mergedTimestamps = (existingTimestamps + newTimestamps)
                .filter { it >= cutoffTime }
                .distinct()
                .sorted()

            // 100件を超えたら古いものから削除（最新100件を保持）
            val finalTimestamps = if (mergedTimestamps.size > 100) {
                mergedTimestamps.takeLast(100)
            } else {
                mergedTimestamps
            }

            val data = hashMapOf(
                "packetId" to packet.packetId,
                "senderId" to packet.senderId,
                "senderName" to packet.senderName,
                "type" to packet.type.name,
                "status" to packet.status?.name,
                "memberStatus" to packet.memberStatus.name,
                "unlockTimestamps" to finalTimestamps,
                "timestamp" to packet.timestamp,
                "message" to packet.message,
                "updatedAt" to FieldValue.serverTimestamp()
            )

            val result = withTimeoutOrNull(15_000L) {
                docRef.set(data, SetOptions.merge()).awaitTask()
                true
            }
            result == true
        } catch (e: Exception) {
            Log.e(tag, "Failed to send packet to Firestore: ${e.message}", e)
            false
        }
    }

    /**
     * グループが既に存在するか（他のメンバーがいるか）判定
     */
    suspend fun checkGroupExists(groupName: String): Boolean {
        if (groupName.isBlank()) return false
        return try {
            val snapshot = withTimeoutOrNull(8_000L) {
                db.collection("groups")
                    .document(groupName)
                    .collection("members")
                    .get()
                    .awaitTask()
            } ?: return false

            // 承認済みメンバー（または自身以外のメンバー）が存在するか
            snapshot.documents.any { doc ->
                val status = doc.getString("memberStatus")
                status == MemberStatus.APPROVED.name || status == null
            }
        } catch (e: Exception) {
            Log.e(tag, "Failed to check group exists: ${e.message}", e)
            false
        }
    }

    /**
     * グループへの参加申請を送信 (memberStatus = PENDING)
     */
    suspend fun requestJoinGroup(targetGroupName: String, userId: String, userName: String): Boolean {
        if (targetGroupName.isBlank()) return false
        return try {
            val data = hashMapOf(
                "senderId" to userId,
                "senderName" to userName,
                "memberStatus" to MemberStatus.PENDING.name,
                "timestamp" to System.currentTimeMillis(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val result = withTimeoutOrNull(10_000L) {
                db.collection("groups")
                    .document(targetGroupName)
                    .collection("members")
                    .document(userId)
                    .set(data, SetOptions.merge())
                    .awaitTask()
                true
            }
            result == true
        } catch (e: Exception) {
            Log.e(tag, "Failed to request join group: ${e.message}", e)
            false
        }
    }

    /**
     * 新規グループ作成（自身をAPPROVEDとして登録）
     */
    suspend fun createGroup(targetGroupName: String, userId: String, userName: String): Boolean {
        if (targetGroupName.isBlank()) return false
        return try {
            val data = hashMapOf(
                "senderId" to userId,
                "senderName" to userName,
                "memberStatus" to MemberStatus.APPROVED.name,
                "timestamp" to System.currentTimeMillis(),
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val result = withTimeoutOrNull(10_000L) {
                db.collection("groups")
                    .document(targetGroupName)
                    .collection("members")
                    .document(userId)
                    .set(data, SetOptions.merge())
                    .awaitTask()
                true
            }
            result == true
        } catch (e: Exception) {
            Log.e(tag, "Failed to create group: ${e.message}", e)
            false
        }
    }

    /**
     * メンバーの参加を承認 (memberStatus = APPROVED)
     */
    suspend fun approveMember(targetGroupName: String, userId: String): Boolean {
        if (targetGroupName.isBlank()) return false
        return try {
            val data = hashMapOf<String, Any>(
                "memberStatus" to MemberStatus.APPROVED.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val result = withTimeoutOrNull(10_000L) {
                db.collection("groups")
                    .document(targetGroupName)
                    .collection("members")
                    .document(userId)
                    .set(data, SetOptions.merge())
                    .awaitTask()
                true
            }
            result == true
        } catch (e: Exception) {
            Log.e(tag, "Failed to approve member: ${e.message}", e)
            false
        }
    }

    /**
     * メンバーの参加を拒否 (memberStatus = REJECTED)
     */
    suspend fun rejectMember(targetGroupName: String, userId: String): Boolean {
        if (targetGroupName.isBlank()) return false
        return try {
            val data = hashMapOf<String, Any>(
                "memberStatus" to MemberStatus.REJECTED.name,
                "updatedAt" to FieldValue.serverTimestamp()
            )
            val result = withTimeoutOrNull(10_000L) {
                db.collection("groups")
                    .document(targetGroupName)
                    .collection("members")
                    .document(userId)
                    .set(data, SetOptions.merge())
                    .awaitTask()
                true
            }
            result == true
        } catch (e: Exception) {
            Log.e(tag, "Failed to reject member: ${e.message}", e)
            false
        }
    }

    override fun isListening(): Boolean = isListeningFlag

    /**
     * グループ内の他の承認済みメンバーの最新パケットを一度だけ取得
     */
    suspend fun fetchPeersOnce(): List<SafetyPacket> {
        if (groupName.isBlank()) return emptyList()

        return try {
            val snapshot = withTimeoutOrNull(10_000L) {
                db.collection("groups")
                    .document(groupName)
                    .collection("members")
                    .get()
                    .awaitTask()
            } ?: return emptyList()

            val packets = mutableListOf<SafetyPacket>()
            for (doc in snapshot.documents) {
                val senderId = doc.getString("senderId") ?: doc.id
                if (senderId == myUserId) continue

                val memberStatus = MemberStatus.fromString(doc.getString("memberStatus"))
                if (memberStatus != MemberStatus.APPROVED) continue

                val packet = parseDocumentToPacket(doc)
                if (packet != null) {
                    packets.add(packet)
                }
            }
            packets
        } catch (e: Exception) {
            Log.e(tag, "Failed to fetch peers from Firestore: ${e.message}", e)
            emptyList()
        }
    }

    private fun parseDocumentToPacket(doc: DocumentSnapshot): SafetyPacket? {
        return try {
            val packetId = doc.getString("packetId") ?: doc.id
            val senderId = doc.getString("senderId") ?: doc.id
            val senderName = doc.getString("senderName") ?: "相手"
            val typeStr = doc.getString("type")
            val statusStr = doc.getString("status")
            val memberStatusStr = doc.getString("memberStatus")
            val message = doc.getString("message") ?: ""
            val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()

            @Suppress("UNCHECKED_CAST")
            val rawTimestamps = doc.get("unlockTimestamps") as? List<*>
            val cutoffTime = System.currentTimeMillis() - 24 * 60 * 60 * 1000L
            val unlockTimestamps = rawTimestamps?.mapNotNull { (it as? Number)?.toLong() }
                ?.filter { it >= cutoffTime }
                ?: emptyList()

            SafetyPacket(
                packetId = packetId,
                senderId = senderId,
                senderName = senderName,
                type = PacketType.fromString(typeStr),
                status = if (!statusStr.isNullOrBlank()) PacketType.fromString(statusStr) else null,
                memberStatus = MemberStatus.fromString(memberStatusStr),
                unlockTimestamps = unlockTimestamps,
                timestamp = timestamp,
                message = message
            )
        } catch (e: Exception) {
            Log.e(tag, "Error parsing Firestore doc ${doc.id}: ${e.message}")
            null
        }
    }

    private suspend fun <T> Task<T>.awaitTask(): T = suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            if (cont.isActive) cont.resume(result)
        }
        addOnFailureListener { e ->
            if (cont.isActive) cont.resumeWithException(e)
        }
        addOnCanceledListener {
            if (cont.isActive) cont.cancel()
        }
    }
}
