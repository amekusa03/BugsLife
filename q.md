# 【Android / WebRTC】親のギガを圧迫しない！毎時05分同期でつながるP2P「緩やかな相互見守り」アプリを作った話

## はじめに

「離れて暮らす親の安否が心配。でも、監視カメラや位置情報トラッキングはプライバシーが気になるし、監視する側・される側という上下関係が息苦しい……」

そんな課題感をきっかけに、**サーバーに一切データを保存せず、スマホの画面をつけた日常の動作だけで安否を確認し合えるAndroidアプリ『BugsLife』** を開発しました。

また、開発にあたって現場の実体験から見えてきたのが、**「見守りアプリに過剰なリアルタイム性（即時通知）は不要である」** という点です。
命に関わる本当の緊急時は119番（救急車）や電話を使うため、「体調が良くない」「病院へ行きたい」といった緩やかな連絡は数十分〜半日ズレても問題ありません。
それよりも、**親世代に多い低容量SIMプラン（0.5GB〜3GB）のギガを絶対に圧迫しない省パケット設計（月間約7MB）** こそが重要でした。

本記事では、このアプリの設計思想や、**NTT SkyWay (WebRTC DataChannel) を用いた完全P2P通信**、**毎時05分の定期同期ウィンドウ方式による省パケット・省電力実装**についてご紹介します。

---

## アプリのコンセプト：対等で緩やかな「相互見守り」

従来の多くの見守りサービスは「親を見守る子」のような一方通行の構造になりがちでした。
しかし、本アプリでは**参加者全員が対等にお互いを気に掛け合う「相互見守りモデル」**を採用しています。

```
[親のスマホ]  <=== (毎時05分に画面点灯シグナルをそっと交換) ===>  [子のスマホ]
```

### 主な特徴
1. **完全P2P (サーバーレス・ゼロデータ蓄積)**:
   - メッセージや利用履歴などのプライベートデータを中央サーバーに一切保管しません。
   - 位置情報（GPS）やカメラ・マイク・操作ログも取得せず、「生存シグナル」のみを暗号化P2Pで交換します。
2. **毎時05分の定期同期ウィンドウ（月間 約7〜8MB）**:
   - 画面点灯時はローカルで回数をカウントするのみ（通信ゼロ）。
   - 毎時05分（10:05, 11:05...）に全端末がSkyWayへ短時間（約90秒）自動接続し、パケット交換後に即座に切断・スリープへ復帰。
3. **24時間無反応アラート (Watchdog)**:
   - 相手の画面点灯が24時間途絶えた場合、音・振動・通知で安否確認を促す警告を発出。
4. **ワンタップ安否報告 & 今すぐ同期**:
   - 「😄 元気です」「😣 良くない」ボタンや「今すぐ同期」で、いつでも即座に近況を共有可能。

---

## システム構成 & アーキテクチャ

通信基盤には、NAT・ファイアウォール越え（STUN/TURN）に対応した **NTT Communications SkyWay (WebRTC DataChannel)** を採用しています。

```mermaid
graph LR
    subgraph 端末A [ピアA]
        UI_A[Jetpack Compose UI]
        Service_A[常駐見守りサービス]
        Screen_A[画面点灯をローカル記録]
        SkyWay_A[SkyWay WebRTC DataChannel]
    end

    subgraph 端末B [ピアB]
        UI_B[Jetpack Compose UI]
        Service_B[常駐見守りサービス]
        Screen_B[画面点灯をローカル記録]
        SkyWay_B[SkyWay WebRTC DataChannel]
    end

    Screen_A -->|画面点灯記録 (通信なし)| Service_A
    Service_A -->|毎時05分に自動接続| SkyWay_A
    UI_A -->|「元気」「良くない」「今すぐ同期」| Service_A
    SkyWay_A <-->|毎時05分 同期ウィンドウ| SkyWay_B
    SkyWay_B --> Service_B
    Service_B -->|状態更新 & 通知| UI_B
    Service_B -->|24時間未受信アラート| UI_B
```

### 技術スタック
- **UI**: Jetpack Compose (Material 3)
- **言語**: Kotlin + Coroutines / StateFlow
- **P2P通信**: NTT Communications SkyWay SDK 2.9 (P2PRoom / DataStream)
- **バックグラウンド**: Foreground Service + BroadcastReceiver
- **対応OS**: Android 6.0 (API 23) 〜 Android 15+

---

## 実装の技術的ポイント

### 1. P2Pにおける「毎時05分 ランデブー同期方式」

WebRTCはサーバーにデータが残らないため、送受信には双方が同時にRoomへ接続している必要があります。
そこで、全端末が時計を合わせて **「毎時05分（XX:05:00）」** に接続し、約90秒間パケットを交換して切断するスケジューラを実装しました。

```kotlin
// 次の毎時05分（XX:05:00）を計算
private fun calculateNextSyncTimestamp(): Long {
    val calendar = Calendar.getInstance()
    val minute = calendar.get(Calendar.MINUTE)
    val second = calendar.get(Calendar.SECOND)
    if (minute < 5 || (minute == 5 && second < 10)) {
        calendar.set(Calendar.MINUTE, 5)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    } else {
        calendar.add(Calendar.HOUR_OF_DAY, 1)
        calendar.set(Calendar.MINUTE, 5)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
    }
    return calendar.timeInMillis
}

// 毎時05分の同期ループ
private fun startScheduledSyncLoop() {
    scheduledSyncJob = serviceScope.launch {
        while (isActive) {
            val nextSyncTime = calculateNextSyncTimestamp()
            val waitMs = (nextSyncTime - System.currentTimeMillis()).coerceAtLeast(1000L)
            delay(waitMs)

            // 毎時05分に約90秒間だけSkyWayに接続して相互交換 -> 自動切断
            performSyncWindow(isManual = false)
        }
    }
}
```

