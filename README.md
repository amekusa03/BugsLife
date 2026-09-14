# BugsLife - P2P Decentralized Mutual Watching App

[![Android](https://img.shields.io/badge/Platform-Android%206.0%2B%20(API%2023%2B)-green.svg)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.2.10-blue.svg)](https://kotlinlang.org)
[![SkyWay](https://img.shields.io/badge/WebRTC-SkyWay%20SDK%202.9.0-orange.svg)](https://skyway.ntt.com)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-purple.svg)](https://developer.android.com/jetpack/compose)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)

[**日本語ドキュメント (Japanese)**](./README.ja.md)

**BugsLife** is a decentralized, peer-to-peer (P2P) mutual safety-watching Android application that allows families, relatives, and close friends to effortlessly keep track of each other's well-being without relying on central surveillance servers.

It connects devices seamlessly via **NTT Communications SkyWay (WebRTC DataChannel)** simply by setting a shared group/room name.

It features an **Equal Mutual Watching Model** and an **Hourly XX:05 Sync Window Model (~7-8MB/month)** designed to minimize mobile data usage for senior-oriented low-capacity SIM plans (0.5GB - 3GB) and maximize battery efficiency.

---

## 💡 Philosophy & Policy

### 🤝 Equal, Peer-to-Peer Mutual Watching
- **Eliminating "Watcher vs Watched" Hierarchies**: Unlike traditional surveillance monitoring apps that enforce an asymmetrical relationship (e.g., watcher vs. watched), BugsLife is designed on the principle of **equal peers caring for each other's safety and well-being**.
- **Minimizing Psychological Burden**: Removes the uncomfortable feeling of being constantly monitored, allowing loved ones to naturally stay informed of each other's safety through everyday phone use (unlocking the screen).
- **Gentle, Non-Urgent Check-in**: "Not Well" status is not meant to replace emergency services (911/119), but rather to gently share physical conditions or requests for a hospital visit, which naturally allows comfortable hourly sync intervals without rush.

---

## 🔒 Security & Privacy & Safety

1. **Completely Serverless & Zero Data Storage (Direct P2P)**:
   - Operates entirely on direct P2P connections; no user data, messages, or activity histories are stored on central cloud servers.
   - Even in the unlikely event of external security incidents, personal safety records cannot be leaked as they simply do not exist on any server.
2. **Minimal Data Transmission (User Status Only)**:
   - Data sent is strictly limited to non-intrusive status indicators (such as daily unlock counts and "Fine / Unwell" signals).
   - No GPS locations, camera feeds, voice recordings, or device activity logs are ever accessed or transmitted.
3. **Eliminating False Triggers from Incoming Calls/Notifications (`ACTION_USER_PRESENT`)**:
   - Only counts explicit user unlock actions (`ACTION_USER_PRESENT`), preventing screen wakeups from incoming phone calls, spam emails, or notifications from mistakenly counting as user activity.
4. **Ultra-Low Data Usage via Hourly XX:05 Sync (~7-8MB/Month)**:
   - Local phone unlocks are recorded locally with zero network usage. All devices briefly connect to SkyWay at 5 minutes past every hour (XX:05) for ~90 seconds to exchange status packets and immediately disconnect.
   - Perfect for low-data mobile plans (0.5GB - 3GB/month), consuming only ~1.5% of the total monthly allowance.

---

## 🌟 Key Features

1. **Hourly XX:05 Sync Window (SkyWay WebRTC DataChannel)**:
   - Synchronizes devices at XX:05 every hour (10:05, 11:05...), exchanging the latest status and immediately returning to deep sleep.
   - Automatically connects via shared room name without configuring IP addresses.

2. **Accurate User Activity Tracking (Screen Unlock)**:
   - Detects when the user explicitly unlocks the screen (`ACTION_USER_PRESENT`) and increments the local count.
   - Summarized as "Activity: X times today" during the XX:05 sync.

3. **24-Hour Inactivity Watchdog Alert**:
   - Continuously tracks the last-seen active timestamp of all connected peers.
   - If no activity signal is received for **24 hours** (configurable from 1 min to 24 hours for testing), a high-priority alarm notification (sound, vibration, heads-up) is triggered.

4. **One-Tap Quick Status Buttons & Manual Sync**:
   - Senior-friendly, large tactile buttons:
     - **😄 Feeling Good (元気です)**: Informs everyone that you are doing great.
     - **😣 Not Well (良くない)**: Promptly alerts all connected peers.
   - **"Sync Now"** button for immediate on-demand synchronization anytime.

5. **Mutual Watching Unified Interface**:
   - Single unified screen: No complex role selection needed.
   - Displays current sync state (Sleep / Syncing), Next Sync Time (e.g., 10:05), and Room Name.
   - Real-time elapsed time counters (e.g. "15 minutes ago", "23 hours ago") for every peer.
   - Real-time communication logs inspection sheet.

6. **Broad Device Compatibility**:
   - Supports **Android 6.0 (API 23, Marshmallow) up to Android 15+**.
   - Stable background operation via persistent Foreground Service.

---

## 🌐 About SkyWay & Getting API Keys

BugsLife uses **SkyWay**, an enterprise-grade WebRTC communication platform provided by NTT Communications, for secure end-to-end P2P DataChannel networking.

### Why SkyWay?
- Direct, encrypted peer-to-peer communication without storing private data on third-party servers.
- Automatic STUN/TURN NAT and firewall traversal across 4G/5G mobile carriers and home Wi-Fi networks.
- **Free tier available for developers and personal use.**

### How to Get Your SkyWay API Keys

1. Go to the [SkyWay Console](https://console.skyway.ntt.com/) and create a free account or log in.
2. In the dashboard menu, navigate to **"Applications"** and click **"Create Application"**.
3. Enter an application name (e.g., `BugsLife`) and submit.
4. From the application details page, copy the following two credentials:
   - **Application ID (App ID)**: `xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx` (UUID format)
   - **Secret Key**: `xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx=` (Base64 string)
5. On all watching devices, open BugsLife, tap **Settings (⚙️)** in the top right corner, and enter the **App ID** and **Secret Key**.

> [!TIP]
> Ensure all family/group members configure the **same App ID, Secret Key, and Room Name** to automatically connect with each other.

---

## 🛠 Architecture & Tech Stack

- **UI**: Jetpack Compose (Material Design 3)
- **Language**: Kotlin + Coroutines / StateFlow
- **Service**: Android Foreground Service (`WatcherForegroundService`)
- **Networking**:
  - **SkyWay WebRTC**: `SkyWayPeerMessenger` (P2PRoom & DataStream)

```mermaid
graph LR
    subgraph Device A [Peer A - Family/Relative]
        UI_A[Jetpack Compose UI]
        Service_A[WatcherForegroundService]
        Screen_A[Screen Unlock Activity Receiver]
        SkyWay_A[SkyWay WebRTC DataChannel]
    end

    subgraph Device B [Peer B - Family/Relative]
        UI_B[Jetpack Compose UI]
        Service_B[WatcherForegroundService]
        Screen_B[Screen Unlock Activity Receiver]
        SkyWay_B[SkyWay WebRTC DataChannel]
    end

    Screen_A -->|Record Screen Unlock| Service_A
    Service_A -->|Auto-connect at XX:05| SkyWay_A
    UI_A -->|Tap 'Fine' / 'Unwell' / 'Sync'| Service_A
    SkyWay_A <-->|XX:05 Sync Window (SkyWay Room)| SkyWay_B
    SkyWay_B --> Service_B
    Service_B -->|Push Notification & Status Update| UI_B
    Service_B -->|24h Inactivity Watchdog| UI_B
```

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug or newer / JDK 17+
- Android Device or Emulator running Android 6.0+ (API 23+)
- Internet connection (Wi-Fi or Mobile Data)
- SkyWay Account (Free) App ID and Secret Key

### Installation & Run

1. Clone this repository:
   ```bash
   git clone https://github.com/amekusa03/BugsLife.git
   cd BugsLife
   ```

2. Build and run debug APK:
   ```bash
   ./gradlew assembleDebug
   ```

---

## 📖 How to Use

1. Launch **BugsLife** on two or more Android devices.
2. Open **Settings (⚙️)**:
   - Ensure **SkyWay Connection** is enabled.
   - Enter your **SkyWay App ID** and **Secret Key**.
   - Set the same **Room Name** (e.g. `tanaka-family-room`) on all devices.
3. Tap **"Sync Now"** or **"😄 元気です"** on Device A: Device B will automatically discover Device A and display the status!
4. In daily use, simply unlocking the phone will record activity, and status updates are sent automatically at **XX:05** every hour.
5. Set the timeout to **"1 minute"** in Settings (⚙️) to test the **"⚠️ Inactivity Alarm Notification"** after 1 minute of inactivity.

---

## 🗺 Development Status

- [x] **Persistent Watching Core System**
  - Explicit Screen Unlock detection (`ACTION_USER_PRESENT` - False alarm prevention)
  - Hourly XX:05 sync scheduling (~7-8MB/month ultra-low data)
  - 24-hour inactivity watchdog & alarm notification alert
  - Senior-friendly large quick status buttons & manual sync
  - Android 6.0 (API 23) to Android 15+ full compatibility
- [x] **Internet P2P Communication (SkyWay WebRTC)**
  - SkyWay SDK v2.9.0 integration (P2PRoom & DataStream)
  - Automatic Auth Token JWT generation
  - STUN/TURN NAT traversal across 4G/5G and separate Wi-Fi networks
  - Room name based automatic discovery & messaging
  - Real-time communication logs inspection sheet

---

## 📄 License

```text
Copyright 2026 amekusa03

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
