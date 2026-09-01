# 📱 iTantra — Android Air-Gapped Neural Speech Transceiver
### ISRO SIH26173 | 100% Offline 0% Internet Device-to-Device Mesh Radio

[![Live Web Demo](https://img.shields.io/badge/Live%20Web%20App-itantra--isro.vercel.app-0052CC?style=for-the-badge&logo=vercel)](https://itantra-isro.vercel.app)
[![Web Repo](https://img.shields.io/badge/Web%20Repo-DakshG26%2Fitantra--web-172B4D?style=for-the-badge&logo=github)](https://github.com/DakshG26/itantra-web)
[![Download APK](https://img.shields.io/badge/Download%20APK-v1.0.0-00875A?style=for-the-badge&logo=android)](https://github.com/DakshG26/itantra-android/releases)

---

## 🌐 Quick Links
- **🔴 Live Web Application:** [https://itantra-isro.vercel.app](https://itantra-isro.vercel.app)
- **💻 Web & Backend Repository:** [https://github.com/DakshG26/itantra-web](https://github.com/DakshG26/itantra-web)
- **⚡ Download Latest Android APK:** [iTantra Releases](https://github.com/DakshG26/itantra-android/releases)

---

## 🛰️ Overview
**iTantra Android** is a native Android application engineered for zero-internet, air-gapped field operations (such as disaster zones, remote military frontiers, and space base communications).

It compresses spoken voice into **18-byte quantized neural token packets** (2,666× compression) and transmits them across phones using **direct radio waves (Bluetooth RFCOMM and Local Wi-Fi/Hotspot P2P)**.

---

## ✨ Features
1. **🔢 3-Digit Radio Channel System:**
   - No IP addresses required. Phone 1 displays its channel (e.g. `#001`), and Phone 2 connects by typing `001`.
2. **📡 Zero-Touch UDP Auto-Discovery:**
   - Phones on the same hotspot/Wi-Fi automatically detect each other and present a 1-tap connect button.
3. **🔵 Bluetooth RFCOMM Radio Scanner:**
   - 100% wireless wave connection without routers or Wi-Fi.
4. **🗣️ On-Device Indic Speech & Neural TTS:**
   - Supports Hindi, Tamil, Marathi, Telugu, Bengali, and English voice synthesis entirely offline.
5. **🚨 Level 0 Tactical Emergency SOS:**
   - Instant 18-byte broadcast with priority vibration alerting all listening nodes.

---

## 🚀 How to Connect 2 Phones (0% Internet)

```
┌─────────────────────────────────┐                 ┌─────────────────────────────────┐
│             PHONE 1             │                 │             PHONE 2             │
├─────────────────────────────────┤                 ├─────────────────────────────────┤
│ 1. Turn ON Hotspot (Data: OFF)  │                 │ 1. Connect Wi-Fi to Phone 1     │
│ 2. Top Card: YOUR CHANNEL: #001 │                 │ 2. Type "001" ➔ Tap [Link #]    │
│ 3. Speak into Mic / Send Msg    │ ── 18-Byte ───► │ 3. Speaks voice out loud!       │
└─────────────────────────────────┘                 └─────────────────────────────────┘
```

---

## 🛠️ Building From Source

```bash
# Clone the repository
git clone git@github.com:DakshG26/itantra-android.git
cd itantra-android

# Build Debug APK
./gradlew assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk
```
