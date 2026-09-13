# BugsLife (遠隔見守り) - P2P相互安否見守りアプリ

[![Android](https://img.shields.io/badge/Platform-Android%206.0%2B%20(API%2023%2B)-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-blue.svg)](https://kotlinlang.org)
[![SkyWay](https://img.shields.io/badge/WebRTC-SkyWay%20SDK%202.2.0-orange.svg)](https://skyway.ntt.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-purple.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

[**English Document**](./README.md)

**BugsLife（遠隔見守り）** は、遠方に住む家族・親類や知人同士がサーバーを介さずに直接端末同士で安否を確認し合える、分散型P2P相互見守りAndroidアプリケーションです。

同一Wi-Fi（LAN）内での直接通信に加え、**NTT Communications SkyWay（WebRTC DataChannel）** によるNAT越え・4G/5G回線経由でのインターネット越しのP2P通信に対応しています。

全端末がお互いを常時見守り合う **「相互見守りモデル」** を採用しています。

---

## 🌟 主な機能

1. **インターネット & LAN ハイブリッドP2P通信 (SkyWay + UDP)**:
   - **インターネット**: NTT Communications SkyWay SDKを利用し、STUN/TURNによるNAT・ファイアウォール越えでモバイル回線（4G/5G）や異なるWi-Fiネットワーク間でもP2P通信を実現。
   - **LAN**: 同一ネットワーク内では超低遅延なUDPダイレクト通信を併用。

2. **画面点灯（Screen ON）時の自動ハートビート送信 (P2P 1:n)**:
   - スマートフォンの画面を点灯またはロック解除した際（`ACTION_SCREEN_ON` / `ACTION_USER_PRESENT`）に、登録されたすべての相手端末へ生存確認パケットを自動同報送信。
   - 15秒間のデバウンス制御により、バッテリーや通信パケットの過剰消費を防止。

3. **24時間無操作・未受信アラート (Watchdog)**:
   - 登録された各相手の直近の操作時刻をバックグラウンドで監視。
   - 画面点灯が **24時間**（テスト用に1分〜24時間で設定変更可能）途絶えた場合、高優先度の安否警告アラート（音・振動・ヘッドアップ通知）を自動発出。

4. **ワンタップ「元気」「良くない」クイックボタン**:
   - シニア世代でも押しやすい大型タッチカードボタン：
     - **😄 元気です**: 相手全員へ元気である旨を即座に通知。
     - **😣 良くない**: 体調不良や異常を相手全員へ緊急高優先度通知。

5. **相互見守り統合UI**:
   - 役割選択や複雑な切り替え不要の1画面レイアウト。
   - 画面上部にSkyWayのRoom名および自端末のIPアドレスを常時明記。
   - 相手端末ごとのリアルタイム経過時間（「15分前」「23時間前」等）および状態バッジ（🟢 正常 / ⚠️ 24時間無反応 / 😄 元気 / 😣 良くない）を表示。
   - リアルタイム通信ログ確認シートを搭載。

6. **幅広い端末対応**:
   - **Android 6.0 (API 23, Marshmallow) 〜 Android 15+** まで幅広く対応。
   - 常駐フォアグラウンドサービスにより、Dozeモード等のOS省電力制限下でも安定稼働。

---

## 🛠 システム構成 & 使用技術

- **UI**: Jetpack Compose (Material 3)
- **言語**: Kotlin + Coroutines / StateFlow
- **常駐監視**: Android Foreground Service (`WatcherForegroundService`)
- **P2P通信**:
  - **SkyWay WebRTC**: `SkyWayPeerMessenger` (P2PRoom & DataStream)
  - **LAN UDP**: `UdpPeerMessenger` (DatagramSocket 1:n)
  - **ハイブリッド中継**: `CompositePeerMessenger`

```mermaid
graph LR
    subgraph 端末A [ピアA - 家族/親類]
        UI_A[Jetpack Compose UI]
        Service_A[常駐見守りサービス]
        Screen_A[画面点灯検知]
        Composite_A[ハイブリッド通信層]
    end

    subgraph 端末B [ピアB - 家族/親類]
        UI_B[Jetpack Compose UI]
        Service_B[常駐見守りサービス]
        Screen_B[画面点灯検知]
        Composite_B[ハイブリッド通信層]
    end

    Screen_A -->|画面ON検知| Service_A
    UI_A -->|「元気」「良くない」押下| Service_A
    Service_A --> Composite_A
    Composite_A <-->|インターネット: SkyWay WebRTC DataChannel| Composite_B
    Composite_A <-->|同一LAN: Direct UDP| Composite_B
    Composite_B --> Service_B
    Service_B -->|通知発出 & 状態更新| UI_B
    Service_B -->|24時間未受信アラート| UI_B
```

---

## 🚀 使い方・動作確認手順

### 必要な環境
- Android 6.0（API 23）以上のAndroidスマートフォン 2台以上
- インターネット接続（Wi-Fi または 4G/5Gモバイル回線）

### セットアップ手順
1. 本アプリを2台のスマートフォン（端末A、端末B）にインストールして起動します。
2. アプリ右上の **設定 (⚙️)** を開きます。
   - **「SkyWay インターネット接続」** がONになっていることを確認します。
   - **「見守りグループ名 (Room名)」** に2台共通の任意の合言葉（例: `tanaka-family-room`）を入力して保存します。
3. 端末Aの画面を一度消灯し、再度点灯させると、端末B側の最終検知時刻がインターネット経由で自動更新されます。
4. **「😄 元気です」** または **「😣 良くない」** ボタンをタップすると、相手端末へ即座にプッシュ通知が表示されます。
5. 設定（⚙️）から無反応検知タイムアウトを **「1分」** に設定し、1分間画面を点灯させずに放置すると、**「⚠️ 安否警告」通知** が鳴動することを確認できます。

---

## 🗺 ロードマップ

- [x] **フェーズ1: 同一LAN内 P2P (UDP)**
  - 画面点灯ブロードキャスト検知 & 常駐フォアグラウンドサービス
  - 1:n UDPパケット同報送信
  - 24時間無操作・未受信Watchdogアラート
  - 大型ステータス通知ボタン
  - Android 6.0+ サポート
- [x] **フェーズ2: インターネット対応 (SkyWay WebRTC)**
  - SkyWay SDK v2.2.0 (P2PRoom & DataStream) 統合
  - JWT Auth Token 自動生成機構
  - 4G/5G回線 & 異ネットワーク間でのP2P NAT越え
  - LAN UDP & SkyWay ハイブリッド通信
- [ ] **フェーズ3: QRコードによるワンタッチペアリング**
  - カメラ読み取りによるグループRoom名・キーの共有

---

## 📄 ライセンス

```text
Copyright 2026 amekusa03

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