### 2. SkyWayクライアント用JWT Auth Tokenの自動生成

SkyWayのRoom接続にはJWT形式のAuth Tokenが必要です。端末内で直接JWTを生成・HMAC-SHA256署名するユーティリティを実装しています。

```kotlin
object SkyWayTokenUtil {
    fun createAuthToken(
        appId: String,
        secretKey: String,
        durationSeconds: Long = 6 * 60 * 60L
    ): String {
        val now = System.currentTimeMillis() / 1000
        val exp = now + durationSeconds

        val headerObj = JSONObject().apply {
            put("alg", "HS256")
            put("typ", "JWT")
        }
        val writeAction = JSONArray(listOf("write"))
        val memberRule = JSONObject().apply {
            put("id", "*")
            put("name", "*")
            put("actions", writeAction)
            put("publication", JSONObject().apply { put("actions", writeAction) })
            put("subscription", JSONObject().apply { put("actions", writeAction) })
        }
        val channelRule = JSONObject().apply {
            put("id", "*")
            put("name", "*")
            put("actions", writeAction)
            put("members", JSONArray().apply { put(memberRule) })
        }
        val appScope = JSONObject().apply {
            put("id", appId)
            put("actions", JSONArray(listOf("read")))
            put("channels", JSONArray().apply { put(channelRule) })
            put("turn", true)
        }

        val payloadObj = JSONObject().apply {
            put("jti", UUID.randomUUID().toString())
            put("iat", now)
            put("exp", exp)
            put("scope", JSONObject().apply { put("app", appScope) })
        }

        val headerBase64 = base64UrlEncode(headerObj.toString().toByteArray(StandardCharsets.UTF_8))
        val payloadBase64 = base64UrlEncode(payloadObj.toString().toByteArray(StandardCharsets.UTF_8))

        val signatureTarget = "$headerBase64.$payloadBase64"
        val signature = hmacSha256(signatureTarget, secretKey)
        val signatureBase64 = base64UrlEncode(signature)

        return "$headerBase64.$payloadBase64.$signatureBase64"
    }

    private fun hmacSha256(data: String, key: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key.toByteArray(StandardCharsets.UTF_8), "HmacSHA256"))
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }
}
```

### 3. スマホ操作検知（ACTION_USER_PRESENT）のローカル記録

着信や通知で画面がついただけの誤検知を防ぐため、ユーザーが明示的にロック解除した時（`ACTION_USER_PRESENT`）のみをローカルに記録します。

```kotlin
class ScreenEventReceiver(
    private val onUserActive: () -> Unit
) : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {
            Intent.ACTION_USER_PRESENT -> {
                onUserActive()
            }
        }
    }
}
```

### 4. 24時間未受信検知のWatchdogタイマー

フォアグラウンドサービス内で定期的にコルーチンを回し、各ピアの「最終検知時刻から24時間以上経過していないか」を監視します。

```kotlin
serviceScope.launch {
    while (isActive) {
        delay(30_000L)
        val now = System.currentTimeMillis()
        val timeoutMs = prefs.timeoutDurationMillis

        prefs.getPeers().forEach { peer ->
            val elapsed = now - peer.lastSeenTimestamp
            if (peer.lastSeenTimestamp > 0L && elapsed >= timeoutMs && !peer.isAlertTriggered) {
                // 24時間未検知アラートを発出（高優先度通知 + 音・振動）
                NotificationHelper.showInactivityAlert(this@WatcherForegroundService, peer.id, peer.name, elapsed / (1000.0 * 3600))
            }
        }
    }
}
```

---

## 📊 パケット量試算（月間 約7.8MB）

親世代に多い 0.5GB〜3GB の格安SIMでも安心して使えるよう徹底的に無駄を削ぎ落としました。

| 通信項目 | 頻度 | 1回あたり | 1ヶ月(30日)の通信量 |
| :--- | :--- | :--- | :--- |
| **毎時05分 定期同期** | 24回 / 日 | 約 10 KB | **約 7.2 MB** |
| **安否ボタン / 手動同期** | 1日 数回 | 約 10 KB | **約 0.6 MB** |
| **スマホ操作検知** | 終日 | 0 bytes (ローカル記録) | **0 MB** |
| **合計** | - | - | **約 7.8 MB / 月** |

---

## UI/UXの工夫 (Jetpack Compose)

- **同期ステータスが一目でわかるダッシュボード**:
  「スリープ待機（次回同期: 10:05）」や「🔄 同期中」の状態を明示。
- **シニア層にも使いやすい大型UI**:
  「😄 元気です」「😣 良くない」の大きなカードボタンを配置。
- **通信ログシート**:
  P2P接続の成否やパケット送受信履歴をボトムシートからリアルタイムに確認可能。

---

## おわりに

過剰なリアルタイム監視を避け、**「1時間に1回（毎時05分）の定期同期」** にすることで、**「親のギガを一切圧迫しない」「バッテリーが長持ちする」「心理的プレッシャーのない緩やかな繋がり」** を実現できました。

家族や大切な人との繋がりを、優しく支えるツールとして役立てば幸いです。

### 参考リンク・関連技術
- [NTT Communications SkyWay ドキュメント](https://skyway.ntt.com/ja/docs/)
- [Android Developers - Foreground Services](https://developer.android.com/guide/components/foreground-services)
- [Jetpack Compose](https://developer.android.com/jetpack/compose)
