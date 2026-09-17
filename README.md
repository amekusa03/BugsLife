# BugsLife - Ultra-low Power Remote Safety Watcher System

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](LICENSE)
[![Platform](https://img.shields.io/badge/Platform-Android_6.0%2B-green.svg)](https://android.com)
[![Firebase](https://img.shields.io/badge/Firebase-Cloud_Firestore-orange.svg)](https://firebase.google.com/)

**BugsLife** is a mutual safety monitoring Android application designed to watch over elderly family members living alone or remotely while preserving privacy and minimizing battery & data consumption.

Designed specifically with senior users in mind: **zero complex UI interactions**, operating completely autonomously in the background.

For technical deep-dive, architecture, and data schemas, please refer to [System Specification (docs/SPECIFICATION.md)](docs/SPECIFICATION.md).

---

## ✨ Key Features

- 🔋 **Ultra-Low Battery & Data Usage**: No persistent connections; hourly differential sync uses only a few MBs per month.
- 🔕 **Zero Effort for Parents**: Parents simply use their smartphone normally. Screen unlock events are automatically logged and synchronized.
- 🔄 **Auto-Recovery on Device Reboot**: Resumes background monitoring automatically after reboot or dead battery recovery without launching the app manually.
- 🚨 **Dual-Layer Anomaly Detection**:
  - **Human Inactivity**: Alerts family with loud alarm sound and notifications when no device activity is detected for 24 hours.
  - **Device Disconnection**: Watchdog warns when regular sync is lost (battery dead, broken device, or no signal).
- 🔐 **Mutual Approval Security**: Prevents unauthorized viewing even if someone knows the group keyword, requiring existing members' explicit approval.

---

## 📱 Quick Setup Guide

1. Place your `google-services.json` in `app/` and install the APK.
2. Launch the app and set:
   - **Your Name**: (e.g. "Mom", "John")
   - **Group Name**: Shared family keyword (e.g. `yamada-family`)
3. **Approval**:
   - The first member is automatically registered.
   - Subsequent members will trigger an approval popup on existing members' devices. Tap **"Approve"** to start mutual monitoring.

---

## 📖 Technical Documentation
- [System Specification (docs/SPECIFICATION.md)](docs/SPECIFICATION.md)
