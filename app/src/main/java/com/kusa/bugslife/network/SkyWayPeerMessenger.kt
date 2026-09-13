package com.kusa.bugslife.network

import android.content.Context
import android.util.Log
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
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.Collections
import java.util.UUID

class SkyWayPeerMessenger(
    private val context: Context,
    private val appId: String,
    private val secretKey: String,
    private val roomName: String,
    private val memberName: String
) : PeerMessenger {
    private val tag = "SkyWayPeerMessenger"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var room: P2PRoom? = null
    private var localMember: LocalP2PRoomMember? = null
    private var localDataStream: LocalDataStream? = null

    private val subscribeMutex = Mutex()
    private val subscribedPubIds = Collections.synchronizedSet(mutableSetOf<String>())

    @Volatile private var isConnected = false
    @Volatile private var isConnecting = false
    @Volatile private var isStopped = false

    private var connectionJob: Job? = null
    private var onPacketCallback: ((SafetyPacket) -> Unit)? = null

    /** 接続確立中または接続済みであれば true を返す */
    override fun isListening(): Boolean = isConnected || isConnecting

    override fun startListening(
        onPacketReceived: (packet: SafetyPacket) -> Unit
    ) {
        onPacketCallback = onPacketReceived
        isStopped = false
        if (isConnected || isConnecting) {
            Log.d(tag, "startListening: already connected/connecting. skip.")
            return
        }
        launchConnection()
    }

    private fun launchConnection() {
        if (isStopped) return
        connectionJob?.cancel()
        isConnecting = true
        connectionJob = scope.launch {
            connectWithRetry()
        }
    }

    private suspend fun connectWithRetry() {
        val maxRetries = 5
        var attempt = 0
        while (currentCoroutineContext().isActive && attempt < maxRetries && !isConnected && !isStopped) {
            attempt++
            if (attempt > 1) {
                val waitSec = minOf(attempt * 5L, 30L)
                Log.d(tag, "SkyWay 接続リトライ #$attempt / $maxRetries (${waitSec}秒後)...")
                delay(waitSec * 1000L)
            }
            try {
                connect()
            } catch (e: Exception) {
                Log.e(tag, "SkyWay 接続エラー (attempt=$attempt): ${e.message}", e)
                isConnected = false
            }
        }
        isConnecting = false
        if (!isConnected && !isStopped) {
            Log.e(tag, "SkyWay: $maxRetries 回試みましたが接続できませんでした。60秒後に再試行します。")
            delay(60_000L)
            if (!isStopped) {
                Log.d(tag, "SkyWay 長期リトライ開始...")
                launchConnection()
            }
        }
    }

    private suspend fun connect() {
        Log.d(tag, "=== SkyWay 接続開始 === Room: $roomName, AppId: $appId")

        if (appId.isBlank() || secretKey.isBlank()) {
            Log.e(tag, "SkyWay AppId または SecretKey が未設定です。設定画面から入力してください。")
            return
        }
        if (roomName.isBlank()) {
            Log.e(tag, "SkyWay Room名が未設定です。設定画面から入力してください。")
            return
        }

        val token = SkyWayTokenUtil.createAuthToken(appId, secretKey)
        Log.d(tag, "Auth Token 生成完了 (先頭40文字): ${token.take(40)}...")

        val options = SkyWayContext.Options(
            logLevel = Logger.LogLevel.INFO
        )

        // リトライ時など既存の native 状態が残っていれば必ずクリーンアップしてから setup()
        if (SkyWayContext.isSetup) {
            Log.d(tag, "SkyWayContext.dispose() → 再 setup のためクリーンアップ")
            try { SkyWayContext.dispose() } catch (e: Exception) {
                Log.e(tag, "SkyWayContext.dispose() エラー: ${e.message}")
            }
        }

        Log.d(tag, "SkyWayContext.setup() を呼び出し中...")
        val setupSuccess = SkyWayContext.setup(
            context,
            token,
            options
        )
        if (!setupSuccess) {
            Log.e(tag, "SkyWayContext.setup() が false を返しました。AppId/SecretKey を確認してください。")
            return
        }
        Log.d(tag, "SkyWayContext.setup() 成功")

        // SkyWayContext エラーハンドラの設定
        SkyWayContext.onErrorHandler = { error ->
            Log.e(tag, "SkyWayContext.onError: $error")
        }
        // トークン期限切れ → 新しいトークンで更新して再接続
        SkyWayContext.onTokenExpiredHandler = {
            Log.w(tag, "SkyWay トークンが期限切れになりました。再接続します。")
            isConnected = false
            scope.launch { dropAndReconnect() }
        }
        // トークンの更新を推奨するタイミング → 早めに更新
        SkyWayContext.onTokenRefreshingNeededHandler = {
            Log.d(tag, "SkyWay トークンの更新タイミング。新しいトークンを発行します。")
            try {
                val newToken = SkyWayTokenUtil.createAuthToken(appId, secretKey)
                SkyWayContext.updateAuthToken(newToken)
                Log.d(tag, "SkyWay トークン更新完了")
            } catch (e: Exception) {
                Log.e(tag, "SkyWay トークン更新エラー: ${e.message}", e)
            }
        }

        Log.d(tag, "P2PRoom.findOrCreate() 呼び出し: roomName=$roomName")
        val targetRoom = P2PRoom.findOrCreate(name = roomName)
        if (targetRoom == null) {
            Log.e(tag, "P2PRoom.findOrCreate() が null を返しました。ネットワーク接続を確認してください。")
            return
        }
        room = targetRoom
        Log.d(tag, "Room 取得成功: ${targetRoom.name}")

        // Room エラーハンドラ
        targetRoom.onErrorHandler = { ex ->
            Log.e(tag, "Room.onError: ${ex.message}", ex)
        }

        // Room がクローズされた場合に自動再接続
        targetRoom.onClosedHandler = {
            Log.w(tag, "SkyWay Room がクローズされました。再接続を試みます。")
            isConnected = false
            clearLocalRefs()
            if (!isStopped) {
                launchConnection()
            }
        }

        val sessionSuffix = UUID.randomUUID().toString().take(6)
        val resolvedMemberName = "${memberName.ifBlank { "member" }}_$sessionSuffix"
        val memberInit = RoomMember.Init(name = resolvedMemberName)
        Log.d(tag, "Room に参加中 (memberName=$resolvedMemberName)...")
        val member = targetRoom.join(memberInit)
        if (member == null) {
            Log.e(tag, "Room.join() が null を返しました。")
            return
        }
        localMember = member
        Log.d(tag, "Room 参加成功: member.name=${member.name}, member.id=${member.id}")

        // Local DataStream の作成 & Publish
        val dataSource = DataSource()
        val dataStream = dataSource.createStream()
        localDataStream = dataStream
        member.publish(dataStream)
        Log.d(tag, "LocalDataStream を公開しました")

        // 相手からの Stream Publication ハンドラを登録
        targetRoom.onStreamPublishedHandler = { publication ->
            scope.launch {
                handleNewPublication(publication)
            }
        }

        targetRoom.onStreamUnpublishedHandler = { publication ->
            Log.d(tag, "Stream unpublished: ${publication.id}")
            subscribedPubIds.remove(publication.id)
        }

        targetRoom.onMemberLeftHandler = { m ->
            Log.d(tag, "Member left: ${m.name} (${m.id})")
        }

        // publish() 直後は SDK 内部の keepalive 初期化 (UpdateMemberTtl) が非同期で走るため、
        // それが完了するまで待機してから subscribe() を呼び出す。
        // 待機が不足すると UpdateMemberTtl 失敗がネイティブ層のクラッシュ (SIGSEGV) を引き起こす。
        delay(1000L)

        // 既に公開されている相手の Stream を順次直列に購読
        val existingPubs = targetRoom.publications.toList()
        for (pub in existingPubs) {
            handleNewPublication(pub)
        }

        isConnected = true
        Log.d(tag, "=== SkyWay 接続完了 === Room=$roomName, Member=${member.name}")
    }

    private suspend fun handleNewPublication(publication: RoomPublication) {
        val member = localMember ?: return
        val publisher = publication.publisher
        if (publisher == null || publisher.id == member.id) {
            return // 自分自身の publication または publisher 不明はスキップ
        }

        if (subscribedPubIds.contains(publication.id)) {
            return // 既に購読済み
        }

        subscribeMutex.withLock {
            if (subscribedPubIds.contains(publication.id)) return@withLock
            if (localMember == null || isStopped) return@withLock

            try {
                Log.d(tag, "Publication 購読試行: pubId=${publication.id}, publisher=${publisher.name}")
                // ネイティブスレッドの競合を防ぐため直列化と小ウェイト
                delay(300L)
                val subscription = member.subscribe(publication.id)
                if (subscription == null) {
                    Log.w(tag, "member.subscribe() が null を返しました (pubId=${publication.id})")
                    return@withLock
                }
                subscribedPubIds.add(publication.id)

                val stream = subscription.stream
                if (stream is RemoteDataStream) {
                    Log.d(tag, "DataStream 購読成功: publisher=${publisher.name}")
                    stream.onDataHandler = { dataString ->
                        Log.d(tag, "パケット受信 from ${publisher.name}: $dataString")
                        val packet = SafetyPacket.fromJson(dataString)
                        if (packet != null) {
                            scope.launch(Dispatchers.Main) {
                                onPacketCallback?.invoke(packet)
                            }
                        }
                    }
                } else {
                    Log.w(tag, "購読したストリームが RemoteDataStream ではありません: ${stream?.javaClass?.simpleName}")
                }
            } catch (t: Throwable) {
                Log.e(tag, "SkyWay Publication 購読エラー (pubId=${publication.id}): ${t.message}", t)
            }
        }
    }

    /** ローカル参照をクリア（Room はそのまま）*/
    private fun clearLocalRefs() {
        subscribedPubIds.clear()
        localDataStream = null
        localMember = null
        room = null
    }

    /** 切断して再接続 */
    private suspend fun dropAndReconnect() {
        isConnected = false
        isConnecting = false
        subscribeMutex.withLock {
            try { localMember?.leave() } catch (_: Throwable) {}
            try { room?.dispose() } catch (_: Throwable) {}
            try { localDataStream?.dispose() } catch (_: Throwable) {}
            clearLocalRefs()
        }
        if (!isStopped) {
            launchConnection()
        }
    }

    override fun stopListening() {
        isStopped = true
        isConnected = false
        isConnecting = false
        connectionJob?.cancel()
        connectionJob = null
        scope.launch {
            subscribeMutex.withLock {
                try {
                    localMember?.leave()
                    localMember = null
                    room?.dispose()
                    room = null
                    localDataStream?.dispose()
                    localDataStream = null
                    subscribedPubIds.clear()
                    Log.d(tag, "SkyWay 切断完了")
                } catch (e: Throwable) {
                    Log.e(tag, "SkyWay 切断エラー: ${e.message}", e)
                }
            }
        }
    }

    override suspend fun sendPacket(
        packet: SafetyPacket
    ): Boolean = withContext(Dispatchers.IO) {
        // 接続中であれば最大20秒待機
        if (!isConnected && (isConnecting || connectionJob?.isActive == true)) {
            Log.d(tag, "SkyWay 接続中... パケット送信まで最大20秒待機")
            val startTime = System.currentTimeMillis()
            while (!isConnected
                && (isConnecting || connectionJob?.isActive == true)
                && (System.currentTimeMillis() - startTime < 20_000L)) {
                delay(300L)
            }
        }

        val jsonStr = packet.toJson()
        return@withContext try {
            val stream = localDataStream
            if (stream != null && isConnected) {
                stream.write(jsonStr)
                Log.d(tag, "SkyWay DataStream 送信成功: $jsonStr")
                true
            } else {
                Log.w(tag, "SkyWay ストリーム未接続 (isConnected=$isConnected, isConnecting=$isConnecting, stream=${stream != null}). 送信できませんでした。")
                false
            }
        } catch (e: Throwable) {
            Log.e(tag, "SkyWay DataStream 送信エラー: ${e.message}", e)
            false
        }
    }
}
