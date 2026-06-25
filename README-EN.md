<div align="center">

<img src="docs/icon.jpg" width="160" height="160" style="border-radius: 32px; box-shadow: 0 12px 40px rgba(0,0,0,0.25);" alt="MotoBuds"/>

# 🎧 MotoBuds for Hyper

### Bring Motorola into HyperOS

[![Platform](https://img.shields.io/badge/Platform-Android%2015+-green?style=for-the-badge&logo=android)](https://android.com)
[![Framework](https://img.shields.io/badge/Framework-LSPosed-blueviolet?style=for-the-badge&logo=github)](https://github.com/LSPosed/LSPosed)
[![ROM](https://img.shields.io/badge/ROM-HyperOS%203-orange?style=for-the-badge&logo=xiaomi)](https://hyperos.mi.com)
[![License](https://img.shields.io/badge/License-GPL--3.0-red?style=for-the-badge)](LICENSE)
[![Version](https://img.shields.io/badge/Version-1.2.0-blue?style=for-the-badge)](https://github.com/pubglite55/motobuds-for-hyper/releases)

<br/>

[English](README-EN.md) | [简体中文](README.md) | [日本語](README-jp.md)

<br/>

*An Xposed module that lets your Moto Buds feel at home in the Xiaomi ecosystem 🐟*

</div>

---

## 🤔 Why MotoBuds for Hyper?

You spent good money on Moto Buds, only to find they're treated like a "foreigner" on HyperOS —

- 😢 No Super Island battery display
- 😢 No Fusion Device Center control
- 😢 Blank notification bar
- 😢 Can't control ANC in system settings

**MotoBuds for Hyper** bridges this gap, giving your Moto Buds the full Xiaomi ecosystem experience! 🎉

---

## 🎯 Core Features at a Glance

<table>
<tr>
<td width="50%">

### 🔇 ANC Control
One-tap switching between **Off** / **Noise Cancellation** / **Adaptive** / **Transparency** — as smooth as native Xiaomi earbuds

### 🎮 Game Mode
Low-latency audio mode with **auto-enable on connect** — no more lag in games

### 🔋 Battery Display
Real-time left earbud, right earbud, and charging case battery — Super Island syncs too

</td>
<td width="50%">

### 🎛️ EQ Presets
5 preset modes to choose from:
- Authentic
- Bright Treble
- Bass Enhancement
- Vocal Enhancement
- Manual Tuning

### 🏝️ Super Island
Supports HyperOS 3 official Super Island or module built-in island

### 📱 Fusion Device Center
Direct earphone control in system settings with **multi-device one-tap handoff**

</td>
</tr>
</table>

---

## 🚀 Quick Start (4 Easy Steps)

```
1️⃣  Install the APK
2️⃣  Enable module in LSPosed → Select recommended scopes
3️⃣  Restart scope (or reboot phone)
4️⃣  Connect your Moto Buds via Bluetooth — You're all set!
```

<details>
<summary>📱 Recommended Scopes (click to view)</summary>

| Scope | Description |
|-------|-------------|
| `com.android.bluetooth` | Bluetooth service (core) |
| `com.milink.service` | MiLink service (Fusion Device Center) |
| `com.xiaomi.bluetooth` | Xiaomi Bluetooth (notifications/Super Island) |

</details>

<details>
<summary>⚠️ FAQ</summary>

**Q: Why does it revert to Adaptive mode when switching ANC?**

A: Please create a personal adaptation in the official `com.motorola.motobuds` app first, then use the module.

**Q: "Module service timeout" after installation?**

A: Make sure the module is enabled in LSPosed with recommended scopes, then restart the scope or reboot.

**Q: Battery display is inaccurate?**

A: Ensure stable Bluetooth connection. The module auto-syncs status every 15 seconds.

</details>

---

## 🛠️ Technical Architecture

```
┌─────────────────────────────────────────────────────┐
│                  MotoBuds for Hyper                  │
├─────────────┬─────────────────┬─────────────────────┤
│  RFCOMM SPP │   Xposed Hook   │    Compose UI       │
│ (Bluetooth) │   (System)      │   (Interface)       │
├─────────────┼─────────────────┼─────────────────────┤
│  UUID:       │   HookEntry:    │   Miuix:            │
│  fc9d9fe0-   │   com.android   │   HyperOS-style     │
│  4899-11ee   │   .bluetooth    │   Compose UI        │
│  -be56-...   │   com.xiaomi    │                     │
│              │   .bluetooth    │                     │
├─────────────┼─────────────────┼─────────────────────┤
│  Protocol:   │   Scopes:       │   Languages:        │
│  MotoBuds    │   Bluetooth/    │   EN/ZH/JA          │
│  SPP Custom  │   MiLink/Xiaomi │                     │
└─────────────┴─────────────────┴─────────────────────┘
```

---

## 📋 Supported Devices

| Device | Model | Codename | Status |
|--------|-------|----------|:------:|
| Moto Buds | XT2443-1 | guitar | ✅ |

---

## 📦 Feature Details

| Feature | Description | Status |
|---------|-------------|:------:|
| 🔇 ANC Control | Off/NC/Adaptive/Transparency | ✅ |
| 🎮 Game Mode | Low-latency audio + auto-enable | ✅ |
| 🔋 Battery Display | Left/Right/Case real-time sync | ✅ |
| 🎛️ EQ Presets | 5 preset modes available | ✅ |
| 🏝️ Super Island | System-level battery island | ✅ |
| 📱 Fusion Device Center | System settings integration | ✅ |
| 🔄 Device Handoff | Multi-device one-tap switch | ✅ |
| 📢 Notification | Notification bar quick control | ✅ |
| 🎯 Quick Popup | Notification/control center popup | ✅ |
| ⚙️ System Settings | Control in Bluetooth settings | ✅ |
| 🌐 Multi-language | Chinese/English/Japanese | ✅ |
| 🔋 Low Battery Alert | Notify when battery below 20% | ✅ |
| 🔄 Auto Reconnect | Auto-reconnect when earphone disconnects | ✅ |
| 📖 First-time Guide | Feature introduction on first launch | ✅ |

> **💡 Tip:** When switching ANC mode, if it reverts to Adaptive mode, please create a personal adaptation in the official `com.motorola.motobuds` app first.

---

## 📸 Screenshots

<table>
<tr>
<td align="center"><img src="docs/微信图片_20260625080850_72_9.jpg" width="270" style="border-radius: 12px;" alt="Earphones Page"/></td>
<td align="center"><img src="docs/微信图片_20260625083102_77_9.jpg" width="270" style="border-radius: 12px;" alt="Module Page"/></td>
</tr>
<tr>
<td align="center">🎧 Earphones Page</td>
<td align="center">🏠 Module Page</td>
</tr>
<tr>
<td align="center"><img src="docs/微信图片_20260625080853_74_9.jpg" width="270" style="border-radius: 12px;" alt="milink Emulated Device"/></td>
<td align="center"><img src="docs/微信图片_20260625080855_75_9.jpg" width="270" style="border-radius: 12px;" alt="Quick Popup"/></td>
</tr>
<tr>
<td align="center">📱 milink Emulated Device</td>
<td align="center">⚡ Quick Popup</td>
</tr>
</table>

---

## 📁 Project Structure

```
MotoBuds/
├── app/
│   └── src/main/
│       ├── java/moe/chenxy/oppopods/
│       │   ├── config/          # Configuration
│       │   ├── hook/            # Xposed Hook
│       │   │   ├── milink/      # MiLink Service Hook
│       │   │   └── ...
│       │   ├── pods/            # Earphone Protocol
│       │   │   ├── Packets.kt   # Protocol Packets
│       │   │   ├── RfcommController.kt  # RFCOMM Controller
│       │   │   └── ...
│       │   ├── ui/              # Compose UI
│       │   │   ├── components/  # UI Components
│       │   │   ├── dialogs/     # Dialogs
│       │   │   └── pages/       # Pages
│       │   └── utils/           # Utilities
│       ├── res/                 # Resources
│       └── assets/
│           └── xposed_init      # Xposed Entry
├── docs/                        # Screenshots
├── README.md                    # Chinese README
├── README-EN.md                 # This file
└── README-jp.md                 # Japanese README
```

---

## 🤝 Credits

This project stands on the shoulders of giants:

| Project | Author | Contribution |
|---------|--------|--------------|
| [OppoPods-Enhanced](https://github.com/1812z/OppoPods) | 1812z | Original framework |
| [HyperPods](https://github.com/Art-Chen/HyperPods) | Art_Chen | Original project |
| [Miuix](https://github.com/YuKongA/miuix) | YuKongA | HyperOS UI components |

---

## 📜 License

This project is licensed under the **GPL-3.0** open source license.

```
Copyright (C) 2026 xiuxiu391

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.
```

---

<div align="center">

**If this project helps you, please give it a ⭐ Star!**

<br/>

<img src="docs/icon.jpg" width="80" height="80" style="border-radius: 16px; box-shadow: 0 4px 16px rgba(0,0,0,0.2);" alt="MotoBuds"/>

<br/>

*Made with ❤️ for the Moto Buds community*

*Thanks for every Star and Issue 🙏*

</div>
